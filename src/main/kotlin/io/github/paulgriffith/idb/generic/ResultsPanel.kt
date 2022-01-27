package io.github.paulgriffith.idb.generic

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.inductiveautomation.ignition.common.util.csv.CSVWriter
import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.FlatScrollPane
import net.miginfocom.swing.MigLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.Base64
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.filechooser.FileNameExtensionFilter

class ResultsPanel : JPanel(MigLayout("ins 0, fill, hidemode 3")) {
    private val table = ResultsTable()

    private val errorDisplay = JLabel("No results - run a query in the text area above")

    private val tableDisplay = FlatScrollPane(table).apply {
        isVisible = false
    }

    var result: QueryResult? = null
        set(value) {
            when (value) {
                is QueryResult.Success -> {
                    table.model = value
                    tableDisplay.isVisible = true
                    errorDisplay.isVisible = false
                    copy.isEnabled = value.rowCount > 0
                    save.isEnabled = value.rowCount > 0
                }
                is QueryResult.Error -> {
                    errorDisplay.text = value.details
                    errorDisplay.icon = ERROR_ICON
                    tableDisplay.isVisible = false
                    errorDisplay.isVisible = true
                }
                else -> Unit
            }
            field = value
        }

    private val copy = Action(
        description = "Copy to Clipboard",
        icon = FlatSVGIcon("icons/bx-clipboard.svg"),
    ) {
        val tsv = buildString {
            table.model.columnNames.joinTo(buffer = this, separator = "\t")
            appendLine()
            val rowsToExport = if (table.selectionModel.isSelectionEmpty) {
                table.model.data.indices.toList()
            } else {
                table.selectionModel.selectedIndices
                    .filter { table.isRowSelected(it) }
                    .map { table.convertRowIndexToModel(it) }
            }
            rowsToExport.map { table.model.data[it] }
                .forEach { line ->
                    line.joinTo(buffer = this, separator = "\t") { cell ->
                        when (cell) {
                            is ByteArray -> BASE64.encodeToString(cell)
                            else -> cell?.toString().orEmpty()
                        }
                    }
                    appendLine()
                }
        }

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(tsv), null)
    }

    private val save = Action(
        description = "Save to File",
        icon = FlatSVGIcon("icons/bx-save.svg")
    ) {
        JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("CSV File", "csv")
            selectedFile = File("query results.csv")
            val save = showSaveDialog(this@ResultsPanel)
            if (save == JFileChooser.APPROVE_OPTION) {
                CSVWriter(selectedFile.writer()).use { csv ->
                    csv.writeNext(table.model.columnNames)
                    val rowsToExport = if (table.selectionModel.isSelectionEmpty) {
                        table.model.data.indices.toList()
                    } else {
                        table.selectionModel.selectedIndices
                            .filter { table.isRowSelected(it) }
                            .map { table.convertRowIndexToModel(it) }
                    }

                    rowsToExport.map { table.model.data[it] }
                        .forEach { line ->
                            csv.writeNext(
                                line.map { cell ->
                                    when (cell) {
                                        is ByteArray -> BASE64.encodeToString(cell)
                                        else -> cell?.toString()
                                    }
                                }
                            )
                        }
                }
            }
        }
    }

    init {
        add(errorDisplay, "cell 0 0, push, grow")
        add(tableDisplay, "cell 0 0, push, grow")
        add(JButton(copy), "cell 1 0, top, flowy")
        add(JButton(save), "cell 1 0")
    }

    companion object {
        private val BASE64: Base64.Encoder = Base64.getEncoder()
        private val ERROR_ICON = FlatSVGIcon("icons/bx-error.svg").derive(3.0F)
    }
}
