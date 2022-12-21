package io.github.paulgriffith.kindling.zip.views

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.zip.SinglePathView
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_CSS
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JSON
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON
import org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_XML
import org.fife.ui.rsyntaxtextarea.Theme
import org.fife.ui.rtextarea.RTextScrollPane
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.UIManager
import kotlin.io.path.extension
import kotlin.io.path.name

class TextFileView(override val provider: FileSystemProvider, override val path: Path) : SinglePathView() {
    private val textArea = RSyntaxTextArea().apply {
        isEditable = false
        syntaxEditingStyle = KNOWN_EXTENSIONS[path.extension] ?: SYNTAX_STYLE_NONE

        updateTheme()
    }

    private fun RSyntaxTextArea.updateTheme() {
        val theme = if (FlatLaf.isLafDark()) Themes.DARK else Themes.LIGHT
        theme.theme.apply(this)
    }

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-file.svg")

    init {
        val text = provider.newInputStream(path).use {
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

    enum class Themes(private val themeName: String) {
        LIGHT("idea.xml"),
        DARK("dark.xml");

        val theme: Theme by lazy {
            Theme::class.java.getResourceAsStream("themes/$themeName").use(Theme::load)
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val JSON_FORMAT = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        private val KNOWN_EXTENSIONS = mapOf(
            "conf" to SYNTAX_STYLE_PROPERTIES_FILE,
            "properties" to SYNTAX_STYLE_PROPERTIES_FILE,
            "py" to SYNTAX_STYLE_PYTHON,
            "json" to SYNTAX_STYLE_JSON,
            "svg" to SYNTAX_STYLE_XML,
            "xml" to SYNTAX_STYLE_XML,
            "css" to SYNTAX_STYLE_CSS,
            "txt" to SYNTAX_STYLE_NONE,
            "md" to SYNTAX_STYLE_NONE,
        )

        private val KNOWN_FILENAMES = setOf(
            "README",
            ".uuid",
        )

        fun isTextFile(path: Path) = path.extension in KNOWN_EXTENSIONS || path.name in KNOWN_FILENAMES
    }
}
