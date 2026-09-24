package bys7.flutterassetsgen.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacyAssetGeneratorTest {

    /** [assetPath] is the printed Dart string; [namingPath] (defaults to [assetPath]) drives identifiers. */
    private fun asset(assetPath: String, namingPath: String = assetPath) = GeneratableAsset(assetPath, namingPath)

    @Test
    fun `flat identifiers use parent folder plus filename`() {
        val assets = listOf(
            asset("assets/images/logo.png", "images/logo.png"),
            asset("assets/images/home_screen.png", "images/home_screen.png"),
            asset("assets/icons/user-profile.svg", "icons/user-profile.svg"),
            asset("assets/data/app_config.json", "data/app_config.json")
        )

        val result = DartAssetsGenerator.generate(assets, "Assets", GenerationConfig())

        assertTrue(result.dartSource.contains("static const String imagesLogo = 'assets/images/logo.png';"))
        assertTrue(result.dartSource.contains("static const String imagesHomeScreen = 'assets/images/home_screen.png';"))
        assertTrue(result.dartSource.contains("static const String iconsUserProfile = 'assets/icons/user-profile.svg';"))
        assertTrue(result.dartSource.contains("static const String dataAppConfig = 'assets/data/app_config.json';"))
        assertEquals(4, result.assetCount)
        assertTrue(result.renamedDuplicates.isEmpty())
    }

    @Test
    fun `namingPath omits the assets root prefix while assetPath keeps the real path`() {
        val assets = listOf(asset("assets/villas_privacy_policy.html", "villas_privacy_policy.html"))

        val result = DartAssetsGenerator.generate(assets, "Assets", GenerationConfig())

        assertTrue(result.dartSource.contains("static const String villasPrivacyPolicy = 'assets/villas_privacy_policy.html';"))
        assertFalse(result.dartSource.contains("assetsVillasPrivacyPolicy"))
    }

    @Test
    fun `parent-folder-disambiguated names do not count as collisions`() {
        val assets = listOf(
            asset("assets/icons/home.png", "icons/home.png"),
            asset("assets/images/home.png", "images/home.png")
        )
        val result = DartAssetsGenerator.generate(assets, "Assets", GenerationConfig())

        assertTrue(result.dartSource.contains("iconsHome"))
        assertTrue(result.dartSource.contains("imagesHome"))
        assertTrue(result.renamedDuplicates.isEmpty())
    }

    @Test
    fun `genuine identifier collisions get a numeric suffix and are reported`() {
        val assets = listOf(
            asset("assets/images/logo.png", "images/logo.png"),
            asset("assets/images_logo.png", "images_logo.png")
        )
        val result = DartAssetsGenerator.generate(assets, "Assets", GenerationConfig())

        assertTrue(result.dartSource.contains("static const String imagesLogo ="))
        assertTrue(result.dartSource.contains("static const String imagesLogo2 ="))
        assertEquals(1, result.renamedDuplicates.size)
    }

    @Test
    fun `snake_case naming style is applied to identifiers`() {
        val assets = listOf(asset("assets/images/home_screen.png", "images/home_screen.png"))
        val config = GenerationConfig(namingStyle = NamingStyle.SNAKE_CASE)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertTrue(result.dartSource.contains("static const String images_home_screen ="))
    }

    @Test
    fun `class leaf type wraps paths in AssetGenImage`() {
        val assets = listOf(asset("assets/images/logo.png", "images/logo.png"))
        val config = GenerationConfig(leafType = LeafType.CLASS)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertTrue(result.dartSource.contains("import 'package:flutter/widgets.dart';"))
        assertTrue(result.dartSource.contains("class AssetGenImage {"))
        assertTrue(result.dartSource.contains("static const AssetGenImage imagesLogo = AssetGenImage('assets/images/logo.png');"))
    }

    @Test
    fun `class leaf type still uses plain String for non-image files`() {
        val assets = listOf(
            asset("assets/data/config.json", "data/config.json"),
            asset("assets/videos/intro.mp4", "videos/intro.mp4")
        )
        val config = GenerationConfig(leafType = LeafType.CLASS)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertTrue(result.dartSource.contains("static const String dataConfig = 'assets/data/config.json';"))
        assertTrue(result.dartSource.contains("static const String videosIntro = 'assets/videos/intro.mp4';"))
    }

    @Test
    fun `String leaf type never imports flutter widgets`() {
        val assets = listOf(asset("assets/images/logo.png", "images/logo.png"))
        val config = GenerationConfig(leafType = LeafType.STRING)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertFalse(result.dartSource.contains("import 'package:flutter/widgets.dart';"))
        assertFalse(result.dartSource.contains("class AssetGenImage"))
    }

    @Test
    fun `include parent directory disabled drops the folder prefix`() {
        val assets = listOf(asset("assets/images/logo.png", "images/logo.png"))
        val config = GenerationConfig(includeParentDirectory = false)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertTrue(result.dartSource.contains("static const String logo = 'assets/images/logo.png';"))
    }

    @Test
    fun `regeneration reflects a fully updated asset list, not an append`() {
        val before = DartAssetsGenerator.generate(
            listOf(asset("assets/images/logo.png", "images/logo.png"), asset("assets/images/old.png", "images/old.png")),
            "Assets", GenerationConfig()
        )
        assertTrue(before.dartSource.contains("imagesOld"))

        // Simulate "old.png" being deleted before the next Alt+G.
        val after = DartAssetsGenerator.generate(
            listOf(asset("assets/images/logo.png", "images/logo.png")), "Assets", GenerationConfig()
        )
        assertFalse(after.dartSource.contains("imagesOld"))
        assertTrue(after.dartSource.contains("imagesLogo"))
    }

    @Test
    fun `empty asset list still generates a compilable empty class`() {
        val result = DartAssetsGenerator.generate(emptyList(), "Assets", GenerationConfig())
        assertTrue(result.dartSource.contains("class Assets {"))
        assertTrue(result.dartSource.contains("Assets._();"))
        assertEquals(0, result.assetCount)
    }
}
