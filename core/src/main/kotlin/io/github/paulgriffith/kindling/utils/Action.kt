package io.github.paulgriffith.kindling.utils

import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.KeyStroke
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * More idiomatic Kotlin wrapper for AbstractAction.
 */
class Action(
    name: String? = null,
    description: String? = null,
    icon: Icon? = null,
    accelerator: KeyStroke? = null,
    private val action: ActionListener,
) : AbstractAction() {
    var name: String? by actionValue(NAME)
    var description: String? by actionValue(SHORT_DESCRIPTION)
    var icon: Icon? by actionValue(SMALL_ICON)
    var accelerator: KeyStroke? by actionValue(ACCELERATOR_KEY)

    init {
        this.name = name
        this.description = description
        this.icon = icon
        this.accelerator = accelerator
    }

    private fun <V> actionValue(name: String) = object : ReadWriteProperty<AbstractAction, V> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: AbstractAction, property: KProperty<*>): V {
            return thisRef.getValue(name) as V
        }

        override fun setValue(thisRef: AbstractAction, property: KProperty<*>, value: V) {
            return thisRef.putValue(name, value)
        }
    }

    override fun actionPerformed(e: ActionEvent) = action.actionPerformed(e)
}
