package io.github.paulgriffith.kindling.utils

import com.formdev.flatlaf.extras.components.FlatTabbedPane
import java.awt.Container
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPopupMenu

interface PopupMenuCustomizer {
    fun customizePopupMenu(menu: JPopupMenu)
}

interface FloatableComponent {
    val icon: Icon?
    val tabName: String
    val tabTooltip: String
}

class TabStrip : FlatTabbedPane() {
    init {
        tabPlacement = TOP
        tabLayoutPolicy = SCROLL_TAB_LAYOUT
        tabAlignment = TabAlignment.leading
        isTabsClosable = true

        setTabCloseCallback { _, i ->
            removeTabAt(i)
        }

        attachPopupMenu { event ->
            val tabIndex = indexAtLocation(event.x, event.y)
            if (tabIndex == -1) return@attachPopupMenu null

            val tab = getComponentAt(tabIndex) as JComponent

            JPopupMenu().apply {
                add(
                    Action("Close") {
                        removeClosableTabAt(tabIndex)
                    },
                )
                add(
                    Action("Close Other Tabs") {
                        for (i in tabCount - 1 downTo 0) {
                            if (i != tabIndex) {
                                removeClosableTabAt(i)
                            }
                        }
                    },
                )
                add(
                    Action("Close Tabs Left") {
                        for (i in tabIndex - 1 downTo 0) {
                            removeClosableTabAt(i)
                        }
                    },
                )
                add(
                    Action("Close Tabs Right") {
                        for (i in tabCount - 1 downTo tabIndex + 1) {
                            removeClosableTabAt(i)
                        }
                    },
                )
                val closable = isTabClosable(tabIndex)
                add(
                    Action(if (closable) "Pin" else "Unpin") {
                        setTabClosable(tabIndex, !closable)
                    },
                )
                if (tab is FloatableComponent) {
                    add(
                        Action("Float") {
                            val frame = createPopupFrame(tab)
                            frame.isVisible = true
                        },
                    )
                }
                if (tab is PopupMenuCustomizer) {
                    tab.customizePopupMenu(this)
                }
            }
        }
    }

    private fun removeClosableTabAt(index: Int) {
        if (isTabClosable(index)) {
            removeTabAt(index)
        }
    }

    fun <T> addTab(
        component: T,
        tabName: String = component.tabName,
        tabTooltip: String = component.tabTooltip,
        icon: Icon? = component.icon,
        select: Boolean = true,
    ) where T : Container, T : FloatableComponent {
        addTab(tabName, icon, component, tabTooltip)
        if (select) {
            selectedIndex = tabCount - 1
        }
    }

    private fun <T> createPopupFrame(tab: T): JFrame where T : Container, T : FloatableComponent {
        return jFrame(tab.tabName, 1024, 768) {
            contentPane = tab

            jMenuBar = JMenuBar().apply {
                add(
                    JMenu("Actions").apply {
                        add(
                            Action(name = "Unfloat") {
                                addTab(
                                    tab.tabName,
                                    tab.icon,
                                    tab,
                                    tab.tabTooltip,
                                )
                                dispose()
                            },
                        )
                    },
                )
            }
        }
    }
}
