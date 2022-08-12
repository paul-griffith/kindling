package io.github.paulgriffith.kindling.utils

import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.KeyStroke

/**
 * More idiomatic Kotlin wrapper for AbstractAction.
 */
class Action(
    name: String? = null,
    description: String? = null,
    icon: Icon? = null,
    accelerator: KeyStroke? = null,
    private val action: (e: ActionEvent) -> Unit
) : AbstractAction() {
    init {
        putNonNull(NAME, name)
        putNonNull(SHORT_DESCRIPTION, description)
        putNonNull(SMALL_ICON, icon)
        putNonNull(ACCELERATOR_KEY, accelerator)
    }

    private fun putNonNull(key: String, value: Any?) {
        if (value != null) {
            putValue(key, value)
        }
    }

    override fun actionPerformed(e: ActionEvent) = action(e)
}
