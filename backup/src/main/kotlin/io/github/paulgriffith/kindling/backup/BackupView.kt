package io.github.paulgriffith.kindling.backup

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatScrollPane
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.idb.generic.GenericView
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.SQLiteConnection
import io.github.paulgriffith.kindling.utils.getLogger
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.div
import kotlin.io.path.name

class BackupView(val path: Path) : ToolPanel() {
    private val gwbk = ZipFile(path.toFile()).also {
        check(it.isValidZipFile) { "Not a valid zip file" }
    }

    private val backupInfo = JLabel().apply {
        val header = gwbk.getFileHeader(BACKUP_INFO)
        val file = XML_FACTORY.newDocumentBuilder().parse(gwbk.getInputStream(header).buffered()).apply {
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

    private val tabs = FlatTabbedPane().apply {
        addTab(GATEWAY_XML, textDocument(gwbk, GATEWAY_XML))
        addTab(REDUNDANCY_XML, textDocument(gwbk, REDUNDANCY_XML))
        addTab(IGNITION_CONF, textDocument(gwbk, IGNITION_CONF))
        addTab(
            DB_BACKUP_SQLITE_IDB,
            JPanel(BorderLayout()).apply {
                addHierarchyListener {
                    if (this.isShowing && componentCount == 0) {
                        val dbTempFile = Files.createTempDirectory("kindling")
                        try {
                            gwbk.extractFile(gwbk.getFileHeader(DB_BACKUP_SQLITE_IDB), dbTempFile.toString())
                            val connection = SQLiteConnection(dbTempFile / DB_BACKUP_SQLITE_IDB)
                            val idbView = GenericView(connection)
                            add(idbView, BorderLayout.CENTER)
                        } catch (e: ZipException) {
                            LOGGER.error("Error extracting $DB_BACKUP_SQLITE_IDB from $path", e)
                            add(JLabel("Unable to open $DB_BACKUP_SQLITE_IDB; ${e.message}"), BorderLayout.CENTER)
                        }
                    }
                }
            }
        )
    }

    private val sidebar = JPanel(MigLayout("ins 0")).apply {
//        add(projects, "width 200, growy, pushy")
        add(FlatScrollPane(ZipFileTree(gwbk)), "wmin 300, push, grow")
    }

    init {
        name = path.name
        toolTipText = path.toString()

        add(backupInfo, "north")
        add(
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                sidebar,
                tabs
            ),
            "push, grow"
        )
    }

    override fun removeNotify() = super.removeNotify().also {
        gwbk.close()
    }

    override val icon: Icon = BackupViewer.icon

    private fun textDocument(zipFile: ZipFile, entry: String): FlatScrollPane {
        val header = zipFile.getFileHeader(entry)
        val file = zipFile.getInputStream(header).bufferedReader().readText()
        return FlatScrollPane(
            JTextArea(file).apply {
                isEditable = false
            }
        )
    }

    companion object Constants {
        const val BACKUP_INFO = "backupinfo.xml"
        const val GATEWAY_XML = "gateway.xml"
        const val REDUNDANCY_XML = "redundancy.xml"
        const val IGNITION_CONF = "ignition.conf"
        const val DB_BACKUP_SQLITE_IDB = "db_backup_sqlite.idb"

        private val XML_FACTORY = DocumentBuilderFactory.newDefaultInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }

        private val LOGGER = getLogger<BackupView>()
    }
}

object BackupViewer : Tool {
    override val title = "Gateway Backup"
    override val description = ".gwbk Files"
    override val icon = FlatSVGIcon("icons/bx-archive.svg")
    override val extensions = listOf("gwbk")
    override fun open(path: Path): ToolPanel = BackupView(path)
}

class BackupViewerProxy : Tool by BackupViewer
