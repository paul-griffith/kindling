package io.github.paulgriffith.kindling.backup.views

import io.github.paulgriffith.kindling.utils.homeLocation
import net.miginfocom.swing.MigLayout
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

@OptIn(ExperimentalPathApi::class)
class ProjectView(provider: FileSystemProvider, private val path: Path) : JPanel(MigLayout("ins 0, fill")) {
    private val exportButton = JButton("Export Project")

    init {
        exportButton.addActionListener {
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

        add(exportButton, "north")
        add(TextFileView(provider, path / "project.json"), "push, grow")
    }

    override fun toString(): String = "ProjectView(${path.name})"

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
