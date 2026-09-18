package bys7.flutterassetsgen.core

/**
 * Converts an asset's path (relative to the assets root) into a flat,
 * camelCase Dart identifier - reproducing FlutterAssetsGenerator 2.4.2's
 * "legacy" naming convention:
 *
 *   images/logo.png            -> imagesLogo
 *   images/home_screen.png     -> imagesHomeScreen
 *   icons/user-profile.svg     -> iconsUserProfile
 *   images/old photo.jpg       -> imagesOldPhoto
 *   data/app_config.json       -> dataAppConfig
 *   images/profile/user.png    -> imagesProfileUser
 *
 * i.e. every path segment (each subdirectory under the assets root, plus
 * the filename without its extension) is split into words and joined with
 * camelCase - NOT the newer hierarchical/nested API style
 * (`Assets.images.logo`).
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

    /**
     * @param assetRelativePath the asset's path relative to the assets root,
     *   using forward slashes, e.g. "images/profile/user.png"
     * @return a valid, flat camelCase Dart identifier, e.g. "imagesProfileUser"
     */
    fun toDartIdentifier(assetRelativePath: String): String {
        val lastSlash = assetRelativePath.lastIndexOf('/')
        val dirPart = if (lastSlash >= 0) assetRelativePath.substring(0, lastSlash) else ""
        val fileName = if (lastSlash >= 0) assetRelativePath.substring(lastSlash + 1) else assetRelativePath
        val fileNameWithoutExt = fileName.substringBeforeLast('.', fileName)

        val words = mutableListOf<String>()
        if (dirPart.isNotEmpty()) {
            dirPart.split('/').forEach { segment ->
                words += WORD_PATTERN.findAll(segment).map { it.value }
            }
        }
        words += WORD_PATTERN.findAll(fileNameWithoutExt).map { it.value }

        if (words.isEmpty()) {
            return "asset"
        }

        val sb = StringBuilder()
        words.forEachIndexed { index, word ->
            sb.append(if (index == 0) word.lowercase() else capitalize(word))
        }

        var identifier = sb.toString()
        if (identifier.isEmpty()) identifier = "asset"
        if (identifier.first().isDigit()) identifier = "a$identifier"
        if (identifier in DART_RESERVED_WORDS) identifier = "${identifier}Asset"
        return identifier
    }

    private fun capitalize(word: String): String {
        if (word.isEmpty()) return word
        val lower = word.lowercase()
        return lower.replaceFirstChar { it.uppercaseChar() }
    }
}
