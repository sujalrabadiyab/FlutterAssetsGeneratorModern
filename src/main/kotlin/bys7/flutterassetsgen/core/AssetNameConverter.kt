package bys7.flutterassetsgen.core

/** Casing applied when joining a name's constituent words into a Dart identifier fragment. */
enum class NamingStyle { CAMEL_CASE, SNAKE_CASE }

/**
 * All Dart-identifier naming rules live here, independent of generation style: splitting raw
 * file/folder names into words, casing them per [NamingStyle], and sanitizing the result into a
 * guaranteed-valid, non-reserved-word Dart identifier.
 *
 * [LegacyAssetGenerator] and [HierarchicalAssetGenerator] both build on top of this object but
 * own their own collision handling, since "uniqueness" means something different for a single
 * flat namespace vs. per-directory groups.
 */
object AssetNameConverter {

    /** Any run of ASCII letters/digits is a "word"; everything else (spaces, -, _, ., @, ...) is a separator. */
    private val WORD_PATTERN = Regex("[A-Za-z0-9]+")

    /** Dart reserved words that would make an otherwise-valid identifier a syntax error. */
    private val DART_RESERVED_WORDS = setOf(
        "assert", "break", "case", "catch", "class", "const", "continue", "default", "do",
        "else", "enum", "extends", "false", "final", "finally", "for", "if", "in", "is",
        "new", "null", "rethrow", "return", "super", "switch", "this", "throw", "true",
        "try", "var", "void", "while", "with",
        "as", "covariant", "deferred", "dynamic", "export", "extension", "external",
        "factory", "function", "get", "implements", "import", "interface", "library",
        "mixin", "operator", "part", "set", "static", "typedef", "late", "required",
        "yield", "async", "await", "show", "hide", "on"
    )

    /** Splits [text] into alphanumeric words, discarding separators (spaces, `-`, `_`, `.`, `@`, ...). */
    fun words(text: String): List<String> = WORD_PATTERN.findAll(text).map { it.value }.toList()

    /** Joins [words] into a single identifier fragment cased per [style]. Not yet sanitized. */
    fun join(words: List<String>, style: NamingStyle): String {
        if (words.isEmpty()) return "asset"
        return when (style) {
            NamingStyle.CAMEL_CASE -> buildString {
                words.forEachIndexed { index, word ->
                    append(if (index == 0) word.lowercase() else capitalizeWord(word))
                }
            }
            NamingStyle.SNAKE_CASE -> words.joinToString("_") { it.lowercase() }
        }
    }

    /** PascalCase join used for generated hierarchical group class names, e.g. ["icons","social"] -> "IconsSocial". */
    fun joinPascal(words: List<String>): String = words.joinToString("") { capitalizeWord(it) }

    fun capitalizeWord(word: String): String {
        if (word.isEmpty()) return word
        return word.lowercase().replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * Makes [identifier] a guaranteed-valid, non-reserved-word Dart identifier: prefixes a
     * leading digit with `_` (e.g. "123Logo" -> "_123Logo") and appends "Asset"/"_asset" if the
     * identifier as-is collides with a Dart keyword.
     */
    fun sanitize(identifier: String, style: NamingStyle): String {
        var id = identifier.ifEmpty { "asset" }
        if (id.first().isDigit()) id = "_$id"
        if (id in DART_RESERVED_WORDS) {
            id = if (style == NamingStyle.SNAKE_CASE) "${id}_asset" else "${id}Asset"
        }
        return id
    }

    /**
     * Legacy/flat identifier for an asset at [assetRelativePath] (e.g. "images/logo.png" ->
     * "imagesLogo"), reproducing FlutterAssetsGenerator 2.4.2's flat naming convention: only the
     * *immediate* parent directory (when [includeParentDirectory] is true) and the filename
     * without its extension are used - deeper ancestors are ignored, matching the original
     * tool's behavior so existing generated files/usages don't shift unexpectedly.
     */
    fun legacyIdentifier(assetRelativePath: String, style: NamingStyle, includeParentDirectory: Boolean): String {
        val lastSlash = assetRelativePath.lastIndexOf('/')
        val dirPart = if (lastSlash >= 0) assetRelativePath.substring(0, lastSlash) else ""
        val fileName = if (lastSlash >= 0) assetRelativePath.substring(lastSlash + 1) else assetRelativePath
        val fileNameWithoutExt = fileName.substringBeforeLast('.', fileName)

        val allWords = mutableListOf<String>()
        if (includeParentDirectory && dirPart.isNotEmpty()) {
            val lastSegment = dirPart.split('/').last()
            allWords += words(lastSegment)
        }
        allWords += words(fileNameWithoutExt)

        return sanitize(join(allWords, style), style)
    }

    /** Identifier for a single filename (no extension) - used for hierarchical leaf field names. */
    fun leafIdentifier(fileNameWithoutExt: String, style: NamingStyle): String =
        sanitize(join(words(fileNameWithoutExt), style), style)

    /** Identifier for a single raw directory segment name - used for hierarchical group field names. */
    fun groupFieldIdentifier(rawSegmentName: String, style: NamingStyle): String =
        sanitize(join(words(rawSegmentName), style), style)
}
