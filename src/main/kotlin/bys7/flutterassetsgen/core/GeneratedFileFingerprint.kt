package bys7.flutterassetsgen.core

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Lets "Generate Flutter Assets" tell whether the generated file is already up to date without
 * relying on a byte-for-byte comparison.
 *
 * A plain text comparison breaks as soon as anything touches the file after we wrote it: `dart
 * format` / format-on-save, a trailing-newline change, or CRLF line endings from git on Windows.
 * Every such change made the old check think the file was stale, so it got rewritten each time.
 *
 * Instead, a SHA-256 of the generated source is stamped into the header as a `// Fingerprint:`
 * comment. Formatters leave comments alone, so the stamp survives, and an unchanged asset list
 * produces the same fingerprint.
 */
object GeneratedFileFingerprint {

    private const val LABEL = "// Fingerprint: "
    private val LINE_PATTERN = Regex("^// Fingerprint: ([0-9a-f]{64})\\s*$")

    /** How many leading lines are searched for the stamp - it always sits in the header comment block. */
    private const val HEADER_LINES = 12

    /** Returns [source] (normalized to `\n` line endings) with a fingerprint line added to its header. */
    fun stamp(source: String): String {
        val normalized = source.replace("\r\n", "\n")
        val line = LABEL + hash(normalized)
        val headerEnd = normalized.indexOf("\n\n")
        return if (headerEnd >= 0) {
            normalized.substring(0, headerEnd) + "\n" + line + normalized.substring(headerEnd)
        } else {
            normalized.trimEnd('\n') + "\n" + line + "\n"
        }
    }

    /** The fingerprint stamped into [source], or null if it has none. */
    fun extract(source: String): String? =
        source.lineSequence().take(HEADER_LINES).firstNotNullOfOrNull { LINE_PATTERN.find(it)?.groupValues?.get(1) }

    /** The fingerprint stamped into [file]'s header, or null if the file is missing, unreadable, or unstamped. */
    fun read(file: File): String? {
        if (!file.isFile) return null
        return try {
            file.bufferedReader().useLines { lines ->
                lines.take(HEADER_LINES).firstNotNullOfOrNull { LINE_PATTERN.find(it)?.groupValues?.get(1) }
            }
        } catch (ex: IOException) {
            null
        } catch (ex: SecurityException) {
            null
        }
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
