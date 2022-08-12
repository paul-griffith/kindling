package io.github.paulgriffith.kindling

import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.attachPopupMenu
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPopupMenu

class TabPanel : FlatTabbedPane() {
    init {
        tabPlacement = LEFT
        tabLayoutPolicy = SCROLL_TAB_LAYOUT
        tabAlignment = TabAlignment.leading
        isTabsClosable = true
        setTabCloseCallback { _, i ->
            removeTabAt(i)
        }

        attachPopupMenu { event ->
            val tabIndex = indexAtLocation(event.x, event.y)
            if (tabIndex == -1) return@attachPopupMenu null
            val tab = getComponentAt(tabIndex) as ToolPanel
            JPopupMenu().apply {
                add(
                    Action(name = "Float") {
                        val frame = createPopupFrame(tab)
                        frame.isVisible = true
                    }
                )
                add(
                    Action(name = "Close") {
                        removeTabAt(tabIndex)
                    }
                )
                val closable = isTabClosable(tabIndex)
                add(
                    Action(name = if (closable) "Pin" else "Unpin") {
                        setTabClosable(tabIndex, !closable)
                    }
                )
                tab.customizePopupMenu(this)
            }
        }
    }

    private fun createPopupFrame(tab: ToolPanel): JFrame = JFrame(tab.name).apply {
        preferredSize = Dimension(1024, 768)
        iconImage = MainPanel.FRAME_ICON
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        add(tab)
        val menuBar = JMenuBar()
        menuBar.add(
            JMenu("Actions").apply {
                add(
                    Action(name = "Unfloat") {
                        addTab(
                            tab.name,
                            tab.icon,
                            tab,
                            tab.toolTipText
                        )
                        dispose()
                    }
                )
            }
        )

        jMenuBar = menuBar
        pack()
    }
}
