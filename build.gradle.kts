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

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        id = properties("pluginGroup").get()
        name = properties("pluginName").get()
        version = properties("pluginVersion").get()

        description = """
            Flutter Assets Generator automatically scans your Flutter project's assets
            directory and generates a type-safe <code>Assets.dart</code> file. Choose
            between flat (<code>Assets.imagesLogo</code>) and hierarchical
            (<code>Assets.images.logo</code>) generation styles, customize naming
            conventions and asset reference types, and regenerate the complete asset
            index with a single keyboard shortcut (<code>Alt+G</code> /
            <code>&#8997;G</code> on macOS).
        """.trimIndent()

        vendor {
            name = "Flutter Assets Generator"
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

    signing {
        certificateChainFile.set(file("certificate/chain.crt"))
        privateKeyFile.set(file("certificate/private.pem"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }

    publishing {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
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

    test {
        useJUnitPlatform()
    }

    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }
}