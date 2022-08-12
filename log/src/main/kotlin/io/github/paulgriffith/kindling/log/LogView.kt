package io.github.paulgriffith.kindling.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.core.MultiTool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import java.awt.Desktop
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JPopupMenu
import javax.swing.JTextPane
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.useLines

class LogView(private val paths: List<Path>) : ToolPanel() {
    lateinit var logPanel: LogPanel

    init {
        val toOpen = paths.sorted()
        name = toOpen.first().name
        toolTipText = toOpen.first().toString()

        try {
            val events = toOpen.flatMap { path ->
                path.useLines { LogPanel.parseLogs(it) }
            }

            logPanel = LogPanel(events)

            add(logPanel, "push, grow")
        } catch (e: Exception) {
            LogPanel.LOGGER.info("Unable to parse ${paths.joinToString()} as a wrapper log; opening as a text file", e)
            add(
                FlatScrollPane(
                    JTextPane().apply {
                        isEditable = false
                        text = toOpen.flatMap { path ->
                            path.readLines()
                        }.joinToString(separator = "\n")
                    }
                ),
                "push, grow"
            )
        }
    }

    override val icon: Icon = LogViewer.icon

    override fun customizePopupMenu(menu: JPopupMenu) {
        if (paths.size == 1) {
            menu.addSeparator()
            menu.add(
                Action(name = "Open in External Editor") {
                    val desktop = Desktop.getDesktop()
                    paths.forEach { path ->
                        desktop.open(path.toFile())
                    }
                }
            )
            menu.add(
                exportMenu { logPanel.table.model }
            )
        }
    }
}

object LogViewer : MultiTool {
    override val title = "Wrapper Log"
    override val description = "wrapper.log(.n) files"
    override val icon = FlatSVGIcon("icons/bx-file.svg")
    override val extensions = listOf("log", "1", "2", "3", "4", "5")
    override fun open(path: Path): ToolPanel = open(listOf(path))
    override fun open(paths: List<Path>): ToolPanel = LogView(paths)
}

class LogViewerProxy : MultiTool by LogViewer
