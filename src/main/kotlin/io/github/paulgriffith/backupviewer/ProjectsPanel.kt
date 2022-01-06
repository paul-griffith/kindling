package io.github.paulgriffith.backupviewer

import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.listCellRenderer
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.miginfocom.swing.MigLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu

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
                                ZipOutputStream(projectDestinationChooser.selectedFile.outputStream()).use { outputFile ->
                                    val zipParameters = ZipParameters()
                                    zipFile.fileHeaders
                                        .filter { it.fileName.startsWith(selected.fileName) }
                                        .filterNot { it.isDirectory } // ZIPs don't care about intermediate directories
                                        .forEach { header ->
                                            val projectRoot = "projects/$projectName/"
                                            zipFile.getInputStream(header).use { file ->
                                                val nextFile = header.fileName.removePrefix(projectRoot)
                                                zipParameters.fileNameInZip = nextFile

                                                outputFile.putNextEntry(zipParameters)
                                                file.transferTo(outputFile)
                                                outputFile.closeEntry()
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
