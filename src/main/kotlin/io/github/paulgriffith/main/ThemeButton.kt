package io.github.paulgriffith.main

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.extras.FlatAnimatedLafChange
import com.formdev.flatlaf.extras.FlatSVGIcon
import javax.swing.Icon
import javax.swing.JToggleButton

class ThemeButton : JToggleButton() {
    init {
        addActionListener {
            FlatAnimatedLafChange.showSnapshot()
            if (isSelected) {
                FlatDarkLaf.setup()
            } else {
                FlatLightLaf.setup()
            }
            FlatLaf.updateUI()
            FlatAnimatedLafChange.hideSnapshotWithAnimation()
        }
    }

    override fun getIcon(): Icon = if (isSelected) {
        DARK_ICON
    } else {
        LIGHT_ICON
    }

    companion object {
        private val LIGHT_ICON = FlatSVGIcon("icons/bx-sun.svg")
        private val DARK_ICON = FlatSVGIcon("icons/bx-moon.svg")
    }
}
