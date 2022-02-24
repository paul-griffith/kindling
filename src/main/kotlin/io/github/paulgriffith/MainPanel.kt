package io.github.paulgriffith

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector
import com.formdev.flatlaf.extras.components.FlatTextArea
import com.jthemedetecor.OsThemeDetector
import io.github.paulgriffith.core.CustomIconView
import io.github.paulgriffith.core.TabPanel
import io.github.paulgriffith.core.ThemeButton
import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.FileTransferHandler
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.MultiTool
import io.github.paulgriffith.utils.Tool
import io.github.paulgriffith.utils.ToolOpeningException
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
import java.awt.event.ActionEvent
import java.io.File
import java.lang.Boolean.getBoolean
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

    private val singleFileChooser = JFileChooser(homeLocation).apply {
        isMultiSelectionEnabled = true
        fileView = CustomIconView()

        Tool.byFilter.keys.forEach(this::addChoosableFileFilter)
        fileFilter = Tool.values().first().filter
    }

    private val openAction = Action(
        name = "Open...",
    ) {
        if (singleFileChooser.showOpenDialog(this@MainPanel) == JFileChooser.APPROVE_OPTION) {
            val selectedTool: Tool? = Tool.byFilter[singleFileChooser.fileFilter]
            openFiles(singleFileChooser.selectedFiles.toList(), selectedTool)
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
                            singleFileChooser.fileFilter = tool.filter
                            if (singleFileChooser.showOpenDialog(this@MainPanel) == JFileChooser.APPROVE_OPTION) {
                                openFiles(singleFileChooser.selectedFiles.toList(), tool)
                            }
                        }
                    )
                }
            }
        )
    }

    private val tabs = TabPanel()

    /**
     * Opens a tool (blocking). In the event of any error, opens an 'Error' tab instead.
     */
    private fun safeOpen(tool: Tool, paths: List<Path>) {
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
            safeOpen(tool, files.map(File::toPath))
        } else {
            files.forEach { file ->
                safeOpen((tool ?: Tool[file]), listOf(file.toPath()))
            }
        }
    }

    init {
        tabs.trailingComponent = JPanel(MigLayout("ins 0, fill")).apply {
            add(ThemeButton(detector.isDark()), "align right")
        }
        add(tabs, "dock center")
    }

    @Suppress("UNNECESSARY_SAFE_CALL") // updateUI is called before our class is constructed
    override fun updateUI() {
        super.updateUI()
        // the file chooser probably won't be visible when the theme is updated, so ensure that it rebuilds
        singleFileChooser?.updateUI()
    }

    companion object {
        val FRAME_ICON: Image = run {
            val toolkit = Toolkit.getDefaultToolkit()
            val mainPanelClass = MainPanel::class.java
            toolkit.getImage(mainPanelClass.getResource("/icons/ignition.png"))
        }

        val detector: OsThemeDetector = OsThemeDetector.getDetector()

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
                        mainPanel.openAction.actionPerformed(ActionEvent(this, -1, null))
                    }

                    transferHandler = FileTransferHandler(mainPanel::openFiles)

                    setLocationRelativeTo(null)
                    isVisible = true
                }
            }
        }

        private fun setupLaf() {
            UIManager.put("ScrollBar.width", 16)
            UIManager.put("TabbedPane.showTabSeparators", true)
            UIManager.put("TabbedPane.selectedBackground", UIManager.getColor("TabbedPane.highlight"))
            PlatformDefaults.setGridCellGap(UnitValue(2.0F), UnitValue(2.0F))
            if (detector.isDark) FlatDarkLaf.setup() else FlatLightLaf.setup()

            Desktop.getDesktop().apply {
                disableSuddenTermination()
                setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS)
            }

            if (getBoolean("kindling.debug")) {
                FlatUIDefaultsInspector.install("ctrl shift Y")
            }
        }
    }
}
