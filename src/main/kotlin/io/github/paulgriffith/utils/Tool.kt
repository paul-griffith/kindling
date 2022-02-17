package io.github.paulgriffith.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.backup.BackupView
import io.github.paulgriffith.cache.CacheView
import io.github.paulgriffith.idb.IdbView
import io.github.paulgriffith.log.LogPanel
import io.github.paulgriffith.thread.ThreadView
import java.io.File
import java.nio.file.Path
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter

class ToolOpeningException(message: String, cause: Throwable) : Exception(message, cause)

enum class Tool(
    val filter: FileFilter,
    val icon: FlatSVGIcon,
) {
    IdbViewer(
        filter = FileNameExtensionFilter("Ignition .idb (SQLite3) files", "idb"),
        icon = FlatSVGIcon("icons/bx-hdd.svg")
    ) {
        override fun openTool(path: Path): ToolPanel = IdbView(path)
    },
    LogViewer(
        filter = FileNameExtensionFilter("Ignition wrapper.log files", "log", "1", "2", "3", "4", "5"),
        icon = FlatSVGIcon("icons/bx-hdd.svg")
    ) {
        override fun openTool(path: Path): ToolPanel = LogPanel.LogView(path)
    },
    ThreadViewer(
        filter = FileNameExtensionFilter("Ignition Thread Dump .json files", "json"),
        icon = FlatSVGIcon("icons/bx-chip.svg")
    ) {
        override fun openTool(path: Path): ToolPanel = ThreadView(path)
    },
    CacheViewer(
        filter = FileNameExtensionFilter("S+F Cache ZIP Files", "zip"),
        icon = FlatSVGIcon("icons/bx-data.svg")
    ) {
        override fun openTool(path: Path): ToolPanel = CacheView(path)
    },
    BackupViewer(
        filter = FileNameExtensionFilter("GWBK Files", "gwbk"),
        icon = FlatSVGIcon("icons/bx-archive.svg")
    ) {
        override fun openTool(path: Path): ToolPanel = BackupView(path)
    };

    abstract fun openTool(path: Path): ToolPanel

    companion object {
        operator fun get(file: File): Tool {
            return getOrNull(file) ?: throw IllegalArgumentException("No tool found for $file")
        }

        fun getOrNull(file: File): Tool? {
            return values().firstOrNull { tool ->
                val result = tool.filter.accept(file)
                LOGGER.trace("%s %s %s", tool.name, if (result) "accepted" else "rejected", file)
                result
            }
        }

        private val LOGGER = getLogger<Tool>()

        val byFilter = values().associateBy(Tool::filter)
    }
}
