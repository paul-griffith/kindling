package io.github.paulgriffith.kindling.sim.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

//fun <T> getTagsWithProperty(conf: TagConfiguration, prop: Property<T>, propValue: T): List<TagConfiguration> {
//    return buildList {
//        conf.children.forEach { child ->
//            if (child.isBrowsable()) {
//                addAll(getTagsWithProperty(child, prop, propValue))
//            }
//            if (child.tagProperties[prop] == propValue) {
//                add(child)
//            }
//        }
//    }
//}

@Serializable
data class TagProviderStructure(
    val name: String?,
    val tagType: String?,
    val tags: List<NodeStructure>
) {
    private val udtDefinitions: List<NodeStructure> = tags.find { struct -> struct.name == "_types_" }?.tags ?: emptyList()

    fun resolveOpcTags() {
        val defParams = udtDefinitions.associate {
            it.name to it.parameters
        }
        tags.forEach { node ->
            node.resolveOpcTags(defParams, mutableListOf())
        }
    }
}



@Serializable
data class NodeStructure(
    // Basic:
    val name: String,

    // Value
    val valueSource: String?,
    val dataType: String?,
    val opcServer: String?,
    val opcItemPath: JsonElement?, // Can be primitive or Object

    // Other
    val tags: List<NodeStructure>?,
    @Serializable(with=UdtParameterListSerializer::class)
    val parameters: List<UdtParameter>,
    val tagType: String,
    val typeId: String?,
    val sourceTagPath: JsonElement?,
) {
    private val isBrowsable = tags != null

//    val parametersAsList: List<UdtParameter> by lazy {
//        parameters?.map { (key, value) ->
//            val parameterProperties = (value as JsonObject)
//            UdtParameter(key, parameterProperties["dataType"].toString(), parameterProperties["value"]?.toString())
//        } ?: emptyList()
//    }

    private fun resolveOpcItemPath(params: List<UdtParameter>) {
        params.onEach { println(it) }
        if (opcItemPath is JsonObject) {
            val rawItemPath = opcItemPath["binding"].toString()
            println("$rawItemPath is gonna get resolved.")
        }
    }

    fun resolveOpcTags(
        defParams: Map<String, List<UdtParameter>>,
        params: MutableList<UdtParameter>
    ) {
        println("Starting resolve...")
        if (valueSource == "opc") {
            println("Opc Tag: $name")
            resolveOpcItemPath(params)
        }
        if (tagType == "UdtInstance") {
            val inheritedParams = defParams[typeId]?.filter { it.value != null }

            if (inheritedParams != null) {
                for (inheritedParam in inheritedParams) {
                    parameters.find { it.name == inheritedParam.name }?.let {
                        it.value = inheritedParam.value
                    }
                }
            }

            parameters.joinToString(", ") {
                "${it.name}: ${it.value}"
            }.let { println(it) }
        }
        if (isBrowsable) {
            tags?.forEach { node ->
                node.resolveOpcTags(defParams, params.plus(parameters).toMutableList())
            }
        }
    }
}

@Serializable
data class UdtParameter(
    val name: String,
    val dataType: String,
    var value: JsonElement?,
)

class UdtParameterListSerializer : KSerializer<List<UdtParameter>> {
    private val delegateSerializer = MapSerializer(String.serializer(), JsonObject.serializer())
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor("UdtParameter", delegateSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: List<UdtParameter>) {
        val data = value.associate { param ->
            param.name to buildJsonObject {
                put("dataType", JsonPrimitive(param.dataType))
                param.value?.let { put("value", it) }
            }
        }
        encoder.encodeSerializableValue(delegateSerializer, data)
    }

    override fun deserialize(decoder: Decoder): List<UdtParameter> {
        val map = decoder.decodeSerializableValue(delegateSerializer)
        return map.entries.map { (key: String, value: JsonObject) ->
            UdtParameter(key, value["dataType"].toString(), value["value"] as JsonPrimitive)
        }
    }
}

/*
@Serializable
data class FullTagStructure(
    // Basic:
    val name: String,
    val enabled: Boolean?,
    val tagGroup: String?,

    // Meta Data
    val tooltip: String?,
    val documentation: String?,

    // Value
    val valueSource: String?,
    val dataType: String?,
    val opcServer: String?,
    val opcItemPath: JsonElement?, // Can be primitive or Object

    // Numeric
    val deadbandMode: String?,
    val deadband: Double?,
    val scaleMode: String?,
    val rawLow: Double?,
    val rawHigh: Double?,
    val scaledLow: Double?,
    val scaledHigh: Double?,
    val clampMode: String?,
    val engUnit: String?,
    val engLow: Double?,
    val engHigh: Double?,
    val engLimitMode: String?,
    val formatString: String?,

    // Security
    val readOnly: Boolean?,
    val readPermissions: JsonObject?,
    val writePermissions: JsonObject?,

    // Scripting
    val eventScripts: JsonArray?,

    // Alarms
    val alarms: JsonArray?,

    // History
    val historyEnabled: Boolean?,
    val historyProvider: String?,
    val historicalDeadbandStyle: String?,
    val historicalDeadbandMode: String?,
    val historicalDeadband: Double?,
    val sampleMode: String?,
    val historyMaxAge: Int?, // Max time between samples
    val historyMaxAgeUnits: String?,
    val historySampleRate: Int?,
    val historySampleRateUnits: String?,

    // Other
    val tags: List<FullTagStructure>?,
    val parameters: JsonObject?,
    val tagType: String,
    val typeId: String?,
    val sourceTagPath: JsonElement?,
) {
    val isBrowsable = tags != null
}
*/
