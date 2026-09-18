import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

// IntelliJ Platform 2026.1 (Build 261) requires Java 21
kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        androidStudio(properties("platformVersion"))
        pluginVerifier()
        zipSigner()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = properties("pluginGroup").get()
        name = properties("pluginName").get()
        version = properties("pluginVersion").get()

        description = """
            Regenerates a flat, type-safe <code>Assets.dart</code> index for a Flutter
            project's <code>assets/</code> directory with a single keyboard shortcut
            (<code>Alt+G</code> / <code>&#8997;G</code> on macOS).
            <br/><br/>
            This is a small, modern replacement for the classic FlutterAssetsGenerator
            2.4.2 plugin, reproducing its flat asset-naming convention
            (e.g. <code>Assets.imagesLogo</code>) without requiring any manual
            selection: just press the shortcut and the whole assets tree is rescanned
            and the generated file is rewritten from scratch.
        """.trimIndent()

        vendor {
            name = "FlutterAssetsGenerator (Modern)"
        }

        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = properties("pluginUntilBuild").map { it.ifBlank { null } }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    withType<JavaCompile>().configureEach {
        sourceCompatibility = properties("javaVersion").get()
        targetCompatibility = properties("javaVersion").get()
    }

    buildSearchableOptions {
        enabled = false
    }

    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }
}