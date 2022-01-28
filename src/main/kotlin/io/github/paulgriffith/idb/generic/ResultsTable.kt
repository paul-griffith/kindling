package io.github.paulgriffith.idb.generic

import com.jidesoft.swing.SearchableUtils
import io.github.paulgriffith.utils.setDefaultRenderer
import io.github.paulgriffith.utils.toFileSizeLabel
import javax.swing.JTable
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellRenderer

class ResultsTable : JTable(QueryResult.Success()) {
    init {
        autoCreateRowSorter = true
        autoResizeMode = AUTO_RESIZE_OFF

        SearchableUtils.installSearchable(this).apply {
            isCaseSensitive = false
            isRepeats = true
        }
        setDefaultRenderer<String> { _, value, _, _, _, _ ->
            text = value
            toolTipText = value
        }

        setDefaultRenderer<ByteArray> { table, value, selected, focused, row, col ->
            text = "${value.size.toLong().toFileSizeLabel()} BLOB"
            toolTipText = "Export to CSV to view full data (b64 encoded)"
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

    override fun getModel(): QueryResult.Success = super.getModel() as QueryResult.Success
}
