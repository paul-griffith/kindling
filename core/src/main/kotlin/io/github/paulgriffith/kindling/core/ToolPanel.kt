package io.github.paulgriffith.kindling.core

import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.FileExtensionFilter
import io.github.paulgriffith.kindling.utils.Properties
import io.github.paulgriffith.kindling.utils.exportToCSV
import io.github.paulgriffith.kindling.utils.exportToXLSX
import io.github.paulgriffith.kindling.utils.homeLocation
import net.miginfocom.swing.MigLayout
import java.io.File
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

    protected fun exportMenu(defaultFileName: String = "", modelSupplier: () -> TableModel): JMenu =
        JMenu("Export").apply {
            for (format in ExportFormat.values()) {
                add(
                    Action("Export as ${format.extension.uppercase()}") {
                        exportFileChooser.apply {
                            selectedFile = File(defaultFileName)
                            resetChoosableFileFilters()
                            fileFilter = format.fileFilter
                            if (showSaveDialog(this@ToolPanel) == JFileChooser.APPROVE_OPTION) {
                                val selectedFile =
                                    if (selectedFile.absolutePath.endsWith(format.extension)) {
                                        selectedFile
                                    } else {
                                        File(selectedFile.absolutePath + ".${format.extension}")
                                    }
                                format.action.invoke(modelSupplier(), selectedFile)
                            }
                        }
                    },
                )
            }
        }

    companion object {
        val exportFileChooser = JFileChooser(homeLocation).apply {
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

        val classMapsByVersion by lazy {
            val versions = requireNotNull(this::class.java.getResourceAsStream("/javadocs")).reader().readLines()
            versions.associateWith { version ->
                Properties(requireNotNull(this::class.java.getResourceAsStream("/javadocs/$version/links.properties")))
            }
        }
    }
}
