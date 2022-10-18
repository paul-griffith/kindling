package io.github.paulgriffith.kindling.backup

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import com.formdev.flatlaf.extras.components.FlatTabbedPane.WRAP_TAB_LAYOUT
import io.github.paulgriffith.kindling.backup.views.GenericFileView
import io.github.paulgriffith.kindling.backup.views.IdbView
import io.github.paulgriffith.kindling.backup.views.ImageView
import io.github.paulgriffith.kindling.backup.views.ProjectView
import io.github.paulgriffith.kindling.backup.views.TextFileView
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.PathNode
import io.github.paulgriffith.kindling.utils.ZipFileTree
import io.github.paulgriffith.kindling.utils.toFileSizeLabel
import net.miginfocom.swing.MigLayout
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.spi.FileSystemProvider
import java.util.function.BiConsumer
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.tree.TreeSelectionModel
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.outputStream

class BackupView(path: Path) : ToolPanel() {
    private val gwbk: FileSystem = FileSystems.newFileSystem(path)
    private val provider: FileSystemProvider = gwbk.provider()

    private val files = ZipFileTree(gwbk).apply {
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }

    private val backupInfo = JLabel().apply {
        val file = XML_FACTORY.newDocumentBuilder().parse(provider.newInputStream(gwbk.getPath(BACKUP_INFO))).apply {
            normalizeDocument()
        }
        val version = file.getElementsByTagName("version").item(0).textContent
        val timestamp = file.getElementsByTagName("timestamp").item(0).textContent
        val edition = file.getElementsByTagName("edition").item(0)?.textContent

        text = buildString {
            append(version)
            append(" - ")
            append(timestamp)
            if (!edition.isNullOrEmpty()) {
                append(" - ")
                append(edition)
            }
        }
    }

    private val tabbedPane = FlatTabbedPane().apply {
        tabLayoutPolicy = WRAP_TAB_LAYOUT

        isTabsClosable = true
        tabCloseCallback = BiConsumer { _, i ->
            removeTabAt(i)
        }
    }

    private val FlatTabbedPane.tabs: Sequence<TabWrapper>
        get() = sequence {
            for (i in 0 until tabCount) {
                yield(getComponentAt(i) as TabWrapper)
            }
        }

    private val sidebar = FlatScrollPane(files)

    init {
        name = path.name
        toolTipText = path.toString()

        files.addTreeSelectionListener {
            val pathNode = it.path.lastPathComponent as PathNode
            val actualPath = pathNode.userObject

            val existingTab = tabbedPane.tabs.find { tab -> tab.path == actualPath }
            if (existingTab == null) {
                val fileView = BackupViewer.createView(actualPath, provider)
                if (fileView != null) {
                    val attributes = provider.readAttributes(actualPath, BasicFileAttributes::class.java)
                    val tab = TabWrapper(actualPath, fileView, attributes)
                    tabbedPane.addTab(
                        actualPath.name,
                        fileView.icon?.derive(16, 16),
                        tab,
                        actualPath.toString(),
                    )
                    tabbedPane.selectedIndex = tabbedPane.tabCount - 1
                }
            } else {
                tabbedPane.selectedComponent = existingTab
            }
        }

        add(backupInfo, "north, pad 6 6 6 6")
        add(sidebar, "west, width 300!, pad 6 6 6 6")
        add(tabbedPane, "dock center")
    }

    class TabWrapper(
        val path: Path,
        pathView: PathView,
        attributes: BasicFileAttributes,
    ) : JPanel(MigLayout("ins 6")) {
        private val saveAs = JButton("Save As").apply {
            addActionListener {
                exportFileChooser.selectedFile = File(path.last().toString())
                if (exportFileChooser.showSaveDialog(this@TabWrapper) == JFileChooser.APPROVE_OPTION) {
                    pathView.provider.newInputStream(path).use { file ->
                        exportFileChooser.selectedFile.toPath().outputStream().use(file::copyTo)
                    }
                }
            }
        }

        init {
            add(JLabel("${path.toString().substring(1)} - ${attributes.size().toFileSizeLabel()}"), "pushx, growx")
            add(saveAs, "ax 100%, wrap")
            add(pathView, "push, grow, span")
        }
    }

    override fun removeNotify() = super.removeNotify().also {
        gwbk.close()
    }

    override val icon: Icon = BackupViewer.icon

    companion object Constants {
        const val BACKUP_INFO = "backupinfo.xml"

        private val XML_FACTORY = DocumentBuilderFactory.newDefaultInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
    }
}

typealias PathPredicate = (Path) -> Boolean

typealias PathViewProvider = (FileSystemProvider, Path) -> PathView

abstract class PathView : JPanel(MigLayout("ins 0, fill")) {
    abstract val provider: FileSystemProvider
    abstract val path: Path

    open val icon: FlatSVGIcon? = null

    override fun toString(): String = "${this::class.simpleName}(path=$path)"
}

object BackupViewer : Tool {
    override val title = "Gateway Backup"
    override val description = ".gwbk Files"
    override val icon = FlatSVGIcon("icons/bx-archive.svg")
    override val extensions = listOf("gwbk")
    override fun open(path: Path): ToolPanel = BackupView(path)

    private val handlers: Map<PathPredicate, PathViewProvider> = buildMap {
        put(TextFileView::isTextFile, ::TextFileView)
        put(ImageView::isImageFile, ::ImageView)
        put(IdbView::isIdbFile, ::IdbView)
        put(ProjectView::isProjectDirectory, ::ProjectView)
    }

    fun createView(path: Path, fileSystemProvider: FileSystemProvider): PathView? {
        val matchEntry = handlers.entries.firstOrNull { (predicate, _) -> predicate(path) }
        val fileView = matchEntry?.value?.invoke(fileSystemProvider, path)
        return when {
            fileView != null -> fileView
            path.isRegularFile() -> GenericFileView(fileSystemProvider, path)
            else -> null
        }
    }
}

class BackupViewerProxy : Tool by BackupViewer
