package io.github.paulgriffith.utils

import com.formdev.flatlaf.extras.components.FlatScrollPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JTable
import javax.swing.ListCellRenderer
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import javax.swing.text.Document

inline fun <reified T> tableCellRenderer(crossinline customize: JLabel.(table: JTable, value: T, selected: Boolean, focused: Boolean, row: Int, col: Int) -> Unit): TableCellRenderer {
    return object : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            focused: Boolean,
            row: Int,
            column: Int,
        ): Component {
            return super.getTableCellRendererComponent(table, value, isSelected, focused, row, column).apply {
                if (value is T) {
                    customize.invoke(this as JLabel, table, value, isSelected, focused, row, column)
                }
            }
        }
    }
}

inline fun <reified T> JTable.setDefaultRenderer(crossinline customize: JLabel.(table: JTable, value: T, selected: Boolean, focused: Boolean, row: Int, col: Int) -> Unit) {
    this.setDefaultRenderer(T::class.java, tableCellRenderer(customize))
}

inline fun <reified T> listCellRenderer(crossinline customize: JLabel.(list: JList<*>, value: T, index: Int, selected: Boolean, focused: Boolean) -> Unit): ListCellRenderer<Any> {
    return object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            selected: Boolean,
            focused: Boolean,
        ): Component {
            return super.getListCellRendererComponent(list, value, index, selected, focused).apply {
                if (value is T) {
                    customize.invoke(this as JLabel, list, value, index, selected, focused)
                }
            }
        }
    }
}

inline fun FlatScrollPane(component: Component, block: FlatScrollPane.() -> Unit = {}): FlatScrollPane {
    return FlatScrollPane().apply {
        setViewportView(component)
        block(this)
    }
}

val Document.text: String
    get() = getText(0, length)

fun <T> debounce(
    waitMs: Long = 300L,
    coroutineScope: CoroutineScope,
    destinationFunction: (T) -> Unit,
): (T) -> Unit {
    var debounceJob: Job? = null
    return { param: T ->
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(waitMs)
            destinationFunction(param)
        }
    }
}

fun String.truncate(length: Int = 20): String {
    return asIterable().joinToString(separator = "", limit = length)
}

inline fun <reified T> getLogger(): Logger {
    return LoggerFactory.getLogger(T::class.java.name)
}

/**
 * A common CoroutineScope bound to the event dispatch thread (see [Dispatchers.Swing]).
 */
val EDT_SCOPE = CoroutineScope(Dispatchers.Swing)
