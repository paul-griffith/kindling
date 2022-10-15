package io.github.paulgriffith.kindling.backup

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.backup.views.IdbView
import io.github.paulgriffith.kindling.backup.views.ImageView
import io.github.paulgriffith.kindling.backup.views.ProjectView
import io.github.paulgriffith.kindling.backup.views.TextFileView
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.PathNode
import io.github.paulgriffith.kindling.utils.ZipFileTree
import org.fife.ui.rsyntaxtextarea.Theme
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.tree.TreeSelectionModel
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.properties.Delegates

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

    private var content: JComponent? by Delegates.observable(null) { _, oldValue, newValue ->
        if (oldValue != newValue) {
            if (oldValue != null) {
                oldValue.isVisible = false
            }
            if (newValue != null) {
                oldValue?.let(::remove)
                add(newValue, "push, grow")
            }
        }
    }

    private val sidebar = FlatScrollPane(files)

    init {
        name = path.name
        toolTipText = path.toString()

        files.addTreeSelectionListener {
            val pathNode = it.path.lastPathComponent as PathNode
            val actualPath = pathNode.userObject
            val fileView = BackupViewer.getFileView(actualPath, provider)
            content = fileView
        }

        add(backupInfo, "north")
        add(sidebar, "west, wmin 200")
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

typealias FileViewProvider = (FileSystemProvider, Path) -> JComponent

object BackupViewer : Tool {
    override val title = "Gateway Backup"
    override val description = ".gwbk Files"
    override val icon = FlatSVGIcon("icons/bx-archive.svg")
    override val extensions = listOf("gwbk")
    override fun open(path: Path): ToolPanel = BackupView(path)

    private val handlers: Map<PathPredicate, FileViewProvider> = buildMap {
        put({ it.extension in TextFileView.KNOWN_EXTENSIONS }, ::TextFileView)
        put({ it.extension in ImageView.KNOWN_EXTENSIONS }, ::ImageView)
        put({ it.extension == "idb" }, ::IdbView)
        put({ it.parent?.name == "projects" }, ::ProjectView)
    }

    fun getFileView(path: Path, fileSystemProvider: FileSystemProvider): JComponent? {
        val matchEntry = handlers.entries.firstOrNull { (predicate, _) -> predicate(path) }
        return matchEntry?.value?.invoke(fileSystemProvider, path)
    }

    enum class Themes(private val themeName: String) {
        LIGHT("idea.xml"),
        DARK("dark.xml");

        val theme: Theme by lazy {
            Theme::class.java.getResourceAsStream("themes/$themeName").use(Theme::load)
        }
    }
}

class BackupViewerProxy : Tool by BackupViewer
