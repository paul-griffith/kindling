package io.github.paulgriffith.log

import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.properties.Delegates

class Header(private val totalRows: Int) : JPanel(MigLayout("ins 0, fill")) {
    private val events = JLabel("$totalRows (of $totalRows) events")

    val levels = JComboBox(Level.values())
    val search = JXSearchField("Search")

    init {
        add(events, "pushx")
        add(search, "width 300, gap unrelated")
        add(JLabel("Minimum Level:"), "gap related")
        add(levels)
    }

    var displayedRows by Delegates.observable(totalRows) { _, _, newValue ->
        events.text = "$newValue (of $totalRows) events"
    }
}
