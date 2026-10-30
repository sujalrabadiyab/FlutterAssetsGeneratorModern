package bys7.flutterassetsgen.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class GeneratedFileFingerprintTest {

    private val assets = listOf(GeneratableAsset("assets/images/logo.png", "images/logo.png"))

    private fun source(list: List<GeneratableAsset> = assets) =
        DartAssetsGenerator.generate(list, "Assets", GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL)).dartSource

    @Test
    fun `same assets give the same fingerprint`() {
        val a = GeneratedFileFingerprint.extract(GeneratedFileFingerprint.stamp(source()))
        val b = GeneratedFileFingerprint.extract(GeneratedFileFingerprint.stamp(source()))
        assertNotNull(a)
        assertEquals(a, b)
    }

    @Test
    fun `changed assets give a different fingerprint`() {
        val more = assets + GeneratableAsset("assets/images/new.png", "images/new.png")
        assertNotEquals(
            GeneratedFileFingerprint.extract(GeneratedFileFingerprint.stamp(source())),
            GeneratedFileFingerprint.extract(GeneratedFileFingerprint.stamp(source(more)))
        )
    }

    @Test
    fun `fingerprint survives reformatting and CRLF line endings`() {
        val stamped = GeneratedFileFingerprint.stamp(source())
        val reformatted = stamped.replace("\n", "\r\n").trimEnd() + "\r\n"
        assertEquals(GeneratedFileFingerprint.extract(stamped), GeneratedFileFingerprint.extract(reformatted))
    }
}
