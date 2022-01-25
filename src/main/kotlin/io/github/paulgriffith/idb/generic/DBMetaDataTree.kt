package io.github.paulgriffith.idb.generic

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTree
import com.jidesoft.swing.StyledLabelBuilder
import com.jidesoft.swing.TreeSearchable
import com.jidesoft.tree.StyledTreeCellRenderer
import java.awt.Font
import javax.swing.JTree
import javax.swing.UIManager
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class DBMetaDataTree(root: TreeNode) : FlatTree() {
    init {
        model = DefaultTreeModel(root)
        isRootVisible = false
        showsRootHandles = true
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        cellRenderer = object : StyledTreeCellRenderer() {
            override fun customizeStyledLabel(
                tree: JTree,
                value: Any?,
                sel: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean,
            ) {
                clearStyleRanges()
                when (value) {
                    is Table -> {
                        text = value.name
                        icon = if (sel) TABLE_ICON_SELECTED else TABLE_ICON
                    }
                    is Column -> {
                        StyledLabelBuilder()
                            .add(value.name)
                            .add("   ")
                            .add(value.type.takeIf { it.isNotEmpty() } ?: "UNKNOWN", Font.ITALIC)
                            .configure(this)
                        icon = if (sel) COLUMN_ICON_SELECTED else COLUMN_ICON
                    }
                    else -> super.customizeStyledLabel(tree, value, sel, expanded, leaf, row, hasFocus)
                }
            }
        }

        object : TreeSearchable(this) {
            init {
                isRecursive = true
                isRepeats = true
            }

            override fun convertElementToString(element: Any?): String {
                return when (val node = (element as? TreePath)?.lastPathComponent) {
                    is Table -> node.name
                    is Column -> node.name
                    else -> ""
                }
            }
        }
    }

    companion object {
        private val TABLE_ICON = FlatSVGIcon("icons/bx-table.svg").derive(0.75F)
        private val TABLE_ICON_SELECTED = FlatSVGIcon("icons/bx-table.svg").derive(0.75F).apply {
            colorFilter = FlatSVGIcon.ColorFilter { UIManager.getColor("Tree.selectionForeground") }
        }
        private val COLUMN_ICON = FlatSVGIcon("icons/bx-column.svg").derive(0.75F)
        private val COLUMN_ICON_SELECTED = FlatSVGIcon("icons/bx-column.svg").derive(0.75F).apply {
            colorFilter = FlatSVGIcon.ColorFilter { UIManager.getColor("Tree.selectionForeground") }
        }
    }
}
