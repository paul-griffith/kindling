package io.github.paulgriffith.kindling.thread.model

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.utils.Column
import io.github.paulgriffith.kindling.utils.ColumnList
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import org.jdesktop.swingx.renderer.StringValues
import java.text.DecimalFormat
import javax.swing.table.AbstractTableModel

class MultiThreadModel(val threadData: List<List<Thread?>>) : AbstractTableModel() {
    override fun getColumnName(column: Int): String = ThreadColumns[column].header
    override fun getRowCount(): Int = threadData.size
    override fun getColumnCount(): Int = size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, ThreadColumns[column])
    override fun getColumnClass(column: Int): Class<*> = ThreadColumns[column].clazz

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columnIndex == ThreadColumns[Mark]
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        check(columnIndex == ThreadColumns[Mark])
        threadData[rowIndex].forEach {
            it?.marked = aValue as Boolean
        }
    }

    operator fun <T> get(row: Int, column: Column<List<Thread?>, T>): T {
        return threadData[row].let { info ->
            column.getValue(info)
        }
    }

    @Suppress("unused")
    companion object ThreadColumns : ColumnList<List<Thread?>>() {
        private val percent = DecimalFormat("0.00%")

        val Mark by column(
            column = {
                minWidth = 25
                maxWidth = 25
                toolTipText = "Marked Threads"
                headerRenderer = DefaultTableRenderer(StringValues.EMPTY) {
                    FlatSVGIcon("icons/bx-search.svg").derive(0.8F)
                }
            },
            value = { it.firstNotNullOf { thread -> thread }.marked }
        )

        val Id by column(
            column = {
                minWidth = 50
                cellRenderer = DefaultTableRenderer(Any?::toString)
            },
            value = { it.firstNotNullOf { thread -> thread?.id } }
        )

        val Name by column { it.firstNotNullOf { thread -> thread?.name } }

        val State by column(
            column = {
                minWidth = 105
            },
            value = {
                with(it) {
                    filterNotNull().map { thread -> thread.state.toString().first() }.joinToString(" -> ")
                }
            }
        )

        val CPU by column(
            column = {
                minWidth = 60
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? Double)?.let { percent.format(it / 100) }.orEmpty()
                }
            },
            value = { threads -> threads.maxByOrNull { it?.cpuUsage ?: 0.0 }?.cpuUsage ?: 0.0 }
        )

        val Depth by column(
            column = { minWidth = 50 },
            value = { threads -> threads.maxByOrNull { it?.stacktrace?.size ?: 0 }?.stacktrace?.size ?: 0 }
        )

        val Blocker by column(
            column = {
                minWidth = 60
                maxWidth = 60
            },
            value = { it.mapNotNull { thread -> thread?.blocker?.owner }.isNotEmpty() }
        )
    }
}
