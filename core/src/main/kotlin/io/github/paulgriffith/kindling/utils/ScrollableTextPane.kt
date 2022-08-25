package io.github.paulgriffith.kindling.utils

import com.formdev.flatlaf.extras.components.FlatScrollPane
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JTextPane

class ScrollableTextPane(private val input: List<String>) : FlatScrollPane() {
    init {
        if (input.isNotEmpty()) {
            viewport.add(
                JTextPane().apply {
                    isEditable = false
                    contentType = "text/html"
                    text = buildString {
                        append(input.joinToString("\n", "<html><pre>", "</pre></html>", transform = String::escapeHtml))
                    }
                }
            )
        }


        horizontalScrollBar.preferredSize = Dimension(0, 10)
        verticalScrollBar.preferredSize = Dimension(10, 0)

        horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED
        verticalScrollBarPolicy = VERTICAL_SCROLLBAR_ALWAYS

//        val screenSize: Dimension = Toolkit.getDefaultToolkit().screenSize
        val newPrefHeight = ((21 * input.size) + 2 * horizontalScrollBar.preferredSize.height).coerceAtMost(250)
        preferredSize = Dimension(Integer.MAX_VALUE, newPrefHeight)

//        maximumSize = preferredSize
        viewport.scrollRectToVisible(Rectangle(0, 0))
    }
}
