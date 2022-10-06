package io.github.paulgriffith.kindling.thread.model

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.utils.Column
import io.github.paulgriffith.kindling.utils.ColumnList
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import org.jdesktop.swingx.renderer.StringValues
import java.text.DecimalFormat
import javax.swing.table.AbstractTableModel

class ThreadModel(val threads: List<Thread>) : AbstractTableModel() {
    override fun getColumnName(column: Int): String = ThreadColumns[column].header
    override fun getRowCount(): Int = threads.size
    override fun getColumnCount(): Int = size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, ThreadColumns[column])
    override fun getColumnClass(column: Int): Class<*> = ThreadColumns[column].clazz

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columnIndex == ThreadColumns[Mark]
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        check(columnIndex == ThreadColumns[Mark])
        threads[rowIndex].marked = aValue as Boolean
    }

    operator fun <T> get(row: Int, column: Column<Thread, T>): T {
        return threads[row].let { info ->
            column.getValue(info)
        }
    }

    @Suppress("unused")
    companion object ThreadColumns : ColumnList<Thread>() {
        private val percent = DecimalFormat("0.00%")

        val Mark by column(
            column = {
                minWidth = 20
                maxWidth = 20
                toolTipText = "Marked Threads"
                headerRenderer = DefaultTableRenderer(StringValues.EMPTY) {
                    FlatSVGIcon("icons/bx-search.svg").derive(0.8F)
                }
            },
            value = Thread::marked
        )
        val Id by column(
            column = {
                cellRenderer = DefaultTableRenderer(Any?::toString)
            },
            value = { it.id }
        )
        val State by column(
            column = {
                minWidth = 105
                maxWidth = 105
            },
            value = { it.state }
        )
        val Name by column { it.name }
        val Daemon by column(
            column = {
                minWidth = 55
                maxWidth = 55
            },
            value = { it.isDaemon }
        )
        val Depth by column { it.stacktrace.size }
        val CPU by column(
            column = {
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? Double)?.let { percent.format(it / 100) }.orEmpty()
                }
            },
            value = Thread::cpuUsage
        )
        val System by column(
            column = {
                minWidth = 75
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? String) ?: "Unassigned"
                }
            },
            value = Thread::system
        )
        val Pool by column(
            column = {
                isVisible = false
                minWidth = 75
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? String?) ?: "(No Pool)"
                }
            },
            value = Thread::pool
        )
        val Blocker by column(
            column = {
                cellRenderer = DefaultTableRenderer {
                    it?.toString().orEmpty()
                }
            },
            value = { thread ->
                thread.blocker?.owner
            }
        )
        val Stacktrace by column(
            column = {
                isVisible = false
                minWidth = 75
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? String?) ?: "No Trace"
                }
            },
            value = { thread ->
                thread.stacktrace.joinToString()
            }
        )
        val Scope by column(
            column = {
                isVisible = false
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? String?) ?: "Unknown"
                }
            },
            value = { thread ->
                thread.scope
            }
        )
    }
}
