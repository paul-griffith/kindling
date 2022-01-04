package io.github.paulgriffith.threadviewer

import io.github.paulgriffith.utils.Column
import io.github.paulgriffith.utils.ColumnList
import javax.swing.table.AbstractTableModel

class SystemModel(private val bySystem: Map<String?, Int>) : AbstractTableModel() {
    override fun getColumnName(column: Int): String = SystemColumns[column].header
    override fun getRowCount(): Int = bySystem.size
    override fun getColumnCount(): Int = size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, SystemColumns[column])
    override fun getColumnClass(column: Int): Class<*> = SystemColumns[column].clazz

    operator fun <T> get(row: Int, column: Column<Map.Entry<String?, Int>, T>): T {
        return bySystem.entries.elementAt(row).let { systemEntry ->
            column.getValue(systemEntry)
        }
    }

    @Suppress("unused")
    companion object SystemColumns : ColumnList<Map.Entry<String?, Int>>() {
        val System by column { it.key }
        val Count by column { it.value }
    }
}
