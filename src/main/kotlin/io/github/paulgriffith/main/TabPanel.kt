package io.github.paulgriffith.main

import com.formdev.flatlaf.extras.components.FlatTabbedPane
import io.github.paulgriffith.MainPanel
import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.ToolPanel
import io.github.paulgriffith.utils.truncate
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPopupMenu
import kotlin.io.path.name

class TabPanel : FlatTabbedPane() {
    init {
        tabLayoutPolicy = SCROLL_TAB_LAYOUT
        isTabsClosable = true
        setTabCloseCallback { _, i ->
            removeTabAt(i)
        }

        addMouseListener(PopupAdapter())
    }

    inner class PopupAdapter : MouseAdapter() {
        private fun showPopupMenu(e: MouseEvent) {
            val tabIndex = indexAtLocation(e.x, e.y)
            if (tabIndex == -1) return
            val tab = getComponentAt(tabIndex) as? ToolPanel ?: return

            JPopupMenu().apply {
                add(
                    Action(name = "Float") {
                        val frame = createPopupFrame(tab)
                        frame.isVisible = true
                    }
                )
            }.show(this@TabPanel, e.x, e.y)
        }

        private fun createPopupFrame(tab: ToolPanel): JFrame = JFrame(tab.path.name).apply {
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
                                tab.path.name.truncate(),
                                tab.icon,
                                tab,
                                tab.path.toString(),
                            )
                            dispose()
                        }
                    )
                }
            )

            jMenuBar = menuBar
            pack()
        }

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
    }
}
