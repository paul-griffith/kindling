package io.github.inductiveautomation.kindling.idb.tagconfig

import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.awt.Font
import java.nio.file.Path
import java.sql.Connection
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTextArea
import kotlin.io.path.Path
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
class TagConfigView(connection: Connection) : ToolPanel() {
    private val tagProviderData: List<TagProviderRecord> = TagProviderRecord.getProvidersFromDB(connection)
    override val icon = null
    val myLabel = JTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val exportButton = JButton("Export Tags").apply {
        addActionListener {
            (providerDropdown.selectedItem as? TagProviderRecord)?.let {
                if (exportFileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    val selectedFilePath = Path(exportFileChooser.selectedFile.absolutePath)
                    exportToJson(it, selectedFilePath)
                }
            } ?: JOptionPane.showMessageDialog(
                this@TagConfigView,
                "You must first select a Tag Provider",
                "Cannot Export Tags",
                JOptionPane.WARNING_MESSAGE,
            )
        }
    }

    private val providerDropdown = JComboBox(tagProviderData.toTypedArray()).apply {
        selectedIndex = -1
        addItemListener { itemEvent ->
            val selectedTagProvider = itemEvent.item as TagProviderRecord
            selectedTagProvider.initProviderNode()
            myLabel.text = "Total UDT Definitions: ${selectedTagProvider.statistics.totalUdtDefinitions}"
        }
        configureCellRenderer { _, value, _, _, _ ->
            text = value?.name ?: "Select a Tag Provider..."
        }
    }
    init {

        add(exportButton)
        add(providerDropdown, "span")
        add(JScrollPane(myLabel), "grow, push, span")
    }

    private fun exportToJson(tagProvider: TagProviderRecord, selectedFilePath: Path) {
        selectedFilePath.outputStream().use {
            JSON.encodeToStream(tagProvider.providerNode.value, it)
        }
    }

    companion object {
        internal val JSON = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }
}
