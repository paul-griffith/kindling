package io.github.paulgriffith.backup

import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.paulgriffith.idb.generic.GenericView
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.SQLiteConnection
import io.github.paulgriffith.utils.Tool
import io.github.paulgriffith.utils.ToolPanel
import io.github.paulgriffith.utils.getLogger
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
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.div

class BackupView(override val path: Path) : ToolPanel() {
    private val gwbk = ZipFile(path.toFile()).also {
        check(it.isValidZipFile) { "Not a valid zip file" }
    }

    private val projects = ProjectsPanel(gwbk)

    private val backupInfo = JLabel().apply {
        val header = gwbk.getFileHeader(BACKUP_INFO)
        val file = XML_FACTORY.newDocumentBuilder().parse(gwbk.getInputStream(header).buffered()).apply {
            normalizeDocument()
        }
        val version = file.getElementsByTagName("version").item(0).textContent
        val timestamp = file.getElementsByTagName("timestamp").item(0).textContent
        val edition = file.getElementsByTagName("edition").item(0).textContent

        text = buildString {
            append(version)
            append(" - ")
            append(timestamp)
            if (edition.isNotEmpty()) {
                append(" - ")
                append(edition)
            }
        }
    }

    private val tabs = FlatTabbedPane().apply {
        addTab(GATEWAY_XML, TextDocument(gwbk, GATEWAY_XML))
        addTab(REDUNDANCY_XML, TextDocument(gwbk, REDUNDANCY_XML))
        addTab(IGNITION_CONF, TextDocument(gwbk, IGNITION_CONF))
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
                            add(FlatScrollPane(idbView), BorderLayout.CENTER)
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
        add(JLabel("Projects:"), "wrap, gaptop 12")
        add(projects, "width 200, growy, pushy")
    }

    init {
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

    override val icon: Icon = Tool.BackupViewer.icon

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
