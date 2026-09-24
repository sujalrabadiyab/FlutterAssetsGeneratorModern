package bys7.flutterassetsgen.core

import java.io.File

/**
 * Reads and edits the `flutter: assets:` list in a Flutter project's `pubspec.yaml`, preserving
 * everything else in the file untouched (comments, key order, blank lines, other sections, and
 * the original line-ending style).
 *
 * Deliberately a light, line-based editor rather than a full YAML parser/serializer:
 * `pubspec.yaml` is meant to stay hand-editable, and round-tripping it through a generic YAML
 * library risks reformatting or reordering content the user never asked us to touch.
 *
 * Supported entry forms (this is all `flutter: assets:` itself supports):
 * ```yaml
 * flutter:
 *   assets:
 *     - assets/              # a directory - covers every file under it, recursively
 *     - assets/home/
 *     - assets/privacy.html  # a single file
 * ```
 */
object PubspecAssetsEditor {

    private val FLUTTER_KEY = Regex("^flutter:\\s*(#.*)?$")
    private val ASSETS_KEY = Regex("^(\\s*)assets:\\s*(#.*)?$")
    private val LIST_ITEM = Regex("^(\\s+)-\\s+(\\S.*)$")

    /** Returns the raw asset path strings declared under `flutter: assets:`, in file order. */
    fun readDeclaredPaths(pubspecFile: File): List<String> {
        val doc = readDocument(pubspecFile) ?: return emptyList()
        return parse(doc.lines).entries.map { it.path }
    }

    /**
     * Adds [assetPath] (already normalized: directories end with `/`, files don't) to the
     * `flutter: assets:` list, unless it's redundant.
     *
     * - Adding a path that's already declared verbatim is a no-op.
     * - Adding a *file* that's already covered by a declared *directory* is a no-op (that
     *   directory already covers it recursively) - folders are never skipped this way, since
     *   declaring `assets/home/` alongside an already-declared `assets/` is still meaningful
     *   (and expected - see "Add Folder and Subfolders").
     * - Adding a *directory* removes any individual *file* entries it now makes redundant, so
     *   `pubspec.yaml` doesn't end up with both `assets/` and `assets/privacy.html`.
     *
     * Creates the `flutter:`/`assets:` section if either is missing.
     *
     * @return true if the file was modified.
     */
    fun addPath(pubspecFile: File, assetPath: String): Boolean {
        val doc = readDocument(pubspecFile) ?: return false
        val block = parse(doc.lines)

        val alreadyPresent = block.entries.any { it.path == assetPath }
        val redundantFile = !assetPath.endsWith("/") &&
            isCoveredByExistingDirectory(assetPath, block.entries.map { it.path })
        if (alreadyPresent || redundantFile) return false

        val lines = doc.lines.toMutableList()
        val insertion = ensureAssetsSection(lines, block)
        lines.add(insertion.insertIndex, "${insertion.itemIndent}- $assetPath")

        // A newly-declared directory makes any previously-separate file entries under it redundant.
        if (assetPath.endsWith("/")) {
            val updated = parse(lines)
            val toRemove = updated.entries
                .filter { !it.path.endsWith("/") && isCoveredByDirectory(it.path, assetPath) }
                .sortedByDescending { it.lineIndex }
            for (entry in toRemove) lines.removeAt(entry.lineIndex)
        }

        writeDocument(pubspecFile, lines, doc.useCrlf, doc.trailingNewline)
        return true
    }

    /** Removes the entry matching [assetPath] exactly, if present. @return true if the file was modified. */
    fun removePath(pubspecFile: File, assetPath: String): Boolean {
        val doc = readDocument(pubspecFile) ?: return false
        val block = parse(doc.lines)
        val toRemove = block.entries.filter { it.path == assetPath }
        if (toRemove.isEmpty()) return false

        val lines = doc.lines.toMutableList()
        for (entry in toRemove.sortedByDescending { it.lineIndex }) lines.removeAt(entry.lineIndex)

        writeDocument(pubspecFile, lines, doc.useCrlf, doc.trailingNewline)
        return true
    }

    /** True if [candidatePath] (file or directory) is already covered by some *directory* entry in [declaredPaths]. */
    fun isCoveredByExistingDirectory(candidatePath: String, declaredPaths: List<String>): Boolean =
        declaredPaths.any { it.endsWith("/") && isCoveredByDirectory(candidatePath, it) }

    private fun isCoveredByDirectory(path: String, directoryPath: String): Boolean =
        path != directoryPath && path.startsWith(directoryPath)

    // ---- internals -------------------------------------------------------

    private data class DeclaredPath(val lineIndex: Int, val path: String)

    private data class Block(
        val flutterLineIndex: Int, // -1 if "flutter:" key not found
        val assetsLineIndex: Int,  // -1 if "assets:" key not found
        val assetsIndent: String,
        val entries: List<DeclaredPath>
    )

    private data class InsertionPoint(val itemIndent: String, val insertIndex: Int)

    private data class Document(val lines: List<String>, val useCrlf: Boolean, val trailingNewline: Boolean)

    private fun parse(lines: List<String>): Block {
        val flutterIndex = lines.indexOfFirst { FLUTTER_KEY.matches(it) }
        if (flutterIndex == -1) return Block(-1, -1, "", emptyList())

        var flutterBlockEnd = lines.size
        for (i in flutterIndex + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            if (!line[0].isWhitespace()) {
                flutterBlockEnd = i
                break
            }
        }

        var assetsIndex = -1
        var assetsIndent = ""
        for (i in flutterIndex + 1 until flutterBlockEnd) {
            val match = ASSETS_KEY.find(lines[i]) ?: continue
            assetsIndex = i
            assetsIndent = match.groupValues[1]
            break
        }
        if (assetsIndex == -1) return Block(flutterIndex, -1, "", emptyList())

        val entries = mutableListOf<DeclaredPath>()
        for (i in assetsIndex + 1 until flutterBlockEnd) {
            val line = lines[i]
            if (line.isBlank()) continue
            val indent = line.takeWhile { it == ' ' }
            if (indent.length <= assetsIndent.length) break // dedented out of the assets: block
            val match = LIST_ITEM.find(line) ?: continue
            val rawPath = match.groupValues[2].substringBefore(" #").trim().trim('"', '\'')
            if (rawPath.isNotEmpty()) entries += DeclaredPath(i, rawPath)
        }

        return Block(flutterIndex, assetsIndex, assetsIndent, entries)
    }

    /**
     * Ensures a `flutter:` key and an `assets:` sub-key exist in [lines] (creating them if
     * needed - mutating [lines] in place) and returns where a new `- path` entry should be
     * inserted: the end of the existing list, at the same indentation as its other items.
     */
    private fun ensureAssetsSection(lines: MutableList<String>, block: Block): InsertionPoint {
        var flutterIndex = block.flutterLineIndex
        var assetsIndex = block.assetsLineIndex
        var assetsIndent = block.assetsIndent

        if (flutterIndex == -1) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add("flutter:")
            flutterIndex = lines.size - 1
        }

        if (assetsIndex == -1) {
            assetsIndent = "  "
            lines.add(flutterIndex + 1, "${assetsIndent}assets:")
            assetsIndex = flutterIndex + 1
        }

        val itemIndent = block.entries.firstOrNull()
            ?.let { lines.getOrNull(it.lineIndex)?.takeWhile { c -> c == ' ' } }
            ?: (assetsIndent + "  ")

        var insertIndex = lines.size
        for (i in assetsIndex + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val indent = line.takeWhile { it == ' ' }
            if (indent.length <= assetsIndent.length) {
                insertIndex = i
                break
            }
        }

        return InsertionPoint(itemIndent, insertIndex)
    }

    private fun readDocument(file: File): Document? {
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val useCrlf = text.contains("\r\n")
        val trailingNewline = text.endsWith("\n")
        val normalized = text.replace("\r\n", "\n")
        val lines = normalized.split("\n").toMutableList()
        // The split of a trailing-newline-terminated string leaves one artificial empty element
        // representing "nothing after the last newline" - drop it so line indices refer only to
        // real content, and re-add exactly one trailing newline on write instead.
        if (trailingNewline && lines.isNotEmpty() && lines.last().isEmpty()) {
            lines.removeAt(lines.size - 1)
        }
        return Document(lines, useCrlf, trailingNewline)
    }

    private fun writeDocument(file: File, lines: List<String>, useCrlf: Boolean, trailingNewline: Boolean) {
        val newline = if (useCrlf) "\r\n" else "\n"
        val body = lines.joinToString(newline)
        file.writeText(if (trailingNewline) body + newline else body)
    }
}
