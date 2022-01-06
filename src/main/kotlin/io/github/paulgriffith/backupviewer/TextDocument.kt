package io.github.paulgriffith.backupviewer

import io.github.paulgriffith.utils.FlatScrollPane
import net.lingala.zip4j.ZipFile
import net.miginfocom.swing.MigLayout
import javax.swing.JPanel
import javax.swing.JTextArea

class TextDocument(zipFile: ZipFile, entry: String) : JPanel(MigLayout("ins 0, fill")) {
    private val header = zipFile.getFileHeader(entry)
    private val file = zipFile.getInputStream(header).bufferedReader().readText()
    private val textArea = JTextArea(file).apply {
        isEditable = false
    }

    init {
        add(FlatScrollPane(textArea), "push, grow")
    }
}
