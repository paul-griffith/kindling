package io.github.paulgriffith.kindling.backup

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import com.formdev.flatlaf.extras.components.FlatTabbedPane.WRAP_TAB_LAYOUT
import io.github.paulgriffith.kindling.backup.BackupViewer.createMultiView
import io.github.paulgriffith.kindling.backup.views.GenericFileView
import io.github.paulgriffith.kindling.backup.views.ImageView
import io.github.paulgriffith.kindling.backup.views.MultiToolView
import io.github.paulgriffith.kindling.backup.views.ProjectView
import io.github.paulgriffith.kindling.backup.views.TextFileView
import io.github.paulgriffith.kindling.backup.views.ToolView
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.PathNode
import io.github.paulgriffith.kindling.utils.ZipFileTree
import io.github.paulgriffith.kindling.utils.attachPopupMenu
import io.github.paulgriffith.kindling.utils.toFileSizeLabel
import net.miginfocom.swing.MigLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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
import javax.swing.JPopupMenu
import javax.swing.tree.TreeSelectionModel
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.outputStream

class BackupView(path: Path) : ToolPanel() {
    private val gwbk: FileSystem = FileSystems.newFileSystem(path)
    private val provider: FileSystemProvider = gwbk.provider()

    private val files = ZipFileTree(gwbk).apply {
        selectionModel.selectionMode = TreeSelectionModel.CONTIGUOUS_TREE_SELECTION
    }

    private val backupInfo = run {
        if (path.extension == "gwbk") {
            JLabel().apply {
                val file =
                    XML_FACTORY.newDocumentBuilder().parse(provider.newInputStream(gwbk.getPath(BACKUP_INFO))).apply {
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
        } else {
            JLabel("ZIP Bundle")
        }
    }

    private val tabbedPane = FlatTabbedPane().apply {
        tabLayoutPolicy = WRAP_TAB_LAYOUT

        isTabsClosable = true
        tabCloseCallback = BiConsumer { _, i ->
            removeTabAt(i)
        }

        attachPopupMenu { event ->
            val tabIndex = indexAtLocation(event.x, event.y)
            if (tabIndex == -1) return@attachPopupMenu null
            val tab = (getComponentAt(tabIndex) as TabWrapper).pathView
            JPopupMenu().apply {
                tab.customizePopupMenu(this)
            }
        }
    }

    private val FlatTabbedPane.tabs: Sequence<JPanel>
        get() = sequence {
            for (i in 0 until tabCount) {
                yield(getComponentAt(i) as JPanel)
            }
        }

    private val sidebar = FlatScrollPane(files)

    init {
        name = path.name
        toolTipText = path.toString()

        files.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                if (e?.clickCount == 2) {
                    val pathNode = files.selectionPath?.lastPathComponent as? PathNode ?: return
                    val actualPath = pathNode.userObject
                    maybeAddTab(actualPath)
                }
            }
        })

        files.attachPopupMenu {
            selectionPaths?.let { selectedPaths ->
                val numFilesSelected = selectedPaths.size
                FlatPopupMenu().apply {
                    var prompt = "Open File"
                    if (numFilesSelected > 1) {
                        add(
                            Action("Open in new aggregate view") {
                                val actualPaths = selectedPaths.map { (it.lastPathComponent as PathNode).userObject }
                                maybeAddMultiTab(actualPaths)
                            }
                        )
                        prompt = "Open $numFilesSelected files individually"
                    }
                    add(
                        Action(prompt) {
                            selectedPaths.forEach {
                                val actualPath = (it.lastPathComponent as PathNode).userObject
                                maybeAddTab(actualPath)
                            }
                        }
                    )
                }
            }
        }

        add(backupInfo, "north, pad 6 6 6 6")
        add(sidebar, "west, width 200::300, pad 6 6 6 6")
        add(tabbedPane, "dock center")
    }

    class TabWrapper(
        val path: Path,
        val pathView: PathView,
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
            val header = buildString {
                append(path.toString().substring(1))
                if (path.isRegularFile()) {
                    append(" - ")
                    append(attributes.size().toFileSizeLabel())
                }
            }
            add(JLabel(header), "pushx, growx")
            if (path.isRegularFile()) {
                add(saveAs, "ax 100%")
            }
            add(pathView, "newline, push, grow, span")
        }
    }

    override val icon: Icon = BackupViewer.icon

    private fun maybeAddTab(path: Path) {
        val existingTab = tabbedPane.tabs.find { tab ->
            if (tab is TabWrapper) {
                tab.path == path
            } else false
        }
        if (existingTab == null) {
            val fileView = BackupViewer.createView(path, provider)
            if (fileView != null) {
                val attributes = provider.readAttributes(path, BasicFileAttributes::class.java)
                val tab = TabWrapper(path, fileView, attributes)
                tabbedPane.addTab(
                    path.name,
                    fileView.icon?.derive(16, 16),
                    tab,
                    path.toString(),
                )
                tabbedPane.selectedIndex = tabbedPane.tabCount - 1
            }
        } else {
            tabbedPane.selectedComponent = existingTab
        }
    }

    private fun maybeAddMultiTab(paths: List<Path>) {
        val existingTab = tabbedPane.tabs.find {
            if (it is MultiPathView) {
                it.paths == paths
            } else false
        }
        if (existingTab == null) {
            val newTab = createMultiView(paths, provider)
            if (newTab != null) {
                tabbedPane.addTab(
                    newTab.paths.joinToString("\n"),
                    newTab.icon?.derive(16, 16),
                    newTab,
                    newTab.paths.joinToString("\n"),
                )
            }
        } else {
            tabbedPane.selectedComponent = existingTab
        }

    }

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

    open fun customizePopupMenu(menu: JPopupMenu) = Unit
    override fun toString(): String = "${this::class.simpleName}(path=$path)"
}

abstract class MultiPathView: JPanel(MigLayout("ins 0, fill")) {
    abstract val provider: FileSystemProvider
    abstract val paths: List<Path>

    open val icon: FlatSVGIcon? = null

    open fun customizePopupMenu(menu: JPopupMenu) = Unit

    override fun toString(): String = "${this::class.simpleName}(paths=$paths)"
}

object BackupViewer : Tool {
    override val title = "Ignition Bundle"
    override val description = "Archives (.gwbk, .zip, .modl)"
    override val icon = FlatSVGIcon("icons/bx-archive.svg")
    override val extensions = listOf("gwbk", "zip", "modl")
    override fun open(path: Path): ToolPanel = BackupView(path)

    private val handlers: Map<PathPredicate, PathViewProvider> = buildMap {
        put(ToolView::maybeIsTool, ::ToolView)
        put(TextFileView::isTextFile, ::TextFileView)
        put(ImageView::isImageFile, ::ImageView)
        put(ProjectView::isProjectDirectory, ::ProjectView)
    }

    fun createView(path: Path, fileSystemProvider: FileSystemProvider): PathView? {
        val possibleViews = handlers.entries.filter { (predicate, _) -> predicate(path) }.map { it.value } + ::GenericFileView
        return possibleViews.firstNotNullOfOrNull { provider ->
            runCatching {
                provider(fileSystemProvider, path)
            }.getOrNull()
        }
    }

    fun createMultiView(paths: List<Path>, fileSystemProvider: FileSystemProvider) : MultiPathView? {
        return runCatching {
            MultiToolView(fileSystemProvider, paths)
        }.getOrNull()
    }
}

class BackupViewerProxy : Tool by BackupViewer
