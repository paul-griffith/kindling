package io.github.paulgriffith.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.backupviewer.BackupView
import io.github.paulgriffith.cacheviewer.CacheView
import io.github.paulgriffith.idb.IdbView
import io.github.paulgriffith.threadviewer.ThreadView
import java.io.File
import java.nio.file.Path
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter

class ToolOpeningException(message: String, cause: Throwable) : Exception(message, cause)

enum class Tool(
    val filter: FileFilter,
    val panelOpener: (path: Path) -> ToolPanel,
    val icon: FlatSVGIcon,
) {
    IdbViewer(
        filter = FileNameExtensionFilter("Ignition .idb (SQLite3) files", "idb"),
        panelOpener = ::IdbView,
        icon = FlatSVGIcon("icons/bx-hdd.svg")
    ),
    ThreadViewer(
        filter = FileNameExtensionFilter("Ignition Thread Dump .json files", "json"),
        panelOpener = ::ThreadView,
        icon = FlatSVGIcon("icons/bx-chip.svg")
    ),
    CacheViewer(
        filter = FileNameExtensionFilter("S+F Cache ZIP Files", "zip"),
        panelOpener = ::CacheView,
        icon = FlatSVGIcon("icons/bx-data.svg")
    ),
    BackupViewer(
        filter = FileNameExtensionFilter("GWBK Files", "gwbk"),
        panelOpener = ::BackupView,
        icon = FlatSVGIcon("icons/bx-archive.svg")
    );

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
