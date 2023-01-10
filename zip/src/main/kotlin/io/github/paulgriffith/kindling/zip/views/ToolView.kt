package io.github.paulgriffith.kindling.zip.views

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolOpeningException
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.zip.SinglePathView
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.zip.ZipException
import javax.swing.JLabel
import javax.swing.JPopupMenu
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.outputStream

class ToolView(override val provider: FileSystemProvider, override val path: Path) : SinglePathView() {
    private val toolPanel: ToolPanel

    init {
        val tempFile = Files.createTempFile("kindling", path.name)
        try {
            provider.newInputStream(path).use { file ->
                tempFile.outputStream().use(file::copyTo)
            }
            /* Tool.get() throws exception if tool not found, but this check is already done with isTool() */
            toolPanel = Tool.byExtension[path.extension]?.open(tempFile)
                ?: throw ToolOpeningException("No tool for files of type .${path.extension}")
            add(toolPanel, "push, grow")
        } catch (e: ZipException) {
            throw ToolOpeningException("Unable to open $path .${path.extension}")
            add(JLabel("Unable to open $path; ${e.message}"), "push, grow")
        }
    }

    override val icon: FlatSVGIcon = toolPanel.icon as FlatSVGIcon

    override fun customizePopupMenu(menu: JPopupMenu) = toolPanel.customizePopupMenu(menu)

    companion object {
        fun maybeIsTool(path: Path) = path.extension in Tool.byExtension
    }
}
