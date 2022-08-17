package io.github.paulgriffith.kindling.utils

import com.formdev.flatlaf.extras.components.FlatScrollPane
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.Toolkit
import javax.swing.JTextPane

class ScrollableTextPane(private val input: List<String>) : FlatScrollPane() {
    init {
        viewport.add(
            JTextPane().apply {
                isEditable = false
                contentType = "text/html"
                text = if (input.isNotEmpty()) {
                    buildString {
                        append("<html><pre>")
                        append(
                            input
                                .joinToString("\n")
                                .replace("<", "&lt;")
                                .replace(">", "&gt;")
                        )
                        append("</pre></html>")
                    }
                } else ""
            }
        )

        horizontalScrollBar.preferredSize = Dimension(0, 10)
        verticalScrollBar.preferredSize = Dimension(10, 0)

        horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED
        verticalScrollBarPolicy = VERTICAL_SCROLLBAR_ALWAYS

        val screenSize: Dimension = Toolkit.getDefaultToolkit().screenSize
        preferredSize = Dimension(Integer.MAX_VALUE, ((23 * input.size) + 3 * horizontalScrollBar.preferredSize.height).coerceAtMost(screenSize.height / 2))

        maximumSize = Dimension(Integer.MAX_VALUE, screenSize.height / 4)
        viewport.scrollRectToVisible(Rectangle(0, 0))
    }
}
