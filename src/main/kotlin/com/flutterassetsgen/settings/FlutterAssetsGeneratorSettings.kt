package bys7.flutterassetsgen.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Simple, project-scoped configuration for FlutterAssetsGenerator.
 *
 * Deliberately tiny: just the three knobs the user actually asked for
 * (assets directory, output file, class name). All defaults reproduce the
 * FlutterAssetsGenerator 2.4.2 behavior out of the box, so most users never
 * need to open this settings page at all.
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

    companion object {
        fun getInstance(project: Project): FlutterAssetsGeneratorSettings =
            project.getService(FlutterAssetsGeneratorSettings::class.java)
    }
}
