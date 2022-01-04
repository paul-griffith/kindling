package io.github.paulgriffith.threadviewer

import com.jidesoft.swing.SearchableUtils
import io.github.paulgriffith.threadviewer.model.ThreadInfo
import io.github.paulgriffith.utils.tableCellRenderer
import java.text.DecimalFormat
import javax.swing.JTable
import javax.swing.table.TableRowSorter

class ThreadsTable(threads: List<ThreadInfo>) : JTable(ThreadModel(threads)) {
    init {
        autoResizeMode = AUTO_RESIZE_ALL_COLUMNS
        rowSorter = ThreadSorter(model)

        columnModel.apply {
            getColumn(ThreadModel[ThreadModel.System]).apply {
                cellRenderer = tableCellRenderer<String?> { _, value, _, _, _, _ ->
                    text = value ?: "Unassigned"
                }
            }
            getColumn(ThreadModel[ThreadModel.CPU]).apply {
                val percent = DecimalFormat("0.00%")
                cellRenderer = tableCellRenderer<Double?> { _, value, _, _, _, _ ->
                    text = value?.let { percent.format(it / 100) } ?: "Unknown"
                }
            }
        }

        SearchableUtils.installSearchable(this).apply {
            searchColumnIndices = intArrayOf(ThreadModel[ThreadModel.Name])
            isRepeats = true
            isCountMatch = true
            isFromStart = false
            searchingDelay = 100
        }
    }

    override fun getRowSorter(): TableRowSorter<ThreadModel> = super.getRowSorter() as ThreadSorter
    override fun getModel(): ThreadModel = super.getModel() as ThreadModel
}
