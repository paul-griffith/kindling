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
import java.sql.Connection
import javax.swing.JScrollPane
import javax.swing.JTextArea
import kotlin.io.path.Path
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

        /* We don't know the tag type.
        * Probably because this record represents an override to a pre-existing node's config.
        * We need to find the tag record, in some definition somehwere, which contains info about this node.
        */
        val originalDefinition = getOriginalDefinition()
        val (resolvedConfig, isTopLevel) = if (config.tagType.isNullOrEmpty()) {
            config.copy(
                name = originalDefinition.config.name,
                tagType = originalDefinition.config.tagType,
            ) to false
        } else {
            config to true
        }

        return when (resolvedConfig.tagType) {
            "Folder",
            "UdtInstance",
            "UdtType",
            -> { // This is a node representing a folder-like structure
                require(resolvedConfig.name != null) { "tagType not null but name is! \nRecord: $this" }
                buildJsonObject {
                    JSON.encodeToJsonElement(
                        TagConfigSerializer,
                        resolvedConfig,
                    ).jsonObject.entries.forEach { (key, value) ->
                        put(key, value)
                    }
                    putJsonArray("tags") {
                        val childNodes = if (isTopLevel) {
                            getDirectChildren()
                        } else {
                            val overriddenChildren = getDirectChildren()
                            val originalChildren = originalDefinition.getDirectChildren()
                            val overriddenIDs = overriddenChildren.map {
                                it.id.split(".").last()
                            }

                            overriddenChildren + originalChildren.filter { originalRecord ->
                                val recordUUID = originalRecord.id.split(".").last()
                                recordUUID !in overriddenIDs
                            }
                        }

                        addAll(childNodes.map { record -> record.createJson() })
                    }
                }
            }

            "AtomicTag" -> { // We name it's name, tagType, so this is a leaf which represents an actual tag
                require(resolvedConfig.name != null) { "tagType not null but name is! \nRecord: $this" }
                JSON.encodeToJsonElement(TagConfigSerializer, resolvedConfig)
            }

            else -> {
                throw IllegalArgumentException("Tag Type is not any predefined types!\n$resolvedConfig")
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
        Path(
            System.getProperty("user.home"),
            "Desktop",
            "tag-export-dev-test.json",
        ).outputStream().use {
            JSON.encodeToStream(jsonExportData, it)
        }
    }
    // Traversal Helper Functions:

    private fun TagRecord.getUdtDefinition(): TagRecord {
        require(config.tagType == "UdtInstance" || config.tagType == "UdtType") {
            "Not a top level UDT Instance or type! $this"
        }
        if (config.tagType == "UdtType") return this
        return udtDefinitions[config.typeId]
            ?: throw IllegalArgumentException("Missing UDT Definition or Type ID. Current typeId: ${this.config.typeId}")
    }

    private fun TagRecord.getDirectChildren(): List<TagRecord> {
        return tagRecordData.filter { tagRecord -> tagRecord.folderId == id }
    }

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

        if (!config.tagType.isNullOrEmpty() && !config.name.isNullOrEmpty()) return this
        val originalIdParts = id.split(".")

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
    @Suppress("unused")
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
