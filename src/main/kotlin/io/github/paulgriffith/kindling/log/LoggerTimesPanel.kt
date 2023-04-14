package io.github.paulgriffith.kindling.log

import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.add
import io.github.paulgriffith.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXDatePicker
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.util.Calendar
import java.util.Date
import java.util.EventListener
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.event.EventListenerList

class LoggerTimesPanel(
        private val lowerBound: Long,
        private val upperBound: Long,
) : JPanel(MigLayout("ins 0, fill")) {

    private val listeners = EventListenerList()
    private val enabledCheckBox = JCheckBox(
            Action("Enabled") {
                fireTimeUpdateEvent()
            }
    ).apply { isSelected = true }
    private var latchedStartTime = lowerBound
    private var latchedEndTime = upperBound
    private val startSelector = DateTimeSelector("From:", latchedStartTime)
    private val endSelector = DateTimeSelector("To:", latchedEndTime)
    private val resetButton = JButton(
            Action("Reset") {
                latchedStartTime = lowerBound
                latchedEndTime = upperBound
                startSelector.updateDisplay(latchedStartTime)
                endSelector.updateDisplay(latchedEndTime)
                fireTimeUpdateEvent()
            }
    )
    private val applyButton = JButton(
            Action("Apply") {
                if (verifyDateTimeSelections()) {
                    startSelector.updateLatchedTime()
                    endSelector.updateLatchedTime()
                    latchedStartTime = startSelector.latchedTime
                    latchedEndTime = endSelector.latchedTime
                    fireTimeUpdateEvent()
                } else {
                    JOptionPane.showMessageDialog(JFrame(),
                            "Your selected start time cannot be greater than your selected end time.",
                            "ERROR: Invalid Selected Time",
                            JOptionPane.ERROR_MESSAGE)
                }
            }
    )

    private fun verifyDateTimeSelections(): Boolean {
        return startSelector.getDisplayedDateTime() < endSelector.getDisplayedDateTime()
    }

    fun isValidLogEvent(event : LogEvent) : Boolean {
        return !enabledCheckBox.isSelected || event.timestamp.toEpochMilli() in latchedStartTime..latchedEndTime
    }

    init {
        add(JLabel("Time Filter"), "align center, wrap")
        add(JPanel(MigLayout("ins 4, fill")).apply {
            border = BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"))
            add(enabledCheckBox, "left, wrap")
            add(startSelector,"spanx 2, pushx, growx, wrap")
            add(endSelector,"spanx 2, push, grow, wrap")
            add(resetButton, "left")
            add(applyButton, "right")
        }, "grow, push")
    }

    fun interface TimeUpdateEventListener : EventListener {
        fun onTimeUpdate()
    }

    fun addTimeUpdateEventListener(listener: TimeUpdateEventListener) {
        listeners.add(listener)
    }

    private fun fireTimeUpdateEvent() {
        for (listener in listeners.getAll<TimeUpdateEventListener>()) {
            listener.onTimeUpdate()
        }
    }
}

class DateTimeSelector(label: String, initialTime: Long) : JPanel(MigLayout("ins 0, fillx")) {

    var latchedTime = initialTime
    private var datePicker = JXDatePicker(Date(initialTime)).apply {
        editor.horizontalAlignment = SwingConstants.CENTER
    }
    private val timeSelector = TimeSelector(initialTime)

    fun updateDisplay(latchedTime: Long) {
        datePicker.date = Date(latchedTime)
        timeSelector.updateDisplay(latchedTime)
    }

    fun getDisplayedDateTime() : Long {
        return datePicker.date.time +
                (timeSelector.hourSelector.selectedIndex * MILLIS_PER_HOUR) +
                (timeSelector.minuteSelector.selectedIndex * MILLIS_PER_MINUTE) +
                (timeSelector.secondSelector.selectedIndex * MILLIS_PER_SECOND)  +
                timeSelector.milliSelector.selectedIndex
    }

    fun updateLatchedTime() {
        latchedTime = getDisplayedDateTime()
    }
    init {
        add(JLabel(label).apply {
            horizontalAlignment = SwingConstants.CENTER
        }, "spanx 2, align center, growx, wrap")
        add(JLabel("Date:"))
        add(datePicker, "growx, wrap")
        add(JLabel("Time:"))
        add(timeSelector,"growx")
    }
    companion object {
        const val MILLIS_PER_HOUR = 3600000
        const val MILLIS_PER_MINUTE = 60000
        const val MILLIS_PER_SECOND = 1000
    }
}

class TimeSelector(time : Long) : JPanel(MigLayout("ins 0")) {

    private val backgroundColor = UIManager.getColor("ComboBox.background")
    val hourSelector = FormattedComboBox(24)
    val minuteSelector = FormattedComboBox(60)
    val secondSelector = FormattedComboBox(60)
    val milliSelector = FormattedComboBox(1000)
    private var latchedTime: Calendar = Calendar.getInstance()
    init {
        border = BorderFactory.createLineBorder(UIManager.getColor("Button.borderColor"))
        add(hourSelector,"wmin 45")
        add(JLabel(":"),"shrinkx").apply { background = backgroundColor }
        add(minuteSelector,"wmin 45")
        add(JLabel(":"),"shrinkx").apply { background = backgroundColor }
        add(secondSelector,"wmin 45")
        add(JLabel(":"),"shrinkx").apply { background = backgroundColor }
        add(milliSelector,"wmin 55")
        updateDisplay(time)
    }

    fun updateDisplay(time : Long) {
        latchedTime.timeInMillis = time
        hourSelector.selectedIndex = latchedTime.get(Calendar.HOUR_OF_DAY)
        minuteSelector.selectedIndex = latchedTime.get(Calendar.MINUTE)
        secondSelector.selectedIndex = latchedTime.get(Calendar.SECOND)
        milliSelector.selectedIndex = latchedTime.get(Calendar.MILLISECOND)
    }
}

class FormattedComboBox(items : Int) : JComboBox<String>() {
    init {
        border = null
        val popup: Any = getUI().getAccessibleChild(this, 0)
        val c: Component = (popup as Container).getComponent(0)
        if (c is JScrollPane) {
            val scrollBar = c.verticalScrollBar
            val scrollBarDim = Dimension(10, scrollBar.preferredSize.height)
            scrollBar.preferredSize = scrollBarDim
        }
        val numDigits = (items - 1).toString().length
        (0 until items).forEach {
            addItem(it.toString().padStart(numDigits, '0'))
        }
        for (component in components) {
            if (component is JButton) {
                remove(component)
            }
        }
    }
}
