package io.github.paulgriffith.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatScrollPane
import com.jidesoft.swing.ListSearchable
import io.github.paulgriffith.utils.ReifiedLabelProvider.Companion.setDefaultRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.jdesktop.swingx.JXTable
import org.jdesktop.swingx.decorator.HighlighterFactory
import org.jdesktop.swingx.renderer.CellContext
import org.jdesktop.swingx.renderer.ComponentProvider
import org.jdesktop.swingx.renderer.DefaultTableRenderer
import org.jdesktop.swingx.renderer.JRendererLabel
import org.jdesktop.swingx.sort.SortController
import org.jdesktop.swingx.table.ColumnControlButton
import java.awt.Color
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListSelectionModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListCellRenderer
import javax.swing.table.TableModel
import javax.swing.text.Document
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.TreeCellRenderer
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

typealias StringProvider<T> = (T?) -> String?
typealias IconProvider<T> = (T?) -> Icon?

class ReifiedLabelProvider<T : Any>(
    private val valueClass: KClass<T>,
    private val getText: StringProvider<T>,
    private val getIcon: IconProvider<T>,
    private val getTooltip: StringProvider<T>,
) : ComponentProvider<JLabel>() {
    override fun createRendererComponent(): JLabel = JRendererLabel()

    override fun configureState(context: CellContext) {
        // TODO - Color icon when selected
        rendererComponent.horizontalAlignment = horizontalAlignment
    }

    override fun format(context: CellContext) {
        rendererComponent.apply {
            val value = valueClass.safeCast(context.value)
            text = getText(value)
            icon = getIcon(value)
            toolTipText = getTooltip(value)
        }
    }

    companion object {
        private val NULL_ICON = FlatSVGIcon("icons/null.svg")

        fun <T> defaultIconFunction(): IconProvider<T> = {
            if (it == null) {
                NULL_ICON
            } else null
        }

        inline operator fun <reified T : Any> invoke(
            noinline getText: StringProvider<T>,
            noinline getIcon: IconProvider<T> = defaultIconFunction(),
            noinline getTooltip: StringProvider<T> = { null },
        ): ReifiedLabelProvider<T> {
            return ReifiedLabelProvider(T::class, getText, getIcon, getTooltip)
        }

        inline fun <reified T : Any> JXTable.setDefaultRenderer(
            noinline getText: StringProvider<T>,
            noinline getIcon: IconProvider<T> = defaultIconFunction(),
            noinline getTooltip: StringProvider<T> = { null },
        ) {
            this.setDefaultRenderer(
                T::class.java,
                DefaultTableRenderer(ReifiedLabelProvider(getText, getIcon, getTooltip))
            )
        }
    }
}

fun JTable.selectedRowIndices(): IntArray {
    return selectionModel.selectedIndices
        .filter { isRowSelected(it) }
        .map { convertRowIndexToModel(it) }
        .toIntArray()
}

fun JTable.selectedOrAllRowIndices(): IntArray {
    return if (selectionModel.isSelectionEmpty) {
        IntArray(model.rowCount) { it }
    } else {
        selectedRowIndices()
    }
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

val MENU_SHORTCUT_KEY_MASK by lazy { Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx }

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
val EDT_SCOPE by lazy { CoroutineScope(Dispatchers.Swing) }

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

class NoSelectionModel : DefaultListSelectionModel() {
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
    private val modelClass: Class<T>,
    columns: ColumnList<*>?,
) : JXTable(model) {
    private val setup = true

    init {
        if (columns != null) {
            installColumnFactory(columns)
        }
        isColumnControlVisible = true

        setDefaultRenderer<String>(
            getText = { it },
            getTooltip = { it },
        )

        // TODO header name as tooltip without breaking sorting

        addHighlighter(HighlighterFactory.createSimpleStriping())

        packAll()
        actionMap.remove("find")
    }

    override fun createDefaultColumnControl(): JComponent {
        return ColumnControlButton(this, FlatSVGIcon("icons/bx-column.svg").derive(0.5F))
    }

    @Suppress("UNCHECKED_CAST")
    override fun getModel(): T = super.getModel() as T

    override fun setModel(model: TableModel) {
        if (setup) { // not sure why this is required, something with Kotlin's late initialization
            require(model::class.java == modelClass) { "Expected $modelClass but got ${model::class.java}" }
        }
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

    companion object {
        inline operator fun <reified T : TableModel> invoke(
            model: T,
            columns: ColumnList<*>? = null,
        ): ReifiedJXTable<T> {
            return ReifiedJXTable(model, T::class.java, columns)
        }
    }
}
