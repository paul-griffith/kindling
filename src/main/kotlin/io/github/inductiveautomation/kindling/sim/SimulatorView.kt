package io.github.inductiveautomation.kindling.sim

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.CheckBoxList
import io.github.inductiveautomation.kindling.core.CustomIconView
import io.github.inductiveautomation.kindling.core.Kindling
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.HomeLocation
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.sim.model.ProgramDataType
import io.github.inductiveautomation.kindling.sim.model.SimulatorFunction
import io.github.inductiveautomation.kindling.sim.model.SimulatorFunction.Companion.generateRandomParametersForFunction
import io.github.inductiveautomation.kindling.sim.model.TagParser
import io.github.inductiveautomation.kindling.sim.model.TagParser.Companion.JSON
import io.github.inductiveautomation.kindling.sim.model.exportToFile
import io.github.inductiveautomation.kindling.utils.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedReader
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.properties.Delegates
import kotlin.reflect.KClass

class SimulatorView(path: Path) : ToolPanel() {
    override val icon: Icon = FlatSVGIcon("icons/bx-archive.svg")

    @OptIn(ExperimentalSerializationApi::class)
    private val tagParser = TagParser(path.inputStream().use(JSON::decodeFromStream))

    private val programs = tagParser.programItems.groupBy {
        it.deviceName
    }.mapValues { (_, programItems) ->
        programItems.toMutableList()
    }

    private var numberOfOpcTags: Int by Delegates.observable(tagParser.programItems.size) { _, _, newValue ->
        countLabel.text = buildString {
            tag("html") {
                tag("b", "Number of OPC Tags: $newValue")
            }
        }
    }

    private val countLabel = JLabel(
        buildString {
            tag("html") {
                tag("b", "Number of OPC Tags: ${tagParser.programItems.size}")
            }
        },
    )

    private val missingDefinitionsWarning = JLabel(
        "Missing UDT Definitions!",
        FlatSVGIcon("icons/bx-error.svg"), // .derive(10, 10),
        JLabel.RIGHT,
    ).apply {
        foreground = Color.decode("#DB5860")
        font = font.deriveFont(Font.BOLD)
        isVisible = tagParser.missingDefinitions.isNotEmpty()
        toolTipText = tagParser.missingDefinitions.joinToString("\n", prefix = MISSING_DEF_WARNING)
    }

    private val unsupportedDataTypesWarning = JLabel(
        "Unsupported tag data types!",
        FlatSVGIcon("icons/bx-error.svg"), // .derive(10, 10),
        JLabel.RIGHT,
    ).apply {
        foreground = Color.decode("#DB5860")
        font = font.deriveFont(Font.BOLD)
        isVisible = tagParser.unsupportedDataTypes.isNotEmpty()
        toolTipText = tagParser.unsupportedDataTypes.entries.joinToString(
            separator = "\n",
            prefix = """The following tag data types, including number of occurances, are not supported.
                |Tags of these data types have been omitted.\n
            """.trimMargin(),
            transform = { (type, num) -> "$type [$num]" }
        )
    }

    private val exportButton = JButton("Export All").apply {
        addActionListener {
            if (directoryChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                val selectedFolder = directoryChooser.selectedFile.toPath()

                programs.entries.forEach { (deviceName, programItems) ->
                    val filePath = selectedFolder.resolve("$deviceName-sim.csv")
                    programItems.exportToFile(filePath)
                }
                JOptionPane.showMessageDialog(this@SimulatorView, "Tag Export Finished.")
            }
        }
    }

    private val randomizeWindow = RandomizeWindow()

    private val randomizeButton = JButton(Action("Randomize") { randomizeWindow.isVisible = true })

    private val infoPanel = JPanel(MigLayout("fill, ins 10")).apply {
        add(countLabel, "west")
        add(missingDefinitionsWarning, "west, gapleft 20")
        add(unsupportedDataTypesWarning, "west, gapleft 20")
        add(exportButton, "east, gapleft 10")
        add(randomizeButton, "east")
    }

    private val tabs = TabStrip().apply {
        programs.entries.forEach { (deviceName, programItems) ->
            val lazyTab = DeviceProgramTab(deviceName, programItems.size) {
                DeviceProgramPanel(deviceName, programItems).apply {
                    addPropertyChangeListener("numItems") { evt ->
                        // Change Main label count
                        val change = evt.newValue as Int - evt.oldValue as Int
                        numberOfOpcTags += change

                        if (evt.newValue as Int == 0) { // Remove tab
                            removeTabAt(selectedIndex)
                        } else { // Change tab title to reflect number of rows
                            setTitleAt(
                                selectedIndex,
                                "$deviceName (${evt.newValue} tags)",
                            )
                        }
                    }
                }
            }

            addTab(
                tabName = "$deviceName (${programItems.size} tags)",
                icon = null,
                component = lazyTab,
                tabTooltip = "Right click to export",
                select = false,
            )
        }

        setTabCloseCallback { thisRef, i ->
            // Update tag count when a tab is closed entirely
            val programName = (thisRef.getComponentAt(i) as DeviceProgramTab).deviceName
            val simulatorprogram = programs[programName]

            if (simulatorprogram != null) {
                numberOfOpcTags -= simulatorprogram.size
                simulatorprogram.clear()
            }

            thisRef.removeTabAt(i)
        }
    }

    private fun addFunctionDataChangeListener(l: FunctionDataChangeListener) = listenerList.add(l)

    private fun fireFunctionDataChanged() {
        listenerList.getAll<FunctionDataChangeListener>().forEach { it.functionDataChange() }
    }

    init {
        name = "Device Simulator"
        toolTipText = path.absolutePathString()

        add(infoPanel, "pushx, growx, span")
        add(tabs, "push, grow, span")

        addFunctionDataChangeListener {
            // We only need to update the UI for "active" panels which have been initialized
            val devicePanels = tabs.indices.asSequence().map {
                (tabs.getComponentAt(it) as TabStrip.LazyTab).component
            }.filter { component ->
                component.isInitialized()
            }.map { component ->
                component.value as DeviceProgramPanel
            }

            devicePanels.forEach { panel ->
                panel.getListeners(FunctionDataChangeListener::class.java).forEach {
                    it.functionDataChange()
                }
            }
        }
    }

    companion object {
        private const val MISSING_DEF_WARNING =
            "The following UDT definitions were not found in the tag export. Some bindings may not be resolved:\n"
        val directoryChooser = JFileChooser(HomeLocation.currentValue.absolutePathString()).apply {
            isMultiSelectionEnabled = false
            fileView = CustomIconView()
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

            Kindling.Preferences.UI.Theme.addChangeListener {
                updateUI()
            }
        }
    }

    inner class RandomizeWindow : JFrame() {
        private val msg = "Warning: Program items with the following data types will not be covered under current function selection"
        private var dataTypesNotCovered = emptyList<ProgramDataType>()
            set(value) {
                field = value
                warningLabel.isVisible = value.isNotEmpty()
                warningLabel.text = buildString {
                    tag("html") {
                        tag("b") {
                            append(msg)
                        }
                        append("<br>")
                        append(dataTypesNotCovered.joinToString(", "))
                    }
                }
            }

        private val checkBoxListOptions = SimulatorFunction.functions.keys.toTypedArray()

        private val checkBoxList = CheckBoxList(checkBoxListOptions).apply {
            cellRenderer = listCellRenderer { _, value: KClass<*>, _, _, _ ->
                text = value.simpleName
            }

            selectAll()

            checkBoxListSelectionModel.addListSelectionListener {
                val possibleDataTypes = programs.values.flatten().map { it.dataType }.distinct()

                val dataTypesCovered = checkBoxListSelectedValues.flatMap {
                    val function = it as KClass<*>
                    SimulatorFunction.compatibleTypes[function]!!
                }.distinct()

                dataTypesNotCovered = possibleDataTypes.filter { it !in dataTypesCovered }
            }
        }

        private val warningLabel = JLabel().apply { isVisible = false }

        init {
            contentPane = JPanel(MigLayout("fill, ins 10, gap 10, hidemode 3")).apply {
                add(JLabel("Select Functions to include in randomization"), "growx, wrap")
                add(checkBoxList, "push, grow, span")
                add(warningLabel, "growx, span")
                add(
                    JButton("Confirm").apply {
                        addActionListener {
                            val programItems = programs.values.flatten()
                            programItems.forEach { item ->
                                val newFun = generateRandomFunction(item.dataType)
                                if (newFun != null) item.valueSource = newFun
                            }
                            fireFunctionDataChanged()
                            this@RandomizeWindow.isVisible = false
                        }
                    },
                    "right",
                )
            }
            setSize(300, 600)
            setLocationRelativeTo(null)
            iconImage = Kindling.frameIcons[3]
            defaultCloseOperation = HIDE_ON_CLOSE
        }

        private fun generateRandomFunction(dataType: ProgramDataType): SimulatorFunction? {
            val availableOptions = SimulatorFunction.compatibleTypes.filter { (_, dataTypes) ->
                dataType in dataTypes
            }.keys

            val newFunClass = checkBoxList.checkBoxListSelectedValues.filter {
                (it as KClass<*>) in availableOptions
            }.ifEmpty {
                return null
            }.random()

            val newFunInstance = SimulatorFunction.functions[newFunClass]!!.invoke()

            newFunInstance.generateRandomParametersForFunction(dataType)

            return newFunInstance
        }
    }

    inner class DeviceProgramTab(
        val deviceName: String,
        numberOfTags: Int,
        supplier: () -> Component,
    ) : TabStrip.LazyTab(
        "$deviceName ($numberOfTags tags)",
        null,
        "$numberOfTags items",
        supplier,
    ), FloatableComponent {
        override fun customizePopupMenu(menu: JPopupMenu) {
            menu.add(
                Action("Export to CSV") {
                    if (directoryChooser.showSaveDialog(this@SimulatorView) == JFileChooser.APPROVE_OPTION) {
                        val outputFile = directoryChooser.selectedFile.toPath().resolve("$deviceName-sim.csv")
                        programs[deviceName]!!.exportToFile(outputFile)
                        JOptionPane.showMessageDialog(this@SimulatorView, "Tag Export Finished.")
                    }
                },
            )
        }
    }
}

object SimulatorViewer : Tool {
    override val description = "Tag Export (json)"
    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-tag.svg")
    override val title = "Tag Export (Device Sim)"
    override val filter = FileFilter(
        description = description,
        predicate = { file ->
            file.extension == "json" &&
                    "\"tagType\": \"Provider\"," in buildString {
                file.bufferedReader().use { br ->
                    repeat(10) { append(br.readLine()) }
                }
            }
        },
    )

    override fun open(path: Path): ToolPanel {
        return SimulatorView(path)
    }
}

class SimulatorViewerProxy : Tool by SimulatorViewer
