package io.github.paulgriffith.threadviewer

import io.github.paulgriffith.threadviewer.model.ThreadInfo
import io.github.paulgriffith.utils.Column
import io.github.paulgriffith.utils.ColumnList
import javax.swing.table.AbstractTableModel

class ThreadModel(private val threads: List<ThreadInfo>) : AbstractTableModel() {
    override fun getColumnName(column: Int): String = ThreadColumns[column].header
    override fun getRowCount(): Int = threads.size
    override fun getColumnCount(): Int = size
    override fun getValueAt(row: Int, column: Int): Any? = get(row, ThreadColumns[column])
    override fun getColumnClass(column: Int): Class<*> = ThreadColumns[column].clazz

    operator fun <T> get(row: Int, column: Column<ThreadInfo, T>): T {
        return threads[row].let { info ->
            column.getValue(info)
        }
    }

    @Suppress("unused")
    companion object ThreadColumns : ColumnList<ThreadInfo>() {
        val Id by column { it.id }
        val Name by column { it.name }
        val State by column { it.state }
        val System by column { it.system }
        val Daemon by column { it.isDaemon }
        val StackDepth by column("Depth") { it.stacktrace.size }
        val CPU by column { it.cpuUsage }
    }
}
