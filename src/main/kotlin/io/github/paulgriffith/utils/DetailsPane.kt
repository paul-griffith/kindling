package io.github.paulgriffith.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTextPane
import net.miginfocom.swing.MigLayout
import org.intellij.lang.annotations.Language
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextPane
import javax.swing.SwingConstants
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument
import kotlin.properties.Delegates

class DetailsPane : JPanel(MigLayout("ins 0, fill")) {
    private val textPane = FlatTextPane().apply {
        contentType = "text/html"
        isEditable = false
    }

    var events: List<Detail> by Delegates.observable(emptyList()) { _, _, newValue ->
        textPane.display(newValue)
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

    private fun Detail.toIcon(): JLabel? {
        return if (details.isNotEmpty()) {
            JLabel(icon).apply {
                toolTipText = details.entries.joinToString(separator = "\n") { (key, value) -> "$key: $value" }
                horizontalAlignment = SwingConstants.RIGHT
            }
        } else {
            null
        }
    }

    private fun JTextPane.display(details: List<Detail>) {
        text = "<html>$STYLE<body>"
        styledDocument.apply {
            details.forEach { detail ->
                appendString(detail.title, StyleConstants.Bold)
                val addtl = detail.toIcon()
                if (addtl != null) {
                    insertComponent(addtl)
                }
                appendString("\n")
                appendString(detail.message.orEmpty())
                if (detail.body.isNotEmpty()) {
                    appendString(
                        detail.body.joinToString(separator = "\n"),
                    )
                } else {
                    appendString("\n")
                }
            }
        }
    }

    private fun StyledDocument.appendString(string: String, vararg attributes: Any) {
        insertString(
            endPosition.offset - 1, string,
            SimpleAttributeSet().apply {
                attributes.forEach { attribute ->
                    addAttribute(attribute, true)
                }
            }
        )
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

        private val icon = FlatSVGIcon("icons/bx-search.svg")
    }
}
