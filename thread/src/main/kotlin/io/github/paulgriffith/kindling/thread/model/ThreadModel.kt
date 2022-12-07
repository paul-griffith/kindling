package io.github.paulgriffith.kindling.thread.model

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.utils.Column
import io.github.paulgriffith.kindling.utils.ColumnList
import io.github.paulgriffith.kindling.utils.firstNotNull
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import org.jdesktop.swingx.renderer.StringValues
import java.text.DecimalFormat
import javax.swing.table.AbstractTableModel

// A thread's lifespan across multiple thread dumps
typealias ThreadLifespan = List<Thread?>
fun ThreadLifespan.toThread(): Thread = firstNotNull()

class ThreadModel(
    val threadData: List<ThreadLifespan>,
) : AbstractTableModel() {
    val columns: ColumnList<ThreadLifespan>
        get() = if (isSingleContext) SingleThreadColumns else MultiThreadColumns

    val isSingleContext: Boolean
        get() = threadData.firstOrNull()?.size == 1

    override fun getColumnName(column: Int): String = columns[column].header

    override fun getRowCount(): Int = threadData.size

    override fun getColumnCount(): Int = columns.size

    override fun getValueAt(row: Int, column: Int): Any? = get(row, columns[column])

    override fun getColumnClass(column: Int): Class<*> = columns[column].clazz

    operator fun <T> get(row: Int, column: Column<ThreadLifespan, T>): T {
        return threadData[row].let { info ->
            column.getValue(info)
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columnIndex == markIndex
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        check(columnIndex == markIndex)
        threadData[rowIndex].forEach {
            it?.marked = aValue as Boolean
        }
    }

    companion object {
        const val markIndex = 0
        const val idIndex = 1
    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    object MultiThreadColumns : ColumnList<ThreadLifespan>() {
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
            value = { threadList ->
                threadList.map { thread ->
                    thread?.state?.toString()?.first() ?: "X"
                }.joinToString(" -> ")
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
        val System by column(
            column = {
                isVisible = false
                minWidth = 75
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? String) ?: "Unassigned"
                }
            },
            value = { it.firstNotNull().system },
        )
        val Pool by column(
            column = {
                isVisible = false
                minWidth = 75
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? String?) ?: "(No Pool)"
                }
            },
            value = { it.firstNotNull().pool },
        )

        val filterableColumns = listOf(
            System,
            Pool,
        )

        val markableColumns = listOf(
            System,
            Pool,
            Blocker,
        )

    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    object SingleThreadColumns : ColumnList<ThreadLifespan>() {
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
            value = { it.toThread().marked }
        )
        val Id by column(
            column = {
                cellRenderer = DefaultTableRenderer(Any?::toString)
            },
            value = { it.toThread().id },
        )
        val Name by column { it.toThread().name }
        val State by column(
            column = {
                minWidth = 105
                maxWidth = 105
            },
            value = { it.toThread().state },
        )
        val Daemon by column(
            column = {
                minWidth = 55
                maxWidth = 55
            },
            value = { it.toThread().isDaemon },
        )
        val Depth by column { it.toThread().stacktrace.size }
        val CPU by column(
            column = {
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? Double)?.let { percent.format(it / 100) }.orEmpty()
                }
            },
            value = { it.toThread().cpuUsage }
        )
        val System by column(
            column = {
                minWidth = 75
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? String) ?: "Unassigned"
                }
            },
            value = { it.toThread().system },
        )
        val Pool by column(
            column = {
                isVisible = false
                minWidth = 75
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? String?) ?: "(No Pool)"
                }
            },
            value = { it.toThread().pool },
        )
        val Blocker by column(
            column = {
                cellRenderer = DefaultTableRenderer {
                    it?.toString().orEmpty()
                }
            },
            value = {
                it.toThread().blocker?.owner
            },
        )
        val Stacktrace by column(
            column = {
                isVisible = false
                minWidth = 75
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? String?) ?: "No Trace"
                }
            },
            value = {
                it.toThread().stacktrace.joinToString()
            },
        )
        val Scope by column(
            column = {
                isVisible = false
                cellRenderer = DefaultTableRenderer { value ->
                    (value as? String?) ?: "Unknown"
                }
            },
            value = {
                it.toThread().scope
            },
        )

        val filterableColumns = listOf(
            State,
            System,
            Pool,
        )

        val markableColumns = listOf(
            State,
            System,
            Pool,
            Blocker,
            Stacktrace,
        )
    }
}

