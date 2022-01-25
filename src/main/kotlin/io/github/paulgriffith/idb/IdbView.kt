package io.github.paulgriffith.idb

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.idb.logviewer.LogView
import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.SQLiteConnection
import io.github.paulgriffith.utils.Tool
import io.github.paulgriffith.utils.ToolPanel
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
            "logging_event" in tables -> IdbTool.Logs
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
            JOptionPane.showInternalMessageDialog(
                this,
                e.message,
                "Error",
                JOptionPane.ERROR_MESSAGE,
                FlatSVGIcon("icons/bx-error.svg")
            )
            e.printStackTrace()
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
                    IdbTool.Logs -> add(
                        Action(name = "Generic IDB") {
                            tool = IdbTool.Generic
                        }
                    )
                    IdbTool.Generic -> add(
                        Action(name = "Log View") {
                            tool = IdbTool.Logs
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
}

enum class IdbTool(val openPanel: (connection: Connection) -> IdbPanel) {
    Logs(::LogView),
    Generic(::GenericView);
}

abstract class IdbPanel : JPanel(MigLayout("ins 0, fill, hidemode 3"))
