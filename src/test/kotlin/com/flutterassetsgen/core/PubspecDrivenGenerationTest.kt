package bys7.flutterassetsgen.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Exercises the full pipeline the "Generate Flutter Assets" action drives: read declared paths
 * from pubspec.yaml -> resolve them to files -> strip the assets-root naming prefix -> generate
 * Assets.dart - without ever touching the physical assets/ directory directly, per "do not
 * blindly scan the physical assets directory".
 */
class PubspecDrivenGenerationTest {

    private fun write(root: File, relativePath: String, content: String = "x") {
        val file = File(root, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun declaredAssets(root: File, pubspecContent: String): List<GeneratableAsset> {
        val pubspec = File(root, "pubspec.yaml")
        pubspec.writeText(pubspecContent.trimIndent())
        val declared = PubspecAssetsEditor.readDeclaredPaths(pubspec)
        val resolved = DeclaredAssetResolver.resolve(root, declared)
        return toGeneratableAssets(resolved, "assets")
    }

    @Test
    fun `assets privacy html declared via a covering directory generates Assets assetsPrivacy-free identifiers`(@TempDir root: File) {
        write(root, "assets/privacy.html")
        write(root, "assets/images/logo.png")

        val assets = declaredAssets(
            root,
            """
            name: my_app
            flutter:
              assets:
                - assets/
            """
        )
        val result = DartAssetsGenerator.generate(assets, "Assets", GenerationConfig())

        // The "assets" root folder is stripped from the identifier, but the printed string keeps
        // the real, full path Flutter needs.
        assertTrue(result.dartSource.contains("static const String privacy = 'assets/privacy.html';"))
        assertTrue(result.dartSource.contains("static const String imagesLogo = 'assets/images/logo.png';"))
    }

    @Test
    fun `a file not covered by any declared path is never generated`(@TempDir root: File) {
        write(root, "assets/declared/logo.png")
        write(root, "assets/not_declared/secret.png")

        val assets = declaredAssets(
            root,
            """
            flutter:
              assets:
                - assets/declared/
            """
        )
        val result = DartAssetsGenerator.generate(assets, "Assets", GenerationConfig())

        assertTrue(result.dartSource.contains("declaredLogo"))
        assertFalse(result.dartSource.contains("secret"))
    }

    @Test
    fun `hierarchical style has no wrapping assets group once the root prefix is stripped`(@TempDir root: File) {
        write(root, "assets/images/common/back_icon_unpress.png")
        write(root, "assets/fonts/CustomFont.ttf")
        write(root, "assets/villas_privacy_policy.html")

        val assets = declaredAssets(
            root,
            """
            flutter:
              assets:
                - assets/
            """
        )
        val result = DartAssetsGenerator.generate(
            assets, "Assets", GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL)
        )

        assertFalse(result.dartSource.contains(".assets."))
        assertFalse(result.dartSource.contains("\$AssetsAssetsGen"))
        assertTrue(result.dartSource.contains("static const \$AssetsImagesGen images = \$AssetsImagesGen();"))
        assertTrue(result.dartSource.contains("static const \$AssetsFontsGen fonts = \$AssetsFontsGen();"))
        assertTrue(result.dartSource.contains("static const String villasPrivacyPolicy = 'assets/villas_privacy_policy.html';"))
        assertTrue(result.dartSource.contains("final String backIconUnpress = 'assets/images/common/back_icon_unpress.png';"))
    }

    @Test
    fun `an asset declared outside the assets root keeps its own top-level group`(@TempDir root: File) {
        write(root, "assets/logo.png")
        write(root, "fonts/CustomFont.ttf")

        val assets = declaredAssets(
            root,
            """
            flutter:
              assets:
                - assets/
                - fonts/CustomFont.ttf
            """
        )
        val result = DartAssetsGenerator.generate(
            assets, "Assets", GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL)
        )

        // "fonts/CustomFont.ttf" isn't under the "assets" root, so it's untouched by the strip
        // and becomes its own top-level constant, still with its own real path. ("CustomFont" is
        // one un-separated word, so camelCase lowercases it entirely - existing, unrelated behavior.)
        assertTrue(result.dartSource.contains("static const String customfont = 'fonts/CustomFont.ttf';"))
        assertTrue(result.dartSource.contains("static const String logo = 'assets/logo.png';"))
    }
}
