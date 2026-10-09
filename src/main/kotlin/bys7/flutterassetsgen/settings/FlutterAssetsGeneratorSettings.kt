package bys7.flutterassetsgen.settings

import bys7.flutterassetsgen.core.GenerationConfig
import bys7.flutterassetsgen.core.GenerationStyle
import bys7.flutterassetsgen.core.LeafType
import bys7.flutterassetsgen.core.NamingStyle
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Project-scoped configuration for FlutterAssetsGenerator.
 *
 * All defaults reproduce the original flat, string-constant behavior out of the box, so existing
 * projects/usages are unaffected until someone opens Settings and opts into a different
 * [GenerationStyle]/[NamingStyle]/[LeafType].
 */
@Service(Service.Level.PROJECT)
@State(
    name = "FlutterAssetsGeneratorSettings",
    storages = [Storage("flutterAssetsGenerator.xml", roamingType = RoamingType.DISABLED)]
)
class FlutterAssetsGeneratorSettings : PersistentStateComponent<FlutterAssetsGeneratorSettings.State> {

    class State {
        /** Path to the assets directory, relative to the project root. */
        var assetsDir: String = "assets"

        /** Path to the generated Dart file, relative to the project root. */
        var outputFile: String = "lib/generated/Assets.dart"

        /** Name of the generated Dart class. */
        var className: String = "Assets"

        /** [GenerationStyle] name, e.g. "LEGACY_FLAT" or "HIERARCHICAL". */
        var generationStyle: String = GenerationStyle.HIERARCHICAL.name

        /** [NamingStyle] name, e.g. "CAMEL_CASE" or "SNAKE_CASE". */
        var namingStyle: String = NamingStyle.CAMEL_CASE.name

        /** [LeafType] name, e.g. "STRING" or "CLASS". */
        var leafType: String = LeafType.CLASS.name

        /** Legacy Flat only: whether the immediate parent folder is folded into the flat identifier. */
        var includeParentDirectory: Boolean = true
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    var assetsDir: String
        get() = state.assetsDir.ifBlank { "assets" }
        set(value) {
            state.assetsDir = value.trim().trim('/').ifBlank { "assets" }
        }

    var outputFile: String
        get() = state.outputFile.ifBlank { "lib/generated/Assets.dart" }
        set(value) {
            state.outputFile = value.trim().trim('/').ifBlank { "lib/generated/Assets.dart" }
        }

    var className: String
        get() = state.className.ifBlank { "Assets" }
        set(value) {
            state.className = value.trim().ifBlank { "Assets" }
        }

    var generationStyle: GenerationStyle
        get() = runCatching { GenerationStyle.valueOf(state.generationStyle) }.getOrDefault(GenerationStyle.LEGACY_FLAT)
        set(value) {
            state.generationStyle = value.name
        }

    var namingStyle: NamingStyle
        get() = runCatching { NamingStyle.valueOf(state.namingStyle) }.getOrDefault(NamingStyle.CAMEL_CASE)
        set(value) {
            state.namingStyle = value.name
        }

    var leafType: LeafType
        get() = runCatching { LeafType.valueOf(state.leafType) }.getOrDefault(LeafType.STRING)
        set(value) {
            state.leafType = value.name
        }

    var includeParentDirectory: Boolean
        get() = state.includeParentDirectory
        set(value) {
            state.includeParentDirectory = value
        }

    /** Convenience bundle of the generation-related settings, ready to hand to [bys7.flutterassetsgen.core.DartAssetsGenerator]. */
    fun generationConfig(): GenerationConfig = GenerationConfig(
        generationStyle = generationStyle,
        namingStyle = namingStyle,
        leafType = leafType,
        includeParentDirectory = includeParentDirectory
    )

    companion object {
        fun getInstance(project: Project): FlutterAssetsGeneratorSettings =
            project.getService(FlutterAssetsGeneratorSettings::class.java)
    }
}
