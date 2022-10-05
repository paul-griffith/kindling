package io.github.paulgriffith.kindling.core

import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.FileExtensionFilter
import io.github.paulgriffith.kindling.utils.escapeHtml
import io.github.paulgriffith.kindling.utils.exportToCSV
import io.github.paulgriffith.kindling.utils.exportToXLSX
import io.github.paulgriffith.kindling.utils.getValue
import io.github.paulgriffith.kindling.utils.homeLocation
import io.github.paulgriffith.kindling.utils.toHtmlLink
import net.miginfocom.swing.MigLayout
import org.jsoup.Jsoup
import java.io.File
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JFileChooser
import javax.swing.JMenu
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.UIManager
import javax.swing.filechooser.FileFilter
import javax.swing.table.TableModel
import kotlin.io.path.Path

abstract class ToolPanel(
    layoutConstraints: String = "ins 6, fill, hidemode 3"
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
                }
            )
        }
    }

    protected fun linkifyStackTrace(stacktrace: List<String>, version: String): List<String> {
        val regex = """(.*/)?(?<path>.*)\..*\(.*\)""".toRegex()

        val classMap = when {
            "8.1" in version -> classMap81
            "8.0" in version -> classMap80
            "7.9" in version -> classMap79
            else -> return stacktrace
        }

        return stacktrace.map { line ->
            val escapedLine = line.escapeHtml()
            val matchResult = regex.find(line)

            if (matchResult != null) {
                val path by matchResult.groups
                val url: String? = classMap[path.value]
                if (url != null) return@map escapedLine.toHtmlLink(url)
            }
            return@map escapedLine
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
            val action: (TableModel, File) -> Unit
        ) {
            CSV("Comma Separated Values", "csv", TableModel::exportToCSV),
            EXCEL("Excel Workbook", "xlsx", TableModel::exportToXLSX);

            val fileFilter: FileFilter = FileExtensionFilter(description, listOf(extension))
        }

        private val classMap81 by lazy {
            val path = Path("core/src/main/resources/All Classes-8.1.html")
            createIaClassMapFromDocs(path)
        }

        private val classMap80 by lazy {
            val path = Path("core/src/main/resources/All Classes-8.0.html")
            createIaClassMapFromDocs(path)
        }

        private val classMap79 by lazy {
            val path = Path("core/src/main/resources/All Classes-7.9.html")
            createIaClassMapFromDocs(path)
        }

        private fun createIaClassMapFromDocs(path: Path): Map<String, String> {
            return buildMap {
                Jsoup.parse(path.toFile()).select("li").forEach { elem ->
                    val a = elem.select("a")

                    val className = a.text()
                    val packageName = a.attr("title").takeLastWhile { it != ' ' }

                    val classPath = "$packageName.$className"
                    val link = a.attr("href")

                    put(classPath, link)
                }
            }
        }
    }
}
