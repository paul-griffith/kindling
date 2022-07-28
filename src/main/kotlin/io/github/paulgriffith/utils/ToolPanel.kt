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

    /*
     By default, all ToolPanels throw a NotImplementedError.
     Implement these export formats by defining a function to export given the output file and overriding these
     entries
     */
    protected var exportFormats: MutableMap<String, (file: File) -> Unit> = mutableMapOf<String, (File) -> Unit>().apply {
        ExportTool.values().forEach{
            put(it.ext) {file -> throw NotImplementedError("${file.extension} is not yet implemented for this input file type.")}
        }
    }
    fun exportData(file: File) {
        exportFormats[ExportTool[file].ext]!!.invoke(file)
    }

    open fun customizePopupMenu(menu: JPopupMenu) = Unit
}
