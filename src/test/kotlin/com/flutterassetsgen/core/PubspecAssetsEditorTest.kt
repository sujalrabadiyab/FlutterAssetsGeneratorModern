package bys7.flutterassetsgen.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PubspecAssetsEditorTest {

    private fun pubspec(dir: File, content: String): File {
        val file = File(dir, "pubspec.yaml")
        file.writeText(content.trimIndent())
        return file
    }

    @Test
    fun `reads declared directory and file entries in order`(@TempDir dir: File) {
        val file = pubspec(dir, """
            name: my_app
            flutter:
              uses-material-design: true
              assets:
                - assets/
                - assets/home/
                - assets/privacy.html
              fonts:
                - family: Roboto
        """)

        assertEquals(listOf("assets/", "assets/home/", "assets/privacy.html"), PubspecAssetsEditor.readDeclaredPaths(file))
    }

    @Test
    fun `returns empty list when there is no assets section`(@TempDir dir: File) {
        val file = pubspec(dir, """
            name: my_app
            flutter:
              uses-material-design: true
        """)

        assertTrue(PubspecAssetsEditor.readDeclaredPaths(file).isEmpty())
    }

    @Test
    fun `adds a new directory entry and preserves everything else`(@TempDir dir: File) {
        val file = pubspec(dir, """
            name: my_app
            flutter:
              uses-material-design: true
              assets:
                - assets/images/
        """)

        val changed = PubspecAssetsEditor.addPath(file, "assets/icons/")

        assertTrue(changed)
        val text = file.readText()
        assertTrue(text.contains("- assets/images/"))
        assertTrue(text.contains("- assets/icons/"))
        assertTrue(text.contains("uses-material-design: true"))
    }

    @Test
    fun `creates the assets section when only flutter key exists`(@TempDir dir: File) {
        val file = pubspec(dir, """
            name: my_app
            flutter:
              uses-material-design: true
        """)

        val changed = PubspecAssetsEditor.addPath(file, "assets/")

        assertTrue(changed)
        assertEquals(listOf("assets/"), PubspecAssetsEditor.readDeclaredPaths(file))
        assertTrue(file.readText().contains("uses-material-design: true"))
    }

    @Test
    fun `creates flutter and assets sections when neither exists`(@TempDir dir: File) {
        val file = pubspec(dir, """
            name: my_app
            environment:
              sdk: ">=3.0.0 <4.0.0"
        """)

        val changed = PubspecAssetsEditor.addPath(file, "assets/logo.png")

        assertTrue(changed)
        assertEquals(listOf("assets/logo.png"), PubspecAssetsEditor.readDeclaredPaths(file))
        assertTrue(file.readText().contains("sdk: \">=3.0.0 <4.0.0\""))
    }

    @Test
    fun `adding an already-declared path is a no-op`(@TempDir dir: File) {
        val file = pubspec(dir, """
            flutter:
              assets:
                - assets/
        """)

        assertFalse(PubspecAssetsEditor.addPath(file, "assets/"))
        assertEquals(listOf("assets/"), PubspecAssetsEditor.readDeclaredPaths(file))
    }

    @Test
    fun `adding a file already covered by a declared directory is a no-op`(@TempDir dir: File) {
        val file = pubspec(dir, """
            flutter:
              assets:
                - assets/
        """)

        assertFalse(PubspecAssetsEditor.addPath(file, "assets/privacy.html"))
        assertEquals(listOf("assets/"), PubspecAssetsEditor.readDeclaredPaths(file))
    }

    @Test
    fun `adding a covering directory removes now-redundant individual file entries`(@TempDir dir: File) {
        val file = pubspec(dir, """
            flutter:
              assets:
                - assets/privacy.html
                - assets/terms.html
        """)

        val changed = PubspecAssetsEditor.addPath(file, "assets/")

        assertTrue(changed)
        assertEquals(listOf("assets/"), PubspecAssetsEditor.readDeclaredPaths(file))
    }

    @Test
    fun `adding a subfolder is NOT skipped even though a parent directory already covers it`(@TempDir dir: File) {
        val file = pubspec(dir, """
            flutter:
              assets:
                - assets/
        """)

        val changed = PubspecAssetsEditor.addPath(file, "assets/home/")

        assertTrue(changed)
        assertEquals(listOf("assets/", "assets/home/"), PubspecAssetsEditor.readDeclaredPaths(file))
    }

    @Test
    fun `add folder and subfolders style sequence produces the expected declared list`(@TempDir dir: File) {
        val file = pubspec(dir, """
            flutter:
              assets:
        """)

        for (path in listOf("assets/", "assets/home/", "assets/rate/", "assets/intro/", "assets/common/")) {
            PubspecAssetsEditor.addPath(file, path)
        }

        assertEquals(
            listOf("assets/", "assets/home/", "assets/rate/", "assets/intro/", "assets/common/"),
            PubspecAssetsEditor.readDeclaredPaths(file)
        )
    }

    @Test
    fun `removes an exact match and preserves everything else`(@TempDir dir: File) {
        val file = pubspec(dir, """
            flutter:
              assets:
                - assets/images/
                - assets/icons/
        """)

        val changed = PubspecAssetsEditor.removePath(file, "assets/icons/")

        assertTrue(changed)
        assertEquals(listOf("assets/images/"), PubspecAssetsEditor.readDeclaredPaths(file))
    }

    @Test
    fun `removing a path that is not declared is a no-op`(@TempDir dir: File) {
        val file = pubspec(dir, """
            flutter:
              assets:
                - assets/images/
        """)

        assertFalse(PubspecAssetsEditor.removePath(file, "assets/icons/"))
    }

    @Test
    fun `preserves CRLF line endings when editing`(@TempDir dir: File) {
        val file = File(dir, "pubspec.yaml")
        file.writeText("flutter:\r\n  assets:\r\n    - assets/images/\r\n")

        PubspecAssetsEditor.addPath(file, "assets/icons/")

        val text = file.readText()
        assertTrue(text.contains("\r\n"))
        // No bare "\n" that isn't part of a "\r\n" pair - i.e. we didn't introduce stray LF-only lines.
        assertFalse(Regex("[^\r]\n").containsMatchIn(text))
        assertEquals(listOf("assets/images/", "assets/icons/"), PubspecAssetsEditor.readDeclaredPaths(file))
    }
}
