@file:OptIn(ExperimentalPathApi::class)

package io.github.paulgriffith.kindling.utils

import io.github.paulgriffith.kindling.core.Tool
import org.jdesktop.swingx.JXTree
import java.nio.file.FileSystem
import java.nio.file.Path
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption.INCLUDE_DIRECTORIES
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.walk

data class PathNode(override val userObject: Path) : TypedTreeNode<Path>()

class RootNode(zipFile: FileSystem) : AbstractTreeNode() {
    init {
        val pathComparator = compareBy<Path> { it.isDirectory() }.thenBy { it.fileName }
        val zipFilePaths = zipFile.rootDirectories.asSequence()
            .flatMap { it.walk(INCLUDE_DIRECTORIES) }
            .sortedWith(pathComparator)

        val seen = mutableMapOf<Path, PathNode>()
        for (path in zipFilePaths) {
            var lastSeen: AbstractTreeNode = this
            var currentDepth = zipFile.getPath("/")
            for (part in path) {
                currentDepth /= part
                val next = seen.getOrPut(part) {
                    val newChild = PathNode(currentDepth)
                    lastSeen.children.add(newChild)
                    newChild
                }
                lastSeen = next
            }
        }
    }
}

class ZipFileModel(fileSystem: FileSystem) : DefaultTreeModel(RootNode(fileSystem))

class ZipFileTree(fileSystem: FileSystem) : JXTree(ZipFileModel(fileSystem)) {
    init {
        isRootVisible = false

        setCellRenderer(
            treeCellRenderer { tree, value, selected, expanded, leaf, row, hasFocus ->
                if (value is PathNode) {
                    val path = value.userObject
                    icon = Tool.byExtension[path.extension]?.icon?.derive(16, 16) ?: icon
                    toolTipText = Tool.byExtension[path.extension]?.description
                    text = path.last().toString()
                }
                this
            },
        )
    }

    override fun getModel(): ZipFileModel? = super.getModel() as ZipFileModel?
    override fun setModel(newModel: TreeModel?) {
        newModel as ZipFileModel
        super.setModel(newModel)
    }
}
