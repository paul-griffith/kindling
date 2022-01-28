package io.github.paulgriffith.idb.logviewer

import com.formdev.flatlaf.extras.components.FlatTextField
import net.miginfocom.swing.MigLayout
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.properties.Delegates

class Header(private val max: Int) : JPanel(MigLayout("ins 0, fill")) {
    private val events = JLabel("$max (of $max) events")

    val levels = JComboBox(Event.Level.values())

    val search = FlatTextField().apply {
        placeholderText = "Search"
    }

    init {
        add(events, "pushx")
        add(search, "width 300, gap unrelated")
        add(JLabel("Minimum Level:"), "gap related")
        add(levels)

        levels.addActionListener {
            firePropertyChange("level", null, levels.selectedItem)
        }
    }

    var displayedRows by Delegates.observable(max) { _, _, newValue ->
        events.text = "$newValue (of $max) events"
    }
}
