package io.github.paulgriffith.kindling.core

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTextPane
import io.github.paulgriffith.kindling.internal.DetailsIcon
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.escapeHtml
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
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
        addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                val desktop = Desktop.getDesktop()
                desktop.browse(event.url.toURI())
            }
        }
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
        icon = FlatSVGIcon("icons/bx-save.svg"),
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

    private val extraButtons = mutableListOf<ButtonWrapper>()
    var isAllExtraButtonsEnabled: Boolean = true
        set(value) {
            field = value
            extraButtons.onEach {
                it.button.isEnabled = value
            }
        }
        get() = extraButtons.all { it.button.isEnabled }

    init {
        add(FlatScrollPane(textPane), "push, grow")
        add(JButton(copy), "cell 1 0, top, flowy, gap 0")
        add(JButton(save), "cell 1 0")
    }

    fun addButton(id: String, button: AbstractButton): String {
        button.isEnabled = isAllExtraButtonsEnabled
        add(button, "cell 1 0")
        extraButtons.add(ButtonWrapper(id, button))
        return id
    }

    fun addButton(id: String, icon: Icon, action: (ActionEvent) -> Unit): String {
        val button = JButton(Action(icon = icon, action = action))
        return addButton(id, button)
    }

    fun setButtonEnabled(id: String) {
        val buttonToEnable = extraButtons.find { it.id == id } ?: return
        buttonToEnable.button.isEnabled = true
    }

    fun setButtonDisabled(id: String) {
        val buttonToDisable = extraButtons.find { it.id == id } ?: return
        buttonToDisable.button.isEnabled = false
    }

    @Suppress("unused")
    fun removeButton(id: String?, button: AbstractButton?): Boolean {
        val buttonToRemove = when {
            id == null -> extraButtons.find { it.button == button }
            button == null -> extraButtons.find { it.id == id }
            else -> extraButtons.find { it == ButtonWrapper(id, button) }
        }
        if (extraButtons.remove(buttonToRemove)) {
            remove(buttonToRemove?.button)
            return true
        }
        return false
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
                    append(event.message.escapeHtml())
                }
                if (event.body.isNotEmpty()) {
                    event.body.joinTo(buffer = this, separator = "\n", prefix = "<pre>", postfix = "</pre>") { (text, link) ->
                        if (link != null) {
                            """<a href="$link">$text</a>"""
                        } else {
                            text
                        }
                    }
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
                event.body.joinTo(buffer = this, separator = "\n") { "\t${it.text}" }
            }
        }
    }
}

class ButtonWrapper(val id: String, val button: AbstractButton)

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
                """.trimIndent(),
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
