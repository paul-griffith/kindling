package io.github.paulgriffith.kindling.backup.views

import com.formdev.flatlaf.FlatLaf
import io.github.paulgriffith.kindling.backup.BackupViewer.Themes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.miginfocom.swing.MigLayout
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_CSS
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JSON
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_XML
import org.fife.ui.rtextarea.RTextScrollPane
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.io.path.extension

class TextFileView(zip: FileSystemProvider, private val path: Path) : JPanel(MigLayout("ins 0, fill")) {
    private val textArea = RSyntaxTextArea().apply {
        isEditable = false
        syntaxEditingStyle = KNOWN_EXTENSIONS[path.extension] ?: SYNTAX_STYLE_NONE

        updateTheme()
    }

    private fun RSyntaxTextArea.updateTheme() {
        val theme = if (FlatLaf.isLafDark()) Themes.DARK else Themes.LIGHT
        theme.theme.apply(this)
    }

    init {
        val text = zip.newInputStream(path).use {
            it.bufferedReader().readText()
        }

        textArea.text = if (path.extension == "json") {
            // pretty-print/normalize json
            JSON_FORMAT.run {
                encodeToString(JsonElement.serializer(), parseToJsonElement(text))
            }
        } else {
            text
        }

        UIManager.addPropertyChangeListener { e ->
            if (e.propertyName == "lookAndFeel") {
                textArea.updateTheme()
            }
        }

        add(RTextScrollPane(textArea), "push, grow")
    }

    override fun toString(): String = "TextFileView(path=$path)"

    companion object {
        private val JSON_FORMAT = Json {
            prettyPrint = true
        }

        val KNOWN_EXTENSIONS = mapOf(
            "conf" to SYNTAX_STYLE_PROPERTIES_FILE,
            "properties" to SYNTAX_STYLE_PROPERTIES_FILE,
            "py" to SYNTAX_STYLE_PYTHON,
            "json" to SYNTAX_STYLE_JSON,
            "svg" to SYNTAX_STYLE_XML,
            "xml" to SYNTAX_STYLE_XML,
            "css" to SYNTAX_STYLE_CSS,
        )
    }
}
