package io.github.paulgriffith.kindling.core

import io.github.paulgriffith.kindling.utils.*
import io.github.paulgriffith.kindling.utils.Action
import net.miginfocom.swing.MigLayout
import java.io.File
import javax.swing.*
import javax.swing.filechooser.FileFilter
import javax.swing.table.TableModel

abstract class ToolPanel(
    layoutConstraints: String = "ins 6, fill, hidemode 3"
) : JPanel(MigLayout(layoutConstraints)) {
    abstract val icon: Icon

    open fun customizePopupMenu(menu: JPopupMenu) = Unit

    protected fun exportMenu(defaultFileName: String = "", modelSupplier: () -> TableModel): JMenu =
        JMenu("Export").apply {
            for (format in ExportFormat.values()) {
                add(
                    Action("Export as ${format.extension.uppercase()}") {
                        exportFileChooser.selectedFile = File(defaultFileName)
                        exportFileChooser.resetChoosableFileFilters()
                        exportFileChooser.fileFilter = format.fileFilter
                        if (exportFileChooser.showSaveDialog(this.parent.parent) == JFileChooser.APPROVE_OPTION) {
                            val selectedFile =
                                if (exportFileChooser.selectedFile.absolutePath.endsWith(format.extension)) {
                                    exportFileChooser.selectedFile
                                } else {
                                    File(exportFileChooser.selectedFile.absolutePath + ".${format.extension}")
                                }
                            format.action.invoke(modelSupplier(), selectedFile)
                        }
                    }
                )
            }
        }

//    protected lateinit var defaultFileName : String

    companion object {
        val exportFileChooser = JFileChooser(homeLocation).apply {
            isMultiSelectionEnabled = false
            isAcceptAllFileFilterUsed = false
            fileView = CustomIconView()
            this.selectedFile
            UIManager.addPropertyChangeListener { e ->
                if (e.propertyName == "lookAndFeel") {
                    updateUI()
                }
            }
        }

        private enum class ExportFormat(description: String, val extension: String, val action: (TableModel, File) -> Unit) {
            CSV("Comma Separated Values", "csv", TableModel::exportToCSV),
            EXCEL("Excel Workbook", "xlsx", TableModel::exportToXLSX);

            val fileFilter: FileFilter = FileExtensionFilter(description, listOf(extension))
        }
    }
}
