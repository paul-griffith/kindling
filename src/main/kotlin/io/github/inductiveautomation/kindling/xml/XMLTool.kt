package io.github.inductiveautomation.kindling.xml

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.inductiveautomation.kindling.core.Tool
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.TabStrip
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.name
import kotlin.io.path.useLines

enum class XMLTools {
    LogbackEditor {
        override val displayName: String = "Logback Editor"

        override fun supports(topLevelElement: String): Boolean = topLevelElement.contains("</configuration>", ignoreCase = true)

        override fun open(path: Path): ToolPanel {
            return LogbackEditor(path)
        }
    },
    XMLViewer {
        override val displayName: String = "Raw XML"

        override fun supports(topLevelElement: String): Boolean = true

        override fun open(path: Path): ToolPanel {
            return XMLViewer(path)
        }
    },
    ;

    abstract val displayName: String

    abstract fun supports(topLevelElement: String): Boolean

    abstract fun open(path: Path): ToolPanel
}

class XMLToolPanel(path: Path) : ToolPanel() {
    private val tabs =
        TabStrip().apply {
            trailingComponent = null
            isTabsClosable = false
            tabType = FlatTabbedPane.TabType.underlined
            tabHeight = 16
            isHideTabAreaWithOneTab = true
        }

    init {
        name = path.name
        toolTipText = path.toString()
        val topLevelElement = path.useLines { lines -> lines.last(String::isNotEmpty) }

        val addedTabs =
            XMLTools.entries
                .filter { it.supports(topLevelElement) }
                .onEach { tool ->
                    tabs.addLazyTab(tool.displayName) {
                        tool.open(path)
                    }
                }
                .count()
        if (addedTabs == 1) {
            tabs.selectedIndex = tabs.indices.last
        }
        add(tabs, "push, grow")
    }

    override val icon: Icon = XMLTool.icon
}

object XMLTool : Tool {
    override val title = "Logback Editor"
    override val description = "Logback (.xml) files"
    override val icon = FlatSVGIcon("icons/bx-code.svg")
    private val extensions = listOf("xml")
    override val filter = FileFilter(description, *extensions.toTypedArray())

    override fun open(path: Path): ToolPanel = XMLToolPanel(path)
}
