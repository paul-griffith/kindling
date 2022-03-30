package io.github.paulgriffith.log

import io.github.paulgriffith.utils.TypeSafeJComboBox
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import java.time.ZoneId
import java.time.zone.ZoneRulesProvider
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.properties.Delegates

class Header(private val totalRows: Int) : JPanel(MigLayout("ins 0, fill")) {
    private val events = JLabel("$totalRows (of $totalRows) events")

    val levels = JComboBox(Level.values())
    val timezone = TypeSafeJComboBox<String>(ZoneRulesProvider.getAvailableZoneIds().sorted().toTypedArray()).apply {
        selectedItem = ZoneId.systemDefault().id
    }
    val search = JXSearchField("Search")

    init {
        add(events, "pushx")
        add(timezone, "gap unrelated")
        add(search, "width 300, gap unrelated")
        add(JLabel("Minimum Level:"), "gap related")
        add(levels)
    }

    var displayedRows by Delegates.observable(totalRows) { _, _, newValue ->
        events.text = "$newValue (of $totalRows) events"
    }
}
