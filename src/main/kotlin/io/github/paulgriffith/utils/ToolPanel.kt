package io.github.paulgriffith.utils

import net.miginfocom.swing.MigLayout
import java.io.File
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JPopupMenu

abstract class ToolPanel(
    layoutConstraints: String = "ins 6, fill, hidemode 3",
) : JPanel(MigLayout(layoutConstraints)) {
    abstract val icon: Icon
    protected var exportFormats: MutableMap<FileExtension, (file: File) -> Unit> = mutableMapOf<FileExtension, (File) -> Unit>().apply {
        put(FileExtension.csv) { throw NotImplementedError("CSV format not supported by this tool type.") }
        put(FileExtension.xml) { throw NotImplementedError("XML format not supported by this tool type.") }
        put(FileExtension.xlsx) { throw NotImplementedError("XLSX format not supported by this tool type.") }
    }
    fun exportData(file: File) {
        try {
            exportFormats[FileExtension.valueOf(file.extension)]!!.invoke(file)
        } catch (e: java.lang.IllegalArgumentException) {
            println("Illegal Argument: ${file.extension}")
        } catch (e: NotImplementedError) {
            println("Not Implemented")
        }
    }

    open fun customizePopupMenu(menu: JPopupMenu) = Unit
}
