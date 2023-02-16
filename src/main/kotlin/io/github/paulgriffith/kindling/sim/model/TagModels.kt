package io.github.paulgriffith.kindling.sim.model

import com.inductiveautomation.ignition.common.config.Property
import com.inductiveautomation.ignition.common.tags.config.TagConfiguration
import com.inductiveautomation.ignition.common.tags.config.properties.WellKnownTagProps
import com.inductiveautomation.ignition.common.tags.config.types.OpcTagTypeProperties
import com.inductiveautomation.ignition.common.tags.config.types.TagObjectType.Folder
import com.inductiveautomation.ignition.common.tags.config.types.TagObjectType.Provider
import com.inductiveautomation.ignition.common.tags.config.types.TagObjectType.UdtInstance
import com.inductiveautomation.ignition.common.tags.config.types.TagObjectType.UdtType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable

fun TagConfiguration.isBrowsable(): Boolean = when(type) {
    Folder, UdtType, UdtInstance, Provider -> true
    else -> false
}
fun <T> getTagsWithProperty(conf: TagConfiguration, prop: Property<T>, propValue: T): List<TagConfiguration> {
    return buildList {
        conf.children.forEach { child ->
            if (child.isBrowsable()) {
                addAll(getTagsWithProperty(child, prop, propValue))
            }
            if (child.tagProperties[prop] == propValue) {
                add(child)
            }
        }
    }
}

@Serializable
data class TagProviderStructure(
    val name: String?,
    val tagType: String?,
    val tags: List<NodeStructure>
) {
    private val udtDefinitions: List<NodeStructure> = tags.find { struct -> struct.name == "_types_" }?.tags ?: emptyList()

    fun resolveOpcTags() {
        val defParams = udtDefinitions.associate {
            it.name to it.parametersAsList
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
    val parameters: JsonObject?,
    val tagType: String,
    val typeId: String?,
    val sourceTagPath: JsonElement?,
) {
    val isBrowsable = tags != null

    val parametersAsList: List<UdtParameter> by lazy {
        parameters?.map { (key, value) ->
            val parameterProperties = (value as JsonObject)
            UdtParameter(key, parameterProperties["dataType"].toString(), parameterProperties["value"]?.toString())
        } ?: emptyList()
    }

    private fun resolveOpcItemPath(params: List<UdtParameter>) {
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
            println("Udt Instance: $name")
            val inheritedParams = defParams[typeId]

            parametersAsList.joinToString(", ") {
                "${it.name}: ${it.value}"
            }.let { println(it) }
        }
        if (isBrowsable) {
            tags?.forEach { node ->
                node.resolveOpcTags(defParams, params.plus(parametersAsList).toMutableList())
            }
        }
    }
}

data class UdtParameter(
    val name: String,
    val dataType: String,
    var value: String?,
)

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
