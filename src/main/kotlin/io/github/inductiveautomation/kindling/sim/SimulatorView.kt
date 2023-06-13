package io.github.inductiveautomation.kindling.sim

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.CustomIconView
import io.github.inductiveautomation.kindling.core.Kindling
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.sim.model.NodeStructure
import io.github.inductiveautomation.kindling.sim.model.ProgramDataType
import io.github.inductiveautomation.kindling.sim.model.ProgramItem
import io.github.inductiveautomation.kindling.sim.model.SimulatorFunction
import io.github.inductiveautomation.kindling.sim.model.SimulatorProgram
import io.github.inductiveautomation.kindling.sim.model.TagDataType
import io.github.inductiveautomation.kindling.sim.model.TagProviderStructure
import io.github.inductiveautomation.kindling.sim.model.UdtParameter
import io.github.inductiveautomation.kindling.sim.model.UdtParameterListSerializer
import io.github.inductiveautomation.kindling.sim.model.exportToFile
import io.github.inductiveautomation.kindling.utils.TabStrip
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.miginfocom.swing.MigLayout
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.io.path.inputStream
import kotlin.properties.Delegates

@OptIn(ExperimentalSerializationApi::class)
class SimulatorView(path: Path) : ToolPanel() {
    override val icon: Icon = FlatSVGIcon("icons/bx-archive.svg")

    private val builtDefinitions = mutableMapOf<String, JsonObject>()
    private val tagProvider: TagProviderStructure = path.inputStream().use(JSON::decodeFromStream)
    private val tags = tagProvider.resolveOpcTags().toList()

    private var numberOfOpcTags: Int by Delegates.observable(0) { _, _, newValue ->
        countLabel.text = "<html><b>Number of OPC Tags: $newValue</b></html>"
    }

    private val countLabel = JLabel()

    private val programs: MutableMap<String, SimulatorProgram> = tags.flatMap {
        it.parseDeviceProgram()
    }.also {
        numberOfOpcTags = it.size
    }.groupBy {
        it.deviceName
    }.mapValues { (_, programItems) ->
        programItems.toMutableList()
    }.toMutableMap()

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
        toolTipText = path.toString()

        add(infoPanel, "pushx, growx, span")

        programs.entries.forEach { (deviceName, programItems) ->
            val devicePanel = DeviceProgramPanel(deviceName, programItems).apply {
                addPropertyChangeListener("numItems") { evt ->

                    // Change Main label count
                    val change = evt.newValue as Int - evt.oldValue as Int
                    numberOfOpcTags += change

                    if (evt.newValue as Int == 0) { // Remove tab and device entry from programs map
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
                val programPanel = thisRef.getComponentAt(i) as DeviceProgramPanel
                numberOfOpcTags -= programPanel.numberOfTags
                programs[programPanel.deviceName]?.clear()
                thisRef.removeTabAt(i)
            }
        }

        add(tabs, "push, grow, span")
    }

    private fun NodeStructure.parseDeviceProgram(): SimulatorProgram {
        return mutableListOf<ProgramItem>().apply {
            tags?.forEach { tag ->
                when {
                    tag.valueSource == "opc" -> {
                        add(
                            ProgramItem(
                                browsePath = when (val path = tag.opcItemPath) {
                                    is JsonObject -> (path["binding"] as JsonPrimitive).content
                                    is JsonPrimitive -> path.content
                                    else -> throw IllegalArgumentException("OPC item path is ${this::class.java.name}")
                                }, // .replaceFirst("\\[.*?]".toRegex(), "[$deviceName]"),
                                dataType = TagDataType.valueOf(tag.dataType ?: "None").toSimDataType(),
                                valueSource = SimulatorFunction.defaultFunction,
                            ),
                        )
                    }

                    !tag.tags.isNullOrEmpty() -> { // Browsable node
                        addAll(tag.parseDeviceProgram())
                    }
                }
            }
        }
    }

    private fun TagProviderStructure.resolveOpcTags(): MutableList<NodeStructure> {
        val (defs, nodes) = tags.partition { node -> node.name == "_types_" }
        val resolvedTags = mutableListOf<NodeStructure>()
        buildUdtMap(defs.first().tags!!)
        iterateTags(nodes, resolvedTags)
        resolveParameters(resolvedTags, emptyList())
        return resolvedTags
    }

    private fun buildUdtMap(defs: List<NodeStructure>, relativePath: String = "") {
        for (udt in defs) {
            if (udt.tagType == "Folder") {
                buildUdtMap(udt.tags ?: emptyList(), "$relativePath${udt.name}/")
            } else {
                builtDefinitions["$relativePath${udt.name}"] = JSON.encodeToJsonElement(udt).jsonObject
            }
        }
    }

    private fun iterateTags(nodes: List<NodeStructure>, resolvedTags: MutableList<NodeStructure>) {
        for (node in nodes) {
            when (node.tagType) {
                "Folder" -> {
                    require(node.tags != null)
                    val folderTags = mutableListOf<NodeStructure>()
                    iterateTags(node.tags, folderTags)
                    resolvedTags.add(
                        NodeStructure(
                            name = node.name,
                            tagType = "Folder",
                            tags = folderTags.toList(),
                        ),
                    )
                }

                "UdtInstance" -> {
                    val defDict = builtDefinitions[node.typeId]!!
                    val retTag = zipObject(defDict, JSON.encodeToJsonElement(node).jsonObject)
                    resolvedTags.add(JSON.decodeFromJsonElement(retTag))
                }

                else -> resolvedTags.add(node)
            }
        }
    }

    private fun resolveParameters(tags: List<NodeStructure>, parameters: List<UdtParameter>) {
        for (tag in tags) {
            if (tag.tagType == "UdtInstance" && tag.parameters.isNotEmpty() && !tag.tags.isNullOrEmpty()) {
                val udtParams = run {
                    val params = JSON.encodeToJsonElement(UdtParameterListSerializer(), parameters).jsonObject
                    val tagParams = JSON.encodeToJsonElement(UdtParameterListSerializer(), tag.parameters).jsonObject
                    zipObject(params, tagParams)
                }

                resolveParameters(tag.tags, JSON.decodeFromJsonElement(UdtParameterListSerializer(), udtParams))
            } else if (tag.tagType == "Folder") {
                resolveParameters(tag.tags!!, parameters)
            } else if (tag.valueSource == "opc" && tag.opcItemPath is JsonObject && (tag.opcItemPath as JsonObject)["bindType"]!!.jsonPrimitive.content == "parameter") {
                for (param in parameters) {
                    val newItemPath = (tag.opcItemPath as JsonObject).toMutableMap().apply {
                        val binding = this["binding"]!!.jsonPrimitive.content.run {
                            val newParamValue = param.value?.jsonPrimitive?.content
                            replace("{${param.name}}", newParamValue ?: "null")
                        }
                        this["binding"] = Json.encodeToJsonElement(binding)
                    }
                    tag.opcItemPath = JSON.encodeToJsonElement(newItemPath)
                }
            }
        }
    }

    private fun zipObject(def: JsonObject, instance: JsonObject): JsonObject {
        return buildJsonObject {
            def.entries.forEach { put(it.key, it.value) }

            instance.entries.forEach { (key, value) ->
                if (key == "tags" && value is JsonArray) {
                    // Both should always be true - this check is for the compiler to smart cast value as JsonArray
                    val defKey = def[key]
                    require(defKey is JsonArray)
                    put(key, zipTags(defKey, value))
                } else if (value is JsonObject) {
                    val defKey = def[key]
                    if (defKey is JsonObject) {
                        put(key, zipObject(defKey, value))
                    } else {
                        put(key, value)
                    }
                } else {
                    put(key, value)
                }
            }
        }
    }

    private fun zipTags(def: JsonArray, instance: JsonArray): JsonArray {
        return buildJsonArray {
            def.forEach { tag ->
                require(tag is JsonObject)

                val instTag = instance.find { iTag ->
                    require(iTag is JsonObject)
                    iTag["name"] == tag["name"]
                }!!.jsonObject

                val retTag = if (tag["tagType"]?.jsonPrimitive?.content == "UdtInstance") {
                    val typeId = tag["typeId"]!!.jsonPrimitive.content
                    val udtOverride = zipObject(builtDefinitions[typeId]!!, tag)
                    zipObject(udtOverride, instTag)
                } else {
                    zipObject(tag, instTag)
                }
                add(retTag)
            }
        }
    }

    companion object {
        private val JSON = Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        val directoryChooser = JFileChooser(Kindling.homeLocation).apply {
            isMultiSelectionEnabled = false
            fileView = CustomIconView()
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

            Kindling.addThemeChangeListener {
                updateUI()
            }
        }

        private fun TagDataType.toSimDataType(): ProgramDataType? {
            return mapOf(
                TagDataType.Short to ProgramDataType.INT16,
                TagDataType.Integer to ProgramDataType.INT32,
                TagDataType.Long to ProgramDataType.INT64,
                TagDataType.Float4 to ProgramDataType.FLOAT,
                TagDataType.Float8 to ProgramDataType.DOUBLE,
                TagDataType.Boolean to ProgramDataType.BOOLEAN,
                TagDataType.String to ProgramDataType.STRING,
                TagDataType.DateTime to ProgramDataType.DATETIME,
                TagDataType.Text to ProgramDataType.STRING,
            )[this]
        }
    }
}

object SimulatorViewer : Tool {
    override val extensions = listOf("json")
    override val description = "Opens a tag export."
    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-box.svg")
    override val title = "Tag Export"
    override fun open(path: Path): ToolPanel {
        return SimulatorView(path)
    }
}

class SimulatorViewerProxy : Tool by SimulatorViewer
