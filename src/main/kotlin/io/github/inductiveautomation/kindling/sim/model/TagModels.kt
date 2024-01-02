package io.github.inductiveautomation.kindling.sim.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
data class NodeStructure(
    // Basic:
    val name: String,

    // Value
    var valueSource: String? = null,
    var dataType: JsonElement? = null,
    var opcServer: JsonElement? = null,
    var opcItemPath: JsonElement? = null, // Can be primitive or Object if bound

    // Other
    var tags: MutableList<NodeStructure> = mutableListOf(),
    @Serializable(with = UdtParameterListSerializer::class)
    var parameters: ParameterList = mutableListOf(),
    var tagType: String,
    var typeId: String? = null,
    var sourceTagPath: JsonElement? = null,
) {
    @Transient
    lateinit var parent: NodeStructure

    init {
        tags.forEach { tag ->
            tag.parent = this
        }
    }

    override fun toString(): String {
        return "Name: $name, Type: $tagType, Parent: $typeId"
    }

    val isTagProvider: Boolean
        get() = tagType == "Provider"
}

enum class TagDataType {
    Short,
    Integer,
    Int1,
    Int2,
    Int4,
    Int8,
    Long,
    Float4,
    Float8,
    Boolean,
    String,
    DateTime,
    Text,
    Document,
    None,

    // Not supported:
    Int1Array,
    Int2Array,
    Int4Array,
    Int8Array,
    Float4Array,
    Float8Array,
    BooleanArray,
    StringArray,
    DateTimeArray,
    ByteArray,
    DataSet,
}

typealias ParameterList = MutableList<UdtParameter>

@Serializable
data class UdtParameter(
    val name: String,
    val dataType: String,
    var value: JsonElement?,
)

object UdtParameterListSerializer : KSerializer<ParameterList> {
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
            UdtParameter(key, value["dataType"].toString(), value["value"])
        }.toMutableList()
    }
}
