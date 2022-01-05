package io.github.paulgriffith.backupviewer

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.ToolPanel
import io.github.paulgriffith.utils.listCellRenderer
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPopupMenu
import javax.swing.JTextArea

class BackupViewer(override val path: Path) : ToolPanel() {
    private val gwbk = ZipFile(path.toFile()).also {
        it.fileHeaders.forEach { header ->
            println(header.fileName)
        }
        check(it.isValidZipFile) { "Not a valid zip file" }
    }

    private val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }

    private val projects: Array<FileHeader> = gwbk.fileHeaders.filter {
        val split = it.fileName.split('/')
        split.size == 3 && it.isDirectory && split.first() == "projects"
    }.toTypedArray()

    private val projectList = JList(
        projects
    ).apply {
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
                        val save = chooser.showSaveDialog(this@BackupViewer)
                        if (save == JFileChooser.APPROVE_OPTION) {
                            val index = locationToIndex(e.point)
                            if (index != -1) {
                                val selected = model.getElementAt(index)
                                gwbk.fileHeaders
                                    .filter { it.fileName.startsWith(selected.fileName) }
                                    .forEach { header ->
                                        gwbk.getInputStream(header).use { file ->
                                            val relativePath = header.fileName.removePrefix("projects/")
                                            val destination = chooser.selectedFile.resolve(relativePath)
                                            if (header.isDirectory) {
                                                destination.mkdirs()
                                            } else {
                                                destination.outputStream().use { os ->
                                                    file.copyTo(os)
                                                }
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
        val infoHeader = gwbk.getFileHeader("backupinfo.xml")
        val info = gwbk.getInputStream(infoHeader).reader().readText()
        add(
            JTextArea(info).apply {
                isEditable = false
            },
            "wrap"
        )
        add(JLabel("Projects:"))
        add(FlatScrollPane(projectList))
    }

    override fun removeNotify() = super.removeNotify().also {
        gwbk.close()
    }

    override val icon: Icon = FlatSVGIcon("icons/bx-archive.svg")
}
