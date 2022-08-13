package io.github.paulgriffith.kindling.backup

import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.JFileChooser
import io.github.paulgriffith.kindling.utils.attachPopupMenu
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import java.util.Collections
import java.util.Enumeration
import javax.swing.JFileChooser
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import kotlin.io.path.outputStream

private sealed interface ZipNode : TreeNode

private class DirectoryNode(
    val name: String,
    val _parent: DirectoryNode?
) : ZipNode {
    val children = LinkedHashMap<String, ZipNode>()

    override fun getAllowsChildren(): Boolean = true
    override fun isLeaf(): Boolean = false
    override fun children(): Enumeration<out TreeNode> = Collections.enumeration(children.values)
    override fun getChildAt(childIndex: Int): TreeNode = children.values.elementAt(childIndex)
    override fun getIndex(node: TreeNode?): Int = children.values.indexOf(node)
    override fun getChildCount(): Int = children.size
    override fun getParent(): TreeNode? = _parent

    override fun toString(): String = name
}

private class FileNode(
    val header: FileHeader,
    val _parent: DirectoryNode?
) : ZipNode {
    override fun getAllowsChildren(): Boolean = false
    override fun isLeaf(): Boolean = true
    override fun children(): Enumeration<out TreeNode> = Collections.emptyEnumeration()
    override fun getChildAt(childIndex: Int): TreeNode? = null
    override fun getIndex(node: TreeNode?): Int = -1
    override fun getChildCount(): Int = 0
    override fun getParent(): TreeNode? = _parent
    override fun toString(): String = header.name
}

class ZipFileTree(private val zipFile: ZipFile) : JTree() {
    init {
        val root = DirectoryNode("root", null)

        for (header in zipFile.fileHeaders) {
            var current: DirectoryNode = root
            val split = header.fileName.split('/')
            for (pathElement in split.subList(0, split.size - 1)) {
                current = current.children.getOrPut(pathElement) { DirectoryNode(pathElement, current) } as DirectoryNode
            }
            val name = split.last()
            current.children[name] = FileNode(header, current)
        }

        model = DefaultTreeModel(root)

        @Suppress("UNCHECKED_CAST")
        attachPopupMenu {
            val partition = selectionModel.selectionPaths.map { it.lastPathComponent }.partition { it is DirectoryNode }
            val directories = partition.first as List<DirectoryNode>
            val files = partition.second as List<FileNode>

            when {
                directories.isEmpty() && files.isEmpty() -> null
//                directories.isNotEmpty() && files.isEmpty() -> menu.add(JMenuItem("Export ${directories.size} directories"))
                directories.isNotEmpty() -> JPopupMenu().apply {
                    add(JMenuItem("Export ${directories.size} directories"))
                }

                else -> JPopupMenu().apply {
                    add(JMenuItem(exportFiles(files)))
                }
            }
        }
    }

    private val fileChooser = JFileChooser {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isMultiSelectionEnabled = false
    }

    private fun exportDirectories(directories: List<DirectoryNode>): Action = Action("Export ${directories.size} directories") {
        TODO("Export ${directories.joinToString()}")
    }

    private fun exportFiles(files: List<FileNode>): Action = Action("Export ${files.size} files") {
        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val exportDirectory = fileChooser.selectedFile.toPath()
            for (file in files) {
                zipFile.getInputStream(file.header).use { zis ->
                    exportDirectory.resolve(file.header.name).outputStream().use { fos ->
                        zis.copyTo(fos)
                    }
                }
            }
        }
    }
}

private val FileHeader.name: String
    get() = fileName.substringAfterLast('/')
