package io.github.paulgriffith.utils

import io.github.paulgriffith.MainPanel
import io.github.paulgriffith.core.CustomIconView
import net.miginfocom.swing.MigLayout
import java.io.File
import javax.swing.Icon
import javax.swing.JFileChooser
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.UIManager

abstract class ToolPanel(
    layoutConstraints: String = "ins 6, fill, hidemode 3",
) : JPanel(MigLayout(layoutConstraints)) {
    abstract val icon: Icon

    /*
    The export button (JMenu) will only contain option which are implemented in the respective subclass.
    To implement a new export format:
    - Create a function on the desired subclass which exports the desired format.
    - Add this function to the exportFormats map with its correspond ExportTool
    - If implemented the first export format for a given ToolPanel, add this line of code before the search bar:
      getMenuBar()?.let { add(it, "align right, gapright 8") }
     */

    private val exportFileChooser = JFileChooser(MainPanel.homeLocation).apply {
        isMultiSelectionEnabled = false
        fileView = CustomIconView()
        fileSelectionMode = JFileChooser.FILES_ONLY

        UIManager.addPropertyChangeListener { e ->
            if (e.propertyName == "lookAndFeel") {
                updateUI()
            }
        }
    }

    protected var exportFormats: MutableMap<ExportTool, (file: File) -> Unit> = mutableMapOf<ExportTool, (File) -> Unit>()

    private fun exportData(file: File) {
        // Will throw IllegalArgumentException if somehow a file of type not specified in ExportTool is given.
        exportFormats[ExportTool[file]]!!.invoke(file)
    }

    protected fun getMenuBar(): JMenuBar? {
        return JMenuBar().apply {
            add(
                JMenu("Export").apply {
                    for (format in exportFormats.keys) {
                        add(
                            Action(
                                name = "Export as ${format.ext.uppercase()}",
                            ) {
                                exportFileChooser.fileFilter = format.filter
                                exportFileChooser.showSaveDialog(this).let {
                                    if (it == JFileChooser.APPROVE_OPTION) {
                                        if (!exportFileChooser.selectedFile.absolutePath.endsWith(format.ext)) {
                                            exportData(File(exportFileChooser.selectedFile.absolutePath + ".${format.ext}"))
                                        } else {
                                            exportData(exportFileChooser.selectedFile)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            )
        }.takeIf { it.componentCount > 0 }
    }

    open fun customizePopupMenu(menu: JPopupMenu) = Unit
}
