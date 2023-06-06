package io.github.paulgriffith.kindling.sim.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class TagProviderStructure(
    val name: String?,
    val tagType: String?,
    val tags: List<NodeStructure>
)

@Serializable
data class NodeStructure(
    // Basic:
    val name: String,

    // Value
    val valueSource: String? = null,
    val dataType: String? = null,
    val opcServer: String? = null,
    var opcItemPath: JsonElement? = null, // Can be primitive or Object

    // Other
    val tags: List<NodeStructure>?,
    @Serializable(with=UdtParameterListSerializer::class)
    val parameters: ParameterList = emptyList(),
    val tagType: String,
    val typeId: String? = null,
    val sourceTagPath: JsonElement? = null,
)

enum class TagDataType {
    Byte,
    Short,
    Integer,
    Long,
    Float4,
    Float8,
    Boolean,
    String,
    DateTime,
    Text,
    None;
}

typealias ParameterList = List<UdtParameter>

@Serializable
data class UdtParameter(
    val name: String,
    val dataType: String,
    var value: JsonElement?,
)

class UdtParameterListSerializer : KSerializer<ParameterList> {
    private val delegateSerializer = MapSerializer(String.serializer(), JsonObject.serializer())
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor("UdtParameterList", delegateSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: ParameterList) {
        val data = value.associate { param ->
            param.name to buildJsonObject {
                put("dataType", JsonPrimitive(param.dataType))
                param.value?.let { put("value", it) }
            }
        }
        encoder.encodeSerializableValue(delegateSerializer, data)
    }

    override fun deserialize(decoder: Decoder): ParameterList {
        val map = decoder.decodeSerializableValue(delegateSerializer)
        return map.entries.map { (key: String, value: JsonObject) ->
            UdtParameter(key, value["dataType"].toString(), value["value"] as JsonPrimitive?)
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
