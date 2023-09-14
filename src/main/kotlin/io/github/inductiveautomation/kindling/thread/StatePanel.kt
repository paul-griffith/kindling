package io.github.inductiveautomation.kindling.thread

import io.github.inductiveautomation.kindling.core.FilterChangeListener
import io.github.inductiveautomation.kindling.core.FilterPanel
import io.github.inductiveautomation.kindling.utils.Column
import io.github.inductiveautomation.kindling.utils.FilterList
import io.github.inductiveautomation.kindling.utils.getAll
import javax.swing.JPopupMenu

class StatePanel : FilterPanel<io.github.inductiveautomation.kindling.thread.model.Thread?>() {
    val stateList = FilterList()
    override val tabName = "State"

    override val component = stateList

    init {
        stateList.selectAll()

        stateList.checkBoxListSelectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                listeners.getAll<FilterChangeListener>().forEach(FilterChangeListener::filterChanged)
            }
        }
    }

    override fun isFilterApplied(): Boolean = stateList.checkBoxListSelectedValues.size != stateList.model.size - 1

    override fun reset() = stateList.selectAll()

    override fun filter(item: io.github.inductiveautomation.kindling.thread.model.Thread?): Boolean = item?.state?.name in stateList.checkBoxListSelectedValues

    override fun customizePopupMenu(menu: JPopupMenu, column: Column<out io.github.inductiveautomation.kindling.thread.model.Thread?, *>, event: io.github.inductiveautomation.kindling.thread.model.Thread?) = Unit
}