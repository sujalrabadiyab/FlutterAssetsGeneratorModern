package bys7.flutterassetsgen.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssetNameConverterTest {

    @Test
    fun `legacy identifier includes immediate parent directory by default`() {
        assertEquals(
            "imagesLogo",
            AssetNameConverter.legacyIdentifier("images/logo.png", NamingStyle.CAMEL_CASE, includeParentDirectory = true)
        )
    }

    @Test
    fun `legacy identifier drops parent directory when disabled`() {
        assertEquals(
            "logo",
            AssetNameConverter.legacyIdentifier("images/logo.png", NamingStyle.CAMEL_CASE, includeParentDirectory = false)
        )
    }

    @Test
    fun `parent directory prevents collisions between same-named files in different folders`() {
        assertEquals(
            "iconsHome",
            AssetNameConverter.legacyIdentifier("icons/home.png", NamingStyle.CAMEL_CASE, includeParentDirectory = true)
        )
        assertEquals(
            "imagesHome",
            AssetNameConverter.legacyIdentifier("images/home.png", NamingStyle.CAMEL_CASE, includeParentDirectory = true)
        )
    }

    @Test
    fun `camelCase naming style`() {
        assertEquals(
            "homeScreen",
            AssetNameConverter.leafIdentifier("home_screen", NamingStyle.CAMEL_CASE)
        )
    }

    @Test
    fun `snake_case naming style`() {
        assertEquals(
            "home_screen",
            AssetNameConverter.leafIdentifier("home_screen", NamingStyle.SNAKE_CASE)
        )
    }

    @Test
    fun `spaces are treated as separators`() {
        assertEquals(
            "oldPhoto",
            AssetNameConverter.legacyIdentifier("old photo.jpg", NamingStyle.CAMEL_CASE, includeParentDirectory = true)
        )
    }

    @Test
    fun `hyphens are treated as separators`() {
        assertEquals(
            "userProfile",
            AssetNameConverter.legacyIdentifier("user-profile.svg", NamingStyle.CAMEL_CASE, includeParentDirectory = true)
        )
    }

    @Test
    fun `leading digit never produces a private underscore name`() {
        assertEquals(
            "i123Logo",
            AssetNameConverter.legacyIdentifier("123-logo.png", NamingStyle.CAMEL_CASE, includeParentDirectory = true)
        )
    }

    @Test
    fun `numeric file name without a parent gets an i prefix`() {
        assertEquals("i1", AssetNameConverter.leafIdentifier("1", NamingStyle.CAMEL_CASE))
        assertEquals("i_1", AssetNameConverter.leafIdentifier("1", NamingStyle.SNAKE_CASE))
    }

    @Test
    fun `numeric file name uses its parent folder as prefix`() {
        assertEquals("icon1", AssetNameConverter.leafIdentifier("1", NamingStyle.CAMEL_CASE, "icon"))
        assertEquals("socialIcons1", AssetNameConverter.leafIdentifier("1", NamingStyle.CAMEL_CASE, "social_icons"))
        assertEquals("icon1", AssetNameConverter.legacyIdentifier("icon/1.png", NamingStyle.CAMEL_CASE, includeParentDirectory = true))
        assertEquals("icon1", AssetNameConverter.legacyIdentifier("icon/1.png", NamingStyle.CAMEL_CASE, includeParentDirectory = false))
        assertEquals("i1", AssetNameConverter.legacyIdentifier("1.png", NamingStyle.CAMEL_CASE, includeParentDirectory = true))
    }

    @Test
    fun `numeric parent folder falls back to i prefix`() {
        assertEquals("i20241", AssetNameConverter.leafIdentifier("1", NamingStyle.CAMEL_CASE, "2024"))
        assertEquals("i2024", AssetNameConverter.groupFieldIdentifier("2024", NamingStyle.CAMEL_CASE))
    }

    @Test
    fun `dart reserved word gets an Asset suffix`() {
        assertEquals("classAsset", AssetNameConverter.sanitize("class", NamingStyle.CAMEL_CASE))
        assertEquals("class_asset", AssetNameConverter.sanitize("class", NamingStyle.SNAKE_CASE))
    }

    @Test
    fun `dots and multiple separators collapse cleanly`() {
        assertEquals(
            "profileImage2x",
            AssetNameConverter.legacyIdentifier("old photos/profile-image@2x.png", NamingStyle.CAMEL_CASE, includeParentDirectory = false)
        )
    }
}
