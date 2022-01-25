package io.github.paulgriffith.idb

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.idb.generic.GenericView
import io.github.paulgriffith.idb.logviewer.LogView
import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.SQLiteConnection
import io.github.paulgriffith.utils.Tool
import io.github.paulgriffith.utils.ToolPanel
import io.github.paulgriffith.utils.getLogger
import io.github.paulgriffith.utils.toList
import net.miginfocom.swing.MigLayout
import java.nio.file.Path
import java.sql.Connection
import javax.swing.JMenu
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import kotlin.properties.Delegates

class IdbView(override val path: Path) : ToolPanel() {
    private val connection = SQLiteConnection(path)

    private val tables: List<String> = connection.metaData.getTables("", "", "", null).toList { rs ->
        rs.getString(3)
    }

    private var tool: IdbTool by Delegates.vetoable(
        when {
            "logging_event" in tables -> IdbTool.Log
//        "SRFEATURES" in tables -> ConfigView(connection)
            else -> IdbTool.Generic
        }
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
                FlatSVGIcon("icons/bx-error.svg")
            )
            LOGGER.error("Unable to swap tool to %s", newValue, e)
            false
        }
    }

    init {
        add(tool.openPanel(connection), "push, grow")
    }

    override val icon = Tool.IdbViewer.icon

    override fun customizePopupMenu(menu: JPopupMenu) {
        menu.add(
            JMenu("View As").apply {
                when (tool) {
                    IdbTool.Log -> add(
                        Action(name = "Generic View") {
                            tool = IdbTool.Generic
                        }
                    )
                    IdbTool.Generic -> add(
                        Action(name = "Log View") {
                            tool = IdbTool.Log
                        }
                    )
                }
            }
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

enum class IdbTool(val openPanel: (connection: Connection) -> IdbPanel) {
    Log(::LogView),
    Generic(::GenericView);
}

abstract class IdbPanel : JPanel(MigLayout("ins 0, fill, hidemode 3"))
