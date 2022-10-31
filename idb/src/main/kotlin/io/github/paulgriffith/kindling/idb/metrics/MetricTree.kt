package io.github.paulgriffith.kindling.idb.metrics

import com.jidesoft.swing.CheckBoxTree
import io.github.paulgriffith.kindling.utils.AbstractTreeNode
import io.github.paulgriffith.kindling.utils.PathNode
import io.github.paulgriffith.kindling.utils.TypedTreeNode
import io.github.paulgriffith.kindling.utils.treeCellRenderer
import javax.swing.JTree

class MetricNode(override val userObject: Metric) : TypedTreeNode<Metric>()

class MetricTree(metrics: List<Metric>) : CheckBoxTree() {

    init {
        isRootVisible = false
        setShowsRootHandles(true)
    }

}