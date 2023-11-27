package io.github.inductiveautomation.kindling.idb.tagconfig.model

import io.github.inductiveautomation.kindling.idb.tagconfig.TagConfigView
import io.github.inductiveautomation.kindling.utils.toList
import java.sql.Connection

class TagProviderRecord(
    val id: Int,
    val name: String,
    val uuid: String,
    val description: String?,
    val enabled: Boolean,
    val typeId: String,
    val allowBackFill: Boolean,
    val dbConnection: Connection,
) {
    val statistics = Statistics()
    val rawNodeData by lazy {
        dbConnection.prepareStatement(tagConfigTableQuery).apply { setInt(1, id) }.executeQuery()
            .toList { rs ->
                runCatching {
                    Node(
                        id = rs.getString(1),
                        providerId = rs.getInt(2),
                        folderId = rs.getString(3),
                        config = TagConfigView.TagExportJson.decodeFromString(TagConfigSerializer, rs.getString(4)),
                        rank = rs.getInt(5),
                        name = rs.getString(6),
                    )
                }.getOrNull()
            }.filterNotNull()
    }

    val nodeGroups: Map<String, NodeGroup> by lazy {
        rawNodeData.groupBy { node ->
            node.id.substring(0, 36)
        }.mapValues { (_, nodes) ->
            nodes.sortedBy { it.id }.toMutableList()
        }.toList().sortedBy { (_, nodes) ->
            nodes.first().rank
        }.toMap()
    }
    val udtDefinitions by lazy {
        rawNodeData.filter { it.isUdtDefinition() }.associateBy { it.getFullPath(nodeGroups) }
    }
    val typesNode = Node.typesNode(id)
    val providerNode =
        lazy {
            providerNode(typesNode).apply {
                for ((_, nodeGroup) in nodeGroups) {
                    with(nodeGroup) {

                        if (parentNode.isUdtDefinition() || parentNode.isUdtInstance()) {
                            if (parentNode.isUdtDefinition()) statistics.totalUdtDefinitions++
                            if (!isResolved) {
                                resolveInheritance(nodeGroups, udtDefinitions)
                            }
                        }
                        resolveHierarchy()
                        when (val folderId = parentNode.folderId) {
                            "_types_" -> typesNode.config.tags.add(parentNode)
                            null -> config.tags.add(parentNode)
                            else -> {
                                val folderGroup =
                                    nodeGroups[folderId] ?: throw IllegalStateException("This should never happen")
                                folderGroup.parentNode.config.tags.add(parentNode)
                            }
                        }
                    }
                }
            }
        }
    val isInitialized
        get() = providerNode.isInitialized()

    fun initProviderNode() {
        providerNode.value
    }

    inner class Statistics() {
        var totalAtomicTags: Int = 0
        var totalFolderTags: Int = 0
        var totalUdtInstances: Int = 0
        var totalUdtDefinitions: Int = 0
        var totalTagsWithAlarms: Int = 0
        var totalTagsWithHistory: Int = 0
        var totalTagsWithScripts: Int = 0
    }

    private fun getStatistics() {}

    private fun processTagProvider() {
    }

    // Traversal Helper Functions:
    private fun Node.getParentType(udtDefinitions: Map<String, Node>): Node? {
        require((isUdtDefinition() || isUdtInstance()) && config.typeId != null) {
            "Not a top level UDT Instance or type! $this"
        }
        return udtDefinitions[config.typeId.lowercase()]
    }

    private fun Node.getFullPath(nodeGroups: Map<String, NodeGroup>): String {
        val lowercaseName = config.name!!.lowercase()
        return if (folderId == "_types_") {
            lowercaseName
        } else {
            "${nodeGroups[folderId!!]!!.parentNode.getFullPath(nodeGroups)}/$lowercaseName"
        }
    }

    private fun NodeGroup.resolveNestedChildInstances(
        nodeGroups: Map<String, NodeGroup>,
        udtDefinitions: Map<String, Node>,
    ) {
        require(parentNode.isUdtDefinition())

        val childInstances = childNodes.filter { it.isUdtInstance() }

        for (childInstance in childInstances) {
            val childDefinition = childInstance.getParentType(udtDefinitions) ?: continue
            val childDefinitionGroup =
                nodeGroups[childDefinition.id] ?: throw IllegalStateException("This should never happen")

            // nodeGroups being ordered by rank will make this check not happen very often
            if (!childDefinitionGroup.isResolved) childDefinitionGroup.resolveInheritance(nodeGroups, udtDefinitions)

            copyChildrenFrom(childDefinitionGroup, instanceId = childInstance.id)
        }
    }

    private fun NodeGroup.resolveInheritance(
        nodeGroups: Map<String, NodeGroup>,
        udtDefinitions: Map<String, Node>,
    ) {
        if (parentNode.isUdtDefinition()) resolveNestedChildInstances(nodeGroups, udtDefinitions)

        if (parentNode.config.typeId.isNullOrEmpty()) {
            isResolved = true
            return
        }

        val inheritedParentNode =
            parentNode.getParentType(udtDefinitions) ?: run {
                isResolved = true
                return
            }
        val inheritedNodeGroup =
            nodeGroups[inheritedParentNode.id] ?: throw IllegalStateException("This should never happen")

        if (!inheritedNodeGroup.isResolved) inheritedNodeGroup.resolveInheritance(nodeGroups, udtDefinitions)

        copyChildrenFrom(inheritedNodeGroup)

        isResolved = true
    }

    companion object {
        fun TagProviderRecord.providerNode(typesNode: Node? = null): Node =
            Node(
                id = this.uuid,
                providerId = this.id,
                folderId = null,
                config =
                TagConfig(
                    name = "",
                    tagType = "Provider",
                    tags = typesNode?.let { mutableListOf(it) } ?: mutableListOf(),
                ),
                rank = 0,
                name = this.name,
            )

        private const val tagProviderTableQuery = "SELECT * FROM TAGPROVIDERSETTINGS ORDER BY NAME"
        private const val tagConfigTableQuery = "SELECT * FROM TAGCONFIG WHERE PROVIDERID = ? ORDER BY ID"

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

        private fun NodeGroup.copyChildrenFrom(
            otherGroup: NodeGroup,
            instanceId: String = this.parentNode.id,
        ) {
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
                            config =
                            overrideNode.config.copy(
                                name = childNode.config.name,
                                tagType = childNode.config.tagType,
                            ),
                        ),
                    )
                }
            }
        }

        fun getProvidersFromDB(connection: Connection): List<TagProviderRecord> {
            return connection.prepareStatement(tagProviderTableQuery).executeQuery().toList { rs ->
                runCatching {
                    TagProviderRecord(
                        id = rs.getInt(1),
                        name = rs.getString(2),
                        uuid = rs.getString(3),
                        description = rs.getString(4),
                        enabled = rs.getBoolean(5),
                        typeId = rs.getString(6),
                        allowBackFill = rs.getBoolean(7),
                        dbConnection = connection,
                    )
                }.getOrNull()
            }.filterNotNull()
        }
    }
}
