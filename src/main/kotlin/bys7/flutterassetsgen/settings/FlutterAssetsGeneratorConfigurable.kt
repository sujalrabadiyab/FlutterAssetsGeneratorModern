package bys7.flutterassetsgen.settings

import bys7.flutterassetsgen.core.GenerationStyle
import bys7.flutterassetsgen.core.LeafType
import bys7.flutterassetsgen.core.NamingStyle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Font
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings > Tools > Flutter Assets Generator.
 *
 * Intentionally built with plain Swing / [FormBuilder] instead of the Kotlin UI DSL: the DSL API
 * has shifted across IntelliJ Platform releases, while these basic Swing building blocks have
 * stayed source-compatible for a very long time. That stability is worth more than the extra
 * boilerplate for a handful of fields, combo boxes, and a checkbox.
 */
class FlutterAssetsGeneratorConfigurable(private val project: Project) : Configurable {

    private val assetsDirField = JBTextField()
    private val outputFileField = JBTextField()
    private val classNameField = JBTextField()

    private val generationStyleCombo = JComboBox(arrayOf("Legacy Flat", "Hierarchical"))
    private val namingStyleCombo = JComboBox(arrayOf("camelCase", "snake_case"))
    private val leafTypeCombo = JComboBox(arrayOf("String", "Class"))
    private val includeParentDirectoryCheckBox = JBCheckBox("Include parent directory")

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Flutter Assets Generator"

    override fun createComponent(): JComponent {
        val builder = FormBuilder.createFormBuilder()

        builder.addLabeledComponent(JBLabel("Assets directory:"), assetsDirField, 1, false)
        builder.addComponent(hint("Folder name to omit from generated identifiers/hierarchy (e.g. \"assets\"). Doesn't control which files are included - that's driven by pubspec.yaml's declared asset paths (see Flutter Configure Paths)."))
        builder.addLabeledComponent(JBLabel("Generated file:"), outputFileField, 1, false)
        builder.addComponent(hint("Path to the generated Dart file, relative to the project root."))
        builder.addLabeledComponent(JBLabel("Class name:"), classNameField, 1, false)
        builder.addComponent(hint("Name of the generated Dart class."))

        builder.addComponent(JPanel().apply { border = JBUI.Borders.emptyTop(12) })
        builder.addComponent(sectionLabel("Generation"))

        builder.addLabeledComponent(JBLabel("Generation Style:"), generationStyleCombo, 1, false)
        builder.addComponent(hint("Legacy Flat: Assets.imagesLogo. Hierarchical: Assets.images.logo."))

        builder.addLabeledComponent(JBLabel("Naming Style:"), namingStyleCombo, 1, false)
        builder.addComponent(hint("Casing for multi-word identifiers, e.g. homeScreen vs home_screen."))

        builder.addLabeledComponent(JBLabel("Leaf Type:"), leafTypeCombo, 1, false)
        builder.addComponent(hint("String: plain path constants. Class: wraps each path in a small typed AssetGenImage."))

        builder.addComponent(includeParentDirectoryCheckBox)
        builder.addComponent(hint("Legacy Flat only - fold the parent folder into the name (imagesHome) to avoid collisions."))

        builder.addComponentFillVertically(JPanel(), 0)

        val built = builder.panel
        built.border = JBUI.Borders.empty(8)
        panel = built
        reset()
        return built
    }

    private fun sectionLabel(text: String): JComponent {
        val label = JBLabel(text)
        label.font = label.font.deriveFont(Font.BOLD)
        return label
    }

    private fun hint(text: String): JComponent {
        val label = JBLabel(text)
        label.font = label.font.deriveFont(Font.ITALIC, label.font.size2D - 1f)
        label.foreground = Color.GRAY
        return label
    }

    override fun isModified(): Boolean {
        val settings = FlutterAssetsGeneratorSettings.getInstance(project)
        return assetsDirField.text.trim() != settings.assetsDir ||
            outputFileField.text.trim() != settings.outputFile ||
            classNameField.text.trim() != settings.className ||
            selectedGenerationStyle() != settings.generationStyle ||
            selectedNamingStyle() != settings.namingStyle ||
            selectedLeafType() != settings.leafType ||
            includeParentDirectoryCheckBox.isSelected != settings.includeParentDirectory
    }

    override fun apply() {
        val settings = FlutterAssetsGeneratorSettings.getInstance(project)
        settings.assetsDir = assetsDirField.text
        settings.outputFile = outputFileField.text
        settings.className = classNameField.text
        settings.generationStyle = selectedGenerationStyle()
        settings.namingStyle = selectedNamingStyle()
        settings.leafType = selectedLeafType()
        settings.includeParentDirectory = includeParentDirectoryCheckBox.isSelected
    }

    override fun reset() {
        val settings = FlutterAssetsGeneratorSettings.getInstance(project)
        assetsDirField.text = settings.assetsDir
        outputFileField.text = settings.outputFile
        classNameField.text = settings.className
        generationStyleCombo.selectedIndex = if (settings.generationStyle == GenerationStyle.HIERARCHICAL) 1 else 0
        namingStyleCombo.selectedIndex = if (settings.namingStyle == NamingStyle.SNAKE_CASE) 1 else 0
        leafTypeCombo.selectedIndex = if (settings.leafType == LeafType.CLASS) 1 else 0
        includeParentDirectoryCheckBox.isSelected = settings.includeParentDirectory
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun selectedGenerationStyle(): GenerationStyle =
        if (generationStyleCombo.selectedIndex == 1) GenerationStyle.HIERARCHICAL else GenerationStyle.LEGACY_FLAT

    private fun selectedNamingStyle(): NamingStyle =
        if (namingStyleCombo.selectedIndex == 1) NamingStyle.SNAKE_CASE else NamingStyle.CAMEL_CASE

    private fun selectedLeafType(): LeafType =
        if (leafTypeCombo.selectedIndex == 1) LeafType.CLASS else LeafType.STRING
}
