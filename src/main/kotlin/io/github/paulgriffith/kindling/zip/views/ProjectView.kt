package io.github.paulgriffith.kindling.zip.views

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.core.Kindling
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.JButton
import javax.swing.JFileChooser
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.div
import kotlin.io.path.name

@OptIn(ExperimentalPathApi::class)
class ProjectView(override val provider: FileSystemProvider, override val path: Path) : SinglePathView() {
    private val exportButton = JButton("Export Project")

    init {
        exportButton.addActionListener {
            exportDirectoryChooser.selectedFile = Kindling.homeLocation.resolve(path.name)
            if (exportDirectoryChooser.showSaveDialog(this@ProjectView) == JFileChooser.APPROVE_OPTION) {
                val exportLocation = exportDirectoryChooser.selectedFile.toPath()
                path.copyToRecursively(exportLocation, followLinks = false, overwrite = true)
            }
        }

        add(exportButton, "north")
        add(TextFileView(provider, path / "project.json"), "push, grow")
    }

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-box.svg").derive(16, 16)

    companion object {
        val exportDirectoryChooser = JFileChooser(Kindling.homeLocation).apply {
            isMultiSelectionEnabled = false
            isAcceptAllFileFilterUsed = false
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

            Kindling.addThemeChangeListener {
                updateUI()
            }
        }

        fun isProjectDirectory(path: Path) = path.parent?.name == "projects"
    }
}
