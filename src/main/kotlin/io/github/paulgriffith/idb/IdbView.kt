package io.github.paulgriffith.idb

import io.github.paulgriffith.idb.logviewer.LogView
import io.github.paulgriffith.utils.Tool
import io.github.paulgriffith.utils.ToolPanel
import io.github.paulgriffith.utils.toList
import net.miginfocom.swing.MigLayout
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.sql.Connection
import javax.swing.JPanel

class IdbView(override val path: Path) : ToolPanel() {
    private val connection = SQLiteDataSource().apply {
        url = "jdbc:sqlite:file:$path"
        setReadOnly(true)
    }.connection

    private val tables: List<String> = connection.metaData.getTables("", "", "", null).toList { rs ->
        rs.getString(3)
    }

    private val panel: IdbPanel = when {
        "logging_event" in tables -> LogView(connection)
//        "SRFEATURES" in tables -> ConfigView(connection)
        else -> GenericView(connection)
    }

    init {
        add(panel, "push, grow")
    }

    override val icon = Tool.IdbViewer.icon

    companion object
}

abstract class IdbPanel(private val connection: Connection) : JPanel(MigLayout("ins 0, fill, hidemode 3")) {
    override fun removeNotify() {
        connection.close()
    }
}
