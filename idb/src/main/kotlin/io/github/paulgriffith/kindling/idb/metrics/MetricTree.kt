package io.github.paulgriffith.kindling.idb.metrics

import com.jidesoft.swing.CheckBoxTree
import io.github.paulgriffith.kindling.utils.AbstractTreeNode
import io.github.paulgriffith.kindling.utils.TypedTreeNode
import javax.swing.tree.DefaultTreeModel

class MetricNode(override val userObject: String) : TypedTreeNode<String>() {
    override fun toString(): String {
        return userObject.replaceFirstChar { it.uppercaseChar() }
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
    }

}

fun Metric.getFullTreePath(): String {
    val legacy = if (this.isLegacy) "Legacy" else "New"
    return "${legacy}.${name}"
}