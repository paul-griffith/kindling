package io.github.inductiveautomation.kindling.idb.tagconfig.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

@Serializable
data class TagConfig(
    // Basic Properties:
    val name: String? = null,
    val tagGroup: String? = null,
    val enabled: Boolean? = null,
    // Value Properties:
    val tagType: String? = null, // Unlisted
    val typeId: String? = null, // Unlisted
    val valueSource: String? = null,
    val dataType: String? = null,
    val value: JsonPrimitive? = null,
    val opcServer: String? = null, // OPC
    val opcItemPath: JsonElement? = null, // OPC
    val sourceTagPath: String? = null, // Derived, Reference
    val executionMode: String? = null,
    val executionRate: Int? = null,
    val expression: String? = null, // Expression
    @SerialName("deriveExpressionGetter")
    val readExpression: String? = null, // Derived
    @SerialName("deriveExpressionSetter")
    val writeExpression: String? = null, // Derived
    val query: String? = null, // Query
    val queryType: String? = null, // Query
    val datasource: String? = null, // Query
    // Numeric Properties:
    val deadband: Double? = null,
    val deadbandMode: String? = null,
    val scaleMode: String? = null,
    val rawLow: Double? = null,
    val rawHigh: Double? = null,
    val scaledLow: Double? = null,
    val scaledHigh: Double? = null,
    val clampMode: String? = null,
    val scaleFactor: Double? = null,
    val engUnit: String? = null,
    val engLow: Double? = null,
    val engHigh: Double? = null,
    val engLimitMode: String? = null,
    val formatString: String? = null,
    // Metadata Properties:
    val tooltip: String? = null,
    val documentation: String? = null,
    val typeColor: JsonPrimitive? = null, // UDT Definitions
    // Security Properties
    val readPermissions: JsonObject? = null,
    val readOnly: Boolean? = null,
    val writePermissions: JsonObject? = null,
    // Scripting Properties
    val eventScripts: JsonArray? = null,
    // Alarm Properties
    val alarms: JsonArray? = null,
    val alarmEvalEnabled: Boolean? = null,
    // Historical Properties
    val historyEnabled: Boolean? = null,
    val historyProvider: String? = null,
    val historicalDeadbandStyle: String? = null,
    val historicalDeadbandMode: String? = null,
    val historicalDeadband: Double? = null,
    val sampleMode: String? = null,
    val historySampleRate: Int? = null,
    val historySampleRateUnits: String? = null,
    val historyTagGroup: String? = null,
    val historyTimeDeadband: Int? = null,
    val historyTimeDeadbandUnits: String? = null,
    val historyMaxAge: Int? = null,
    val historyMaxAgeUnits: String? = null,
    val tags: NodeGroup = mutableListOf(),
    // UDT
    val parameters: JsonObject? = null,
    // Custom Properties:,
    val customProperties: JsonObject? = null,
)

object TagConfigSerializer : JsonTransformingSerializer<TagConfig>(TagConfig.serializer()) {
    @OptIn(ExperimentalSerializationApi::class)
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val elementNames =
            List(TagConfig.serializer().descriptor.elementsCount) {
                TagConfig.serializer().descriptor.getElementName(it)
            }.filter { it != "customProperties" }
        val elementMap = element.jsonObject.toMutableMap()
        val customPropertiesMap =
            elementMap.filter { it.key !in elementNames }.onEach { (key, value) ->
                elementMap.remove(key, value)
            }
        elementMap["customProperties"] =
            if (customPropertiesMap.isEmpty()) JsonNull else JsonObject(customPropertiesMap)
        return JsonObject(elementMap)
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        val tagConfig = element.jsonObject.toMutableMap()

        val customProperties =
            tagConfig.remove("customProperties")?.let {
                if (it is JsonNull) {
                    return JsonObject(tagConfig)
                } else {
                    it.jsonObject
                }
            }
        customProperties?.entries?.forEach { (key, value) ->
            tagConfig[key] = value
        }
        return JsonObject(tagConfig)
    }
}

object MinimalTagConfigSerializer : JsonTransformingSerializer<TagConfig>(TagConfig.serializer()) {
    @OptIn(ExperimentalSerializationApi::class)
    override fun transformDeserialize(element: JsonElement): JsonElement {
        throw UnsupportedOperationException("This serializer is not meant for deserialization!")
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        val tagConfig = element.jsonObject.toMutableMap()
        tagConfig.remove("tags")

        val customProperties =
            tagConfig.remove("customProperties")?.let {
                if (it is JsonNull) {
                    return JsonObject(tagConfig)
                } else {
                    it.jsonObject
                }
            }
        customProperties?.entries?.forEach { (key, value) ->
            tagConfig[key] = value
        }
        return JsonObject(tagConfig)
    }
}
