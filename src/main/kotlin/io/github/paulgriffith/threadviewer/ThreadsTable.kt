package io.github.paulgriffith.threadviewer

import com.jidesoft.swing.SearchableUtils
import io.github.paulgriffith.threadviewer.ThreadModel.ThreadColumns.Id
import io.github.paulgriffith.threadviewer.ThreadModel.ThreadColumns.Name
import io.github.paulgriffith.threadviewer.ThreadModel.ThreadColumns.StackDepth
import io.github.paulgriffith.threadviewer.ThreadModel.ThreadColumns.System
import io.github.paulgriffith.threadviewer.model.ThreadInfo
import io.github.paulgriffith.utils.Column
import io.github.paulgriffith.utils.ColumnList
import io.github.paulgriffith.utils.setupColumns
import io.github.paulgriffith.utils.tableCellRenderer
import java.text.DecimalFormat
import javax.swing.JTable
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

class ThreadModel(private val threads: List<ThreadInfo>) : AbstractTableModel() {
    override fun getColumnName(column: Int): String = ThreadColumns[column].header
    override fun getRowCount(): Int = threads.size
    override fun getColumnCount(): Int = size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, ThreadColumns[column])
    override fun getColumnClass(column: Int): Class<*> = ThreadColumns[column].clazz

    operator fun <T> get(row: Int, column: Column<ThreadInfo, T>): T {
        return threads[row].let { info ->
            column.getValue(info)
        }
    }

    @Suppress("unused")
    companion object ThreadColumns : ColumnList<ThreadInfo>() {
        private val percent = DecimalFormat("0.00%")
        val Id by column(value = ThreadInfo::id)
        val Name by column(value = ThreadInfo::name)
        val State by column(value = ThreadInfo::state)
        val System by column(
            column = {
                cellRenderer = tableCellRenderer<String?> { _, value, _, _, _, _ ->
                    text = value ?: "Unassigned"
                }
            },
            value = ThreadInfo::system
        )
        val Daemon by column(value = ThreadInfo::isDaemon)
        val StackDepth by column("Depth") { it.stacktrace.size }
        val CPU by column(
            column = {
                cellRenderer = tableCellRenderer<Double?> { _, value, _, _, _, _ ->
                    text = value?.let { percent.format(it / 100) } ?: "Unknown"
                }
            },
            value = ThreadInfo::cpuUsage
        )
    }
}

class ThreadsTable(model: ThreadModel) : JTable(model) {
    init {
        autoResizeMode = AUTO_RESIZE_LAST_COLUMN
        autoCreateRowSorter = false
        rowSorter = ThreadSorter(model).apply {
            sortKeys = listOf(
                RowSorter.SortKey(ThreadModel[Id], SortOrder.ASCENDING)
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

    override fun getRowSorter(): TableRowSorter<ThreadModel>? = super.getRowSorter() as? ThreadSorter
    override fun getModel(): ThreadModel = super.getModel() as ThreadModel
    override fun createDefaultColumnsFromModel() = Unit

    override fun setModel(model: TableModel) {
        require(model is ThreadModel)
        val sort = rowSorter?.sortKeys.orEmpty()
        super.setModel(model)
        rowSorter = ThreadSorter(model).apply {
            sortKeys = sort
        }
    }
}

class ThreadSorter(model: ThreadModel) : TableRowSorter<ThreadModel>(model) {
    init {
        setComparator(ThreadModel[Id], naturalOrder<Int>())
        setComparator(ThreadModel[System], nullsFirst(String.CASE_INSENSITIVE_ORDER))
        setComparator(ThreadModel[StackDepth], naturalOrder<Int>())
    }

    override fun toggleSortOrder(column: Int) {
        when (column) {
            // Sort some columns _descending_ on first click
            ThreadModel[ThreadModel.CPU], ThreadModel[StackDepth] -> {
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
