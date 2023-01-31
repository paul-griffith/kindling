package io.github.paulgriffith.kindling.core

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.cache.CacheViewer
import io.github.paulgriffith.kindling.idb.IdbViewer
import io.github.paulgriffith.kindling.log.LogViewer
import io.github.paulgriffith.kindling.thread.MultiThreadViewer
import io.github.paulgriffith.kindling.utils.FileExtensionFilter
import io.github.paulgriffith.kindling.utils.loadService
import io.github.paulgriffith.kindling.zip.ZipViewer
import java.io.File
import java.nio.file.Path
import javax.swing.filechooser.FileFilter

interface Tool {
    val title: String
    val description: String
    val icon: FlatSVGIcon
    val extensions: List<String>

    fun open(path: Path): ToolPanel

    val filter: FileExtensionFilter
        get() = FileExtensionFilter(description, extensions)

    companion object {
        val tools: List<Tool> by lazy {
            listOf(
                ZipViewer,
                MultiThreadViewer,
                LogViewer,
                IdbViewer,
                CacheViewer,
            ) + loadService<Tool>().sortedBy { it.title }
        }

        val byFilter: Map<FileFilter, Tool> by lazy {
            tools.associateBy(Tool::filter)
        }

        val byExtension by lazy {
            buildMap {
                for (tool in tools) {
                    for (extension in tool.extensions) {
                        put(extension, tool)
                    }
                }
            }
        }

        operator fun get(file: File): Tool {
            return checkNotNull(
                tools.find { tool ->
                    tool.filter.accept(file)
                },
            ) { "No tool found for $file" }
        }
    }
}

interface MultiTool : Tool {
    fun open(paths: List<Path>): ToolPanel

    override fun open(path: Path): ToolPanel = open(listOf(path))
}

interface ClipboardTool : Tool {
    fun open(data: String): ToolPanel
}

class ToolOpeningException(message: String, cause: Throwable? = null) : Exception(message, cause)
