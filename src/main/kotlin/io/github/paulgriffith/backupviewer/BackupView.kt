package io.github.paulgriffith.backupviewer

import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.paulgriffith.utils.Tool
import io.github.paulgriffith.utils.ToolPanel
import net.lingala.zip4j.ZipFile
import net.miginfocom.swing.MigLayout
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class BackupView(override val path: Path) : ToolPanel() {
    private val gwbk = ZipFile(path.toFile()).also {
        check(it.isValidZipFile) { "Not a valid zip file" }
    }

    private val projects = ProjectsPanel(gwbk)

    private val backupInfo = JLabel().apply {
        val header = gwbk.getFileHeader("backupinfo.xml")
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
        addTab("gateway.xml", TextDocument(gwbk, "gateway.xml"))
        addTab("redundancy.xml", TextDocument(gwbk, "redundancy.xml"))
        addTab("ignition.conf", TextDocument(gwbk, "ignition.conf"))
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

    companion object {
        private val XML_FACTORY = DocumentBuilderFactory.newDefaultInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
    }
}
