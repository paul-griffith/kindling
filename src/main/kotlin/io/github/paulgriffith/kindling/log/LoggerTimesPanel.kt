package io.github.paulgriffith.kindling.log

import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager


class LoggerTimesPanel(events: List<LogEvent>) : JPanel(MigLayout("ins 0, fillx")) {

    private val startTime = events.first().timestamp
    private val endTime = events.last().timestamp
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS")
            .withZone(ZoneId.systemDefault())

    private val startTimeDisplay = JButton("Start: ${dateTimeFormatter.format(startTime)}")
    private val endTimeDisplay = JButton("End ${dateTimeFormatter.format(endTime)}")

    private val circularBarplot = CircularBarplot(mapOf())

    init {

        add(JLabel("Start Time"), "align center, wrap")
        add(startTimeDisplay, "growx, wrap")
        add(JLabel("End Time"), "align center, wrap")
        add(endTimeDisplay, "growx, wrap")
        add(circularBarplot, "w 200!, h 200!, align center")
    }
}

class CircularBarplot(points : Map<Int, Int>) : JPanel(MigLayout("ins 0, fill")) {
private val circle = DrawCircle()
    init {
        setSize(200, 200)
//        add(circle, "push, grow")
    }

}

class DrawCircle() : JComponent() {
    init {
        isVisible = true
    }

    override fun paint(g: Graphics) {
        val g2d = g as Graphics2D
        g.color = Color.CYAN
        g2d.fillOval(0, 0, 200, 200)
        g.color = Color.BLACK
        g2d.drawOval(0, 0, 200, 200)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            DrawCircle()
        }
    }
}