package io.github.paulgriffith.kindling.utils

import com.formdev.flatlaf.extras.components.FlatScrollPane
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JTextPane

class ScrollingTextPane() : FlatScrollPane() {
    var text: String?
        get() = textPane.text
        set(value) {
            val newPrefHeight = ((21 * (value?.split("\n")?.size ?: 1)) + 2 * horizontalScrollBar.preferredSize.height).coerceAtMost(250)
            preferredSize = Dimension(Integer.MAX_VALUE, newPrefHeight)
            textPane.text = value
        }
    private val textPane = JTextPane().apply {
        isEditable = false
        contentType = "text/html"
    }
    init {
        setViewportView(textPane)
//            textPane.apply {
//                text = buildString {
//                    append(input.joinToString("\n", "<html><pre>", "</pre></html>", transform = String::escapeHtml))
//                }
//            }
//        )

        horizontalScrollBar.preferredSize = Dimension(0, 10)
        verticalScrollBar.preferredSize = Dimension(10, 0)

        verticalScrollBarPolicy = VERTICAL_SCROLLBAR_ALWAYS
        preferredSize = Dimension(Integer.MAX_VALUE, 0)
        viewport.scrollRectToVisible(Rectangle(0, 0))
    }
}
