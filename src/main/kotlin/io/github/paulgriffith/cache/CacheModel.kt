package io.github.paulgriffith.cache

import io.github.paulgriffith.utils.Column
import io.github.paulgriffith.utils.ColumnList
import javax.swing.table.AbstractTableModel

class CacheModel(private val entries: List<CacheEntry>) : AbstractTableModel() {
    override fun getColumnName(column: Int): String = CacheColumns[column].header
    override fun getRowCount(): Int = entries.size
    override fun getColumnCount(): Int = size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, CacheColumns[column])
    override fun getColumnClass(column: Int): Class<*> = CacheColumns[column].clazz

    operator fun <T> get(row: Int, column: Column<CacheEntry, T>): T {
        return entries[row].let { info ->
            column.getValue(info)
        }
    }

    @Suppress("unused")
    companion object CacheColumns : ColumnList<CacheEntry>() {
        val Id by column { it.id }
        val SchemaId by column { it.schemaId }
        val Timestamp by column { it.timestamp }
        val AttemptCount by column(name = "Attempt Count") { it.attemptCount }
        val DataCount by column(name = "Data Count") { it.dataCount }
    }
}
