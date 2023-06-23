package io.github.inductiveautomation.kindling.sim

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.CustomIconView
import io.github.inductiveautomation.kindling.core.Kindling
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.sim.model.SimulatorProgram
import io.github.inductiveautomation.kindling.sim.model.TagParser
import io.github.inductiveautomation.kindling.sim.model.TagParser.Companion.JSON
import io.github.inductiveautomation.kindling.sim.model.exportToFile
import io.github.inductiveautomation.kindling.utils.TabStrip
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import net.miginfocom.swing.MigLayout
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.io.path.absolutePathString
import kotlin.io.path.inputStream
import kotlin.properties.Delegates

class SimulatorView(path: Path) : ToolPanel() {
    override val icon: Icon = FlatSVGIcon("icons/bx-archive.svg")

    private var numberOfOpcTags: Int by Delegates.observable(0) { _, _, newValue ->
        countLabel.text = "<html><b>Number of OPC Tags: $newValue</b></html>"
    }

    private val countLabel = JLabel()

    @OptIn(ExperimentalSerializationApi::class)
    private val tagParser = TagParser(path.inputStream().use(JSON::decodeFromStream))

    private val programs: Map<String, SimulatorProgram> = run {
        tagParser.deviceSimProgramItems.also {
            numberOfOpcTags = it.size
        }.groupBy {
            it.deviceName
        }.mapValues { (_, programItems) ->
            programItems.toMutableList()
        }
    }

    private val exportButton = JButton("Export All").apply {
        addActionListener {
            if (directoryChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                val selectedFolder = directoryChooser.selectedFile.toPath()

                programs.entries.forEach { (deviceName, programItems) ->
                    val filePath = selectedFolder.resolve("$deviceName-sim.csv")
                    programItems.exportToFile(filePath)
                }
            }
        }
    }

    private val infoPanel = JPanel(MigLayout("fill, ins 10")).apply {
        add(countLabel, "west")
        add(exportButton, "east")
    }

    private val tabs = TabStrip()

    init {
        name = "Device Simulator"
        toolTipText = path.absolutePathString()

        add(infoPanel, "pushx, growx, span")

        programs.entries.forEach { (deviceName, programItems) ->
            val devicePanel = DeviceProgramPanel(deviceName, programItems).apply {
                addPropertyChangeListener("numItems") { evt ->

                    // Change Main label count
                    val change = evt.newValue as Int - evt.oldValue as Int
                    numberOfOpcTags += change

                    if (evt.newValue as Int == 0) { // Remove tab
                        tabs.remove(this)
                    } else { // Change tab title to reflect number of rows
                        tabs.setTitleAt(
                            tabs.indexOfComponent(this),
                            "$deviceName (${evt.newValue} tags)",
                        )
                    }
                }
            }

            tabs.addTab(
                "$deviceName (${programItems.size} tags)",
                devicePanel,
            )

            tabs.setTabCloseCallback { thisRef, i ->
                // Update tag count when a tab is closed entirely
                val programPanel = thisRef.getComponentAt(i) as DeviceProgramPanel
                numberOfOpcTags -= programPanel.numberOfTags
                programs[programPanel.deviceName]?.clear() // Remove program items so they don't get exported
                thisRef.removeTabAt(i)
            }
        }

        add(tabs, "push, grow, span")
    }

    companion object {
        val directoryChooser = JFileChooser(Kindling.homeLocation).apply {
            isMultiSelectionEnabled = false
            fileView = CustomIconView()
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

            Kindling.addThemeChangeListener {
                updateUI()
            }
        }
    }
}

object SimulatorViewer : Tool {
    override val extensions = listOf("json")
    override val description = "Opens a tag export as parses to a device simulator builder."
    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-box.svg")
    override val title = "Tag Export (Device Sim)"
    override fun open(path: Path): ToolPanel {
        return SimulatorView(path)
    }
}

class SimulatorViewerProxy : Tool by SimulatorViewer
