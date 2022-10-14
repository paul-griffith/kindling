package io.github.paulgriffith.kindling.backup

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.PathNode
import io.github.paulgriffith.kindling.utils.ZipFileTree
import io.github.paulgriffith.kindling.utils.getLogger
import io.github.paulgriffith.kindling.utils.homeLocation
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.UIManager
import javax.swing.tree.TreeSelectionModel
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

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

    private val contentPanel = object : JPanel(BorderLayout()) {
        var content: JComponent? = null
            set(value) {
                if (field != null) {
                    remove(field)
                }
                if (value != null) {
                    add(value, BorderLayout.CENTER)
                }
                field = value
            }
    }

//        addTab(GATEWAY_XML, TextDocument(provider, gwbk.getPath(GATEWAY_XML)))
//        addTab(REDUNDANCY_XML, TextDocument(provider, gwbk.getPath(REDUNDANCY_XML)))
//        addTab(IGNITION_CONF, TextDocument(provider, gwbk.getPath(IGNITION_CONF)))
//        addTab(
//            DB_BACKUP_SQLITE_IDB,
//            JPanel(BorderLayout()).apply {
//                addHierarchyListener {
//                    if (this.isShowing && componentCount == 0) {
//                        val dbTempFile = Files.createTempFile("kindling", "idb")
//                        try {
//                            provider.newInputStream(Path(DB_BACKUP_SQLITE_IDB)).use { idb ->
//                                idb.copyTo(dbTempFile.outputStream())
//                            }
//                            val connection = SQLiteConnection(dbTempFile)
//                            val idbView = GenericView(connection)
//                            add(idbView, BorderLayout.CENTER)
//                        } catch (e: ZipException) {
//                            LOGGER.error("Error extracting $DB_BACKUP_SQLITE_IDB from $path", e)
//                            add(JLabel("Unable to open $DB_BACKUP_SQLITE_IDB; ${e.message}"), BorderLayout.CENTER)
//                        }
//                    }
//                }
//            },
//        )

    private val sidebar = FlatScrollPane(files)

    init {
        name = path.name
        toolTipText = path.toString()

        files.addTreeSelectionListener {
            val pathNode = it.path.lastPathComponent as PathNode
            val actualPath = pathNode.userObject
            val fileView = BackupViewer.getFileView(actualPath, provider)
            contentPanel.content = fileView
        }

        add(backupInfo, "north")
        add(
            JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, contentPanel),
            "push, grow",
        )
    }

    override fun removeNotify() = super.removeNotify().also {
        gwbk.close()
    }

    override val icon: Icon = BackupViewer.icon

    companion object Constants {
        const val BACKUP_INFO = "backupinfo.xml"
        const val GATEWAY_XML = "gateway.xml"
        const val REDUNDANCY_XML = "redundancy.xml"
        const val IGNITION_CONF = "ignition.conf"
        const val DB_BACKUP_SQLITE_IDB = "db_backup_sqlite.idb"

        private val XML_FACTORY = DocumentBuilderFactory.newDefaultInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

        private val LOGGER = getLogger<BackupView>()
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

    private val textExtensions = setOf("conf", "json", "properties", "py", "xml")

    private val handlers: Map<PathPredicate, FileViewProvider> = buildMap {
        put({ it.extension in textExtensions }) { provider, path ->
            FlatScrollPane(TextDocument(provider, path))
        }

        put({ it.parent?.name == "projects" }, ::ProjectView)
    }

    fun getFileView(path: Path, fileSystemProvider: FileSystemProvider): JComponent? {
        val matchEntry = handlers.entries.firstOrNull { (predicate, _) -> predicate(path) }
        return matchEntry?.value?.invoke(fileSystemProvider, path)
    }
}

@OptIn(ExperimentalPathApi::class)
class ProjectView(private val provider: FileSystemProvider, private val path: Path) : JPanel(MigLayout("ins 0")) {
    private val exportButton = JButton("Export Project").apply {
        addActionListener {
            exportDirectoryChooser.selectedFile = homeLocation.resolve(path.name)
            if (exportDirectoryChooser.showSaveDialog(this@ProjectView) == JFileChooser.APPROVE_OPTION) {
                val exportLocation = exportDirectoryChooser.selectedFile.toPath()
                for (projectPath in path.walk(PathWalkOption.INCLUDE_DIRECTORIES)) {
                    val unqualified = projectPath.relativeTo(path)
                    var writeLocation = exportLocation
                    for (part in unqualified) {
                        writeLocation /= part.name
                    }
                    projectPath.copyTo(writeLocation.apply { parent?.createDirectories() }, overwrite = true)
                }
            }
        }
    }

    init {
        add(exportButton, "north")
        add(TextDocument(provider, path.resolve("project.json")), "push, grow")
    }

    companion object {
        val exportDirectoryChooser = JFileChooser(homeLocation).apply {
            isMultiSelectionEnabled = false
            isAcceptAllFileFilterUsed = false
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            UIManager.addPropertyChangeListener { e ->
                if (e.propertyName == "lookAndFeel") {
                    updateUI()
                }
            }
        }
    }
}

class BackupViewerProxy : Tool by BackupViewer
