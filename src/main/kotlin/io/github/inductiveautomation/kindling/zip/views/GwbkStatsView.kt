package io.github.inductiveautomation.kindling.zip.views

import io.github.inductiveautomation.kindling.statistics.Statistics
import kotlinx.coroutines.runBlocking
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

    private val stats = runBlocking { Statistics.create(path) }

    init {
        add(
            JScrollPane(
                JEditorPane().apply {
                    text = stats.toString()
                }
            )
        )
    }

    companion object {
        fun isGatewayBackup(path: Path): Boolean = path.extension.lowercase() == "gwbk"
    }
}