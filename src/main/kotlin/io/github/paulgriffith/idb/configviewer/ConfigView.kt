package io.github.paulgriffith.idb.configviewer

import io.github.paulgriffith.idb.IdbPanel
import java.sql.Connection
import javax.swing.JLabel

class ConfigView(connection: Connection) : IdbPanel(connection) {
    init {
        add(JLabel("WIP"))
    }
}
