package io.github.paulgriffith.threadviewer

import io.github.paulgriffith.utils.tableCellRenderer
import java.text.DecimalFormat
import javax.swing.JTable
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.table.TableRowSorter

class SystemTable(private val systemCounts: Map<String?, Int>) : JTable(SystemModel(systemCounts)) {
    init {
        autoResizeMode = AUTO_RESIZE_ALL_COLUMNS
        autoCreateRowSorter = true

        columnModel.apply {
            getColumn(SystemModel[SystemModel.System]).apply {
                cellRenderer = tableCellRenderer<String?> { _, value, _, _, _, _ ->
                    text = value ?: "Unassigned"
                }
            }
            getColumn(SystemModel[SystemModel.Count]).apply {
                val total = systemCounts.values.sum()
                val percentages = systemCounts.mapValues { (_, count) ->
                    val percentage = count.toFloat() / total
                    DecimalFormat.getPercentInstance().format(percentage)
                }

                cellRenderer = tableCellRenderer<Int> { _, value, _, _, row, _ ->
                    val state = model[convertRowIndexToModel(row), SystemModel.System]
                    text = "$value (${percentages.getValue(state)})"
                }
            }
        }

        rowSorter.apply {
            setComparator(SystemModel[SystemModel.Count], naturalOrder<Int>())
            sortKeys = listOf(
                RowSorter.SortKey(SystemModel[SystemModel.Count], SortOrder.DESCENDING),
            )
        }
    }

    override fun getModel(): SystemModel = super.getModel() as SystemModel

    @Suppress("UNCHECKED_CAST")
    override fun getRowSorter(): TableRowSorter<SystemModel> = super.getRowSorter() as TableRowSorter<SystemModel>
}
