package io.github.paulgriffith.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.Popup
import javax.swing.PopupFactory

class DetailsIcon(details: Map<String, String>) : JLabel(detailsIcon) {
    init {
        alignmentY = 0.7F
        val table = JTable(DetailsModel(details.entries.toList())).apply {
            setShowGrid(true)
        }
        val listener = object : MouseAdapter() {
            var popup: Popup? = null

            override fun mouseEntered(e: MouseEvent) {
                popup = popupFactory.getPopup(this@DetailsIcon, table, e.xOnScreen, e.yOnScreen)
                popup?.show()
            }

            override fun mouseExited(e: MouseEvent) {
                popup?.hide()
            }
        }
        addMouseListener(listener)
    }

    companion object Marker {
        private val detailsIcon = FlatSVGIcon("icons/bx-search.svg").derive(0.75F)
        private val popupFactory: PopupFactory = PopupFactory.getSharedInstance()
    }
}
