package io.github.paulgriffith.kindling.core

import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.FileExtensionFilter
import io.github.paulgriffith.kindling.utils.Properties
import io.github.paulgriffith.kindling.utils.exportToCSV
import io.github.paulgriffith.kindling.utils.exportToXLSX
import io.github.paulgriffith.kindling.utils.homeLocation
import net.miginfocom.swing.MigLayout
import java.io.File
import java.io.InputStream
import java.util.Properties
import javax.swing.Icon
import javax.swing.JFileChooser
import javax.swing.JMenu
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.UIManager
import javax.swing.filechooser.FileFilter
import javax.swing.table.TableModel

abstract class ToolPanel(
    layoutConstraints: String = "ins 6, fill, hidemode 3",
) : JPanel(MigLayout(layoutConstraints)) {
    abstract val icon: Icon

    open fun customizePopupMenu(menu: JPopupMenu) = Unit

    protected fun exportMenu(modelSupplier: () -> TableModel): JMenu = JMenu("Export").apply {
        for (format in ExportFormat.values()) {
            add(
                Action("Export as ${format.extension.uppercase()}") {
                    exportFileChooser.resetChoosableFileFilters()
                    exportFileChooser.fileFilter = format.fileFilter
                    if (exportFileChooser.showSaveDialog(this.parent.parent) == JFileChooser.APPROVE_OPTION) {
                        val selectedFile = if (exportFileChooser.selectedFile.endsWith(format.extension)) {
                            exportFileChooser.selectedFile
                        } else {
                            File(exportFileChooser.selectedFile.absolutePath + ".${format.extension}")
                        }
                        format.action.invoke(modelSupplier(), selectedFile)
                    }
                },
            )
        }
    }

    companion object {
        private val exportFileChooser = JFileChooser(homeLocation).apply {
            isMultiSelectionEnabled = false
            isAcceptAllFileFilterUsed = false
            fileView = CustomIconView()

            UIManager.addPropertyChangeListener { e ->
                if (e.propertyName == "lookAndFeel") {
                    updateUI()
                }
            }
        }

        private enum class ExportFormat(
            description: String,
            val extension: String,
            val action: (TableModel, File) -> Unit,
        ) {
            CSV("Comma Separated Values", "csv", TableModel::exportToCSV),
            EXCEL("Excel Workbook", "xlsx", TableModel::exportToXLSX);

            val fileFilter: FileFilter = FileExtensionFilter(description, listOf(extension))
        }

        val classMapsByVersion = this::class.java.getResourceAsStream("/javadocs")!!
            .reader()
            .readLines()
            .associateWith { version ->
                requireNotNull(this::class.java.getResourceAsStream("/javadocs/$version/links.properties")).parseClassLinks()
            }

        private fun InputStream.parseClassLinks(): Properties {
            return Properties(this)
        }
    }
}
