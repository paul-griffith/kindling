package io.github.paulgriffith.logviewer

import javax.swing.JLabel
import kotlin.properties.Delegates

class Header(private val max: Int) : JLabel() {
    init {
        text = "$max (of $max) events"
    }

    var displayedRows by Delegates.observable(max) { _, _, newValue ->
        text = "$newValue (of $max) events"
    }
}
