package io.github.paulgriffith.kindling.idb

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.SQLiteConnection
import io.github.paulgriffith.kindling.utils.getLogger
import io.github.paulgriffith.kindling.utils.toList
import java.nio.file.Path
import javax.swing.JMenu
import javax.swing.JOptionPane
import javax.swing.JPopupMenu
import kotlin.io.path.name
import kotlin.properties.Delegates

class IdbView(path: Path) : ToolPanel() {
    private val connection = SQLiteConnection(path)

    private val tables: List<String> = connection.metaData.getTables("", "", "", null).toList { rs ->
        rs.getString(3)
    }

    private var tool: IdbTool by Delegates.vetoable(
        when {
            "logging_event" in tables -> IdbTool.Log
            "SYSTEM_METRICS" in tables -> IdbTool.Metrics
            else -> IdbTool.Generic
        },
    ) { _, _, newValue ->
        try {
            val newPanel = newValue.openPanel(connection)
            removeAll()
            add(newPanel, "push, grow")
            true
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Unable to open as a ${newValue.name}; ${e.message}",
                "Error",
                JOptionPane.ERROR_MESSAGE,
                FlatSVGIcon("icons/bx-error.svg"),
            )
            LOGGER.error("Unable to swap tool to {}", newValue, e)
            false
        }
    }

    init {
        name = path.name
        toolTipText = path.toString()

        add(tool.openPanel(connection), "push, grow")
    }

    override val icon = IdbViewer.icon

    override fun customizePopupMenu(menu: JPopupMenu) {
        menu.addSeparator()
        menu.add(
            JMenu("View As").apply {
                when (tool) {
                    IdbTool.Log -> add(
                        Action(name = "Generic View") {
                            tool = IdbTool.Generic
                        },
                    )

                    IdbTool.Metrics -> add(
                        Action(name = "Generic View") {
                            tool = IdbTool.Generic
                        },
                    )

                    IdbTool.Generic -> {
                        add(
                            Action(name = "Log View") {
                                tool = IdbTool.Log
                            },
                        )
                        add(
                            Action(name = "Metrics View") {
                                tool = IdbTool.Metrics
                            },
                        )
                    }
                }
            },
        )
    }

    override fun removeNotify() {
        super.removeNotify()
        connection.close()
    }

    companion object {
        private val LOGGER = getLogger<IdbView>()
    }
}
