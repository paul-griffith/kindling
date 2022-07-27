package io.github.paulgriffith

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector
import com.formdev.flatlaf.extras.components.FlatTextArea
import com.jthemedetecor.OsThemeDetector
import io.github.paulgriffith.core.CustomIconView
import io.github.paulgriffith.core.TabPanel
import io.github.paulgriffith.utils.*
import net.miginfocom.layout.PlatformDefaults
import net.miginfocom.layout.UnitValue
import net.miginfocom.swing.MigLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Image
import java.awt.Toolkit
import java.awt.desktop.QuitStrategy
import java.awt.event.ItemEvent
import java.io.File
import javax.swing.ButtonGroup
import javax.swing.JCheckBoxMenuItem
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.io.path.Path

class MainPanel : JPanel(MigLayout("ins 6, fill")) {
    private val homeLocation: File = Path(System.getProperty("user.home"), "Downloads").toFile()

    private val fileChooser = JFileChooser(homeLocation).apply {
        isMultiSelectionEnabled = true
        fileView = CustomIconView()

        Tool.byFilter.keys.forEach(this::addChoosableFileFilter)
        fileFilter = Tool.IdbViewer.filter

        UIManager.addPropertyChangeListener { e ->
            if (e.propertyName == "lookAndFeel") {
                updateUI()
            }
        }
    }

    private val exportFileChooser = JFileChooser(homeLocation).apply {
        isMultiSelectionEnabled = false
        fileView = CustomIconView()
        fileSelectionMode = JFileChooser.FILES_ONLY

        fileFilter = FileExtensionFilter(".csv file", listOf(".csv"))

        UIManager.addPropertyChangeListener { e ->
            if (e.propertyName == "lookAndFeel") {
                updateUI()
            }
        }
    }

    private val openAction = Action(
        name = "Open...",
    ) {
        fileChooser.chooseFiles(this)?.let { selectedFiles ->
            val selectedTool: Tool? = Tool.byFilter[fileChooser.fileFilter]
            openFiles(selectedFiles, selectedTool)
        }
    }

    private val exportAction = Action(
        name = "Export as CSV",
        description = "Export this data in CSV format"
    ) {
        exportFileChooser.showSaveDialog(this).let {
            if (it == JFileChooser.APPROVE_OPTION) exportToFile(exportFileChooser.selectedFile)
        }
    }

    private val menuBar = JMenuBar().apply {
        add(
            JMenu("File").apply {
                add(openAction)
                for (tool in Tool.values()) {
                    add(
                        Action(
                            name = "Open ${tool.title}",
                        ) {
                            fileChooser.fileFilter = tool.filter
                            fileChooser.chooseFiles(this@MainPanel)?.let { selectedFiles ->
                                openFiles(selectedFiles, tool)
                            }
                        }
                    )
                }
                add(exportAction)
            }
        )
        add(
            JMenu("Theme").apply {
                val group = ButtonGroup()
                add(
                    JCheckBoxMenuItem("Light", !THEME_DETECTOR.isDark).apply {
                        addItemListener {
                            if (it.stateChange == ItemEvent.SELECTED) {
                                LIGHT_THEME.display(true)
                            }
                        }
                        group.add(this)
                    }
                )
                add(
                    JCheckBoxMenuItem("Dark", THEME_DETECTOR.isDark).apply {
                        addItemListener {
                            if (it.stateChange == ItemEvent.SELECTED) {
                                DARK_THEME.display(true)
                            }
                        }
                        group.add(this)
                    }
                )
            }
        )
        add(
            JMenu("Debug").apply {
                add(
                    Action("UI Inspector") {
                        FlatUIDefaultsInspector.show()
                    }
                )
            }
        )
    }

    private val tabs = TabPanel()

    /**
     * Opens a path in a tool (blocking). In the event of any error, opens an 'Error' tab instead.
     */
    private fun Tool.openOrError(vararg files: File) {
        runCatching {
            val toolPanel = if (this is MultiTool) {
                open(files.map(File::toPath))
            } else {
                open(files.single().toPath())
            }
            tabs.addTab(
                toolPanel.name.truncate(),
                toolPanel.icon,
                toolPanel,
                toolPanel.toolTipText,
            )
        }.getOrElse { ex ->
            LOGGER.error("Failed to open ${files.joinToString()} as a $title", ex)
            tabs.addTab(
                "ERROR",
                FlatSVGIcon("icons/bx-error.svg"),
                FlatScrollPane(
                    FlatTextArea().apply {
                        isEditable = false
                        text = buildString {
                            if (ex is ToolOpeningException) {
                                appendLine(ex.message)
                            } else {
                                appendLine("Error opening ${files.joinToString()}: ${ex.message}")
                            }
                            append(ex.cause?.stackTraceToString().orEmpty())
                        }
                    }
                )
            )
        }
        tabs.selectedIndex = tabs.tabCount - 1
    }

    fun openFiles(files: List<File>, tool: Tool? = null) = if (tool is MultiTool) {
        tool.openOrError(*files.toTypedArray())
    } else {
        files.groupBy { tool ?: Tool[it] }
            .forEach { (tool, filesByTool) ->
                if (tool is MultiTool) {
                    tool.openOrError(*filesByTool.toTypedArray())
                } else {
                    filesByTool.forEach { file ->
                        tool.openOrError(file)
                    }
                }
            }
    }

    private fun exportToFile(file: File) {
        with(tabs.getComponentAt(tabs.selectedIndex) as ToolPanel) {
            try {
                exportData(file)
            } catch (e: java.lang.Exception) {
                LOGGER.error("Failed to open $file", e)
                tabs.addTab(
                    "ERROR",
                    FlatSVGIcon("icons/bx-error.svg"),
                    FlatScrollPane(
                        FlatTextArea().apply {
                            isEditable = false
                            text = buildString {
                                if (e is IllegalArgumentException) {
                                    appendLine(e.message)
                                } else {
                                    appendLine("Error opening $file: ${e.message}")
                                }
                                append(e.cause?.stackTraceToString().orEmpty())
                            }
                        }
                    )
                )
            }


        }
    }

    init {
        add(tabs, "dock center")
    }

    companion object {
        val FRAME_ICON: Image = run {
            val toolkit = Toolkit.getDefaultToolkit()
            val mainPanelClass = MainPanel::class.java
            toolkit.getImage(mainPanelClass.getResource("/icons/ignition.png"))
        }

        val THEME_DETECTOR: OsThemeDetector = OsThemeDetector.getDetector()

        val LOGGER = getLogger<MainPanel>()

        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("apple.awt.application.name", "Kindling")
            System.setProperty("apple.laf.useScreenMenuBar", "true")

            EventQueue.invokeLater {
                setupLaf()

                JFrame("Kindling").apply {
                    defaultCloseOperation = JFrame.EXIT_ON_CLOSE
                    preferredSize = Dimension(1280, 800)
                    iconImage = FRAME_ICON

                    val mainPanel = MainPanel()
                    add(mainPanel)
                    pack()
                    jMenuBar = mainPanel.menuBar

                    if (args.isEmpty()) {
                        mainPanel.fileChooser.chooseFiles(mainPanel)?.let { mainPanel.openFiles(it) }
                    } else {
                        args.map(::File).let(mainPanel::openFiles)
                    }

                    Desktop.getDesktop().apply {
                        // MacOS specific stuff
                        runCatching {
                            disableSuddenTermination()
                            setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS)
                            setOpenFileHandler { event ->
                                mainPanel.openFiles(event.files)
                            }
                        }
                    }

                    transferHandler = FileTransferHandler(mainPanel::openFiles)

                    setLocationRelativeTo(null)
                    isVisible = true
                }
            }
        }

        private fun setupLaf() {
            val osTheme = if (THEME_DETECTOR.isDark) DARK_THEME else LIGHT_THEME
            osTheme.display()

            UIManager.getDefaults().apply {
                put("ScrollBar.width", 16)
                put("TabbedPane.tabType", "card")
                put("MenuItem.minimumIconSize", Dimension()) // https://github.com/JFormDesigner/FlatLaf/issues/328
            }

            PlatformDefaults.setGridCellGap(UnitValue(2.0F), UnitValue(2.0F))
        }
    }
}
