package io.github.inductiveautomation.kindling.idb.tagconfig

import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.idb.tagconfig.model.Node
import io.github.inductiveautomation.kindling.idb.tagconfig.model.NodeGroup
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagConfig
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagConfigSerializer
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.awt.Font
import java.nio.file.Path
import java.sql.Connection
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JScrollPane
import javax.swing.JTextArea
import kotlin.io.path.Path
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
class TagConfigView(connection: Connection) : ToolPanel() {

    private val providerToTagData: MutableMap<TagProviderRecord, Node> = mutableMapOf()

    private val tagProviderTableQuery =
        connection.prepareStatement("SELECT * FROM TAGPROVIDERSETTINGS ORDER BY NAME")

    private val tagProviderData: List<TagProviderRecord> = tagProviderTableQuery.executeQuery().toList { rs ->
        runCatching {
            TagProviderRecord(
                id = rs.getInt(1),
                name = rs.getString(2),
                uuid = rs.getString(3),
                description = rs.getString(4),
                enabled = rs.getBoolean(5),
                typeId = rs.getString(6),
                allowBackFill = rs.getBoolean(7),
            )
        }.getOrNull()
    }.filterNotNull()

    private val tagConfigTableQuery =
        connection.prepareStatement("SELECT * FROM TAGCONFIG WHERE PROVIDERID = ? ORDER BY ID")

    override val icon = null
    private val exportButton = JButton("Export Me").apply {
        addActionListener {
            if (exportFileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                val selectedFilePath = Path(exportFileChooser.selectedFile.absolutePath)
                exportToJson(providerDropdown.selectedItem as TagProviderRecord, selectedFilePath)
            }
        }
    }

    private val providerDropdown = JComboBox(tagProviderData.toTypedArray()).apply {
        selectedIndex = -1
        addItemListener { itemEvent ->
            processTagProviders(itemEvent.item as TagProviderRecord)
        }
        configureCellRenderer { _, value, _, _, _ ->
            text = value?.name ?: "Select a Tag Provider..."
        }
    }
    init {

        val fullPaths = tagProviderData.joinToString("\n")
        val myLabel = JTextArea(fullPaths).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }
        add(exportButton)
        add(providerDropdown, "span")
        add(JScrollPane(myLabel), "grow, push, span")
    }
    private fun processTagProviders(selectedTagProvider: TagProviderRecord) {
        if (selectedTagProvider in providerToTagData.keys) return
        val selectedProviderId = selectedTagProvider.id
        val rawNodeData: List<Node> =
            tagConfigTableQuery.apply { setInt(1, selectedProviderId) }.executeQuery()
                .toList { rs ->
                    runCatching {
                        Node(
                            id = rs.getString(1),
                            providerId = rs.getInt(2),
                            folderId = rs.getString(3),
                            config = JSON.decodeFromString(TagConfigSerializer, rs.getString(4)),
                            rank = rs.getInt(5),
                            name = rs.getString(6),
                        )
                    }.getOrNull()
                }.filterNotNull()

        val nodeGroups: Map<String, NodeGroup> = rawNodeData.groupBy { node ->
            node.id.substring(0, 36)
        }.mapValues { (_, nodes) ->
            nodes.sortedBy { it.id }.toMutableList()
        }.toList().sortedBy { (_, nodes) ->
            nodes.first().rank
        }.toMap()

        val udtDefinitions = rawNodeData.filter { it.isUdtDefinition() }.associateBy { it.getFullPath(nodeGroups) }
        val typesNode = Node.typesNode(selectedProviderId)
        val providerNode = Node.providerNode(selectedProviderId, typesNode)

        for ((_, nodeGroup) in nodeGroups) {
            with(nodeGroup) {
                if (parentNode.isUdtDefinition() || parentNode.isUdtInstance()) {
                    if (!isResolved) {
                        resolveInheritance(nodeGroups, udtDefinitions)
                    }
                }
                resolveHierarchy()
                when (val folderId = parentNode.folderId) {
                    "_types_" -> typesNode.config.tags.add(parentNode)
                    null -> providerNode.config.tags.add(parentNode)
                    else -> {
                        val folderGroup =
                            nodeGroups[folderId] ?: throw IllegalStateException("This should never happen")
                        folderGroup.parentNode.config.tags.add(parentNode)
                    }
                }
            }
        }
        providerToTagData[selectedTagProvider] = providerNode
    }
    private fun exportToJson(tagProvider: TagProviderRecord, selectedFilePath: Path) {
        selectedFilePath.outputStream().use {
            JSON.encodeToStream(providerToTagData[tagProvider], it)
        }
    }

    // Traversal Helper Functions:
    private fun Node.getParentType(udtDefinitions: Map<String, Node>): Node {
        require((isUdtDefinition() || isUdtInstance()) && config.typeId != null) {
            "Not a top level UDT Instance or type! $this"
        }
        return udtDefinitions[config.typeId.lowercase()]
            ?: throw IllegalArgumentException("Missing UDT Definition or Type ID. Current typeId: ${this.config.typeId}")
    }

    private fun Node.getFullPath(nodeGroups: Map<String, NodeGroup>): String {
        val lowercaseName = config.name!!.lowercase()
        return if (folderId == "_types_") {
            lowercaseName
        } else {
            "${nodeGroups[folderId!!]!!.parentNode.getFullPath(nodeGroups)}/$lowercaseName"
        }
    }

    private fun NodeGroup.resolveNestedChildInstances(nodeGroups: Map<String, NodeGroup>, udtDefinitions: Map<String, Node>) {
        require(parentNode.isUdtDefinition())

        val childInstances = childNodes.filter { it.isUdtInstance() }

        for (childInstance in childInstances) {
            val childDefinition = childInstance.getParentType(udtDefinitions)
            // TODO: Handle missing definitions
            val childDefinitionGroup =
                nodeGroups[childDefinition.id] ?: throw IllegalStateException("This should never happen")

            // nodeGroups being ordered by rank will make this check not happen very often
            if (!childDefinitionGroup.isResolved) childDefinitionGroup.resolveInheritance(nodeGroups, udtDefinitions)

            copyChildrenFrom(childDefinitionGroup, instanceId = childInstance.id)
        }
    }

    private fun NodeGroup.resolveInheritance(nodeGroups: Map<String, NodeGroup>, udtDefinitions: Map<String, Node>) {
        if (parentNode.isUdtDefinition()) resolveNestedChildInstances(nodeGroups, udtDefinitions)

        if (parentNode.config.typeId.isNullOrEmpty()) {
            isResolved = true
            return
        }

        val inheritedParentNode = parentNode.getParentType(udtDefinitions)
        // TODO: Handle missing definitions
        val inheritedNodeGroup =
            nodeGroups[inheritedParentNode.id] ?: throw IllegalStateException("This should never happen")

        if (!inheritedNodeGroup.isResolved) inheritedNodeGroup.resolveInheritance(nodeGroups, udtDefinitions)

        copyChildrenFrom(inheritedNodeGroup)

        isResolved = true
    }

    companion object {
        internal val JSON = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        private fun Node.isUdtDefinition(): Boolean = config.tagType == "UdtType"

        private fun Node.isUdtInstance(): Boolean = config.tagType == "UdtInstance"

        val NodeGroup.parentNode: Node
            get() = first()

        val NodeGroup.childNodes: MutableList<Node>
            get() = subList(1, size)

        var NodeGroup.isResolved: Boolean
            get() = first().resolved
            set(value) {
                first().resolved = value
            }

        private fun NodeGroup.resolveHierarchy() {
            if (size == 1) return

            for (i in 1..<size) {
                val childNode = get(i)
                find { node -> node.id == childNode.folderId }?.config?.tags?.add(childNode)
            }
        }

        private fun NodeGroup.copyChildrenFrom(otherGroup: NodeGroup, instanceId: String = this.parentNode.id) {
            otherGroup.childNodes.forEach { childNode ->
                val newId = childNode.id.replace(otherGroup.parentNode.id, instanceId)
                val newFolderId = childNode.folderId!!.replace(otherGroup.parentNode.id, instanceId)
                val overrideNode = find { it.id == newId }

                if (overrideNode == null) {
                    add(
                        childNode.copy(
                            id = newId,
                            folderId = newFolderId,
                            config = TagConfig(
                                name = childNode.config.name,
                                tagType = childNode.config.tagType,
                            ),
                        ),
                    )
                } else {
                    remove(overrideNode)
                    add(
                        overrideNode.copy(
                            config = overrideNode.config.copy(
                                name = childNode.config.name,
                                tagType = childNode.config.tagType,
                            ),
                        ),
                    )
                }
            }
        }
    }
}
