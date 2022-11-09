package io.github.paulgriffith.kindling.idb.metrics

import com.jidesoft.swing.CheckBoxTree
import io.github.paulgriffith.kindling.utils.AbstractTreeNode
import io.github.paulgriffith.kindling.utils.TypedTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class MetricNode(override val userObject: String) : TypedTreeNode<String>() {
    val metricName: String
        get() {
            var name: String = userObject
            var currentNode = this.parent ?: return name
            while (currentNode.parent !is RootNode) {
                name = "$currentNode.$name"
                currentNode = currentNode.parent as MetricNode
            }
            return name
        }

    override fun toString(): String {
        return userObject
    }
}

class MetricModel(metrics: List<Metric>) : DefaultTreeModel(RootNode(metrics))

class RootNode(metrics: List<Metric>) : AbstractTreeNode() {
    init {
        metrics.forEach { metric ->
            var currentNode: AbstractTreeNode = this
            val fullPath: String = metric.getFullTreePath()

            fullPath.split(".").forEach { part ->
                var next = currentNode.children.find { part == (it as MetricNode).userObject } as MetricNode?
                if (next == null) {
                    next = MetricNode(part)
                    currentNode.children.add(next)
                }
                currentNode = next
            }
        }
    }
}

class MetricTree(metrics: List<Metric>) : CheckBoxTree(MetricModel(metrics)) {

    init {
        isRootVisible = false
        setShowsRootHandles(true)
        expandAll()
        selectAll()
    }

    val selectedLeafNodes: List<MetricNode>
        get() {
            return checkBoxTreeSelectionModel.selectionPaths.toList().flatMap {
                getLeavesFromNode(it.lastPathComponent as AbstractTreeNode)
            }
        }

    private fun getLeavesFromNode(node: AbstractTreeNode): List<MetricNode> {
        if (node.isLeaf) return listOf(node as MetricNode)

        return buildList {
            addAll(node.children.filter { it.isLeaf }.map { it as MetricNode })
            val nonLeaves = node.children.filter { !it.isLeaf }
            nonLeaves.forEach {
                addAll(getLeavesFromNode(it as MetricNode))
            }
        }
    }

    private fun selectAll() = checkBoxTreeSelectionModel.addSelectionPath(TreePath(model.root))

    private fun expandAll() {
        var i = 0
        while (i < rowCount) {
            expandRow(i)
            i += 1
        }
    }
}

fun Metric.getFullTreePath(): String {
    val legacy = if (this.isLegacy) "Legacy" else "New"
    return "${legacy}.${name}"
}