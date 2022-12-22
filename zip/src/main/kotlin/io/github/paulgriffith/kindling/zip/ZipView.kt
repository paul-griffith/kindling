package io.github.paulgriffith.kindling.zip

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import com.formdev.flatlaf.extras.components.FlatTabbedPane.WRAP_TAB_LAYOUT
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.PathNode
import io.github.paulgriffith.kindling.utils.ZipFileTree
import io.github.paulgriffith.kindling.utils.attachPopupMenu
import io.github.paulgriffith.kindling.utils.getLogger
import io.github.paulgriffith.kindling.utils.toFileSizeLabel
import io.github.paulgriffith.kindling.zip.ZipViewer.createView
import io.github.paulgriffith.kindling.zip.ZipViewer.logger
import io.github.paulgriffith.kindling.zip.views.GenericFileView
import io.github.paulgriffith.kindling.zip.views.ImageView
import io.github.paulgriffith.kindling.zip.views.MultiToolView
import io.github.paulgriffith.kindling.zip.views.ProjectView
import io.github.paulgriffith.kindling.zip.views.TextFileView
import io.github.paulgriffith.kindling.zip.views.ToolView
import net.miginfocom.swing.MigLayout
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

class ZipView(path: Path) : ToolPanel() {
    private val zipFile: FileSystem = FileSystems.newFileSystem(path)
    private val provider: FileSystemProvider = zipFile.provider()

    private val files = ZipFileTree(zipFile).apply {
        selectionModel.selectionMode = TreeSelectionModel.CONTIGUOUS_TREE_SELECTION
    }

    private val backupInfo = run {
        if (path.extension == "gwbk") {
            JLabel().apply {
                val file =
                    XML_FACTORY.newDocumentBuilder().parse(provider.newInputStream(zipFile.getPath(BACKUP_INFO))).apply {
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

        files.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent?) {
                    if (e?.clickCount == 2) {
                        val pathNode = files.selectionPath?.lastPathComponent as? PathNode ?: return
                        val actualPath = pathNode.userObject
                        maybeAddNewTab(actualPath)
                    }
                }
            },
        )

        files.attachPopupMenu {
            selectionPaths?.let { selectedPaths ->
                FlatPopupMenu().apply {
                    val openIndividually = Action("Open File") {
                        for (treePath in selectedPaths) {
                            val actualPath = (treePath.lastPathComponent as PathNode).userObject
                            maybeAddNewTab(actualPath)
                        }
                    }
                    if (selectedPaths.size > 1) {
                        add(
                            Action("Open in new aggregate view") {
                                val actualPaths = Array(selectedPaths.size) {
                                    (selectedPaths[it].lastPathComponent as PathNode).userObject
                                }
                                maybeAddNewTab(*actualPaths)
                            },
                        )
                        openIndividually.name = "Open ${selectedPaths.size} files individually"
                    }
                    add(openIndividually)
                }
            }
        }

        add(backupInfo, "north, pad 6 6 6 6")
        add(sidebar, "west, width 200::300, pad 6 6 6 6")
        add(tabbedPane, "dock center")
    }

    data class ZipFile(val path: Path, val attributes: BasicFileAttributes)

    class TabWrapper(
        val attributes: List<ZipFile>,
        val pathView: PathView,
    ) : JPanel(MigLayout("ins 6")) {
        private val path = attributes.singleOrNull()?.path

        private val saveAs = JButton("Save As").apply {
            addActionListener {
                exportFileChooser.selectedFile = File(path?.last().toString())
                if (exportFileChooser.showSaveDialog(this@TabWrapper) == JFileChooser.APPROVE_OPTION) {
                    pathView.provider.newInputStream(path).use { file ->
                        exportFileChooser.selectedFile.toPath().outputStream().use(file::copyTo)
                    }
                }
            }
        }

        init {
            val header = attributes.joinToString(", ") { (path, attribute) ->
                buildString {
                    append(path.subpath(1, path.nameCount - 1))
                    if (path.isRegularFile()) {
                        append(" - ")
                        append(attribute.size().toFileSizeLabel())
                    }
                }
            }
            add(JLabel(header), "pushx, growx")
            if (path?.isRegularFile() == true) {
                add(saveAs, "ax 100%")
            }
            add(pathView, "newline, push, grow, span")
        }
    }

    override val icon: Icon = ZipViewer.icon

    private fun maybeAddNewTab(vararg paths: Path) {
        val existingTab = tabbedPane.tabs.find { tab ->
            tab.attributes.map { it.path } == paths.toList()
        }
        if (existingTab == null) {
            val maybeView = createView(provider, *paths)
            maybeView.onSuccess { fileView ->
                val attributes = paths.map { path ->
                    ZipFile(path, provider.readAttributes(path, BasicFileAttributes::class.java))
                }
                val newTab = TabWrapper(attributes, fileView)

                tabbedPane.addTab(
                    fileView.tabName,
                    fileView.icon?.derive(16, 16),
                    newTab,
                    fileView.pathDetails,
                )
                tabbedPane.selectedComponent = newTab
            }.onFailure { ex ->
                logger.error("Failed to open ${paths.contentToString()}", ex)
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

typealias PathViewProvider = (FileSystemProvider, Path) -> SinglePathView

sealed class PathView : JPanel(MigLayout("ins 0, fill")) {
    abstract val provider: FileSystemProvider
    abstract val tabName: String
    abstract val pathDetails: String
    open val icon: FlatSVGIcon? = null
    open fun customizePopupMenu(menu: JPopupMenu) = Unit
}

abstract class SinglePathView : PathView() {
    abstract val path: Path
    override val tabName by lazy { path.name }
    override val pathDetails by lazy { path.toString().substring(1) }
    override fun toString(): String = "${this::class.simpleName}(path=$path)"
}

abstract class MultiPathView : PathView() {
    abstract val paths: List<Path>
    override val tabName by lazy { "${paths.size} files" }
    override val pathDetails by lazy { paths.joinToString("\n") { it.toString().substring(1) } }
    override fun toString(): String = "${this::class.simpleName}(paths=$paths)"
}

object ZipViewer : Tool {
    override val title = "Ignition Bundle"
    override val description = "Archives (.gwbk, .zip, .modl)"
    override val icon = FlatSVGIcon("icons/bx-archive.svg")
    override val extensions = listOf("gwbk", "zip", "modl")
    override fun open(path: Path): ToolPanel = ZipView(path)

    private val handlers: Map<PathPredicate, PathViewProvider> = buildMap {
        put(ToolView::maybeIsTool, ::ToolView)
        put(TextFileView::isTextFile, ::TextFileView)
        put(ImageView::isImageFile, ::ImageView)
        put(ProjectView::isProjectDirectory, ::ProjectView)
    }

    fun createView(fileSystemProvider: FileSystemProvider, vararg paths: Path): Result<PathView> = runCatching {
        if (paths.size > 1) {
            MultiToolView(fileSystemProvider, paths.toList())
        } else {
            val path = paths.first()
            handlers.firstNotNullOfOrNull { (predicate, provider) ->
                if (predicate(path)) {
                    provider(fileSystemProvider, path)
                } else {
                    null
                }
            } ?: GenericFileView(fileSystemProvider, path)
        }
    }

    val logger = getLogger<ZipViewer>()
}

class ZipViewerProxy : Tool by ZipViewer
