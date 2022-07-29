package io.github.paulgriffith.utils

import java.io.File
import javax.swing.filechooser.FileFilter

sealed interface ExportTool {
    val ext: String
    val description: String

    val filter: FileFilter
        get() = FileExtensionFilter(description, listOf(ext))

    object ExportCSV: ExportTool {
        override val ext = "csv"
        override val description = ".csv (Comma-separated values)"
    }
    object ExportXLSX: ExportTool {
        override val ext = "xlsx"
        override val description = ".xlsx (Excel Spreadsheet)"
    }
    object ExportXML: ExportTool {
        override val ext = "xml"
        override val description = ".xml (Extended Markup Language)"
    }

    companion object {
        operator fun get(file: File): ExportTool {
            return values().firstOrNull {
                it.filter.accept(file)
            } ?: throw java.lang.IllegalArgumentException("${file.extension} is not a valid export format.")
        }

        // Add tools to this list to make available on the exports menu
        fun values(): List<ExportTool> = listOf(ExportCSV, ExportXLSX, ExportXML)
    }


}