package io.github.inductiveautomation.kindling.core

import io.github.inductiveautomation.kindling.utils.Column
import java.util.EventListener
import javax.swing.JComponent
import javax.swing.JPopupMenu

interface FilterPanel<T> : Filter<T> {
    val tabName: String
    fun isFilterApplied(): Boolean
    val component: JComponent
    fun addFilterChangeListener(listener: FilterChangeListener)

    fun reset()

    fun customizePopupMenu(
        menu: JPopupMenu,
        column: Column<out T, *>,
        event: T,
    ) = Unit
}

fun interface Filter<T> {
    /**
     * Return true if this filter should display this event.
     */
    fun filter(event: T): Boolean
}

fun interface FilterChangeListener : EventListener {
    fun filterChanged()
}