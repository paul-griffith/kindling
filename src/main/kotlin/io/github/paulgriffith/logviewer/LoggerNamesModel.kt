package io.github.paulgriffith.logviewer

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.CheckBoxList
import com.jidesoft.swing.ListSearchable
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.listCellRenderer
import net.miginfocom.swing.MigLayout
import javax.swing.AbstractListModel
import javax.swing.ButtonGroup
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JToggleButton
import kotlin.properties.Delegates

data class LoggerName(
    val name: String,
    val eventCount: Int,
)

class LoggerNamesModel(names: List<LoggerName>) : AbstractListModel<Any>() {
    var data: List<LoggerName> by Delegates.observable(names) { _, _, newValue ->
        fireContentsChanged(this, 0, newValue.size - 1)
    }

    override fun getSize(): Int = data.size + 1
    override fun getElementAt(index: Int): Any {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            data[index - 1]
        }
    }
}

class LoggerNamesPanel(events: List<Event>) : JPanel(MigLayout("ins 0, fill, wrap 1")) {
    private val loggerNames: List<LoggerName> = events.groupingBy(Event::logger)
        .eachCount()
        .entries
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
        .map { (key, value) -> LoggerName(key, value) }

    private val listModel: LoggerNamesModel = LoggerNamesModel(loggerNames)

    val list = CheckBoxList(listModel).apply {
        SearchableImpl(this)
        cellRenderer = listCellRenderer<Any> { _, value, _, _, _ ->
            when (value) {
                is LoggerName -> {
                    text = "${value.name.substringAfterLast(".")} - [${value.eventCount}]"
                    toolTipText = value.name
                }
                else -> {
                    text = value.toString()
                }
            }
        }
    }

    class SearchableImpl(list: JList<Any?>) : ListSearchable(list) {
        init {
            isCaseSensitive = false
            isRepeats = true
            isCountMatch = true
        }

        override fun convertElementToString(element: Any?): String {
            return if (element is LoggerName) {
                element.name
            } else {
                element.toString()
            }
        }
    }

    inner class SortButton(
        icon: FlatSVGIcon,
        toolTip: String,
        comparator: Comparator<LoggerName>,
    ) : JToggleButton(icon) {
        init {
            toolTipText = toolTip
            addActionListener {
                listModel.data = listModel.data.sortedWith(comparator)
            }
        }
    }

    init {
        val sortButtons = ButtonGroup()

        val naturalAsc = SortButton(
            icon = NATURAL_SORT_ASCENDING,
            toolTip = "Sort A-Z",
            comparator = byName,
        )
        listOf(
            naturalAsc,
            SortButton(
                icon = NATURAL_SORT_DESCENDING,
                toolTip = "Sort Z-A",
                comparator = byName.reversed(),
            ),
            SortButton(
                icon = NUMERIC_SORT_DESCENDING,
                toolTip = "Sort by Count",
                comparator = byCount.reversed() then byName,
            ),
            SortButton(
                icon = NUMERIC_SORT_ASCENDING,
                toolTip = "Sort by Count (ascending)",
                comparator = byCount then byName
            )
        ).forEach { sortButton ->
            sortButtons.add(sortButton)
            add(sortButton, "cell 0 0")
        }

        sortButtons.setSelected(naturalAsc.model, true)

        add(FlatScrollPane(list), "push, growy, span 4")
    }

    companion object {
        private val byName = compareBy(String.CASE_INSENSITIVE_ORDER, LoggerName::name)
        private val byCount = compareBy(LoggerName::eventCount)

        private val NATURAL_SORT_ASCENDING = FlatSVGIcon("icons/bx-sort-a-z.svg", 16, 16)
        private val NATURAL_SORT_DESCENDING = FlatSVGIcon("icons/bx-sort-z-a.svg", 16, 16)
        private val NUMERIC_SORT_ASCENDING = FlatSVGIcon("icons/bx-sort-up.svg", 16, 16)
        private val NUMERIC_SORT_DESCENDING = FlatSVGIcon("icons/bx-sort-down.svg", 16, 16)
    }
}
