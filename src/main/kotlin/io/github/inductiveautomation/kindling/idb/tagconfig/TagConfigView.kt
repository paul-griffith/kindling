package io.github.inductiveautomation.kindling.idb.tagconfig

import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagConfig
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagConfigSerializer
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.putJsonArray
import java.awt.Font
import java.nio.file.Path
import java.sql.Connection
import javax.swing.JScrollPane
import javax.swing.JTextArea
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
class TagConfigView(connection: Connection) : ToolPanel() {

    private val errs = mutableListOf<Exception>()

    private val tagRecordData: List<TagRecord> =
        connection.prepareStatement("SELECT * FROM TAGCONFIG").executeQuery().toList { rs ->
            try {
                TagRecord(
                    id = rs.getString(1),
                    providerId = rs.getInt(2),
                    folderId = rs.getString(3),
                    config = JSON.decodeFromString(TagConfigSerializer, rs.getString(4)),
                    rank = rs.getInt(5),
                    name = rs.getString(6),
                )
            } catch (e: NullPointerException) {
                println("catching NPE...")
                null
            } catch (e: SerializationException) {
                errs.add(e)
                e.printStackTrace()
                null
            }
        }.filterNotNull().filter { record -> record.providerId == 0 }
    private val udtDefinitions = TagRecord.extractUdtDefinitions(tagRecordData)
    override val icon = null

    // private val unknownKeyRegex = """Encountered an unknown key '(?<unknownKey>.*)' at path""".toRegex()

    private val nullPropRegex = """\b\w+=null\b, """.toRegex()

    init {
        println("tagRecordData size: ${tagRecordData.size}")
        val tagConfigs = tagRecordData.map { record ->
            record.config
        }
        val textToDisplay = nullPropRegex.replace(tagConfigs.joinToString("\n\n"), "")

        val myLabel = JTextArea(textToDisplay).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }

        add(JScrollPane(myLabel), "grow")
        // doo eet
        exportTagsToJson()
    }

    private fun TagRecord.createJson(): JsonElement {
        // check tag record / node type
        // if type unknown
        // // refer to udt definition map
        // else if tag type known
        // // what is tag type?
        // ////// is Folder, udt instance, udt def?
        // //////// Create JSON for the folder
        // ////////// for any elements in folders tags array
        // //////////// call createJson

        return when (config.tagType) {
            "Folder",
            "UdtInstance",
            "UdtType",
            -> { // This is a node representing a folder-like structure
                require(config.name != null) { "tagType not null but name is! \nRecord: $this" }
                buildJsonObject {
                    configAsJson.entries.forEach { (key, value) ->
                        put(key, value)
                    }
                    putJsonArray("tags") {
                        val childNodes = getDirectChildren()
                        addAll(childNodes.map { record -> record.createJson() })
                    }
                }
            }

            "AtomicTag" -> { // We name it's name, tagType, so this is a leaf which represents an actual tag
                require(config.name != null) { "tagType not null but name is! \nRecord: $this" }
                configAsJson
            }

            else -> {
                /* We don't know the tag type.
                * Probably because this record represents an override to a pre-existing node's config.
                * We need to find the tag record, in some definition somehwere, which contains info about this node.
                */
                println("Config does not contain tagType information! $config")
                configAsJson
            }
        }
    }

    private fun exportTagsToJson() {
        val topLevelDefinitionNodes = tagRecordData.filter {
            it.folderId == "_types_"
        }

        val typesFolderEntry = mutableMapOf(
            "name" to JsonPrimitive("_types_"),
            "tagType" to JsonPrimitive("Folder"),
            "tags" to buildJsonArray {
                addAll(topLevelDefinitionNodes.map { tagRecord -> tagRecord.createJson() })
            },
        )
        // initialize root of tag export structure with processed tags array
        val jsonExportData = JsonObject(
            mutableMapOf(
                "name" to JsonPrimitive(""),
                "tagType" to JsonPrimitive("Provider"),
                "tags" to JsonArray(listOf(JsonObject(typesFolderEntry))),
            ),
        )
        Path.of("C:\\Users\\jortega\\Desktop\\patriarchy.json").outputStream().use {
            JSON.encodeToStream(jsonExportData, it)
        }
    }
    // Traversal Helper Functions:

    private fun TagRecord.getUdtDefinition(): TagRecord {
        require(this.config.tagType == "UdtInstance" && this.config.typeId != null)
        return udtDefinitions[this.config.typeId]
            ?: throw IllegalArgumentException("Missing UDT Definition or Type ID. Current typeId: ${this.config.typeId}")
    }

    private fun TagRecord.getDirectChildren(): List<TagRecord> {
        return tagRecordData.filter { tagRecord -> tagRecord.folderId == id }
    }

//    private fun TagRecord.getOriginalDefinition(): TagRecord {
//        // Hierarchy: UdtInstanceA/UdtInstanceB/Tag <- This tag has an override or two or ten
//        // UdtInstanceA_ID.UdtInstanceB_InsideDefinitionA_ID.TagDefinitionInsideDefinitionB
//        val idParts = id.split(".")
//
//        val idOfInstanceA = idParts.first() // This is a top level UDT Instance
//        val instanceARecord = tagRecordData.find { record -> record.id == idOfInstanceA }!!
//        val definitionARecord = instanceARecord.getUdtDefinition()
//
//        val idOfinstanceBwithinDefinitionA = "${definitionARecord.id}.${idParts[1]}"
//        val instanceBwithinDefinitionARecord =
//            tagRecordData.find { record -> record.id == idOfinstanceBwithinDefinitionA }!!
//        val definitionBRecord = instanceBwithinDefinitionARecord.getUdtDefinition()
//
//        val originalTagDefinition = definitionBRecord.getDirectChildren().find { record ->
//            record.id.split(".").last() == idParts.last()
//        }!!
//
//        return originalTagDefinition
//    }

    private fun TagRecord.getOriginalDefinition(): TagRecord {
        fun TagRecord.getOriginalDefinitionRecursive(idParts: List<String>): TagRecord {
            if (idParts.size == 1) {
                val finalId = "$id.${idParts.first()}"
                return tagRecordData.find { record -> record.id == finalId }!!
            }
            val topLevelInstanceId = "$id.${idParts.first()}"
            val topLeveInstanceRecord = tagRecordData.find { record -> record.id == topLevelInstanceId }!!
            val topLevelDefinition = topLeveInstanceRecord.getUdtDefinition()

            return topLevelDefinition.getOriginalDefinitionRecursive(idParts.subList(1, idParts.size))
        }

        val originalIdParts = id.split(".")
        if (originalIdParts.size == 1) return this

        val topLevelInstanceId = originalIdParts.first()
        val topLeveInstanceRecord = tagRecordData.find { record -> record.id == topLevelInstanceId }!!
        val topLevelDefinition = topLeveInstanceRecord.getUdtDefinition()

        return topLevelDefinition.getOriginalDefinitionRecursive(originalIdParts.subList(1, originalIdParts.size))
    }

    companion object {
        internal val JSON = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
            prettyPrint = true
        }
    }
}

data class TagRecord(
    val id: String,
    val providerId: Int,
    val folderId: String?,
    val config: TagConfig,
    val rank: Int,
    val name: String?,
) {
    val configAsJson: JsonObject
        get() = TagConfigView.JSON.encodeToJsonElement(TagConfigSerializer, config).jsonObject

    companion object {
        fun extractUdtDefinitions(tagRecordData: List<TagRecord>): Map<String, TagRecord> = buildMap {
            tagRecordData.forEach { record ->
                if (record.config.tagType == "UdtType") {
                    var fullPath = record.config.name!!
                    var currentRecord = record
                    while (currentRecord.folderId != "_types_") {
                        currentRecord = tagRecordData.find { folderRecord ->
                            folderRecord.id == currentRecord.folderId
                        }!!
                        fullPath = "${currentRecord.name}/$fullPath"
                    }
                    this[fullPath] = record
                }
            }
        }
    }
}
