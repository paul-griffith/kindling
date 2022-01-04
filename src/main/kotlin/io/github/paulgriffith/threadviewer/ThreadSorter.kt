package io.github.paulgriffith.threadviewer

import javax.swing.SortOrder
import javax.swing.table.TableRowSorter

class ThreadSorter(model: ThreadModel) : TableRowSorter<ThreadModel>(model) {
    init {
        setComparator(ThreadModel[ThreadModel.Id], naturalOrder<Int>())
        setComparator(ThreadModel[ThreadModel.System], nullsFirst(String.CASE_INSENSITIVE_ORDER))
        setComparator(ThreadModel[ThreadModel.StackDepth], naturalOrder<Int>())

        sortKeys = listOf(
            SortKey(ThreadModel[ThreadModel.Id], SortOrder.ASCENDING)
        )
    }

    override fun toggleSortOrder(column: Int) {
        when (column) {
            // Sort some columns _descending_ on first click
            ThreadModel[ThreadModel.CPU], ThreadModel[ThreadModel.StackDepth] -> {
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
