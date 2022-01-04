package io.github.paulgriffith.threadviewer

import io.github.paulgriffith.utils.tableCellRenderer
import java.text.DecimalFormat
import javax.swing.JTable
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.table.TableRowSorter

class StateTable(private val stateCounts: Map<Thread.State, Int>) : JTable(StateModel(stateCounts)) {
    init {
        autoResizeMode = AUTO_RESIZE_ALL_COLUMNS
        autoCreateRowSorter = true

        columnModel.apply {
            getColumn(StateModel[StateModel.State]).apply {
                cellRenderer = tableCellRenderer<Thread.State> { _, value, _, _, _, _ ->
                    text = value.name
                }
            }
            getColumn(StateModel[StateModel.Count]).apply {
                val total = stateCounts.values.sum()
                val percentages = stateCounts.mapValues { (_, count) ->
                    val percentage = count.toFloat() / total
                    DecimalFormat.getPercentInstance().format(percentage)
                }

                cellRenderer = tableCellRenderer<Int> { _, value, _, _, row, _ ->
                    val state = model[row, StateModel.State]
                    text = "$value (${percentages.getValue(state)})"
                }
            }
        }

        rowSorter.apply {
            setComparator(StateModel[StateModel.Count], naturalOrder<Int>())
            sortKeys = listOf(
                RowSorter.SortKey(StateModel[StateModel.Count], SortOrder.DESCENDING)
            )
        }
    }

    override fun getModel(): StateModel = super.getModel() as StateModel

    @Suppress("UNCHECKED_CAST")
    override fun getRowSorter(): TableRowSorter<StateModel> {
        return super.getRowSorter() as TableRowSorter<StateModel>
    }
}
