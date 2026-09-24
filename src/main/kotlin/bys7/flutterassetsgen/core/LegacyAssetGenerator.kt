package bys7.flutterassetsgen.core

/**
 * Flat, FlutterAssetsGenerator 2.4.2-style generator: every asset becomes a single top-level
 * constant, e.g. `Assets.imagesLogo`. Naming itself lives in [AssetNameConverter], operating on
 * each asset's [GeneratableAsset.namingPath] (not its real, printed [GeneratableAsset.assetPath]);
 * this object only assembles duplicate-safe entries (within one flat namespace) and renders Dart
 * source.
 */
object LegacyAssetGenerator {

    private data class Entry(val identifier: String, val assetPath: String, val fieldType: FieldType)

    fun generate(
        assets: List<GeneratableAsset>,
        className: String,
        config: GenerationConfig
    ): GenerationOutput {
        val usedNames = HashMap<String, Int>()
        val renamedDuplicates = mutableListOf<String>()
        val entries = mutableListOf<Entry>()

        // assets is already sorted by path, so output order is deterministic.
        for (asset in assets) {
            val baseIdentifier = AssetNameConverter.legacyIdentifier(
                asset.namingPath, config.namingStyle, config.includeParentDirectory
            )
            val identifier = disambiguate(baseIdentifier, usedNames, config.namingStyle)
            if (identifier != baseIdentifier) {
                renamedDuplicates += "${asset.assetPath} -> $identifier (name collision with another asset)"
            }
            entries += Entry(
                identifier = identifier,
                assetPath = asset.assetPath,
                fieldType = resolveFieldType(asset.fileName, config.leafType)
            )
        }

        return GenerationOutput(
            assetCount = entries.size,
            renamedDuplicates = renamedDuplicates,
            dartSource = render(className, entries, config.leafType)
        )
    }

    private fun disambiguate(baseIdentifier: String, usedNames: MutableMap<String, Int>, style: NamingStyle): String {
        val seenCount = usedNames[baseIdentifier]
        if (seenCount == null) {
            usedNames[baseIdentifier] = 1
            return baseIdentifier
        }
        val separator = if (style == NamingStyle.SNAKE_CASE) "_" else ""
        var attempt = seenCount + 1
        var candidate = "$baseIdentifier$separator$attempt"
        while (usedNames.containsKey(candidate)) {
            attempt += 1
            candidate = "$baseIdentifier$separator$attempt"
        }
        usedNames[baseIdentifier] = attempt
        usedNames[candidate] = 1
        return candidate
    }

    private fun render(className: String, entries: List<Entry>, leafType: LeafType): String {
        val sb = StringBuilder()
        sb.appendLine(GENERATED_HEADER)
        sb.appendLine()
        if (leafType == LeafType.CLASS) {
            sb.appendLine(FLUTTER_WIDGETS_IMPORT)
            sb.appendLine()
            sb.appendLine(ASSET_GEN_IMAGE_CLASS)
            sb.appendLine()
        }
        sb.appendLine("class $className {")
        sb.appendLine("  $className._();")

        if (entries.isNotEmpty()) {
            sb.appendLine()
            for (entry in entries) {
                sb.appendLine("  ${fieldDeclaration(entry.identifier, entry.assetPath, entry.fieldType)}")
            }
        }

        sb.appendLine("}")
        sb.appendLine()
        return sb.toString()
    }
}
