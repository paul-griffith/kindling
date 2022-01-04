package io.github.paulgriffith.logviewer

import io.github.paulgriffith.utils.Column
import io.github.paulgriffith.utils.ColumnList
import javax.swing.table.AbstractTableModel
import kotlin.properties.Delegates

class LogExportModel(data: List<Event>) : AbstractTableModel() {
    var data: List<Event> by Delegates.observable(data) { _, _, _ ->
        fireTableDataChanged()
    }

    override fun getColumnName(column: Int): String = EventColumns[column].header
    override fun getRowCount(): Int = data.size
    override fun getColumnCount(): Int = size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, EventColumns[column])
    override fun getColumnClass(column: Int): Class<*> = EventColumns[column].clazz

    operator fun get(row: Int): Event = data[row]
    operator fun <T> get(row: Int, column: Column<Event, T>): T? {
        return data.getOrNull(row)?.let { event ->
            column.getValue(event)
        }
    }

    @Suppress("unused")
    companion object EventColumns : ColumnList<Event>() {
        val EventId by column { it.eventId }
        val Timestamp by column { it.timestamp }
        val Message by column { it.message }
        val Logger by column { it.logger }
        val Thread by column { it.thread }
        val Level by column { it.level }
    }
}
