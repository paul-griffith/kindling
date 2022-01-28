package io.github.paulgriffith.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatScrollPane
import com.jidesoft.swing.ListSearchable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteDataSource
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.math.BigDecimal
import java.nio.file.Path
import java.sql.Connection
import java.sql.Date
import java.sql.JDBCType
import java.sql.ResultSet
import java.sql.Time
import java.sql.Timestamp
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListSelectionModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListCellRenderer
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import javax.swing.text.Document
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.TreeCellRenderer
import kotlin.math.log2
import kotlin.math.pow

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

fun treeCellRenderer(customize: JLabel.(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) -> Component): TreeCellRenderer {
    return object : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ): Component {
            val soup = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            return customize.invoke(soup as JLabel, tree, value, sel, expanded, leaf, row, hasFocus)
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

/**
 * Launches [destinationFunction] on [EDT_SCOPE] no more frequently than [waitMs].
 */
fun <T> debounce(
    waitMs: Long = 300L,
    destinationFunction: (T) -> Unit,
): (T) -> Unit {
    var debounceJob: Job? = null
    return { param: T ->
        debounceJob?.cancel()
        debounceJob = EDT_SCOPE.launch {
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

/**
 * Exhausts (and closes) a ResultSet into a list using [transform].
 */
fun <T> ResultSet.toList(
    transform: (ResultSet) -> T,
): List<T> {
    return use { rs ->
        buildList {
            while (rs.next()) {
                add(transform(rs))
            }
        }
    }
}

val JDBCType.javaType: Class<*>
    get() = when (this) {
        JDBCType.BIT -> Boolean::class
        JDBCType.TINYINT -> Short::class
        JDBCType.SMALLINT -> Short::class
        JDBCType.INTEGER -> Int::class
        JDBCType.BIGINT -> Long::class
        JDBCType.FLOAT -> Float::class
        JDBCType.REAL -> Double::class
        JDBCType.DOUBLE -> Double::class
        JDBCType.NUMERIC -> BigDecimal::class
        JDBCType.DECIMAL -> BigDecimal::class
        JDBCType.CHAR -> String::class
        JDBCType.VARCHAR -> String::class
        JDBCType.LONGVARCHAR -> String::class
        JDBCType.DATE -> Date::class
        JDBCType.TIME -> Time::class
        JDBCType.TIMESTAMP -> Timestamp::class
        JDBCType.BINARY -> ByteArray::class
        JDBCType.VARBINARY -> ByteArray::class
        JDBCType.LONGVARBINARY -> ByteArray::class
        JDBCType.BOOLEAN -> Boolean::class
        JDBCType.ROWID -> Long::class
        JDBCType.NCHAR -> String::class
        JDBCType.NVARCHAR -> String::class
        JDBCType.LONGNVARCHAR -> String::class
        JDBCType.BLOB -> ByteArray::class
        JDBCType.CLOB -> ByteArray::class
        JDBCType.NCLOB -> ByteArray::class
        else -> Any::class
    }.javaObjectType

inline fun Component.attachPopupMenu(
    crossinline menuFn: Component.(event: MouseEvent) -> JPopupMenu?,
) {
    addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
            maybeShowPopup(e)
        }

        override fun mouseReleased(e: MouseEvent) {
            maybeShowPopup(e)
        }

        private fun maybeShowPopup(e: MouseEvent) {
            if (e.isPopupTrigger) {
                menuFn.invoke(this@attachPopupMenu, e)?.show(e.component, e.x, e.y)
            }
        }
    })
}

fun SQLiteConnection(path: Path): Connection {
    return SQLiteDataSource().apply {
        url = "jdbc:sqlite:file:$path"
        setReadOnly(true)
    }.connection
}

fun FlatSVGIcon.derive(colorer: (Color) -> Color): FlatSVGIcon {
    return FlatSVGIcon(name, scale).apply {
        colorFilter = FlatSVGIcon.ColorFilter(colorer)
    }
}

private val prefix = arrayOf("", "k", "m", "g", "t", "p", "e", "z", "y")

fun Long.toFileSizeLabel(): String = when {
    this == 0L -> "0B"
    else -> {
        val digits = log2(toDouble()).toInt() / 10
        val precision = when (digits) {
            0 -> 0
            1 -> 1
            else -> 2
        }
        "%,.${precision}f${prefix[digits]}b".format(toDouble() / 2.0.pow(digits * 10.0))
    }
}

fun JList<*>.installSearchable(setup: ListSearchable.() -> Unit, conversion: (Any?) -> String): ListSearchable {
    return object : ListSearchable(this) {
        init {
            setup()
        }

        override fun convertElementToString(element: Any?): String {
            return element.let(conversion)
        }
    }
}

class EmptySelectionModel : DefaultListSelectionModel() {
    override fun setSelectionInterval(index0: Int, index1: Int) = Unit
    override fun addSelectionInterval(index0: Int, index1: Int) = Unit
    override fun removeSelectionInterval(index0: Int, index1: Int) = Unit
    override fun getMinSelectionIndex(): Int = -1
    override fun getMaxSelectionIndex(): Int = -1
    override fun isSelectedIndex(index: Int): Boolean = false
    override fun getAnchorSelectionIndex(): Int = -1
    override fun setAnchorSelectionIndex(index: Int) = Unit
    override fun getLeadSelectionIndex(): Int = -1
    override fun setLeadSelectionIndex(index: Int) = Unit
    override fun clearSelection() = Unit
    override fun isSelectionEmpty(): Boolean = true
    override fun insertIndexInterval(index: Int, length: Int, before: Boolean) = Unit
    override fun removeIndexInterval(index0: Int, index1: Int) = Unit
}
