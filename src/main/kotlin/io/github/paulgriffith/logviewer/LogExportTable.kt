package io.github.paulgriffith.logviewer

import io.github.paulgriffith.utils.setDefaultRenderer
import io.github.paulgriffith.utils.tableCellRenderer
import java.time.Instant
import javax.swing.JTable
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.table.TableRowSorter

class LogExportTable(model: LogExportModel) : JTable(model) {
    init {
        autoResizeMode = AUTO_RESIZE_ALL_COLUMNS
        autoCreateRowSorter = true

        setDefaultRenderer<String> { _, value, _, _, _, _ ->
            text = value
            toolTipText = value
        }

        columnModel.apply {
            getColumn(LogExportModel[LogExportModel.Timestamp]).apply {
                preferredWidth = 140
                maxWidth = 140
                cellRenderer = tableCellRenderer<Instant> { _, value, _, _, _, _ ->
                    text = LogView.DATE_FORMAT.format(value)
                }
            }
            getColumn(LogExportModel[LogExportModel.Logger]).apply {
                preferredWidth = 160
                cellRenderer = tableCellRenderer<String> { _, value, _, _, _, _ ->
                    text = value.substringAfterLast('.')
                    toolTipText = value
                }
            }
            getColumn(LogExportModel[LogExportModel.Thread]).apply {
                preferredWidth = 160
            }
            getColumn(LogExportModel[LogExportModel.Level]).apply {
                preferredWidth = 40
                maxWidth = 40
            }
            removeColumn(columnModel.getColumn(LogExportModel[LogExportModel.EventId]))
        }

        rowSorter.sortKeys = listOf(
            RowSorter.SortKey(LogExportModel[LogExportModel.Timestamp], SortOrder.ASCENDING)
        )
    }

    override fun getModel(): LogExportModel = super.getModel() as LogExportModel

    @Suppress("UNCHECKED_CAST")
    override fun getRowSorter(): TableRowSorter<LogExportModel> {
        return super.getRowSorter() as TableRowSorter<LogExportModel>
    }
}
