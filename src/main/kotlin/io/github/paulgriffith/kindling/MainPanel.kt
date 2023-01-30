package io.github.paulgriffith.kindling

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector
import com.formdev.flatlaf.extras.components.FlatTextArea
import com.jthemedetecor.OsThemeDetector
import com.sun.tools.attach.VirtualMachine
import io.github.paulgriffith.kindling.attach.AttachedPanel
import io.github.paulgriffith.kindling.core.ClipboardTool
import io.github.paulgriffith.kindling.core.CustomIconView
import io.github.paulgriffith.kindling.core.Kindling
import io.github.paulgriffith.kindling.core.MultiTool
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolOpeningException
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.internal.FileTransferHandler
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.DARK_THEME
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.LIGHT_THEME
import io.github.paulgriffith.kindling.utils.TabStrip
import io.github.paulgriffith.kindling.utils.chooseFiles
import io.github.paulgriffith.kindling.utils.display
import io.github.paulgriffith.kindling.utils.getLogger
import io.github.paulgriffith.kindling.utils.onMenuSelected
import io.github.paulgriffith.kindling.utils.truncate
import net.miginfocom.layout.PlatformDefaults
import net.miginfocom.layout.UnitValue
import net.miginfocom.swing.MigLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
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

class MainPanel(empty: Boolean) : JPanel(MigLayout("ins 6, fill")) {
    private val fileChooser = JFileChooser(Kindling.homeLocation).apply {
        isMultiSelectionEnabled = true
        fileView = CustomIconView()

        Tool.byFilter.keys.forEach(this::addChoosableFileFilter)
        fileFilter = Tool.tools.first().filter

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

    private val tabs = TabStrip()
    private val openButton = JButton(openAction)

    private val menuBar = JMenuBar().apply {
        add(
            JMenu("File").apply {
                add(openAction)
                for (tool in Tool.tools) {
                    add(
                        Action(
                            name = "Open ${tool.title}",
                        ) {
                            fileChooser.fileFilter = tool.filter
                            fileChooser.chooseFiles(this@MainPanel)?.let { selectedFiles ->
                                openFiles(selectedFiles, tool)
                            }
                        },
                    )
                }
            },
        )
        add(
            JMenu("Paste").apply {
                for (clipboardTool in Tool.tools.filterIsInstance<ClipboardTool>()) {
                    add(
                        Action(
                            name = "Paste ${clipboardTool.title}",
                        ) {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                                val clipString = clipboard.getData(DataFlavor.stringFlavor) as String
                                openOrError(clipboardTool.title, "clipboard data") {
                                    clipboardTool.open(clipString)
                                }
                            } else {
                                println("No string data found on clipboard")
                            }
                        },
                    )
                }
            },
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
                    },
                )
                add(
                    JCheckBoxMenuItem("Dark", THEME_DETECTOR.isDark).apply {
                        addItemListener {
                            if (it.stateChange == ItemEvent.SELECTED) {
                                DARK_THEME.display(true)
                            }
                        }
                        group.add(this)
                    },
                )
            },
        )
        add(
            JMenu("Attach").apply {
                onMenuSelected {
                    removeAll()
                    for (descriptor in VirtualMachine.list()) {
                        add(
                            Action(
                                name = descriptor.displayName().truncate(50),
                                description = descriptor.displayName(),
                            ) {
                                openOrError("attached JVM", descriptor.displayName()) {
                                    AttachedPanel(descriptor)
                                }
                            },
                        )
                    }
                }
            },
        )
        add(
            JMenu("Debug").apply
            {
                add(
                    Action("UI Inspector") {
                        FlatUIDefaultsInspector.show()
                    },
                )
            },
        )
    }

    /**
     * Opens a path in a tool (blocking). In the event of any error, opens an 'Error' tab instead.
     */
    private fun openOrError(title: String, description: String, openFunction: () -> ToolPanel) {
        synchronized(treeLock) {
            val child = getComponent(0)
            if (child == openButton) {
                remove(openButton)
                add(tabs, "dock center")
            }
        }
        runCatching {
            val toolPanel = openFunction()
            tabs.addTab(
                toolPanel.tabName,
                toolPanel.icon,
                toolPanel,
                toolPanel.tabTooltip,
            )
        }.getOrElse { ex ->
            LOGGER.error("Failed to open $description as a $title", ex)
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
                                appendLine("Error opening $description: ${ex.message}")
                            }
                            append((ex.cause ?: ex).stackTraceToString())
                        }
                    },
                ),
            )
        }
        tabs.selectedIndex = tabs.tabCount - 1
    }

    fun openFiles(files: List<File>, tool: Tool? = null) {
        if (tool is MultiTool) {
            openOrError(tool.title, files.joinToString()) {
                tool.open(files.map(File::toPath))
            }
        } else {
            files.groupBy { tool ?: Tool[it] }
                .forEach { (tool, filesByTool) ->
                    if (tool is MultiTool) {
                        openOrError(tool.title, filesByTool.joinToString()) {
                            tool.open(filesByTool.map(File::toPath))
                        }
                    } else {
                        filesByTool.forEach { file ->
                            openOrError(tool.title, file.toString()) {
                                tool.open(file.toPath())
                            }
                        }
                    }
                }
        }
    }

    init {
        if (empty) {
            add(openButton, "dock center")
        } else {
            add(tabs, "dock center")
        }
    }

    companion object {
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
                    iconImage = Kindling.frameIcon

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
                put("Tree.showDefaultIcons", true)
            }

            PlatformDefaults.setGridCellGap(UnitValue(2.0F), UnitValue(2.0F))
        }
    }
}
