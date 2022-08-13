package io.github.paulgriffith.kindling.idb.generic

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.ReifiedJXTable
import io.github.paulgriffith.kindling.utils.ReifiedLabelProvider.Companion.setDefaultRenderer
import io.github.paulgriffith.kindling.utils.exportMenu
import io.github.paulgriffith.kindling.utils.toFileSizeLabel
import net.miginfocom.swing.MigLayout
import javax.swing.JLabel
import javax.swing.JMenuBar
import javax.swing.JPanel

class ResultsPanel : JPanel(MigLayout("ins 0, fill, hidemode 3")) {
    private val table = ReifiedJXTable(QueryResult.Success()).apply {
        setDefaultRenderer<ByteArray>(
            getText = {
                if (it != null) {
                    "${it.size.toLong().toFileSizeLabel()} BLOB"
                } else {
                    ""
                }
            },
            getTooltip = { "Export to CSV to view full data (b64 encoded)" }
        )
    }

    private val errorDisplay = JLabel("No results - run a query in the text area above")

    private val tableDisplay = FlatScrollPane(table).apply {
        isVisible = false
    }

    private val exportMenu = JMenuBar().apply {
        add(exportMenu { table.model })
    }

    var result: QueryResult? = null
        set(value) {
            when (value) {
                is QueryResult.Success -> {
                    table.model = value
                    tableDisplay.isVisible = true
                    errorDisplay.isVisible = false
                    exportMenu.isEnabled = value.rowCount > 0
                }

                is QueryResult.Error -> {
                    errorDisplay.text = value.details
                    errorDisplay.icon = ERROR_ICON
                    tableDisplay.isVisible = false
                    errorDisplay.isVisible = true
                    exportMenu.isEnabled = false
                }

                else -> Unit
            }
            field = value
        }

    init {
        add(errorDisplay, "cell 0 0, push, grow")
        add(tableDisplay, "cell 0 0, push, grow")
        add(exportMenu, "cell 1 0, top, flowy")
    }

    companion object {
        private val ERROR_ICON = FlatSVGIcon("icons/bx-error.svg").derive(3.0F)
    }
}
