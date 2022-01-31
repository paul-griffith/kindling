package io.github.paulgriffith

import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector
import com.formdev.flatlaf.extras.components.FlatTextArea
import io.github.paulgriffith.main.TabPanel
import io.github.paulgriffith.main.ThemeButton
import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.FileTransferHandler
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.Tool
import io.github.paulgriffith.utils.ToolOpeningException
import io.github.paulgriffith.utils.getLogger
import io.github.paulgriffith.utils.truncate
import net.miginfocom.layout.PlatformDefaults
import net.miginfocom.layout.UnitValue
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Image
import java.awt.Toolkit
import java.io.File
import java.lang.Boolean.getBoolean
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.UIManager
import javax.swing.filechooser.FileView
import kotlin.io.path.nameWithoutExtension

class MainPanel : JPanel(MigLayout("ins 6, fill")) {
    private val fileChooser = JFileChooser().apply {
        isMultiSelectionEnabled = true
        fileView = object : FileView() {
            override fun getIcon(file: File): Icon? {
                return if (file.isFile) {
                    Tool.getOrNull(file)?.icon?.derive(16, 16)
                } else {
                    null
                }
            }
        }

        Tool.byFilter.keys.forEach(this::addChoosableFileFilter)
        fileFilter = Tool.values().first().filter
    }

    private val chooseButton = JButton(
        Action(
            name = "Choose File(s)",
            icon = FlatSVGIcon("icons/bx-file-find.svg")
        ) {
            when (fileChooser.showOpenDialog(this@MainPanel)) {
                JFileChooser.APPROVE_OPTION -> {
                    val selectedTool = Tool.byFilter[fileChooser.fileFilter]
                    fileChooser.selectedFiles.forEach { file ->
                        val actualTool = selectedTool ?: Tool[file]
                        actualTool.openFragile(file.toPath())
                    }
                }
            }
        }
    )

    private val tabs = TabPanel()

    /**
     * Opens a tool (blocking). In the event of any error, opens an 'Error' tab instead.
     */
    private fun Tool.openFragile(path: Path) {
        runCatching {
            val toolPanel = panelOpener(path)
            tabs.addTab(
                path.nameWithoutExtension.truncate(),
                toolPanel.icon,
                toolPanel,
                path.toString(),
            )
        }.getOrElse { ex ->
            LOGGER.error("Failed to open $path as a ${this.name}", ex)

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
                                appendLine("Error opening $path: ${ex.message}")
                            }
                            append(ex.cause?.stackTraceToString().orEmpty())
                        }
                    }
                )
            )
        }
        tabs.selectedIndex = tabs.tabCount - 1
    }

    /**
     * Opens a list of files, attempting to determine the appropriate tool automatically (based on [Tool.filter])
     */
    fun openFiles(files: List<File>) {
        files.forEach { file ->
            Tool[file].openFragile(file.toPath())
        }
    }

    init {
        tabs.leadingComponent = chooseButton
        tabs.trailingComponent = JPanel(MigLayout("ins 0, fill")).apply {
            add(ThemeButton(), "align right")
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

        val LOGGER = getLogger<MainPanel>()

        @JvmStatic
        fun main(args: Array<String>) = EventQueue.invokeLater {
            setupLaf()

            JFrame("Kindling").apply {
                defaultCloseOperation = JFrame.EXIT_ON_CLOSE
                preferredSize = Dimension(1280, 800)
                iconImage = FRAME_ICON

                val mainPanel = MainPanel()
                add(mainPanel)
                pack()

                args.map(::File).let(mainPanel::openFiles)

                transferHandler = FileTransferHandler(mainPanel::openFiles)

                setLocationRelativeTo(null)
                isVisible = true
            }
        }

        private fun setupLaf() {
            UIManager.put("ScrollBar.width", 16)
            UIManager.put("TabbedPane.showTabSeparators", true)
            UIManager.put("TabbedPane.selectedBackground", UIManager.getColor("TabbedPane.highlight"))
            PlatformDefaults.setGridCellGap(UnitValue(2.0F), UnitValue(2.0F))
            FlatLightLaf.setup()

            if (getBoolean("kindling.debug")) {
                FlatUIDefaultsInspector.install("ctrl shift Y")
            }
        }
    }
}
