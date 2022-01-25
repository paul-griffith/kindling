package io.github.paulgriffith.idb.generic

import com.jidesoft.swing.SearchableUtils
import io.github.paulgriffith.utils.setDefaultRenderer
import javax.swing.JTable
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellRenderer

class ResultsTable : JTable(ResultModel()) {
    init {
        autoCreateRowSorter = true
        SearchableUtils.installSearchable(this).apply {
            isCaseSensitive = false
            isRepeats = true
        }
        setDefaultRenderer<String> { _, value, _, _, _, _ ->
            text = value
            toolTipText = value
        }

        tableHeader = JTableHeader(columnModel).apply {
            val original = defaultRenderer
            defaultRenderer = TableCellRenderer { table, value, isSelected, hasFocus, row, column ->
                original.getTableCellRendererComponent(
                    table,
                    value,
                    isSelected,
                    hasFocus,
                    row,
                    column
                ).apply {
                    toolTipText = value?.toString()
                }
            }
        }
    }

    override fun getModel(): ResultModel = super.getModel() as ResultModel
}
