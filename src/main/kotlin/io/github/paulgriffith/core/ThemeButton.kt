package io.github.paulgriffith.core

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.utils.DARK_THEME
import io.github.paulgriffith.utils.LIGHT_THEME
import io.github.paulgriffith.utils.display
import javax.swing.Icon
import javax.swing.JToggleButton

class ThemeButton(isDark: Boolean = false) : JToggleButton(null as Icon?, isDark) {
    init {
        addActionListener {
            val theme = if (isSelected) DARK_THEME else LIGHT_THEME
            theme.display(true)
        }
    }

    override fun getIcon(): Icon = if (isSelected) DARK_ICON else LIGHT_ICON

    companion object {
        private val LIGHT_ICON = FlatSVGIcon("icons/bx-sun.svg")
        private val DARK_ICON = FlatSVGIcon("icons/bx-moon.svg")
    }
}
