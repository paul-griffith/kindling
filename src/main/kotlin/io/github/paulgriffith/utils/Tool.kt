package io.github.paulgriffith.utils

import io.github.paulgriffith.backupviewer.BackupViewer
import io.github.paulgriffith.cacheviewer.CacheView
import io.github.paulgriffith.logviewer.LogView
import io.github.paulgriffith.threadviewer.ThreadView
import java.io.File
import java.nio.file.Path
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter

enum class Tool(
    val filter: FileFilter,
    val panelOpener: (path: Path) -> ToolPanel,
    val fileDescription: String,
) {
    LogViewer(
        filter = FileNameExtensionFilter("Ignition Log .idb files", "idb"),
        panelOpener = ::LogView,
        fileDescription = "log file",
    ),
    ThreadViewer(
        filter = FileNameExtensionFilter("Ignition Thread Dump .json files", "json"),
        panelOpener = ::ThreadView,
        fileDescription = "thread dump",
    ),
    CacheViewer(
        filter = FileNameExtensionFilter("S+F Cache ZIP Files", "zip"),
        panelOpener = ::CacheView,
        fileDescription = "cache dump",
    ),
    BackupViewer(
        filter = FileNameExtensionFilter("GWBK Files", "gwbk"),
        panelOpener = ::BackupViewer,
        fileDescription = "backup",
    );

    companion object {
        operator fun get(file: File): Tool {
            val foundTool = values().first { tool ->
                val result = tool.filter.accept(file)
                LOGGER.trace("%s %s %s", tool.name, if (result) "accepted" else "rejected", file)
                result
            }
            return foundTool
        }

        private val LOGGER = getLogger<Tool>()

        val byFilter = values().associateBy(Tool::filter)
    }
}
