package bys7.flutterassetsgen.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HierarchicalAssetGeneratorTest {

    /** [assetPath] is the printed Dart string; [namingPath] (defaults to [assetPath]) drives identifiers/hierarchy. */
    private fun asset(assetPath: String, namingPath: String = assetPath) = GeneratableAsset(assetPath, namingPath)

    @Test
    fun `assets nest under their directory as typed groups`() {
        val assets = listOf(
            asset("assets/images/logo.png", "images/logo.png"),
            asset("assets/images/home_screen.png", "images/home_screen.png"),
            asset("assets/icons/user-profile.svg", "icons/user-profile.svg"),
            asset("assets/data/app_config.json", "data/app_config.json")
        )
        val config = GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertTrue(result.dartSource.contains("static const \$AssetsImagesGen images = \$AssetsImagesGen();"))
        assertTrue(result.dartSource.contains("static const \$AssetsIconsGen icons = \$AssetsIconsGen();"))
        assertTrue(result.dartSource.contains("static const \$AssetsDataGen data = \$AssetsDataGen();"))
        assertTrue(result.dartSource.contains("class \$AssetsImagesGen {"))
        assertTrue(result.dartSource.contains("final String logo = 'assets/images/logo.png';"))
        assertTrue(result.dartSource.contains("final String homeScreen = 'assets/images/home_screen.png';"))
        assertTrue(result.dartSource.contains("class \$AssetsIconsGen {"))
        assertTrue(result.dartSource.contains("final String userProfile = 'assets/icons/user-profile.svg';"))
        assertTrue(result.dartSource.contains("class \$AssetsDataGen {"))
        assertTrue(result.dartSource.contains("final String appConfig = 'assets/data/app_config.json';"))
        assertEquals(4, result.assetCount)
    }

    @Test
    fun `stripping the assets root prefix avoids double nesting under an assets group`() {
        // Real on-disk paths all start with "assets/" (declared as a single "assets/" folder in
        // pubspec.yaml), but the naming path has that root folder stripped - exactly the
        // scenario that used to produce Assets.assets.images.logo instead of Assets.images.logo.
        val assets = listOf(
            asset("assets/images/common/back_icon_unpress.png", "images/common/back_icon_unpress.png"),
            asset("assets/fonts/CustomFont.ttf", "fonts/CustomFont.ttf"),
            asset("assets/villas_privacy_policy.html", "villas_privacy_policy.html")
        )
        val config = GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        // No wrapping "assets" group at all.
        assertFalse(result.dartSource.contains("\$AssetsAssetsGen"))
        assertFalse(result.dartSource.contains(".assets."))

        assertTrue(result.dartSource.contains("static const \$AssetsImagesGen images = \$AssetsImagesGen();"))
        assertTrue(result.dartSource.contains("static const \$AssetsFontsGen fonts = \$AssetsFontsGen();"))
        // A file directly under the stripped root becomes a plain top-level constant, with the
        // real on-disk path (still including "assets/") as its value.
        assertTrue(result.dartSource.contains("static const String villasPrivacyPolicy = 'assets/villas_privacy_policy.html';"))

        assertTrue(result.dartSource.contains("class \$AssetsCommonGen {"))
        assertTrue(result.dartSource.contains("final String backIconUnpress = 'assets/images/common/back_icon_unpress.png';"))
    }

    @Test
    fun `nested subdirectories become nested groups with an explicit const constructor call`() {
        val assets = listOf(
            asset("assets/icons/social/facebook.png", "icons/social/facebook.png"),
            asset("assets/icons/gear.png", "icons/gear.png")
        )
        val config = GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertTrue(result.dartSource.contains("class \$AssetsIconsGen {"))
        // Non-static "final" field on a const-constructible class MUST spell out "const" on its
        // initializer, or it's a compile error ("field is initialized with a non-constant value").
        assertTrue(result.dartSource.contains("final \$AssetsIconsSocialGen social = const \$AssetsIconsSocialGen();"))
        assertTrue(result.dartSource.contains("final String gear = 'assets/icons/gear.png';"))
        assertTrue(result.dartSource.contains("class \$AssetsIconsSocialGen {"))
        assertTrue(result.dartSource.contains("final String facebook = 'assets/icons/social/facebook.png';"))
    }

    @Test
    fun `root-level loose files become direct top-level constants`() {
        val assets = listOf(asset("assets/favicon.png", "favicon.png"))
        val config = GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertTrue(result.dartSource.contains("static const String favicon = 'assets/favicon.png';"))
    }

    @Test
    fun `class leaf type wraps image files in a const AssetGenImage, both at root and nested`() {
        val assets = listOf(asset("assets/logo.png", "logo.png"), asset("assets/images/icon.png", "images/icon.png"))
        val config = GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL, leafType = LeafType.CLASS)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertTrue(result.dartSource.contains("import 'package:flutter/widgets.dart';"))
        assertTrue(result.dartSource.contains("class AssetGenImage {"))
        // Root: already a `static const` context, so no redundant explicit `const` needed.
        assertTrue(result.dartSource.contains("static const AssetGenImage logo = AssetGenImage('assets/logo.png');"))
        // Nested: `final` field on a const class - explicit `const` is required to compile.
        assertTrue(result.dartSource.contains("final AssetGenImage icon = const AssetGenImage('assets/images/icon.png');"))
    }

    @Test
    fun `class leaf type still uses plain String for non-image files`() {
        val assets = listOf(
            asset("assets/data/config.json", "data/config.json"),
            asset("assets/videos/intro.mp4", "videos/intro.mp4")
        )
        val config = GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL, leafType = LeafType.CLASS)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertTrue(result.dartSource.contains("final String config = 'assets/data/config.json';"))
        assertTrue(result.dartSource.contains("final String intro = 'assets/videos/intro.mp4';"))
    }

    @Test
    fun `String leaf type never imports flutter widgets`() {
        val assets = listOf(asset("assets/images/logo.png", "images/logo.png"))
        val config = GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL, leafType = LeafType.STRING)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertFalse(result.dartSource.contains("import 'package:flutter/widgets.dart';"))
        assertFalse(result.dartSource.contains("class AssetGenImage"))
    }

    @Test
    fun `snake_case naming style applies to both group and leaf field names`() {
        val assets = listOf(asset("assets/app_icons/home_screen.png", "app_icons/home_screen.png"))
        val config = GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL, namingStyle = NamingStyle.SNAKE_CASE)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertTrue(result.dartSource.contains("app_icons ="))
        assertTrue(result.dartSource.contains("final String home_screen ="))
    }

    @Test
    fun `identically named folders in different branches never collide, class name gets a numeric suffix`() {
        val assets = listOf(asset("assets/a/common/icon.png", "a/common/icon.png"), asset("assets/b/common/icon.png", "b/common/icon.png"))
        val config = GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        assertTrue(result.dartSource.contains("class \$AssetsCommonGen {"))
        assertTrue(result.dartSource.contains("class \$AssetsCommonGen2 {"))
    }

    @Test
    fun `every declared class actually appears with balanced braces`() {
        val assets = listOf(asset("assets/images/logo.png", "images/logo.png"), asset("assets/icons/social/facebook.png", "icons/social/facebook.png"))
        val config = GenerationConfig(generationStyle = GenerationStyle.HIERARCHICAL, leafType = LeafType.CLASS)
        val result = DartAssetsGenerator.generate(assets, "Assets", config)

        val openBraces = result.dartSource.count { it == '{' }
        val closeBraces = result.dartSource.count { it == '}' }
        assertEquals(openBraces, closeBraces)
    }
}
