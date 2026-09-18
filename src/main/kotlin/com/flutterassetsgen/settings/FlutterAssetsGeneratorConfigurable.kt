package com.flutterassetsgen.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings > Tools > Flutter Assets Generator.
 *
 * Intentionally built with plain Swing / [FormBuilder] instead of the
 * Kotlin UI DSL: the DSL API has shifted across IntelliJ Platform releases,
 * while these basic Swing building blocks have stayed source-compatible
 * for a very long time. For three text fields, that stability is worth
 * more than the extra boilerplate.
 */
class FlutterAssetsGeneratorConfigurable(private val project: Project) : Configurable {

    private val assetsDirField = JBTextField()
    private val outputFileField = JBTextField()
    private val classNameField = JBTextField()

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Flutter Assets Generator"

    override fun createComponent(): JComponent {
        val built = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Assets directory:"), assetsDirField, 1, false)
            .addLabeledComponent(JBLabel("Generated file:"), outputFileField, 1, false)
            .addLabeledComponent(JBLabel("Class name:"), classNameField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        built.border = JBUI.Borders.empty(8)
        panel = built
        reset()
        return built
    }

    override fun isModified(): Boolean {
        val settings = FlutterAssetsGeneratorSettings.getInstance(project)
        return assetsDirField.text.trim() != settings.assetsDir ||
            outputFileField.text.trim() != settings.outputFile ||
            classNameField.text.trim() != settings.className
    }

    override fun apply() {
        val settings = FlutterAssetsGeneratorSettings.getInstance(project)
        settings.assetsDir = assetsDirField.text
        settings.outputFile = outputFileField.text
        settings.className = classNameField.text
    }

    override fun reset() {
        val settings = FlutterAssetsGeneratorSettings.getInstance(project)
        assetsDirField.text = settings.assetsDir
        outputFileField.text = settings.outputFile
        classNameField.text = settings.className
    }

    override fun disposeUIResources() {
        panel = null
    }
}
