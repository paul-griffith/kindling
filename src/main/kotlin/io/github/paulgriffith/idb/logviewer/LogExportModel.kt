package io.github.paulgriffith.idb.logviewer

import io.github.paulgriffith.utils.Column
import io.github.paulgriffith.utils.ColumnList
import io.github.paulgriffith.utils.tableCellRenderer
import java.time.Instant
import javax.swing.table.AbstractTableModel

class LogExportModel(val data: List<Event>) : AbstractTableModel() {
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
        val Level by column(
            column = {
                minWidth = 45
                maxWidth = 45
            },
            value = Event::level
        )
        val Timestamp by column(
            column = {
                minWidth = 130
                maxWidth = 130
                cellRenderer = tableCellRenderer<Instant> { _, value, _, _, _, _ ->
                    text = LogView.DATE_FORMAT.format(value)
                    toolTipText = value.toEpochMilli().toString()
                }
            },
            value = Event::timestamp
        )
        val Thread by column(
            column = {
                preferredWidth = 160
            },
            value = Event::thread
        )
        val Logger by column(
            column = {
                preferredWidth = 160
                cellRenderer = tableCellRenderer<String> { _, value, _, _, _, _ ->
                    text = value.substringAfterLast('.')
                    toolTipText = value
                }
            },
            value = Event::logger
        )
        val Message by column(
            column = {
                preferredWidth = 1000
            },
            value = Event::message
        )
    }
}
