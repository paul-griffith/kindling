package io.github.paulgriffith.utils

import javax.swing.table.AbstractTableModel
import kotlin.properties.Delegates

class DetailsModel(details: List<Pair<String, String>>) : AbstractTableModel() {
    var details: List<Pair<String, String>> by Delegates.observable(details) { _, _, _ ->
        fireTableDataChanged()
    }

    override fun getColumnName(column: Int): String = DetailsColumns[column].header
    override fun getRowCount(): Int = details.size
    override fun getColumnCount(): Int = size

    override fun getValueAt(row: Int, column: Int): Any? {
        return details[row].let { entry ->
            DetailsColumns[column].getValue(entry)
        }
    }

    override fun getColumnClass(column: Int): Class<*> = DetailsColumns[column].clazz

    @Suppress("unused")
    companion object DetailsColumns : ColumnList<Pair<String, String>>() {
        val Key by column { it.first }
        val Value by column { it.second }
    }
}
