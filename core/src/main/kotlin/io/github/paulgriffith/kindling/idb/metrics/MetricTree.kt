package io.github.paulgriffith.kindling.idb.metrics

import com.jidesoft.swing.CheckBoxTree
import io.github.paulgriffith.kindling.utils.AbstractTreeNode
import io.github.paulgriffith.kindling.utils.TypedTreeNode
import io.github.paulgriffith.kindling.utils.treeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

data class MetricNode(override val userObject: List<String>) : TypedTreeNode<List<String>>() {
    constructor(vararg parts: String) : this(parts.toList())

    val name by lazy { userObject.joinToString(".") }
}

class RootNode(metrics: List<Metric>) : AbstractTreeNode() {
    init {
        val legacy = MetricNode("Legacy")
        val modern = MetricNode("New")

        val seen = mutableMapOf<List<String>, MetricNode>()
        for (metric in metrics) {
            var lastSeen = if (metric.isLegacy) legacy else modern
            val currentLeadingPath = mutableListOf(lastSeen.name)
            for (part in metric.name.split('.')) {
                currentLeadingPath.add(part)
                val next = seen.getOrPut(currentLeadingPath.toList()) {
                    val newChild = MetricNode(currentLeadingPath.drop(1))
                    lastSeen.children.add(newChild)
                    newChild
                }
                lastSeen = next
            }
        }

        when {
            legacy.childCount == 0 && modern.childCount > 0 -> {
                for (zoomer in modern.children) {
                    children.add(zoomer)
                }
            }

            modern.childCount == 0 && legacy.childCount > 0 -> {
                for (boomer in legacy.children) {
                    children.add(boomer)
                }
            }

            else -> {
                children.add(legacy)
                children.add(modern)
            }
        }
    }

    private val Metric.isLegacy: Boolean
        get() = name.first().isUpperCase()
}

class MetricModel(metrics: List<Metric>) : DefaultTreeModel(RootNode(metrics))

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
                    text = path.last()
                    toolTipText = value.name
                }
                this
            },
        )
    }

    val selectedLeafNodes: List<MetricNode>
        get() = checkBoxTreeSelectionModel.selectionPaths
            .flatMap {
                (it.lastPathComponent as AbstractTreeNode).depthFirstChildren()
            }.filterIsInstance<MetricNode>()

    private fun TreeNode.depthFirstChildren(): Sequence<AbstractTreeNode> = sequence {
        if (isLeaf) {
            yield(this@depthFirstChildren as AbstractTreeNode)
        } else {
            for (child in children()) {
                yieldAll(child.depthFirstChildren())
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
