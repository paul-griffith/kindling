package io.github.paulgriffith.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTextPane
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.text.ComponentView
import javax.swing.text.Element
import javax.swing.text.StyleConstants
import javax.swing.text.View
import javax.swing.text.ViewFactory
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import kotlin.properties.Delegates

class DetailsPane : JPanel(MigLayout("ins 0, fill")) {
    var events: List<Detail> by Delegates.observable(emptyList()) { _, _, newValue ->
        textPane.text = newValue.toDisplayFormat()
        EventQueue.invokeLater {
            textPane.scrollRectToVisible(Rectangle(0, 0, 0, 0))
        }
    }

    private val textPane = FlatTextPane().apply {
        isEditable = false
        editorKit = DetailsEditorKit()
    }

    private val copy = Action(
        description = "Copy to Clipboard",
        icon = FlatSVGIcon("icons/bx-clipboard.svg"),
    ) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(events.toClipboardFormat()), null)
    }

    private val save = Action(
        description = "Save to File",
        icon = FlatSVGIcon("icons/bx-save.svg")
    ) {
        JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("Text File", "txt")
            val save = showSaveDialog(this@DetailsPane)
            if (save == JFileChooser.APPROVE_OPTION) {
                selectedFile.writeText(events.toClipboardFormat())
            }
        }
    }

    init {
        add(FlatScrollPane(textPane), "push, grow")
        add(JButton(copy), "cell 1 0, top, flowy, gap 0")
        add(JButton(save), "cell 1 0")
    }

    private fun List<Detail>.toDisplayFormat(): String {
        return joinToString(separator = "", prefix = "<html>") { event ->
            buildString {
                append("<b>").append(event.title)
                if (event.details.isNotEmpty()) {
                    append("&nbsp;<object ")
                    event.details.entries.joinTo(buffer = this, separator = " ") { (key, value) ->
                        "data-$key = \"$value\""
                    }
                    append("/>")
                }
                append("</b>")
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

    private fun List<Detail>.toClipboardFormat(): String {
        return joinToString(separator = "\n\n") { event ->
            buildString {
                appendLine(event.title)
                if (event.message != null) {
                    appendLine(event.message)
                }
                event.body.joinTo(buffer = this, separator = "\n") { "\t$it" }
            }
        }
    }
}

class DetailsEditorKit : HTMLEditorKit() {
    init {
        styleSheet.apply {
            //language=CSS
            addRule(
                """
                b { 
                    font-size: larger; 
                }
                pre { 
                    font-size: 10px; 
                }
                object { 
                    padding-left: 16px; 
                }
                """.trimIndent()
            )
        }
    }

    override fun getViewFactory(): ViewFactory {
        return object : HTMLFactory() {
            override fun create(elem: Element): View {
                val attrs = elem.attributes
                val o = attrs.getAttribute(StyleConstants.NameAttribute)
                if (o == HTML.Tag.OBJECT) {
                    return object : ComponentView(elem) {
                        override fun createComponent(): Component {
                            val details: Map<String, String> =
                                elem.attributes.attributeNames.toList().filterIsInstance<String>()
                                    .associateWith { elem.attributes.getAttribute(it) as String }
                            return DetailsIcon(details)
                        }
                    }
                }
                return super.create(elem)
            }
        }
    }
}
