package bys7.flutterassetsgen.core

import java.io.File

/**
 * Adds font declarations to the `flutter: fonts:` section of a Flutter project's `pubspec.yaml`,
 * preserving everything else in the file untouched (comments, key order, blank lines, other
 * sections, and the original line-ending style). Like [PubspecAssetsEditor] this is a light,
 * line-based editor rather than a full YAML round-trip.
 *
 * Produces entries in the standard Flutter form:
 * ```yaml
 * flutter:
 *   fonts:
 *     - family: Gilroy-Light
 *       fonts:
 *         - asset: assets/fonts/Gilroy-Light.ttf
 * ```
 */
object PubspecFontsEditor {

    /** File extensions (lowercase, no dot) that Flutter accepts as font assets. */
    val FONT_EXTENSIONS: Set<String> = setOf("ttf", "otf", "ttc")

    fun isFontFile(file: File): Boolean = file.isFile && file.extension.lowercase() in FONT_EXTENSIONS

    /** One font to declare: [family] is the font family name, [asset] the project-relative path (forward slashes). */
    data class FontEntry(val family: String, val asset: String)

    data class Result(val added: Int, val skipped: Int) {
        val modified: Boolean get() = added > 0
    }

    /** Builds a [FontEntry] from a project-relative asset path; the family is the file name without its extension. */
    fun entryForAsset(relativeAssetPath: String): FontEntry =
        FontEntry(relativeAssetPath.substringAfterLast('/').substringBeforeLast('.'), relativeAssetPath)

    /** Returns every `asset:` path currently declared under `flutter: fonts:`. */
    fun readDeclaredAssets(pubspecFile: File): List<String> {
        val doc = readDocument(pubspecFile) ?: return emptyList()
        val section = parse(doc.lines).fontsSection ?: return emptyList()
        return section.families.flatMap { it.assets.map { a -> a.path } }
    }

    /**
     * Declares each of [entries] under `flutter: fonts:`, creating the `flutter:` / `fonts:`
     * sections if missing.
     *
     * - An asset that's already declared anywhere in `fonts:` is skipped.
     * - If the family name is already declared, the asset is appended to that family's `fonts:` list.
     * - Otherwise a new family block is appended at the end of the `fonts:` list.
     *
     * The file is written once, and only if something was added.
     */
    fun addFonts(pubspecFile: File, entries: List<FontEntry>): Result {
        val doc = readDocument(pubspecFile) ?: return Result(0, 0)
        val lines = doc.lines.toMutableList()
        var added = 0
        var skipped = 0

        for (entry in entries) {
            var parsed = parse(lines)
            val declared = parsed.fontsSection?.families?.flatMap { f -> f.assets.map { it.path } }?.toSet().orEmpty()
            if (entry.asset in declared) {
                skipped++
                continue
            }

            if (parsed.fontsSection == null) {
                ensureFontsSection(lines, parsed)
                parsed = parse(lines)
            }
            val section = parsed.fontsSection ?: continue
            val family = section.families.firstOrNull { it.name == entry.family }

            if (family != null) {
                val assetIndent = family.assets.firstOrNull()?.indent
                if (assetIndent != null) {
                    lines.add(family.lastContentLine + 1, "$assetIndent- asset: ${scalar(entry.asset)}")
                } else {
                    val inner = family.itemIndent + "  "
                    lines.addAll(
                        family.lastContentLine + 1,
                        listOf("${inner}fonts:", "$inner  - asset: ${scalar(entry.asset)}")
                    )
                }
            } else {
                val famIndent = section.familyIndent
                lines.addAll(
                    section.lastContentLine + 1,
                    listOf(
                        "$famIndent- family: ${scalar(entry.family)}",
                        "$famIndent  fonts:",
                        "$famIndent    - asset: ${scalar(entry.asset)}"
                    )
                )
            }
            added++
        }

        if (added > 0) writeDocument(pubspecFile, lines, doc.useCrlf, doc.trailingNewline)
        return Result(added, skipped)
    }

    // ---- internals -------------------------------------------------------

    private val FLUTTER_KEY = Regex("^flutter:\\s*(#.*)?$")
    private val FONTS_KEY = Regex("^(\\s+)fonts:\\s*(#.*)?$")
    private val FAMILY_ITEM = Regex("^(\\s*)-\\s+family:\\s*(\\S.*)$")
    private val ASSET_ITEM = Regex("^(\\s*)-\\s+asset:\\s*(\\S.*)$")
    private val NEEDS_QUOTES = Regex("^[\\s\\-?:,\\[\\]{}#&*!|>'\"%@`]|[:#]\\s|\\s$|:$")

    private data class AssetRef(val lineIndex: Int, val indent: String, val path: String)

    private data class Family(
        val name: String,
        val itemIndent: String,
        val lastContentLine: Int,
        val assets: List<AssetRef>
    )

    private data class FontsSection(
        val keyLine: Int,
        val familyIndent: String,
        val lastContentLine: Int,
        val families: List<Family>
    )

    private data class Parsed(
        val flutterLine: Int,        // -1 if "flutter:" not found
        val flutterLastContent: Int, // last content line of the flutter: block
        val childIndent: String,
        val fontsSection: FontsSection?
    )

    private data class Document(val lines: List<String>, val useCrlf: Boolean, val trailingNewline: Boolean)

    private fun isContent(line: String) = line.isNotBlank() && !line.trimStart().startsWith("#")

    private fun indentOf(line: String) = line.takeWhile { it == ' ' }

    /**
     * Exclusive end of the block that starts at [startIndex]: the first content line indented
     * at or below [parentIndent]. When [dashSameIndentBelongs] is set, a `- item` at exactly
     * [parentIndent] still belongs to the block (valid YAML: `fonts:` followed by `- family:`
     * at the same column).
     */
    private fun blockEnd(lines: List<String>, startIndex: Int, parentIndent: Int, dashSameIndentBelongs: Boolean = false): Int {
        for (i in startIndex + 1 until lines.size) {
            val line = lines[i]
            if (!isContent(line)) continue
            val indent = indentOf(line).length
            if (indent < parentIndent) return i
            if (indent == parentIndent && !(dashSameIndentBelongs && line.trimStart().startsWith("-"))) return i
        }
        return lines.size
    }

    private fun lastContentLine(lines: List<String>, startIndex: Int, endExclusive: Int): Int {
        for (i in endExclusive - 1 downTo startIndex + 1) if (isContent(lines[i])) return i
        return startIndex
    }

    private fun parse(lines: List<String>): Parsed {
        val flutterIndex = lines.indexOfFirst { FLUTTER_KEY.matches(it) }
        if (flutterIndex == -1) return Parsed(-1, -1, "  ", null)

        val flutterEnd = blockEnd(lines, flutterIndex, 0)
        val flutterLast = lastContentLine(lines, flutterIndex, flutterEnd)
        val childIndent = (flutterIndex + 1 until flutterEnd)
            .firstOrNull { isContent(lines[it]) }
            ?.let { indentOf(lines[it]) }
            ?: "  "

        var fontsIndex = -1
        for (i in flutterIndex + 1 until flutterEnd) {
            val m = FONTS_KEY.find(lines[i]) ?: continue
            if (m.groupValues[1].length == childIndent.length) {
                fontsIndex = i
                break
            }
        }
        if (fontsIndex == -1) return Parsed(flutterIndex, flutterLast, childIndent, null)

        val fontsEnd = minOf(blockEnd(lines, fontsIndex, childIndent.length, dashSameIndentBelongs = true), flutterEnd)
        val fontsLast = lastContentLine(lines, fontsIndex, fontsEnd)

        val familyStarts = (fontsIndex + 1 until fontsEnd).filter { FAMILY_ITEM.matches(lines[it]) }
        val familyIndent = familyStarts.firstOrNull()?.let { indentOf(lines[it]) } ?: (childIndent + "  ")
        val topLevelStarts = familyStarts.filter { indentOf(lines[it]) == familyIndent }

        val families = topLevelStarts.mapIndexed { n, start ->
            val end = topLevelStarts.getOrNull(n + 1) ?: fontsEnd
            val name = cleanScalar(FAMILY_ITEM.find(lines[start])!!.groupValues[2])
            val assets = (start + 1 until end).mapNotNull { i ->
                ASSET_ITEM.find(lines[i])?.let { AssetRef(i, it.groupValues[1], cleanScalar(it.groupValues[2])) }
            }
            Family(name, familyIndent, lastContentLine(lines, start, end), assets)
        }

        return Parsed(flutterIndex, flutterLast, childIndent, FontsSection(fontsIndex, familyIndent, fontsLast, families))
    }

    /** Ensures `flutter:` and its `fonts:` key exist (mutating [lines]). */
    private fun ensureFontsSection(lines: MutableList<String>, parsed: Parsed) {
        if (parsed.flutterLine == -1) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add("flutter:")
            lines.add("  fonts:")
        } else {
            lines.add(parsed.flutterLastContent + 1, "${parsed.childIndent}fonts:")
        }
    }

    private fun cleanScalar(raw: String): String =
        raw.substringBefore(" #").trim().trim('"', '\'')

    /** Renders [value] as a YAML scalar, double-quoting only when plain style would be unsafe. */
    private fun scalar(value: String): String =
        if (NEEDS_QUOTES.containsMatchIn(value)) "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" else value

    private fun readDocument(file: File): Document? {
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val useCrlf = text.contains("\r\n")
        val trailingNewline = text.endsWith("\n")
        val lines = text.replace("\r\n", "\n").split("\n").toMutableList()
        if (trailingNewline && lines.isNotEmpty() && lines.last().isEmpty()) lines.removeAt(lines.size - 1)
        return Document(lines, useCrlf, trailingNewline)
    }

    private fun writeDocument(file: File, lines: List<String>, useCrlf: Boolean, trailingNewline: Boolean) {
        val newline = if (useCrlf) "\r\n" else "\n"
        val body = lines.joinToString(newline)
        file.writeText(if (trailingNewline) body + newline else body)
    }
}
