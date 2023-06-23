package io.github.inductiveautomation.kindling.sim.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TagParser(private val tagProvider: TagProviderStructure) {
    val deviceSimProgramItems = run {
        // Source of truth for UDT definitions defined in the tag export
        val udtPathsToStructures: MutableMap<String, JsonObject> = mutableMapOf<String, JsonObject>().apply {
            val defs: NodeStructure? = tagProvider.tags.find { node -> node.name == "_types_" }
            buildUdtMap(defs?.tags)
        }

        val resolvedTags: MutableList<NodeStructure> = tagProvider.run {
            val nodes = tags.filter { node -> node.name != "_types_" }
            val resolvedTags = mutableListOf<NodeStructure>()

            iterateTags(nodes, resolvedTags, udtPathsToStructures)
            resolveParameters(resolvedTags, emptyList(), udtPathsToStructures)
            resolvedTags
        }
        resolvedTags.flatMap(::parseDeviceProgram)
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val JSON = Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
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

        fun parseDeviceProgram(tagStructure: NodeStructure): SimulatorProgram {
            return mutableListOf<ProgramItem>().apply {
                tagStructure.tags?.forEach { tag ->
                    when {
                        tag.valueSource == "opc" -> {
                            add(
                                ProgramItem(
                                    browsePath = when (val path = tag.opcItemPath) {
                                        is JsonObject -> (path["binding"] as JsonPrimitive).content
                                        is JsonPrimitive -> path.content
                                        else -> throw IllegalArgumentException("OPC item path is ${this::class.java.name}")
                                    },
                                    dataType = TagDataType.valueOf(tag.dataType ?: "None").toSimDataType(),
                                    valueSource = SimulatorFunction.defaultFunction,
                                ),
                            )
                        }

                        !tag.tags.isNullOrEmpty() -> { // Browsable node
                            addAll(parseDeviceProgram(tag))
                        }
                    }
                }
            }
        }

//        fun TagProviderStructure.resolveOpcTags(): MutableList<NodeStructure> {
//            val nodes = tags.filter { node -> node.name != "_types_" }
//            val resolvedTags = mutableListOf<NodeStructure>()
//
//            iterateTags(nodes, resolvedTags)
//            resolveParameters(resolvedTags, emptyList())
//            return resolvedTags
//        }

        private fun MutableMap<String, JsonObject>.buildUdtMap(defs: List<NodeStructure>?, relativePath: String = "") {
            if (defs == null) return
            for (udt in defs) {
                if (udt.tagType == "Folder") {
                    buildUdtMap(udt.tags ?: emptyList(), "$relativePath${udt.name}/")
                } else {
                    put("$relativePath${udt.name}", JSON.encodeToJsonElement(udt).jsonObject)
                }
            }
        }

        private fun iterateTags(
            nodes: List<NodeStructure>,
            resolvedTags: MutableList<NodeStructure>,
            udtDefinitionMap: MutableMap<String, JsonObject>,
        ) {
            for (node in nodes) {
                when (node.tagType) {
                    "Folder" -> {
                        require(node.tags != null)
                        val folderTags = mutableListOf<NodeStructure>()
                        iterateTags(node.tags, folderTags, udtDefinitionMap)
                        resolvedTags.add(
                            NodeStructure(
                                name = node.name,
                                tagType = "Folder",
                                tags = folderTags.toList(),
                            ),
                        )
                    }

                    "UdtInstance" -> {
                        val defDict = udtDefinitionMap[node.typeId]
                        val retTag = zipObject(defDict, JSON.encodeToJsonElement(node).jsonObject, udtDefinitionMap)
                        resolvedTags.add(JSON.decodeFromJsonElement(retTag))
                    }

                    else -> resolvedTags.add(node)
                }
            }
        }

        private fun resolveParameters(
            tags: List<NodeStructure>,
            parameters: List<UdtParameter>,
            udtDefinitionMap: MutableMap<String, JsonObject>,
        ) {
            for (tag in tags) {
                if (tag.tagType == "UdtInstance" && tag.parameters.isNotEmpty() && !tag.tags.isNullOrEmpty()) {
                    val udtParams = run {
                        val params = JSON.encodeToJsonElement(UdtParameterListSerializer(), parameters).jsonObject
                        val tagParams =
                            JSON.encodeToJsonElement(UdtParameterListSerializer(), tag.parameters).jsonObject
                        zipObject(params, tagParams, udtDefinitionMap)
                    }

                    resolveParameters(
                        tag.tags,
                        JSON.decodeFromJsonElement(UdtParameterListSerializer(), udtParams),
                        udtDefinitionMap,
                    )
                } else if (tag.tagType == "Folder") {
                    resolveParameters(tag.tags!!, parameters, udtDefinitionMap)
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

        private fun zipObject(
            def: JsonObject?,
            instance: JsonObject,
            udtDefinitionMap: MutableMap<String, JsonObject>,
        ): JsonObject {
            if (def == null) {
                return instance
            } else {
                return buildJsonObject {
                    def.entries.forEach { put(it.key, it.value) }

                    instance.entries.forEach { (key, value) ->
                        if (key == "tags" && value is JsonArray) {
                            // Both should always be true - this check is for the compiler to smart cast value as JsonArray
                            val defKey = def[key]
                            require(defKey is JsonArray)
                            put(key, zipTags(defKey, value, udtDefinitionMap))
                        } else if (value is JsonObject) {
                            val defKey = def[key]
                            if (defKey is JsonObject) {
                                put(key, zipObject(defKey, value, udtDefinitionMap))
                            } else {
                                put(key, value)
                            }
                        } else {
                            put(key, value)
                        }
                    }
                }
            }
        }

        private fun zipTags(
            def: JsonArray,
            instance: JsonArray,
            udtDefinitionMap: MutableMap<String, JsonObject>,
        ): JsonArray {
            return buildJsonArray {
                def.forEach { tag ->
                    require(tag is JsonObject)

                    val instTag = instance.find { iTag ->
                        require(iTag is JsonObject)
                        iTag["name"] == tag["name"]
                    }!!.jsonObject

                    val retTag = if (tag["tagType"]?.jsonPrimitive?.content == "UdtInstance") {
                        val typeId = tag["typeId"]!!.jsonPrimitive.content
                        val udtOverride = zipObject(udtDefinitionMap[typeId]!!, tag, udtDefinitionMap)
                        zipObject(udtOverride, instTag, udtDefinitionMap)
                    } else {
                        zipObject(tag, instTag, udtDefinitionMap)
                    }
                    add(retTag)
                }
            }
        }
    }
}
