package io.github.paulgriffith.kindling.backup

import io.github.paulgriffith.kindling.utils.FlatScrollPane
import net.miginfocom.swing.MigLayout
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.JPanel
import javax.swing.JTextArea

class TextDocument(zip: FileSystemProvider, path: Path) : JPanel(MigLayout("ins 0, fill")) {
    private val file = zip.newInputStream(path).use {
        it.bufferedReader().readText()
    }
    private val textArea = JTextArea(file).apply {
        isEditable = false
    }

    init {
        add(FlatScrollPane(textArea), "push, grow")
    }
}
