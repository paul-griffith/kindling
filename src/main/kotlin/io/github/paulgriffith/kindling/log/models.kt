package io.github.paulgriffith.kindling.log // ktlint-disable filename

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.comparator.AlphanumComparator
import io.github.paulgriffith.kindling.utils.Column
import io.github.paulgriffith.kindling.utils.ColumnList
import io.github.paulgriffith.kindling.utils.ReifiedLabelProvider
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import org.jdesktop.swingx.renderer.StringValues
import java.time.Instant
import javax.swing.table.AbstractTableModel

class LogsModel<T : LogEvent>(
    val data: List<T>,
    val columns: ColumnList<T>
) : AbstractTableModel() {
    override fun getColumnName(column: Int): String = columns[column].header
    override fun getRowCount(): Int = data.size
    override fun getColumnCount(): Int = columns.size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, columns[column])
    override fun getColumnClass(column: Int): Class<*> = columns[column].clazz

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columnIndex == 0
    }
    operator fun get(row: Int): T = data[row]
    operator fun <R> get(row: Int, column: Column<T, R>): R? {
        return data.getOrNull(row)?.let { event ->
            column.getValue(event)
        }
    }
    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        require(isCellEditable(rowIndex, columnIndex))
        data[rowIndex].marked = aValue as Boolean
    }
}

@Suppress("unused", "PropertyName")
class SystemLogsColumns(panel: LogPanel) : ColumnList<SystemLogsEvent>() {
    val Marked by column(
        column = {
            minWidth = 25
            maxWidth = 25
            toolTipText = "Marked Threads"
            headerRenderer = DefaultTableRenderer(StringValues.EMPTY) {
                FlatSVGIcon("icons/bx-search.svg").derive(0.8F)
            }
        },
        value = { it.marked }
    )
    val Level by column(
        column = {
            minWidth = 55
            maxWidth = 55
        },
        value = { it.level }
    )
    val Timestamp by column(
        column = {
            minWidth = 155
            maxWidth = 155
            cellRenderer = DefaultTableRenderer {
                panel.dateFormatter.format(it as Instant)
            }
        },
        value = SystemLogsEvent::timestamp
    )
    val Thread by column(
        column = {
            minWidth = 50
        },
        value = { it.thread }
    )
    val Logger by column(
        column = {
            minWidth = 50

            val valueExtractor: (String?) -> String? = {
                if (panel.header.isShowFullLoggerName) {
                    it
                } else {
                    it?.substringAfterLast('.')
                }
            }

            cellRenderer = DefaultTableRenderer(
                ReifiedLabelProvider(
                    getText = valueExtractor,
                    getTooltip = { it }
                )
            )
            comparator = compareBy(AlphanumComparator(), valueExtractor)
        },
        value = SystemLogsEvent::logger
    )

    val Message by column { it.message }
}

@Suppress("unused", "PropertyName")
class WrapperLogColumns(panel: LogPanel) : ColumnList<WrapperLogEvent>() {
    val Marked by column(
        column = {
            minWidth = 25
            maxWidth = 25
            toolTipText = "Marked Threads"
            headerRenderer = DefaultTableRenderer(StringValues.EMPTY) {
                FlatSVGIcon("icons/bx-search.svg").derive(0.8F)
            }
        },
        value = { it.marked  }
    )
    val Level by column(
        column = {
            minWidth = 55
            maxWidth = 55
        },
        value = { it.level }
    )
    val Timestamp by column(
        column = {
            minWidth = 155
            maxWidth = 155
            cellRenderer = DefaultTableRenderer {
                panel.dateFormatter.format(it as Instant)
            }
        },
        value = { it.timestamp }
    )
    val Logger by column(
        column = {
            minWidth = 50

            val valueExtractor: (String?) -> String? = {
                if (panel.header.isShowFullLoggerName) {
                    it
                } else {
                    it?.substringAfterLast('.')
                }
            }

            cellRenderer = DefaultTableRenderer(
                ReifiedLabelProvider(
                    getText = valueExtractor,
                    getTooltip = { it }
                )
            )
            comparator = compareBy(AlphanumComparator(), valueExtractor)
        },
        value = { it.logger }
    )
    val Message by column { it.message }
}
