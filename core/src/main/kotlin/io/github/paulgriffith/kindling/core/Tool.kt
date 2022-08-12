package io.github.paulgriffith.kindling.core

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.utils.FileExtensionFilter
import io.github.paulgriffith.kindling.utils.loadService
import java.io.File
import java.nio.file.Path
import javax.swing.filechooser.FileFilter

class ToolOpeningException(message: String, cause: Throwable) : Exception(message, cause)

interface MultiTool : Tool {
    fun open(paths: List<Path>): ToolPanel
}

interface Tool {
    val title: String
    val description: String
    val icon: FlatSVGIcon
    val extensions: List<String>

    fun open(path: Path): ToolPanel

    val filter: FileFilter
        get() = FileExtensionFilter(description, extensions)

    companion object {
        val tools: List<Tool> by lazy { loadService<Tool>().sortedBy { it.title } }
        val byFilter: Map<FileFilter, Tool> by lazy { tools.associateBy(Tool::filter) }

        operator fun get(file: File): Tool {
            return requireNotNull(getOrNull(file)) { "No tool found for $file" }
        }

        fun getOrNull(file: File): Tool? {
            return tools.firstOrNull { tool ->
                tool.filter.accept(file)
            }
        }
    }
}
