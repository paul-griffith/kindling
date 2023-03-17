package io.github.paulgriffith.kindling.sim

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.inductiveautomation.ignition.common.gson.GsonBuilder
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.sim.model.NodeStructure
import io.github.paulgriffith.kindling.sim.model.TagProviderStructure
import io.github.paulgriffith.kindling.sim.model.UdtParameter
import io.github.paulgriffith.kindling.sim.model.UdtParameterListSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Font
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JScrollPane
import javax.swing.JTextArea
import kotlin.io.path.inputStream

@OptIn(ExperimentalSerializationApi::class)
class SimulatorView(path: Path) : ToolPanel() {
    override val icon: Icon = FlatSVGIcon("icons/bx-archive.svg")

    private val tagProvider: TagProviderStructure = path.inputStream().use(JSON::decodeFromStream)

    private val builtDefinitions = mutableMapOf<String, JsonObject>()

    init {
        name = "Sim"
        toolTipText = "Hello World"

        val tags = tagProvider.resolveOpcTags()

        val gson = GsonBuilder().setPrettyPrinting().create()
        val lines = tags.joinToString("\n") { tag ->
            gson.toJson(tag)
        }

        add(
            JScrollPane(
                JTextArea(lines).apply {
                    lineWrap = true
                    font = Font.getFont(Font.MONOSPACED)
                }
            ), "push, grow, span"
        )
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
                val params = JSON.encodeToJsonElement(UdtParameterListSerializer(), parameters).jsonObject
                val tagParams = JSON.encodeToJsonElement(UdtParameterListSerializer(), tag.parameters).jsonObject
                val udtParams = zipObject(params, tagParams)
                resolveParameters(tag.tags, JSON.decodeFromJsonElement(UdtParameterListSerializer(), udtParams))
            } else if (tag.tagType == "Folder") {
                resolveParameters(tag.tags!!, parameters)
            } else if (tag.valueSource == "opc" && tag.opcItemPath is JsonObject && (tag.opcItemPath as JsonObject)["bindType"]!!.jsonPrimitive.content == "parameter") {
                for (param in parameters) {
                    val newItemPath = (tag.opcItemPath as JsonObject).toMutableMap().apply {
                        val binding = this["binding"]!!.jsonPrimitive.content.apply {
                            val newParamValue = parameters.find {
                                it.name == param.name
                            }?.value?.jsonPrimitive?.content
                            replace("{${param.value}}", newParamValue ?: "null")
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