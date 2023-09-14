package io.github.inductiveautomation.kindling.log

import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.FilterModel
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JToggleButton

internal class ThreadPanel(events: List<LogEvent>) : FilterPanel<LogEvent>() {
    private val filterList = FilterList().apply {
        setModel(FilterModel(events.groupingBy { (it as SystemLogEvent).thread }.eachCount()))
    }

    override val component = JPanel(MigLayout("ins 0, fill"))
    override val tabName: String = "Thread"

    init {
        val bg = ButtonGroup()
        for (sortAction in filterList.sortActions) {
            val sortToggle = JToggleButton(sortAction)
            bg.add(sortToggle)
            component.add(sortToggle, "split, gapx 2")
        }

        component.add(FlatScrollPane(filterList), "newline, push, grow")

        filterList.selectAll()
        filterList.checkBoxListSelectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override fun isFilterApplied(): Boolean = filterList.checkBoxListSelectedIndices.size < filterList.model.size - 1

    override fun filter(item: LogEvent): Boolean {
        return (item as SystemLogEvent).thread in filterList.checkBoxListSelectedValues
    }

    override fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out LogEvent, *>,
        event: LogEvent,
    ) {
        if (column == SystemLogColumns.Thread) {
            val threadIndex = filterList.model.indexOf((event as SystemLogEvent).thread)
            menu.add(
                Action("Show only ${event.thread} events") {
                    filterList.checkBoxListSelectedIndex = threadIndex
                    filterList.ensureIndexIsVisible(threadIndex)
                },
            )
            menu.add(
                Action("Exclude ${event.thread} events") {
                    filterList.removeCheckBoxListSelectedIndex(threadIndex)
                },
            )
        }
    }

    override fun reset() = filterList.selectAll()
}
