package io.github.paulgriffith.kindling.idb.metrics

import com.jidesoft.swing.CheckBoxTree
import io.github.paulgriffith.kindling.utils.AbstractTreeNode
import io.github.paulgriffith.kindling.utils.TypedTreeNode
import io.github.paulgriffith.kindling.utils.treeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

data class MetricNode(override val userObject: String) : TypedTreeNode<String>()

class MetricModel(metrics: List<Metric>) : DefaultTreeModel(RootNode(metrics))

class RootNode(metrics: List<Metric>) : AbstractTreeNode() {
    init {
        val legacy = MetricNode("Legacy")
        children.add(legacy)
        val modern = MetricNode("New")
        children.add(modern)

        val seen = mutableMapOf<List<String>, MetricNode>()
        for (metric in metrics) {
            var lastSeen: MetricNode = if (metric.isLegacy) legacy else modern
            val currentLeadingPath = mutableListOf(lastSeen.userObject)
            for (part in metric.name.split('.')) {
                currentLeadingPath.add(part)
                val next = seen.getOrPut(currentLeadingPath.toList()) {
                    val newChild = MetricNode(
                        currentLeadingPath.drop(1).joinToString(separator = "."),
                    )
                    lastSeen.children.add(newChild)
                    newChild
                }
                lastSeen = next
            }
        }

        if (legacy.isLeaf) children.remove(legacy)
        if (modern.isLeaf) children.remove(modern)
    }

    private val Metric.isLegacy: Boolean
        get() = name.first().isUpperCase()
}

class MetricTree(metrics: List<Metric>) : CheckBoxTree(MetricModel(metrics)) {
    init {
        isRootVisible = false
        setShowsRootHandles(true)
        expandAll()
        selectAll()

        setCellRenderer(
            treeCellRenderer { _, value, _, _, _, _, _ ->
                if (value is MetricNode) {
                    val path = value.userObject
                    toolTipText = path
                    text = path.substringAfterLast('.')
                }
                this
            },
        )
    }

    val selectedLeafNodes: List<MetricNode>
        get() {
            return checkBoxTreeSelectionModel.selectionPaths.flatMap {
                getLeavesFromNode(it.lastPathComponent as AbstractTreeNode)
            }
        }

    private fun getLeavesFromNode(node: AbstractTreeNode): List<MetricNode> {
        if (node.isLeaf) return listOf(node as MetricNode)

        return buildList {
            val (leaves, nonLeaves) = node.children.partition { it.isLeaf }
            addAll(leaves as List<MetricNode>)

            for (nonLeaf in nonLeaves) {
                addAll(getLeavesFromNode(nonLeaf as MetricNode))
            }
        }
    }

    private fun expandAll() {
        var i = 0
        while (i < rowCount) {
            expandRow(i)
            i += 1
        }
    }

    private fun selectAll() = checkBoxTreeSelectionModel.addSelectionPath(TreePath(model.root))
}
