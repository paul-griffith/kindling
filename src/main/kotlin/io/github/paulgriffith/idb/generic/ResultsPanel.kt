package io.github.paulgriffith.idb.generic

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.inductiveautomation.ignition.common.util.csv.CSVWriter
import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.FlatScrollPane
import net.miginfocom.swing.MigLayout
import java.awt.Toolkit
import java.io.File
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.TransferHandler
import javax.swing.filechooser.FileNameExtensionFilter

class ResultsPanel : JPanel(MigLayout("ins 0, fill, hidemode 3")) {
    private val table = ResultsTable()

    private val errorDisplay = JLabel("No results - run a query in the text area above").apply {
        isVisible = false
    }

    var model: ResultModel by table::model

    private val copy = Action(
        description = "Copy to Clipboard",
        icon = FlatSVGIcon("icons/bx-clipboard.svg"),
    ) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        table.transferHandler.exportToClipboard(table, clipboard, TransferHandler.COPY)
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
                    table.model.data.forEach { line ->
                        csv.writeNext(line.map { it?.toString() })
                    }
                }
            }
        }
    }

    init {
        table.addPropertyChangeListener("model") {
            copy.isEnabled = model.rowCount > 0
            save.isEnabled = model.rowCount > 0
            if (model.rowCount == 0 && model.columnCount == 0) {
                isVisible = false
                errorDisplay.isVisible = true
            } else {
                isVisible = true
                errorDisplay.isVisible = false
            }
        }

        add(errorDisplay, "cell 0 0")
        add(FlatScrollPane(table), "cell 0 0, push, grow")
        add(JButton(copy), "cell 1 0, top, flowy")
        add(JButton(save), "cell 1 0")
    }
}
