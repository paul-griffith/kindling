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
import org.jdesktop.swingx.JXTable
import org.jdesktop.swingx.decorator.HighlighterFactory
import org.jdesktop.swingx.sort.SortController
import org.jdesktop.swingx.table.ColumnControlButton
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListSelectionModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListCellRenderer
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel
import javax.swing.text.Document
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.TreeCellRenderer

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

/**
 * A common CoroutineScope bound to the event dispatch thread (see [Dispatchers.Swing]).
 */
val EDT_SCOPE = CoroutineScope(Dispatchers.Swing)
inline fun <T : Component> T.attachPopupMenu(
    crossinline menuFn: T.(event: MouseEvent) -> JPopupMenu?,
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

fun FlatSVGIcon.derive(colorer: (Color) -> Color): FlatSVGIcon {
    return FlatSVGIcon(name, scale).apply {
        colorFilter = FlatSVGIcon.ColorFilter(colorer)
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

class ReifiedJXTable<T : TableModel>(
    model: T,
    columns: ColumnList<*>,
) : JXTable(model) {
    private val setup = true

    init {
        installColumnFactory(columns)
        isColumnControlVisible = true
        addHighlighter(HighlighterFactory.createSimpleStriping())

        setDefaultRenderer<String> { _, value, _, _, _, _ ->
            text = value
            toolTipText = value
        }

        packAll()
        actionMap.remove("find")
    }

    override fun createDefaultColumnControl(): JComponent {
        return ColumnControlButton(this, FlatSVGIcon("icons/bx-column.svg").derive(0.5F))
    }

    @Suppress("UNCHECKED_CAST")
    override fun getModel(): T = super.getModel() as T

    override fun setModel(model: TableModel) {
//        require(model::class == modelClass) { "Expected $modelClass but got ${model::class}" }
        val sortedColumn = sortedColumnIndex
        val sortOrder = if (sortedColumn >= 0) (rowSorter as SortController<*>).getSortOrder(sortedColumn) else null

        super.setModel(model)

        if (sortOrder != null) {
            setSortOrder(sortedColumn, sortOrder)
        }
        if (setup) {
            packAll()
        }
    }
}
