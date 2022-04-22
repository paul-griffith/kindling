package io.github.paulgriffith.utils

import net.miginfocom.swing.MigLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JPopupMenu

abstract class ToolPanel(
    layoutConstraints: String = "ins 6, fill, hidemode 3",
) : JPanel(MigLayout(layoutConstraints)) {
    abstract val icon: Icon

    open fun customizePopupMenu(menu: JPopupMenu) = Unit
}
