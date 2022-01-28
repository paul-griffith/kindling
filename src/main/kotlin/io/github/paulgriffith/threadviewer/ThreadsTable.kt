package io.github.paulgriffith.threadviewer

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.SearchableUtils
import io.github.paulgriffith.threadviewer.ThreadModel.ThreadColumns.Depth
import io.github.paulgriffith.threadviewer.ThreadModel.ThreadColumns.Id
import io.github.paulgriffith.threadviewer.ThreadModel.ThreadColumns.Name
import io.github.paulgriffith.threadviewer.ThreadModel.ThreadColumns.System
import io.github.paulgriffith.threadviewer.model.Thread
import io.github.paulgriffith.utils.Column
import io.github.paulgriffith.utils.ColumnList
import io.github.paulgriffith.utils.setupColumns
import io.github.paulgriffith.utils.tableCellRenderer
import java.text.DecimalFormat
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.UIManager
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

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
                maxWidth = 20
                val headerIcon = JLabel(FlatSVGIcon("icons/bx-search.svg").derive(0.8F)).apply {
                    toolTipText = "Marked Threads"
                    border = BorderFactory.createLineBorder(UIManager.getColor("TableHeader.separatorColor"))
                }
                setHeaderRenderer { _, _, _, _, _, _ ->
                    headerIcon
                }
            },
            name = "",
            value = Thread::marked
        )
        val Id by column(
            column = {
                maxWidth = 40
            },
            value = Thread::id
        )
        val State by column(
            column = {
            },
            value = Thread::state
        )
        val Name by column(
            column = {
            },
            value = Thread::name
        )
        val Daemon by column(
            column = {
                maxWidth = 60
            },
            value = Thread::isDaemon
        )
        val Depth by column(
            column = {
                maxWidth = 60
            },
            value = { it.stacktrace.size }
        )
        val CPU by column(
            column = {
                cellRenderer = tableCellRenderer<Double?> { _, value, _, _, _, _ ->
                    text = value?.let { percent.format(it / 100) } ?: "Unknown"
                }
            },
            value = Thread::cpuUsage
        )
        val System by column(
            column = {
                cellRenderer = tableCellRenderer<String?> { _, value, _, _, _, _ ->
                    text = value ?: "Unassigned"
                }
            },
            value = Thread::system
        )
        val Blocker by column(
            name = "Blocking Thread",
            column = {
                maxWidth = 40
            },
            value = { thread ->
                thread.blocker?.owner
            }
        )
    }
}

class ThreadsTable(threads: List<Thread>) : JTable(ThreadModel(threads)) {
    init {
        autoResizeMode = AUTO_RESIZE_LAST_COLUMN
        autoCreateRowSorter = false
        rowSorter = ThreadSorter(model).apply {
            sortKeys = listOf(
                SortKey(ThreadModel[Id], SortOrder.ASCENDING)
            )
        }

        setupColumns(ThreadModel.ThreadColumns)

        SearchableUtils.installSearchable(this).apply {
            searchColumnIndices = intArrayOf(ThreadModel[Name])
            isRepeats = true
            isCountMatch = true
            isFromStart = false
            searchingDelay = 100
        }
    }

    override fun getModel(): ThreadModel = super.getModel() as ThreadModel
    override fun createDefaultColumnsFromModel() = Unit

    override fun setModel(model: TableModel) {
        require(model is ThreadModel)
        val keys = rowSorter?.sortKeys.orEmpty()
        super.setModel(model)
        rowSorter = ThreadSorter(model).apply {
            sortKeys = keys
        }
    }
}

class ThreadSorter(model: ThreadModel) : TableRowSorter<ThreadModel>(model) {
    init {
        setComparator(ThreadModel[Id], naturalOrder<Int>())
        setComparator(ThreadModel[System], nullsFirst(String.CASE_INSENSITIVE_ORDER))
        setComparator(ThreadModel[Depth], naturalOrder<Int>())
    }

    override fun toggleSortOrder(column: Int) {
        when (column) {
            // Sort some columns _descending_ on first click
            ThreadModel[ThreadModel.CPU], ThreadModel[Depth] -> {
                val existingKey = sortKeys.singleOrNull()?.takeIf { it.column == column }
                sortKeys = listOf(
                    when {
                        // no existing sort key -> descending
                        existingKey == null -> SortKey(column, SortOrder.DESCENDING)
                        // descending sort key -> swap to ascending
                        existingKey.sortOrder == SortOrder.DESCENDING -> SortKey(column, SortOrder.ASCENDING)
                        // ascending sort key -> swap to descending
                        else -> SortKey(column, SortOrder.DESCENDING)
                    }
                )
            }
            else -> super.toggleSortOrder(column)
        }
    }
}
