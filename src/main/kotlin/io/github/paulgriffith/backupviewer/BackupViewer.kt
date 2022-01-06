package io.github.paulgriffith.backupviewer

import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.Tool
import io.github.paulgriffith.utils.ToolPanel
import io.github.paulgriffith.utils.listCellRenderer
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.miginfocom.swing.MigLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class BackupViewer(override val path: Path) : ToolPanel() {
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
            append(path)
            append(" - ")
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
        addTab("Projects", projects)
        addTab("gateway.xml", XmlDocument(gwbk, "gateway.xml"))
        addTab("redundancy.xml", XmlDocument(gwbk, "redundancy.xml"))
    }

    init {
        add(backupInfo, "north")
        add(tabs, "push, grow")
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

class XmlDocument(zipFile: ZipFile, name: String) : JPanel(MigLayout("ins 0, fill")) {
    private val header = zipFile.getFileHeader(name)
    private val file = zipFile.getInputStream(header).bufferedReader().readText()
    private val edit = JButton(
        Action(name = "Edit") {
            zipFile.addStream(
                textArea.text.byteInputStream(),
                ZipParameters().apply {
                    fileNameInZip = name
                }
            )
            isEnabled = false
        }
    ).apply {
        isEnabled = false
    }

    private val textArea = JTextArea(file)

    init {
        textArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                edit.isEnabled = true
            }

            override fun removeUpdate(e: DocumentEvent?) {
                edit.isEnabled = true
            }

            override fun changedUpdate(e: DocumentEvent?) {
                edit.isEnabled = true
            }
        })
        add(FlatScrollPane(textArea), "push, grow")
        add(edit, "south")
    }
}

class ProjectsPanel(private val zipFile: ZipFile) : JPanel(MigLayout("ins 0, fill")) {
    private val projectDestinationChooser = object : JFileChooser() {
        init {
            fileSelectionMode = FILES_ONLY
        }

        override fun approveSelection() {
            val f = selectedFile
            return if (f.exists() && dialogType == SAVE_DIALOG) {
                val result = JOptionPane.showConfirmDialog(
                    this,
                    "$f already exists, overwrite?", "Overwrite?",
                    JOptionPane.YES_NO_CANCEL_OPTION
                )
                when (result) {
                    JOptionPane.YES_OPTION -> super.approveSelection()
                    JOptionPane.CANCEL_OPTION -> cancelSelection()
                    else -> Unit
                }
            } else {
                super.approveSelection()
            }
        }
    }

    private val projects: Array<FileHeader> = zipFile.fileHeaders.filter {
        val split = it.fileName.split('/')
        split.size == 3 && it.isDirectory && split.first() == "projects"
    }.toTypedArray()

    private val projectList = JList(projects).apply {
        cellRenderer = listCellRenderer<FileHeader> { _, value, _, _, _ ->
            text = value.fileName.split('/')[1]
        }
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showPopupMenu(e)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showPopupMenu(e)
                }
            }

            private fun showPopupMenu(e: MouseEvent) {
                val popup = JPopupMenu()
                popup.add(
                    Action(name = "Export") {
                        val index = locationToIndex(e.point)
                        if (index != -1) {
                            val selected = model.getElementAt(index)
                            val projectName = selected.fileName.split('/')[1]
                            // suggest the project name
                            projectDestinationChooser.selectedFile = File("$projectName.zip")
                            val save = projectDestinationChooser.showSaveDialog(this@ProjectsPanel)
                            if (save == JFileChooser.APPROVE_OPTION) {
                                ZipOutputStream(projectDestinationChooser.selectedFile.outputStream()).use { zipFile ->
                                    val zipParameters = ZipParameters()
                                    this@ProjectsPanel.zipFile.fileHeaders
                                        .filter { it.fileName.startsWith(selected.fileName) }
                                        .filterNot { it.isDirectory } // ZIPs don't care about intermediate directories
                                        .forEach { header ->
                                            val projectRoot = "projects/$projectName/"
                                            this@ProjectsPanel.zipFile.getInputStream(header).use { file ->
                                                val nextFile = header.fileName.removePrefix(projectRoot)
                                                println(nextFile)
                                                zipParameters.fileNameInZip = nextFile

                                                zipFile.putNextEntry(zipParameters)
                                                file.transferTo(zipFile)
                                                zipFile.closeEntry()
                                            }
                                        }
                                }
                            }
                        }
                    }
                )
                popup.show(this@apply, e.x, e.y)
            }
        })
    }

    init {
        add(FlatScrollPane(projectList), "push, grow")
    }
}
