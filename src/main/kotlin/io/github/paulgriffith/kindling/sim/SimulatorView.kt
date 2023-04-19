package io.github.paulgriffith.kindling.sim

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.sim.model.NodeStructure
import io.github.paulgriffith.kindling.sim.model.ProgramDataType
import io.github.paulgriffith.kindling.sim.model.ProgramItem
import io.github.paulgriffith.kindling.sim.model.SimulatorFunction
import io.github.paulgriffith.kindling.sim.model.SimulatorProgram
import io.github.paulgriffith.kindling.sim.model.TagDataType
import io.github.paulgriffith.kindling.sim.model.TagProviderStructure
import io.github.paulgriffith.kindling.sim.model.UdtParameter
import io.github.paulgriffith.kindling.sim.model.UdtParameterListSerializer
import io.github.paulgriffith.kindling.sim.model.toSimulatorCsv
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
import java.io.File
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.io.path.inputStream

@OptIn(ExperimentalSerializationApi::class)
class SimulatorView(path: Path) : ToolPanel() {
    override val icon: Icon = FlatSVGIcon("icons/bx-archive.svg")
    private val deviceName = "Sim"

    private val builtDefinitions = mutableMapOf<String, JsonObject>()
    private val tagProvider: TagProviderStructure = path.inputStream().use(JSON::decodeFromStream)
    private val tags = tagProvider.resolveOpcTags().toList()

    private val program: SimulatorProgram = tags.flatMap { it.parseDeviceProgram() }

    val countLabel = JLabel("Number of OPC Tags: ${program.size}")
    val exportButton = FlatButton().apply {
        text = "Export"
        addActionListener {
            val approve = exportFileChooser.showSaveDialog(this)
            if (approve == JFileChooser.APPROVE_OPTION) {
                val selectedFile = exportFileChooser.selectedFile
                val selectedFileWithExt =
                    if (selectedFile.absolutePath.endsWith("csv")) {
                        selectedFile
                    } else {
                        File(selectedFile.absolutePath + "csv")
                    }
                selectedFileWithExt.writeText(program.toSimulatorCsv())
            }
        }
    }

    init {
        name = "Device Simulator"
        toolTipText = path.toString()

        add(
            JPanel(MigLayout("fill, ins 2")).apply {
                add(countLabel, "west")
                add(exportButton, "east")
            },
            "push, grow, span"
        )

        add(
            JScrollPane(
                JPanel(MigLayout("fill, ins 0")).apply {
                    program.forEach {
                        add(ProgramItemPanel(it), "grow, span")
                    }
                }
            ),
            "push, grow, span"
        )
    }

    private fun NodeStructure.parseDeviceProgram(): SimulatorProgram {
        return buildList {
            tags?.forEach { tag ->
                when {
                    tag.valueSource == "opc" -> {
                        add(
                            ProgramItem(
                                browsePath = when (val path = tag.opcItemPath) {
                                    is JsonObject -> (path["binding"] as JsonPrimitive).content
                                    is JsonPrimitive -> path.content
                                    else -> throw IllegalArgumentException("OPC item path is ${this::class.java.name}")
                                }.replaceFirst("\\[.*?]".toRegex(), "[$deviceName]"),
                                dataType = tagToSimDataType(TagDataType.valueOf(tag.dataType ?: "None")),
                                valueSource = SimulatorFunction.defaultFunction
                            )
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
                        )
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
                    val defKey = def[key]
                    require(defKey is JsonArray)
                    put(key, zipTags(defKey, value))
                } else if (value is JsonObject) {
                    val defKey = def[key]
                    if (defKey is JsonObject) {
                        put(key, zipObject(defKey, value))
                    } else put(key, value)
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

        private fun tagToSimDataType(type: TagDataType): ProgramDataType? {
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
            )[type]
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