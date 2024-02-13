package io.github.inductiveautomation.kindling.xml

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.UI.Theme
import io.github.inductiveautomation.kindling.core.Theme.Companion.theme
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import io.github.inductiveautomation.kindling.utils.NumericEntryField
import io.github.inductiveautomation.kindling.utils.chooseFiles
import io.github.inductiveautomation.kindling.utils.rightBuddy
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ItemEvent
import java.io.File
import java.nio.file.Path
import java.util.Vector
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.border.LineBorder
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.name

class LogbackEditor(path: Path) : ToolPanel() {
    private val configsFromXml = LogbackConfigDeserializer().getObjectFromXML(path.toString()) ?: LogbackConfigData()
    private val logbackConfigManager = LogbackConfigManager(configsFromXml)

    private val selectedLoggersList = logbackConfigManager.getLoggerConfigs()

    private val scanEnabled = logbackConfigManager.configs.scan ?: false
    private val scanPeriod = logbackConfigManager.configs.scanPeriod?.filter(Char::isDigit)?.toLong()

    private val scanPeriodField =
        sizeEntryField(scanPeriod, "Sec", ::updateData).apply {
            isEditable = scanEnabled
        }

    private val scanForChangesCheckbox =
        JCheckBox("Scan for config changes?").apply {
            isSelected = scanEnabled
        }

    private val clearAllButton =
        JButton("Clear all configured loggers").apply {
            addActionListener {
                clearAll()
            }
        }

    private val loggerItems =
        javaClass.getResourceAsStream("/loggers.txt")
            ?.bufferedReader()
            ?.useLines { lines ->
                lines.flatMap { line ->
                    line.splitToSequence('.').runningReduce { acc, next ->
                        "$acc.$next"
                    }
                }.toSet()
            }
            .orEmpty()

    private val loggerComboBox =
        JComboBox(Vector(loggerItems)).apply {
            isEditable = true
            insertItemAt("", 0)
            selectedIndex = -1
            AutoCompleteDecorator.decorate(this)
            setPrototypeDisplayValue("X".repeat(50))
        }

    private val addButton =
        JButton("Add logger").apply {
            addActionListener {
                val selectedItem = loggerComboBox.selectedItem as String?
                if (selectedItem != null &&
                    selectedItem != "" &&
                    selectedItem !in selectedLoggersList.map { logger -> logger.name }
                ) {
                    selectedLoggersList.add(SelectedLogger(selectedItem))
                    selectedLoggersPanel.add(
                        SelectedLoggerCard(selectedLoggersList.last(), ::updateData),
                        "north, growx, shrinkx",
                    )
                    revalidate()
                    updateData()
                    loggerComboBox.selectedIndex = -1
                }
            }
        }

    private val selectedLoggersPanel =
        JPanel(MigLayout("fill, gap 5 5 3 3, wrap 1")).apply loggers@{
            selectedLoggersList.forEach { selectedLogger ->
                add(
                    SelectedLoggerCard(selectedLogger, ::updateData).apply {
                        closeButton.addActionListener {
                            selectedLoggersList.remove(logger)
                            this@loggers.components.forEachIndexed { index, component ->
                                if ((component as SelectedLoggerCard).name == logger.name) {
                                    this@loggers.remove(index)
                                }
                            }
                            updateData()
                        }
                    },
                    "growx, shrinkx",
                )
            }
        }

    private val logHomeDir = logbackConfigManager.configs.logHomeDir?.value
    private val logHomeField = JTextField(logHomeDir?.replace("\\\\", "\\") ?: System.getProperty("user.home"))

    private val fileChooser =
        JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }

    private val logHomeBrowseButton =
        JButton("Browse").apply {
            addActionListener {
                logHomeField.text = fileChooser.chooseFiles(this)?.singleOrNull()?.absolutePath
                updateData()
            }
        }

    private val editorPanel =
        JPanel(MigLayout("ins 0, fill, wrap 1, hidemode 3")).apply {
            add(JLabel("Log Home Directory"), "growx")
            add(logHomeField, "growx, split 2")
            add(logHomeBrowseButton, "sgx b")
            add(scanForChangesCheckbox, "split 2")
            add(scanPeriodField, "right, sgx b")

            add(JLabel("Logger Selection"), "growx")
            add(loggerComboBox, "growx, split 2")
            add(addButton, "sgx b")
            add(JLabel("Configured Loggers"))

            val loggerScrollPane =
                JScrollPane(selectedLoggersPanel).apply {
                    verticalScrollBar.unitIncrement = 16
                }
            add(loggerScrollPane, "grow, push")
            add(clearAllButton, "growx, sgx b")
        }

    private val xmlOutputPreview =
        RSyntaxTextArea(logbackConfigManager.getXmlString()).apply {
            lineWrap = true
            isEditable = false
            caretPosition = 0
            syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_XML
            theme = Theme.currentValue

            Theme.addChangeListener { newTheme ->
                theme = newTheme
            }
        }

    private val copyXmlButton =
        JButton("Copy to clipboard").apply {
            addActionListener {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(xmlOutputPreview.text), null)
            }
        }

    private val saveXmlButton =
        JButton("Save XML file").apply {
            addActionListener {
                updateData()
                JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter("XML file", "xml")
                    selectedFile = File("logback.xml")
                    val save = showSaveDialog(null)
                    if (save == JFileChooser.APPROVE_OPTION) {
                        logbackConfigManager.writeXmlFile(selectedFile.absolutePath)
                    }
                }
            }
        }

    private val scrollPane = RTextScrollPane(xmlOutputPreview)
    private val previewPanel =
        JPanel(MigLayout("fill")).apply {
            add(scrollPane, "push, grow, wrap")
            add(copyXmlButton, "south")
            add(saveXmlButton, "south")
        }

    private fun updateData() {
        val temp = xmlOutputPreview.caretPosition

        logbackConfigManager.configs.logHomeDir =
            LogHomeDirectory(
                "LOG_HOME",
                logHomeField.text.replace("\\", "\\\\"),
            )

        logbackConfigManager.configs.scan = if (scanForChangesCheckbox.isSelected) true else null
        logbackConfigManager.configs.scanPeriod =
            if (scanForChangesCheckbox.isSelected) {
                "${scanPeriodField.text} seconds"
            } else {
                null
            }

//        selectedLoggersPanel.components.forEachIndexed { index, card ->
//            val selectedLogger = selectedLoggersList[index]
//            selectedLogger.level = (card as SelectedLoggerCard).loggerLevelSelector.selectedItem as String
//            selectedLogger.separateOutput = card.loggerSeparateOutput.isSelected
//            selectedLogger.outputFolder = card.loggerOutputFolder.text
//            selectedLogger.filenamePattern = card.loggerFilenamePattern.text
//            selectedLogger.maxFileSize = card.maxFileSize.value as Long
//            selectedLogger.totalSizeCap = card.totalSizeCap.value as Long
//            selectedLogger.maxDaysHistory = card.maxDays.value as Long
//        }
//        logbackConfigManager.updateLoggerConfigs(selectedLoggersList)

        xmlOutputPreview.text = logbackConfigManager.getXmlString()
        xmlOutputPreview.caretPosition = if (temp > xmlOutputPreview.text.length) xmlOutputPreview.text.length else temp
    }

    init {
        name = path.name
        toolTipText = path.toString()

        scanPeriodField.addPropertyChangeListener("value") {
            updateData()
        }
        scanForChangesCheckbox.addItemListener {
            scanPeriodField.isEditable = it.stateChange == ItemEvent.SELECTED
            revalidate()
            updateData()
        }

        add(HorizontalSplitPane(editorPanel, previewPanel), "push, grow")
//        updateData()
    }

    private fun clearAll() {
        selectedLoggersList.clear()
        selectedLoggersPanel.apply {
            removeAll()
            revalidate()
            repaint()
            updateData()
        }
    }

    override val icon: Icon = XMLTool.icon
}

private fun sizeEntryField(
    inputValue: Long?,
    units: String,
    callback: () -> Unit,
): NumericEntryField =
    NumericEntryField(inputValue).apply {
        rightBuddy = JLabel(units)
        addPropertyChangeListener("value") { callback() }
    }

data class SelectedLogger(
    val name: String = "Logger name",
    var level: String = "INFO",
    var separateOutput: Boolean = false,
    var outputFolder: String = "\${LOG_HOME}\\\\AdditionalLogs\\\\",
    var filenamePattern: String = "${name.replace(".", "")}.%d{yyyy-MM-dd}.%i.log",
    var maxFileSize: Long = 10,
    var totalSizeCap: Long = 1000,
    var maxDaysHistory: Long = 5,
)

private class SelectedLoggerCard(
    val logger: SelectedLogger,
    private val callback: () -> Unit,
) : JPanel(MigLayout("fill, ins 5, hidemode 3")) {
    private val loggerLevelSelector =
        JComboBox(loggingLevels).apply {
            selectedItem = logger.level
        }

    private val loggerSeparateOutput =
        JCheckBox("Output to separate location?").apply {
            isSelected = logger.separateOutput
        }

    val closeButton =
        JButton(FlatSVGIcon("icons/bx-x.svg")).apply {
            border = null
            background = null
        }

    val loggerOutputFolder =
        JTextField(logger.outputFolder).apply {
            addActionListener { callback() }
        }

    val loggerFilenamePattern =
        JTextField(logger.filenamePattern).apply {
            addActionListener { callback() }
        }

    val maxFileSize = sizeEntryField(logger.maxFileSize, "MB", callback)
    val totalSizeCap = sizeEntryField(logger.totalSizeCap, "MB", callback)
    val maxDays = sizeEntryField(logger.maxDaysHistory, "Days", callback)

    private val redirectOutputPanel =
        JPanel(MigLayout("ins 0, fill")).apply {
            isVisible = loggerSeparateOutput.isSelected
            loggerSeparateOutput.addItemListener {
                isVisible = it.stateChange == ItemEvent.SELECTED
            }

            add(JLabel("Output Folder", SwingConstants.RIGHT), "split 2, spanx, sgx a")
            add(loggerOutputFolder, "growx")
            add(JLabel("Filename Pattern", SwingConstants.RIGHT), "split 2, spanx, sgx a")
            add(loggerFilenamePattern, "growx")

            add(JLabel("Max File Size", SwingConstants.RIGHT), "sgx a")
            add(maxFileSize, "sgx e, growx")
            add(JLabel("Total Size Cap", SwingConstants.RIGHT), "sgx a")
            add(totalSizeCap, "sgx e, growx")
            add(JLabel("Max Days", SwingConstants.RIGHT), "sgx a")
            add(maxDays, "sgx e, growx")
        }

    init {
        name = logger.name
        border = LineBorder(UIManager.getColor("Component.borderColor"), 3, true)

        loggerLevelSelector.addActionListener { callback() }
        loggerSeparateOutput.addActionListener { callback() }

        add(
            JLabel(logger.name).apply {
                font = font.deriveFont(Font.BOLD, 14F)
            },
        )
        add(closeButton, "right, wrap")
        add(loggerLevelSelector)
        add(loggerSeparateOutput, "right, wrap")
        add(redirectOutputPanel, "growx, span")
    }

    companion object {
        private val loggingLevels =
            arrayOf(
                "OFF",
                "ERROR",
                "WARN",
                "INFO",
                "DEBUG",
                "TRACE",
                "ALL",
            )
    }
}
