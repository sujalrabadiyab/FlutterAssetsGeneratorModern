package bys7.flutterassetsgen.core

import java.util.IdentityHashMap

/**
 * FlutterAssetsGenerator 3.x-style hierarchical generator: each asset subdirectory becomes a
 * nested, typed group so callers get `Assets.images.logo` instead of `Assets.imagesLogo`.
 *
 * The directory tree (and therefore identifiers/nesting) is built from each asset's
 * [GeneratableAsset.namingPath], while the *printed* Dart string always uses the real
 * [GeneratableAsset.assetPath] - so declaring the project's whole `assets/` folder in
 * `pubspec.yaml` doesn't force every generated name to start with `Assets.assets...`, even
 * though the on-disk path genuinely does start with `assets/`.
 *
 * Shape of the output, for assets whose *naming* paths are `images/logo.png` and
 * `icons/social/facebook.png` (class name `Assets`, Leaf Type `String`):
 *
 * ```dart
 * class Assets {
 *   Assets._();
 *
 *   static const $AssetsImagesGen images = $AssetsImagesGen();
 *   static const $AssetsIconsGen icons = $AssetsIconsGen();
 * }
 *
 * class $AssetsImagesGen {
 *   const $AssetsImagesGen();
 *
 *   final String logo = 'assets/images/logo.png';
 * }
 *
 * class $AssetsIconsGen {
 *   const $AssetsIconsGen();
 *
 *   final $AssetsIconsSocialGen social = const $AssetsIconsSocialGen();
 * }
 *
 * class $AssetsIconsSocialGen {
 *   const $AssetsIconsSocialGen();
 *
 *   final String facebook = 'assets/icons/social/facebook.png';
 * }
 * ```
 *
 * Group class names follow the `$<ClassName><Folder>Gen` convention used by the wider
 * FlutterAssetsGenerator/flutter_gen ecosystem, built from the folder's own name (not its full
 * path). Since two differently-nested folders can share a name (two separate `common/` folders,
 * say) and would otherwise generate the same class name, every group class name still goes
 * through the same numeric-suffix collision resolution as field names, so the output always
 * compiles even in that rare case.
 *
 * Every `final` (non-static) field here sits on a class with a `const` constructor, so its
 * initializer must itself be a compile-time constant - unlike a `static const` field, Dart does
 * not infer that automatically for a plain `final` field, so [fieldDeclaration] and the
 * group-reference fields below add an explicit leading `const` wherever it's required.
 */
object HierarchicalAssetGenerator {

    /** One directory level of the naming tree, keyed by its full naming-path chain from the root. */
    private class DirNode(val pathSegments: List<String>) {
        val subDirs = LinkedHashMap<String, DirNode>()
        val files = mutableListOf<GeneratableAsset>()
    }

    fun generate(
        assets: List<GeneratableAsset>,
        className: String,
        config: GenerationConfig
    ): GenerationOutput {
        val root = DirNode(emptyList())
        for (asset in assets) {
            val segments = asset.namingPath.split('/')
            var node = root
            for (i in 0 until segments.size - 1) {
                val segment = segments[i]
                node = node.subDirs.getOrPut(segment) { DirNode(node.pathSegments + segment) }
            }
            node.files += asset
        }

        // Assign every group's class name up front (post-order, folder-name based, with a
        // numeric fallback on collision) so both "declare the field of this type" and "declare
        // the class itself" agree on the same name without re-deriving it twice.
        val classNameOf = IdentityHashMap<DirNode, String>()
        val usedClassNames = HashMap<String, Int>()
        fun assignClassNames(node: DirNode) {
            for (sub in node.subDirs.values) {
                assignClassNames(sub)
                val folderWords = AssetNameConverter.words(sub.pathSegments.last())
                val base = "\$$className${AssetNameConverter.joinPascal(folderWords).ifEmpty { "Group" }}Gen"
                classNameOf[sub] = disambiguateClassName(base, usedClassNames)
            }
        }
        assignClassNames(root)

        val renamedDuplicates = mutableListOf<String>()
        var leafCount = 0
        val onLeaf = { leafCount += 1 }

        val groupClasses = StringBuilder()
        fun renderGroupsPostOrder(node: DirNode) {
            for (sub in node.subDirs.values) {
                renderGroupsPostOrder(sub)
                groupClasses.append(
                    renderGroupClass(classNameOf.getValue(sub), sub, config, classNameOf, renamedDuplicates, onLeaf)
                )
                groupClasses.appendLine()
            }
        }
        renderGroupsPostOrder(root)

        val sb = StringBuilder()
        sb.appendLine(GENERATED_HEADER)
        sb.appendLine()
        if (config.leafType == LeafType.CLASS) {
            sb.appendLine(FLUTTER_WIDGETS_IMPORT)
            sb.appendLine()
            sb.appendLine(ASSET_GEN_IMAGE_CLASS)
            sb.appendLine()
        }
        sb.appendLine("class $className {")
        sb.appendLine("  $className._();")
        sb.append(renderMembers(root, config, classNameOf, renamedDuplicates, isRoot = true, onLeaf = onLeaf))
        sb.appendLine("}")
        sb.appendLine()
        sb.append(groupClasses)

        return GenerationOutput(
            assetCount = leafCount,
            renamedDuplicates = renamedDuplicates,
            dartSource = sb.toString()
        )
    }

    private fun renderGroupClass(
        groupClassName: String,
        node: DirNode,
        config: GenerationConfig,
        classNameOf: Map<DirNode, String>,
        renamedDuplicates: MutableList<String>,
        onLeaf: () -> Unit
    ): String {
        val sb = StringBuilder()
        sb.appendLine("class $groupClassName {")
        sb.appendLine("  const $groupClassName();")
        sb.append(renderMembers(node, config, classNameOf, renamedDuplicates, isRoot = false, onLeaf = onLeaf))
        sb.appendLine("}")
        return sb.toString()
    }

    /** Field declarations for [node]'s members (its subgroup fields + direct-file fields). */
    private fun renderMembers(
        node: DirNode,
        config: GenerationConfig,
        classNameOf: Map<DirNode, String>,
        renamedDuplicates: MutableList<String>,
        isRoot: Boolean,
        onLeaf: () -> Unit
    ): String {
        if (node.subDirs.isEmpty() && node.files.isEmpty()) return ""

        val usedFieldNames = HashMap<String, Int>()
        val prefix = if (isRoot) "static const" else "final"
        val constPrefix = if (isRoot) "" else "const "
        val sb = StringBuilder()
        sb.appendLine()

        for ((rawName, sub) in node.subDirs) {
            val base = AssetNameConverter.groupFieldIdentifier(rawName, config.namingStyle)
            val fieldName = disambiguateField(base, usedFieldNames, config.namingStyle, sub.pathSegments.joinToString("/"), renamedDuplicates)
            val groupClassName = classNameOf.getValue(sub)
            sb.appendLine("  $prefix $groupClassName $fieldName = $constPrefix$groupClassName();")
        }

        for (asset in node.files) {
            val fileNameWithoutExt = asset.fileName.substringBeforeLast('.', asset.fileName)
            val base = AssetNameConverter.leafIdentifier(fileNameWithoutExt, config.namingStyle)
            val fieldName = disambiguateField(base, usedFieldNames, config.namingStyle, asset.assetPath, renamedDuplicates)
            val fieldType = resolveFieldType(asset.fileName, config.leafType)
            sb.appendLine("  ${fieldDeclaration(fieldName, asset.assetPath, fieldType, prefix)}")
            onLeaf()
        }

        return sb.toString()
    }

    private fun disambiguateField(
        base: String,
        usedNames: MutableMap<String, Int>,
        style: NamingStyle,
        sourcePath: String,
        renamedDuplicates: MutableList<String>
    ): String {
        val seenCount = usedNames[base]
        if (seenCount == null) {
            usedNames[base] = 1
            return base
        }
        val separator = if (style == NamingStyle.SNAKE_CASE) "_" else ""
        var attempt = seenCount + 1
        var candidate = "$base$separator$attempt"
        while (usedNames.containsKey(candidate)) {
            attempt += 1
            candidate = "$base$separator$attempt"
        }
        usedNames[base] = attempt
        usedNames[candidate] = 1
        renamedDuplicates += "$sourcePath -> $candidate (name collision with a sibling)"
        return candidate
    }

    private fun disambiguateClassName(base: String, usedNames: MutableMap<String, Int>): String {
        val seenCount = usedNames[base]
        if (seenCount == null) {
            usedNames[base] = 1
            return base
        }
        var attempt = seenCount + 1
        var candidate = "$base$attempt"
        while (usedNames.containsKey(candidate)) {
            attempt += 1
            candidate = "$base$attempt"
        }
        usedNames[base] = attempt
        usedNames[candidate] = 1
        return candidate
    }
}
