package io.github.inductiveautomation.kindling.zip.views

import io.github.inductiveautomation.kindling.statistics.Statistics
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.Icon
import javax.swing.JEditorPane
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import kotlin.io.path.extension

class GwbkStatsView(override val provider: FileSystemProvider, override val path: Path) : SinglePathView() {
    override val icon: Icon? = null
    override val tabName: String = "Statistics"
    override fun customizePopupMenu(menu: JPopupMenu) = Unit

    init {
        EDT_SCOPE.launch {
            val stats = Statistics.create(path)

            stats.meta.forEach {
                launch { println("${it.name}: ${it.getValue()}") }
            }

            add(
                JScrollPane(
                    JEditorPane().apply {
                        text = stats.toString()
                    }
                ), "push, grow, span"
            )
        }
    }

    companion object {
        fun isGatewayBackup(path: Path): Boolean = path.extension.lowercase() == "gwbk"
    }
}