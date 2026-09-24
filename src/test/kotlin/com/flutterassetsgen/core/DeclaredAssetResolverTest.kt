package bys7.flutterassetsgen.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DeclaredAssetResolverTest {

    private fun write(root: File, relativePath: String, content: String = "x") {
        val file = File(root, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `directory declaration resolves every file under it recursively`(@TempDir root: File) {
        write(root, "assets/images/logo.png")
        write(root, "assets/images/icons/gear.png")
        write(root, "assets/data/config.json")

        val resolved = DeclaredAssetResolver.resolve(root, listOf("assets/"))

        assertEquals(
            listOf("assets/data/config.json", "assets/images/icons/gear.png", "assets/images/logo.png"),
            resolved.map { it.relativePath }
        )
    }

    @Test
    fun `nested directory declarations each resolve their own subtree`(@TempDir root: File) {
        write(root, "assets/home/bg.png")
        write(root, "assets/home/sub/icon.png")
        write(root, "assets/other/unrelated.png")

        val resolved = DeclaredAssetResolver.resolve(root, listOf("assets/home/"))

        assertEquals(
            listOf("assets/home/bg.png", "assets/home/sub/icon.png"),
            resolved.map { it.relativePath }
        )
    }

    @Test
    fun `a single declared file resolves to exactly that file`(@TempDir root: File) {
        write(root, "assets/privacy.html")
        write(root, "assets/terms.html") // not declared - must not appear

        val resolved = DeclaredAssetResolver.resolve(root, listOf("assets/privacy.html"))

        assertEquals(listOf("assets/privacy.html"), resolved.map { it.relativePath })
    }

    @Test
    fun `the assets privacy html case resolves via a covering directory declaration too`(@TempDir root: File) {
        write(root, "assets/privacy.html")
        write(root, "assets/images/logo.png")

        val resolved = DeclaredAssetResolver.resolve(root, listOf("assets/"))

        val privacy = resolved.single { it.relativePath == "assets/privacy.html" }
        val identifier = AssetNameConverter.legacyIdentifier(privacy.relativePath, NamingStyle.CAMEL_CASE, includeParentDirectory = true)
        assertEquals("assetsPrivacy", identifier)
    }

    @Test
    fun `a file already covered by a declared parent directory is not duplicated`(@TempDir root: File) {
        write(root, "assets/privacy.html")

        // Both a directory entry AND a redundant explicit file entry declared for the same file.
        val resolved = DeclaredAssetResolver.resolve(root, listOf("assets/", "assets/privacy.html"))

        assertEquals(1, resolved.count { it.relativePath == "assets/privacy.html" })
        assertEquals(1, resolved.size)
    }

    @Test
    fun `declared paths from unrelated locations are all resolved`(@TempDir root: File) {
        write(root, "assets/logo.png")
        write(root, "fonts/CustomFont.ttf")

        val resolved = DeclaredAssetResolver.resolve(root, listOf("assets/", "fonts/CustomFont.ttf"))

        assertEquals(
            setOf("assets/logo.png", "fonts/CustomFont.ttf"),
            resolved.map { it.relativePath }.toSet()
        )
    }

    @Test
    fun `stale declarations pointing at nothing on disk are skipped, not errors`(@TempDir root: File) {
        write(root, "assets/logo.png")

        val resolved = DeclaredAssetResolver.resolve(root, listOf("assets/logo.png", "assets/does_not_exist.png", "missing_folder/"))

        assertEquals(listOf("assets/logo.png"), resolved.map { it.relativePath })
    }

    @Test
    fun `hidden files and directories are excluded from directory resolution`(@TempDir root: File) {
        write(root, "assets/logo.png")
        write(root, "assets/.DS_Store")
        write(root, "assets/.hidden/dot_file.png")

        val resolved = DeclaredAssetResolver.resolve(root, listOf("assets/"))

        assertEquals(listOf("assets/logo.png"), resolved.map { it.relativePath })
    }

    @Test
    fun `results are sorted by relative path for deterministic output`(@TempDir root: File) {
        write(root, "assets/zebra.png")
        write(root, "assets/apple.png")

        val resolved = DeclaredAssetResolver.resolve(root, listOf("assets/"))

        assertTrue(resolved.map { it.relativePath } == listOf("assets/apple.png", "assets/zebra.png"))
    }
}
