package io.github.paulgriffith.idb.logviewer

import io.github.paulgriffith.idb.logviewer.LogExportModel.EventColumns.Timestamp
import io.github.paulgriffith.utils.setDefaultRenderer
import io.github.paulgriffith.utils.setupColumns
import javax.swing.JTable
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

class LogExportTable(model: LogExportModel) : JTable(model) {
    init {
        autoResizeMode = AUTO_RESIZE_LAST_COLUMN
        autoCreateRowSorter = false
        rowSorter = TableRowSorter(model).apply {
            sortKeys = listOf(
                SortKey(LogExportModel[Timestamp], SortOrder.ASCENDING)
            )
        }

        setDefaultRenderer<String> { _, value, _, _, _, _ ->
            text = value
            toolTipText = value
        }

        setupColumns(LogExportModel.EventColumns)
    }

    override fun createDefaultColumnsFromModel() = Unit

    override fun getModel(): LogExportModel = super.getModel() as LogExportModel

    override fun setModel(model: TableModel) {
        require(model is LogExportModel)
        val keys: List<SortKey>? = rowSorter?.sortKeys
        super.setModel(model)
        if (keys != null) {
            rowSorter.sortKeys = keys
        }
    }
}
