package io.github.paulgriffith.kindling.utils

import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXDatePicker
import java.awt.Label
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.*
import javax.swing.*
import kotlin.properties.Delegates


class TimelineSelector(lowerBound : Long, upperBound : Long) : JPanel(MigLayout("fill")) {
    var mtsWidth = 600
    val thumbWidth = 4
    private val thumbHeight = 30
    var listeners = ArrayList<() -> Unit>()
    private var lowerBoundTime = lowerBound
    private var upperBoundTime = upperBound
    var lowerSelectedTime by Delegates.observable(lowerBound) { _, _, _ -> listeners.forEach { if (!mtsChangeDetector) it() } }
    var upperSelectedTime by Delegates.observable(upperBound) { _, _, _ -> listeners.forEach { if (!mtsChangeDetector) it() } }
    private var startDatePicker = JXDatePicker(Date(lowerBound))
    private var endDatePicker = JXDatePicker(Date(upperBound))
    private var startTimePicker = TimePicker(lowerBound)
    private var endTimePicker = TimePicker(upperBound)
    val lowerThumb = DraggableThumb(0, thumbWidth, thumbHeight)
    val upperThumb = DraggableThumb(mtsWidth - thumbWidth , thumbWidth, thumbHeight)
    private val fullTimeLine = SliderLine()
    private val selectedTimeLine = SliderLine()
    private val multiThumbSlider = JPanel()
    private var mtsChangeDetector = false
    private var resetButton = JButton("Reset")
    private var hideButton = JButton("x")

    init {
        border = BorderFactory.createLineBorder(UIManager.getColor("InternalFrame.borderDarkShadow"), 1)
        fullTimeLine.border = BorderFactory.createLineBorder(UIManager.getColor("TaskPaneContainer.background"), 5)
        selectedTimeLine.border = BorderFactory.createLineBorder(UIManager.getColor("MenuItem.underlineSelectionColor"), 5)

        addMouseListenersToThumb(lowerThumb)
        addMouseListenersToThumb(upperThumb)

        startDatePicker.addActionListener(lowerSelectedTimeAction())
        startTimePicker.hours.addActionListener(lowerSelectedTimeAction())
        startTimePicker.minutes.addActionListener(lowerSelectedTimeAction())
        startTimePicker.seconds.addActionListener(lowerSelectedTimeAction())

        endDatePicker.addActionListener(upperSelectedTimeAction())
        endTimePicker.hours.addActionListener(upperSelectedTimeAction())
        endTimePicker.minutes.addActionListener(upperSelectedTimeAction())
        endTimePicker.seconds.addActionListener(upperSelectedTimeAction())

        initMultiThumbSlider()

        lowerThumb.toolTipText = Date(lowerSelectedTime).toString()
        upperThumb.toolTipText = Date(upperSelectedTime).toString()

        resetButton.addActionListener {
            startDatePicker.date = Date(lowerBoundTime)
            endDatePicker.date = Date(upperBoundTime)
            startTimePicker.updatePicker(lowerBoundTime - 1)
            endTimePicker.updatePicker(upperBoundTime + 1)
            lowerThumb.setBounds(0, 5, thumbWidth, thumbHeight)
            upperThumb.setBounds(mtsWidth - thumbWidth, 5, thumbWidth, thumbHeight)
            selectedTimeLine.setBounds(0, 15, mtsWidth - thumbWidth, 10)

        }

        hideButton.apply {
            background = null
            border = null
            addActionListener {
                parent.isVisible = false
            }
        }

        add(Label("Start Time:"), "")
        add(startDatePicker, "w 150!")
        add(startTimePicker, "")
        add(hideButton, "wrap, top, right")
        add(Label("End Time:"), "")
        add(endDatePicker, "w 150!")
        add(endTimePicker, "")
        add(resetButton, "wrap, align right")
        add(multiThumbSlider, "w 600!, h 40!, spanx 4")
    }

    private fun lowerSelectedTimeAction(): (e: ActionEvent) -> Unit = {
        val unvalidatedStartDate = getDate(startDatePicker.date.time, startTimePicker.hours.selectedIndex, startTimePicker.minutes.selectedIndex, startTimePicker.seconds.selectedIndex)
        if (unvalidatedStartDate < lowerBoundTime) {
            startDatePicker.date = Date(lowerBoundTime)
            startTimePicker.updatePicker(lowerBoundTime)
        } else if (unvalidatedStartDate > endTimePicker.cal.timeInMillis) {
            startDatePicker.date = endDatePicker.date
            startTimePicker.hours.selectedIndex = endTimePicker.hours.selectedIndex
            startTimePicker.minutes.selectedIndex = endTimePicker.minutes.selectedIndex
            startTimePicker.seconds.selectedIndex = endTimePicker.seconds.selectedIndex
        }
        updateLowerSelectedTime()
        updateMTS()
        lowerThumb.toolTipText = Date(lowerSelectedTime).toString()
    }

    private fun upperSelectedTimeAction(): (e: ActionEvent) -> Unit = {
        val unvalidatedEndDate = getDate(endDatePicker.date.time, endTimePicker.hours.selectedIndex, endTimePicker.minutes.selectedIndex, endTimePicker.seconds.selectedIndex)
        if (unvalidatedEndDate > upperBoundTime) {
            endDatePicker.date = Date(upperBoundTime)
            endTimePicker.updatePicker(upperBoundTime)
        } else if (unvalidatedEndDate < startTimePicker.cal.timeInMillis) {
            endDatePicker.date = startDatePicker.date
            endTimePicker.hours.selectedIndex = startTimePicker.hours.selectedIndex
            endTimePicker.minutes.selectedIndex = startTimePicker.minutes.selectedIndex
            endTimePicker.seconds.selectedIndex = startTimePicker.seconds.selectedIndex
        }
        updateUpperSelectedTime()
        updateMTS()
        upperThumb.toolTipText = Date(upperSelectedTime).toString()
    }

    private fun initMultiThumbSlider() {
        multiThumbSlider.background = UIManager.getColor("controlHighlight")
        multiThumbSlider.setBounds(0, 0, mtsWidth, thumbHeight * 2)
        multiThumbSlider.layout = null
        multiThumbSlider.add(lowerThumb)
        multiThumbSlider.add(upperThumb)

        val timeRange = upperBoundTime - lowerBoundTime
        val minuteCount = timeRange / 60000
        val hourCount = minuteCount / 60
        val dayCount = hourCount / 24
        val minuteOffset = ((59 - startTimePicker.cal.get(Calendar.MINUTE).toDouble()) / minuteCount.toDouble()) * (mtsWidth - thumbWidth).toDouble()
        val dayInterval = (mtsWidth - thumbWidth).toDouble() / (timeRange.toDouble() / 86400000)
        val hourInterval = dayInterval / 24
        val startDate: LocalDate = LocalDate.of(startTimePicker.cal.get(Calendar.YEAR), startTimePicker.cal.get(Calendar.MONTH) + 1, startTimePicker.cal.get(Calendar.DAY_OF_MONTH))

        for (i in 0 .. hourCount.toInt()) {
            val tickX = minuteOffset.toInt() + (i * hourInterval).toInt() + 1
            if (i % 24 == 23 - startTimePicker.cal.get(Calendar.HOUR_OF_DAY)) {
                multiThumbSlider.add(Tick(tickX, 20))
                val period = Period.of(0, 0, (i / 24) + 1)
                multiThumbSlider.add(
                        Label(startDate.plus(period).format(DateTimeFormatter.ofPattern("MM/dd"))).apply {
                            setBounds(tickX - 15, 28, 40, 15)
                            font = UIManager.getFont("mini.font")
                        },
                )
            } else if (dayCount < 3) {
                multiThumbSlider.add(Tick(tickX, 12))
            }
        }
        multiThumbSlider.add(selectedTimeLine)
        multiThumbSlider.add(fullTimeLine)
    }

    private fun addMouseListenersToThumb(draggableThumb: DraggableThumb) {
        draggableThumb.addMouseListener(
                object : MouseListener {
                    override fun mouseClicked(e: MouseEvent?) {}
                    override fun mousePressed(e: MouseEvent) {
                        mtsChangeDetector = true
                        draggableThumb.screenX = e.xOnScreen
                        draggableThumb.myX = draggableThumb.x
                    }
                    override fun mouseReleased(e: MouseEvent?) {
                        mtsChangeDetector = false
                        if (lowerBoundTime + ((upperBoundTime - lowerBoundTime).toDouble() * (lowerThumb.x.toDouble() / (mtsWidth - thumbWidth))).toLong() < upperSelectedTime && lowerThumb.x >= 0 && lowerThumb.x <= mtsWidth - thumbWidth) {
                            lowerSelectedTime = lowerBoundTime + ((upperBoundTime - lowerBoundTime).toDouble() * (lowerThumb.x.toDouble() / (mtsWidth - thumbWidth))).toLong()
                            lowerThumb.toolTipText = Date(lowerSelectedTime).toString()
                        }
                        if (lowerBoundTime + ((upperBoundTime - lowerBoundTime).toDouble() * (upperThumb.x.toDouble() / (mtsWidth - thumbWidth))).toLong() > lowerSelectedTime && upperThumb.x >= 0 && upperThumb.x <= mtsWidth - thumbWidth) {
                            upperSelectedTime = lowerBoundTime + ((upperBoundTime - lowerBoundTime).toDouble() * (upperThumb.x.toDouble() / (mtsWidth - thumbWidth))).toLong()
                            upperThumb.toolTipText = Date(upperSelectedTime).toString()
                        }
                    }
                    override fun mouseEntered(e: MouseEvent?) {}
                    override fun mouseExited(e: MouseEvent?) {}
                },
        )
        draggableThumb.addMouseMotionListener(
                object : MouseMotionListener {
                    override fun mouseDragged(e: MouseEvent) {
                        val deltaX: Int = e.xOnScreen - draggableThumb.screenX
                        val newX = draggableThumb.myX + deltaX
                        if (newX in 0..mtsWidth - thumbWidth) {
                            draggableThumb.setLocation(newX, 5)
                            if (lowerThumb.x > upperThumb.x) { lowerThumb.setLocation(upperThumb.x, 5) }
                            if (lowerBoundTime + ((upperBoundTime - lowerBoundTime).toDouble() * (lowerThumb.x.toDouble() / (mtsWidth - thumbWidth))).toLong() < upperSelectedTime && lowerThumb.x >= 0 && lowerThumb.x <= mtsWidth - thumbWidth) {
                                lowerSelectedTime = lowerBoundTime + ((upperBoundTime - lowerBoundTime).toDouble() * (lowerThumb.x.toDouble() / (mtsWidth - thumbWidth))).toLong()
                            }
                            if (lowerBoundTime + ((upperBoundTime - lowerBoundTime).toDouble() * (upperThumb.x.toDouble() / (mtsWidth - thumbWidth))).toLong() > lowerSelectedTime && upperThumb.x >= 0 && upperThumb.x <= mtsWidth - thumbWidth) {
                                upperSelectedTime = lowerBoundTime + ((upperBoundTime - lowerBoundTime).toDouble() * (upperThumb.x.toDouble() / (mtsWidth - thumbWidth))).toLong()
                            }

                            selectedTimeLine.setBounds(lowerThumb.x, 15, upperThumb.x - lowerThumb.x, 10)
                            updateDatePickers()
                            updateTimePickers()
                        }
                    }
                    override fun mouseMoved(e: MouseEvent?) {}
                },
        )
    }


    private fun updateLowerSelectedTime() {
        val newTime = getDate(startDatePicker.date.time, startTimePicker.hours.selectedIndex, startTimePicker.minutes.selectedIndex, startTimePicker.seconds.selectedIndex)
        lowerSelectedTime = newTime
        startTimePicker.updatePicker(newTime)
    }

    private fun updateUpperSelectedTime() {
        val newTime = getDate(endDatePicker.date.time, endTimePicker.hours.selectedIndex, endTimePicker.minutes.selectedIndex, endTimePicker.seconds.selectedIndex)
        upperSelectedTime = newTime
        endTimePicker.updatePicker(newTime)
    }

    private fun updateMTS() {
        if (!mtsChangeDetector) {
            val newLowerX = ((mtsWidth - thumbWidth) * ((lowerSelectedTime - lowerBoundTime).toDouble() / (upperBoundTime - lowerBoundTime).toDouble())).toInt()
            val newUpperX = ((mtsWidth - thumbWidth) * ((upperSelectedTime - lowerBoundTime).toDouble() / (upperBoundTime - lowerBoundTime).toDouble())).toInt()
            lowerThumb.setBounds(newLowerX, 5, thumbWidth, thumbHeight)
            upperThumb.setBounds(newUpperX, 5, thumbWidth, thumbHeight)
            selectedTimeLine.setBounds(lowerThumb.x, 15, upperThumb.x - lowerThumb.x, 10)
        }
    }

    fun updateDatePickers() {
        startDatePicker.date = Date(lowerSelectedTime)
        endDatePicker.date = Date(upperSelectedTime)
    }

    fun updateTimePickers() {
        startTimePicker.updatePicker(lowerSelectedTime)
        endTimePicker.updatePicker(upperSelectedTime)
    }

    private fun getDate(d : Long, h : Int, m : Int, s : Int) : Long {
        var newTime : Long = d
        newTime += h * 60 * 60 * 1000
        newTime += m * 60 * 1000
        newTime += s * 1000
        return newTime
    }
}

class FormattedComboBox(items : Int) : JComboBox<Int>() {
    init {
        (0 until items).forEach { addItem(it) }
        for (component in components) {
            if (component is JButton) {
                remove(component)
            }
        }
    }
}

class TimePicker (time: Long) : JPanel(MigLayout()) {
    var hours = FormattedComboBox(24)
    var minutes = FormattedComboBox(60)
    var seconds = FormattedComboBox(60)
    var cal: Calendar = Calendar.getInstance()

    init {
        updatePicker(time)
        add(Label("H:"))
        add(hours, "w 50!")
        add(Label("M:"))
        add(minutes, "w 50!")
        add(Label("S:"))
        add(seconds, "w 50!")
    }

    fun updatePicker(time: Long) {
        cal.timeInMillis = time
        hours.model.selectedItem = cal.get(Calendar.HOUR_OF_DAY)
        minutes.model.selectedItem = cal.get(Calendar.MINUTE)
        seconds.model.selectedItem = cal.get(Calendar.SECOND)
    }
}

class DraggableThumb(x : Int, width : Int, height : Int) : JComponent() {
    var screenX = 0
    var myX = 0

    init {
        border = BorderFactory.createLineBorder(UIManager.getColor("windowBorder"), 5)
        setBounds(x, 5, width, height)
    }
}

class Tick(x :Int, height: Int) : JComponent() {
    init {
        border = BorderFactory.createLineBorder(UIManager.getColor("windowBorder"),  1)
        setBounds(x, (40 - height) / 2, 1, height)
    }
}

class SliderLine : JComponent() {
    init {
        setBounds(0, 15, 600, 10)
    }
}
