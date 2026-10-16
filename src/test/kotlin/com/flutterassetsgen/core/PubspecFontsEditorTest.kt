package bys7.flutterassetsgen.core

import bys7.flutterassetsgen.core.PubspecFontsEditor.FontEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PubspecFontsEditorTest {

    private fun pubspec(dir: File, content: String): File {
        val file = File(dir, "pubspec.yaml")
        file.writeText(content.trimIndent() + "\n")
        return file
    }

    private val light = PubspecFontsEditor.entryForAsset("assets/fonts/Gilroy-Light.ttf")
    private val bold = PubspecFontsEditor.entryForAsset("assets/fonts/Gilroy-Bold.ttf")

    @Test
    fun `entryForAsset uses file name without extension as family`() {
        assertEquals(FontEntry("Gilroy-Light", "assets/fonts/Gilroy-Light.ttf"), light)
    }

    @Test
    fun `creates fonts section and preserves the rest`(@TempDir dir: File) {
        val file = pubspec(dir, """
            name: my_app
            flutter:
              uses-material-design: true
              assets:
                - assets/
        """)

        val result = PubspecFontsEditor.addFonts(file, listOf(light, bold))

        assertEquals(2, result.added)
        assertEquals(
            """
            name: my_app
            flutter:
              uses-material-design: true
              assets:
                - assets/
              fonts:
                - family: Gilroy-Light
                  fonts:
                    - asset: assets/fonts/Gilroy-Light.ttf
                - family: Gilroy-Bold
                  fonts:
                    - asset: assets/fonts/Gilroy-Bold.ttf
            """.trimIndent() + "\n",
            file.readText()
        )
    }

    @Test
    fun `appends to existing fonts section before the next top-level key`(@TempDir dir: File) {
        val file = pubspec(dir, """
            flutter:
              fonts:
                - family: Roboto
                  fonts:
                    - asset: assets/fonts/Roboto.ttf
                      weight: 700

            other_key: 1
        """)

        PubspecFontsEditor.addFonts(file, listOf(light))

        assertEquals(
            """
            flutter:
              fonts:
                - family: Roboto
                  fonts:
                    - asset: assets/fonts/Roboto.ttf
                      weight: 700
                - family: Gilroy-Light
                  fonts:
                    - asset: assets/fonts/Gilroy-Light.ttf

            other_key: 1
            """.trimIndent() + "\n",
            file.readText()
        )
    }

    @Test
    fun `skips assets that are already declared`(@TempDir dir: File) {
        val file = pubspec(dir, """
            flutter:
              fonts:
                - family: Gilroy-Light
                  fonts:
                    - asset: assets/fonts/Gilroy-Light.ttf
        """)
        val before = file.readText()

        val result = PubspecFontsEditor.addFonts(file, listOf(light))

        assertEquals(0, result.added)
        assertEquals(1, result.skipped)
        assertEquals(before, file.readText())
    }

    @Test
    fun `adds a second asset to an existing family`(@TempDir dir: File) {
        val file = pubspec(dir, """
            flutter:
              fonts:
                - family: Gilroy-Light
                  fonts:
                    - asset: assets/fonts/Gilroy-Light.otf
        """)

        PubspecFontsEditor.addFonts(file, listOf(light))

        assertEquals(
            listOf("assets/fonts/Gilroy-Light.otf", "assets/fonts/Gilroy-Light.ttf"),
            PubspecFontsEditor.readDeclaredAssets(file)
        )
    }

    @Test
    fun `creates flutter section when missing`(@TempDir dir: File) {
        val file = pubspec(dir, "name: my_app")

        PubspecFontsEditor.addFonts(file, listOf(light))

        assertTrue(file.readText().contains("flutter:\n  fonts:\n    - family: Gilroy-Light"))
        assertEquals(listOf("assets/fonts/Gilroy-Light.ttf"), PubspecFontsEditor.readDeclaredAssets(file))
    }

    @Test
    fun `keeps CRLF line endings`(@TempDir dir: File) {
        val file = File(dir, "pubspec.yaml")
        file.writeText("flutter:\r\n  uses-material-design: true\r\n")

        PubspecFontsEditor.addFonts(file, listOf(light))

        val text = file.readText()
        assertFalse(Regex("(?<!\r)\n").containsMatchIn(text))
        assertTrue(text.contains("- asset: assets/fonts/Gilroy-Light.ttf"))
    }

    @Test
    fun `isFontFile recognises ttf otf ttc case-insensitively`(@TempDir dir: File) {
        for (name in listOf("a.ttf", "b.OTF", "c.ttc")) assertTrue(PubspecFontsEditor.isFontFile(File(dir, name).apply { writeText("") }))
        assertFalse(PubspecFontsEditor.isFontFile(File(dir, "d.png").apply { writeText("") }))
    }
}
