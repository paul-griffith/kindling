package io.github.paulgriffith

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector
import com.formdev.flatlaf.extras.components.FlatTextArea
import com.jthemedetecor.OsThemeDetector
import io.github.paulgriffith.core.CustomIconView
import io.github.paulgriffith.core.TabPanel
import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.DARK_THEME
import io.github.paulgriffith.utils.FileTransferHandler
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.LIGHT_THEME
import io.github.paulgriffith.utils.MultiTool
import io.github.paulgriffith.utils.Tool
import io.github.paulgriffith.utils.ToolOpeningException
import io.github.paulgriffith.utils.chooseFiles
import io.github.paulgriffith.utils.display
import io.github.paulgriffith.utils.getLogger
import io.github.paulgriffith.utils.truncate
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
import javax.swing.JButton
import javax.swing.JCheckBoxMenuItem
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.io.path.Path

class MainPanel(empty: Boolean) : JPanel(MigLayout("ins 6, fill")) {

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

    private val openAction = Action(
        name = "Open...",
    ) {
        fileChooser.chooseFiles(this)?.let { selectedFiles ->
            val selectedTool: Tool? = Tool.byFilter[fileChooser.fileFilter]
            openFiles(selectedFiles, selectedTool)
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

    init {
        if (empty) {
            val openButton = JButton(openAction)
            openButton.addActionListener {
                remove(openButton)
                add(tabs, "dock center")
            }
            add(openButton, "dock center")
        } else {
            add(tabs, "dock center")
        }
    }

    companion object {
        val homeLocation: File = Path(System.getProperty("user.home"), "Downloads").toFile()

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

                    val mainPanel = MainPanel(args.isEmpty())
                    add(mainPanel)
                    pack()
                    jMenuBar = mainPanel.menuBar

                    if (args.isNotEmpty()) {
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
