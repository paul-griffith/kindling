package io.github.paulgriffith.threadviewer

import io.github.paulgriffith.utils.Column
import io.github.paulgriffith.utils.ColumnList
import java.lang.Thread.State
import javax.swing.table.AbstractTableModel

class StateModel(private val byState: Map<State, Int>) : AbstractTableModel() {
    override fun getColumnName(column: Int): String = StateColumns[column].header
    override fun getRowCount(): Int = byState.size
    override fun getColumnCount(): Int = size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, StateColumns[column])
    override fun getColumnClass(column: Int): Class<*> = StateColumns[column].clazz

    operator fun <T> get(row: Int, column: Column<Map.Entry<State, Int>, T>): T {
        return byState.entries.elementAt(row).let { stateEntry ->
            column.getValue(stateEntry)
        }
    }

    @Suppress("unused")
    companion object StateColumns : ColumnList<Map.Entry<State, Int>>() {
        val State by column { it.key }
        val Count by column { it.value }
    }
}
