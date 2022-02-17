package io.github.paulgriffith.log

import io.github.paulgriffith.utils.Column
import io.github.paulgriffith.utils.ColumnList
import io.github.paulgriffith.utils.ReifiedLabelProvider
import org.jdesktop.swingx.renderer.DefaultTableRenderer
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
            value = { it.level },
        )
        val Timestamp by column(
            column = {
                minWidth = 140
                maxWidth = 140
                cellRenderer = DefaultTableRenderer {
                    LogPanel.DATE_FORMAT.format(it as Instant)
                }
            },
            value = Event::timestamp
        )
        val Thread by column(
            column = {
                preferredWidth = 100
            },
            value = { it.thread }
        )
        val Logger by column(
            column = {
                preferredWidth = 100
                cellRenderer = DefaultTableRenderer(
                    ReifiedLabelProvider<String>(
                        getText = { it?.substringAfterLast('.') },
                        getTooltip = { it }
                    )
                )
            },
            value = Event::logger
        )
        val Message by column { it.message }
    }
}
