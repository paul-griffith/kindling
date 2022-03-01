package io.github.paulgriffith

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector
import com.formdev.flatlaf.extras.components.FlatTextArea
import com.jthemedetecor.OsThemeDetector
import io.github.paulgriffith.core.CustomIconView
import io.github.paulgriffith.core.TabPanel
import io.github.paulgriffith.core.ThemeButton
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
import java.io.File
import java.nio.file.Path
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
     * Opens a tool (blocking). In the event of any error, opens an 'Error' tab instead.
     */
    private fun openOrError(tool: Tool, paths: List<Path>) {
        runCatching {
            val toolPanel = if (tool is MultiTool) {
                tool.open(paths.toList())
            } else {
                tool.open(paths.single())
            }
            tabs.addTab(
                toolPanel.name.truncate(),
                toolPanel.icon,
                toolPanel,
                toolPanel.toolTipText,
            )
        }.getOrElse { ex ->
            LOGGER.error("Failed to open ${paths.joinToString()} as a ${tool.title}", ex)
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
                                appendLine("Error opening ${paths.joinToString()}: ${ex.message}")
                            }
                            append(ex.cause?.stackTraceToString().orEmpty())
                        }
                    }
                )
            )
        }
        tabs.selectedIndex = tabs.tabCount - 1
    }

    fun openFiles(files: List<File>, tool: Tool? = null) {
        if (tool is MultiTool) {
            openOrError(
                tool = tool,
                paths = files.map(File::toPath)
            )
        } else {
            files.forEach { file ->
                openOrError(
                    tool = tool ?: Tool[file],
                    paths = listOf(file.toPath())
                )
            }
        }
    }

    init {
        tabs.trailingComponent = JPanel(MigLayout("ins 0, fill")).apply {
            add(ThemeButton(THEME_DETECTOR.isDark), "align right")
        }
        add(tabs, "dock center")
    }

    @Suppress("UNNECESSARY_SAFE_CALL") // updateUI is called before our class is constructed
    override fun updateUI() {
        super.updateUI()
        // the file chooser probably won't be visible when the theme is updated, so ensure that it rebuilds
        fileChooser?.updateUI()
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

                    args.map(::File).let(mainPanel::openFiles)

                    if (args.isEmpty()) {
                        mainPanel.fileChooser.chooseFiles(mainPanel)?.let { selectedFiles ->
                            mainPanel.openFiles(selectedFiles)
                        }
                    }

                    transferHandler = FileTransferHandler(mainPanel::openFiles)

                    setLocationRelativeTo(null)
                    isVisible = true
                }
            }
        }

        private fun setupLaf() {
            UIManager.getDefaults().apply {
                put("ScrollBar.width", 16)
                put("TabbedPane.showTabSeparators", true)
                put("TabbedPane.selectedBackground", getColor("TabbedPane.highlight"))
                put("MenuItem.minimumIconSize", Dimension()) // https://github.com/JFormDesigner/FlatLaf/issues/328
            }

            PlatformDefaults.setGridCellGap(UnitValue(2.0F), UnitValue(2.0F))
            if (THEME_DETECTOR.isDark) DARK_THEME.display() else LIGHT_THEME.display()

            Desktop.getDesktop().apply {
                runCatching {
                    disableSuddenTermination()
                    setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS)
                }
            }
        }
    }
}
