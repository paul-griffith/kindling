package io.github.paulgriffith.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTextPane
import net.miginfocom.swing.MigLayout
import org.intellij.lang.annotations.Language
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTable
import kotlin.properties.Delegates

class DetailsPane : JPanel(MigLayout("ins 0, fill")) {
    private val textPane = FlatTextPane().apply {
        contentType = "text/html"
        isEditable = false
    }

    var events: List<Detail> by Delegates.observable(emptyList()) { _, _, newValue ->
        textPane.text = newValue.toDisplayFormat()
        detailsModel.details = newValue.collapseDetails()
    }

    private val copy = Action(
        description = "Copy",
        icon = FlatSVGIcon("icons/bx-clipboard.svg"),
    ) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(events.toClipboardFormat(), null)
    }

    private fun List<Detail>.collapseDetails(): List<Pair<String, String>> {
        return this.fold(mutableListOf()) { acc, detail ->
            acc += detail.details.map { it.toPair() }
            acc
        }
    }

    private val detailsModel = DetailsModel(events.collapseDetails())
    private val detailsTable = JTable(detailsModel).apply {
        setDefaultRenderer<String> { _, value, _, _, _, _ ->
            text = value
            toolTipText = value
        }
    }

    init {
        add(FlatScrollPane(textPane), "push, grow")
        add(JButton(copy), "top")
        add(FlatScrollPane(detailsTable), "growy, pushy, width 15%")
    }

    private fun List<Detail>.toDisplayFormat(): String {
        return joinToString(separator = "", prefix = "<html>$STYLE") { event ->
            buildString {
                append("<b>").append(event.title).append("</b>")
                if (event.message != null) {
                    append("<br>")
                    append(event.message)
                }
                if (event.body.isNotEmpty()) {
                    event.body.joinTo(buffer = this, separator = "\n", prefix = "<pre>", postfix = "</pre>")
                } else {
                    append("<br>")
                }
            }
        }
    }

    private fun List<Detail>.toClipboardFormat(): StringSelection {
        return StringSelection(
            joinToString(separator = "\n\n") { event ->
                buildString {
                    appendLine(event.title)
                    if (event.message != null) {
                        appendLine(event.message)
                    }
                    event.body.joinTo(buffer = this, separator = "\n") { "\t$it" }
                }
            }
        )
    }

    companion object {
        @Language("HTML")
        private val STYLE = """
            <style>
            pre {
               font-size: 10px;
            }
            </style>
        """.trimIndent()
    }
}
