package io.github.inductiveautomation.kindling.idb.tagconfig

import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.idb.tagconfig.model.MinimalTagConfigSerializer
import io.github.inductiveautomation.kindling.idb.tagconfig.model.Node
import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.utils.AbstractTreeNode
import io.github.inductiveautomation.kindling.utils.FloatableComponent
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import io.github.inductiveautomation.kindling.utils.TabStrip
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import net.miginfocom.swing.MigLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.sql.Connection
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import kotlin.io.path.Path
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class)
class TagConfigView(connection: Connection) : ToolPanel() {
    private val tagProviderData: List<TagProviderRecord> = TagProviderRecord.getProvidersFromDB(connection)

    override val icon = null

    private val exportButton =
        JButton("Export Tags").apply {
            addActionListener {
                if (exportFileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    val selectedFilePath = Path(exportFileChooser.selectedFile.absolutePath)

                    (providerDropdown.selectedItem as? TagProviderRecord)?.let {
                        exportToJson(it, selectedFilePath)
                    } ?: JOptionPane.showMessageDialog(
                        this@TagConfigView,
                        "You must first select a Tag Provider",
                        "Cannot Export Tags",
                        JOptionPane.WARNING_MESSAGE,
                    )
                }
            }
        }

    private val providerDropdown =
        JComboBox<Any>(tagProviderData.toTypedArray()).apply {
            val defaultPrompt = "Select a Tag Provider..."
            selectedIndex = -1

            addItemListener { itemEvent ->
                val selectedTagProvider = itemEvent.item as TagProviderRecord
                selectedTagProvider.initProviderNode()

                tabs.setTitleAt(0, selectedTagProvider.name)
                providerTab.provider = selectedTagProvider

                tagProviderTree.model = DefaultTreeModel(LazyTreeNode.fromNode(selectedTagProvider.providerNode.value))
            }

            prototypeDisplayValue =
                run {
                    val longestName =
                        tagProviderData.maxBy { record ->
                            record.name.length
                        }.name

                    if (defaultPrompt.length > longestName.length) {
                        defaultPrompt.length
                    } else {
                        longestName.length
                    }
                }

            configureCellRenderer { _, value, _, _, _ ->
                text = (value as? TagProviderRecord)?.name ?: defaultPrompt
            }
        }

    private val tagProviderTree =
        JTree(DefaultMutableTreeNode("Select a Tag Provider to Browse")).apply {
            isRootVisible = false
            addMouseListener(
                object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent?) {
                        if (e?.clickCount == 2) {
                            selectionPath?.let {
                                tabs.addTab(NodeConfigTextPane(it))
                            }
                        }
                    }
                },
            )
        }

    private val providerTab =
        object : JPanel(MigLayout("fill, ins 0")), PopupMenuCustomizer {
            var provider: TagProviderRecord? = null
                set(newProvider) {
                    field = newProvider
                    textArea.text = "${newProvider?.statistics?.totalUdtDefinitions}"
                }

            private val textArea =
                JTextArea("No Tag Provider Selected").apply {
                    font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                }

            init {
                add(textArea, "push, grow, span")
            }

            override fun customizePopupMenu(menu: JPopupMenu) {
                menu.removeAll()
            }
        }

    private val tabs =
        TabStrip().apply {
            addTab("Tag Provider Statistics", providerTab)
            setTabClosable(0, false)
        }

    init {
        val leftPane =
            JPanel(MigLayout("fill, ins 5")).apply {
                add(providerDropdown, "pushx, growx")
                add(exportButton, "wrap")
                add(JScrollPane(tagProviderTree), "push, grow, span")
            }

        add(
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftPane,
                tabs,
            ).apply { resizeWeight = 0.25 },
            "push, grow, span",
        )
    }

    private fun exportToJson(
        tagProvider: TagProviderRecord,
        selectedFilePath: Path,
    ) {
        selectedFilePath.outputStream().use {
            TagExportJson.encodeToStream(tagProvider.providerNode.value, it)
        }
    }

    class NodeConfigTextPane private constructor(
        treePath: TreePath,
        treeNode: LazyTreeNode,
    ) : JTextArea(TagExportJson.encodeToString(MinimalTagConfigSerializer, treeNode.originalNode.config)),
        FloatableComponent,
        PopupMenuCustomizer {
        constructor(treePath: TreePath) : this(treePath, treePath.lastPathComponent as LazyTreeNode)

        override val icon: Icon? = null
        override val tabName: String = "${treeNode.originalNode.config.name ?: treeNode.originalNode.name}"
        override val tabTooltip: String = treePath.toTagPath()

        override fun customizePopupMenu(menu: JPopupMenu) = Unit

        init {
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }

        companion object {
            fun TreePath.toTagPath(): String {
                return path.joinToString("/") {
                    (it as LazyTreeNode).name
                }
            }
        }
    }

    companion object {
        internal val TagExportJson =
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
            }
    }
}

class LazyTreeNode(
    val name: String,
    val tagType: String?,
    originalNode: Node,
) : AbstractTreeNode() {
    val originalNode by lazy { originalNode }

    override fun toString(): String = name

    companion object {
        fun fromNode(node: Node): LazyTreeNode {
            return LazyTreeNode(
                name =
                if (node.config.name.isNullOrEmpty()) {
                    node.name.toString()
                } else {
                    node.config.name
                },
                tagType = node.config.tagType,
                originalNode = node,
            ).apply {
                for (childNode in node.config.tags) {
                    children.add(fromNode(childNode))
                }
            }
        }
    }
}
