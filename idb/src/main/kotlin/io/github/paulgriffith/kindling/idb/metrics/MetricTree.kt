package io.github.paulgriffith.kindling.idb.metrics

import com.jidesoft.swing.CheckBoxTree
import io.github.paulgriffith.kindling.utils.AbstractTreeNode
import io.github.paulgriffith.kindling.utils.PathNode
import io.github.paulgriffith.kindling.utils.TypedTreeNode
import io.github.paulgriffith.kindling.utils.treeCellRenderer
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

class MetricNode(override val userObject: String) : TypedTreeNode<String>()

class MetricModel(metrics: List<Metric>) : DefaultTreeModel(RootNode(metrics))

class RootNode(metrics: List<Metric>) : AbstractTreeNode() {
    init {
        metrics.forEach { metric ->
            val currentNode = this
            val fullPath: String = metric.getFullTreePath()

            fullPath.split(".").forEach { part ->
                val next: MetricNode? = null
                val children = currentNode.children.asSequence()
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
    return this.isLegacy.toString() + "." + this.name
}