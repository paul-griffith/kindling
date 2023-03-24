package io.github.paulgriffith.kindling.log

import com.formdev.flatlaf.ui.FlatScrollBarUI
import io.github.paulgriffith.kindling.utils.Column
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.action.AbstractActionExt
import java.awt.event.ActionEvent
import java.awt.geom.AffineTransform
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import java.time.temporal.TemporalUnit
import io.github.paulgriffith.kindling.utils.EDT_SCOPE
import io.github.paulgriffith.kindling.utils.FilterList
import io.github.paulgriffith.kindling.utils.FilterModel
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.ReifiedJXTable
import io.github.paulgriffith.kindling.utils.attachPopupMenu
import io.github.paulgriffith.kindling.utils.getValue
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JMenu
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollBar
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.SortOrder
import javax.swing.SwingConstants
import javax.swing.UIManager
import kotlin.math.absoluteValue
import io.github.paulgriffith.kindling.core.Detail as DetailEvent



class LogPanel(
    private val rawData: List<LogEvent>
) : JPanel(MigLayout("ins 0, fill, hidemode 3")) {
    private val totalRows: Int = rawData.size

    var dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS")
        .withZone(ZoneId.systemDefault())

    private val densityDisplay = GroupingScrollBar()
    private var showDensityDisplay: Boolean = true

    val header = Header(totalRows)
    private val startTime = rawData.minBy{ it.timestamp }.timestamp.toEpochMilli()
    private val endTime = rawData.maxBy{ it.timestamp }.timestamp.toEpochMilli()
    private var messageFilter = ""
    private var stackTraceFilter = object {
        var enabled = false
        var completeMatch = false
        var stacktrace = emptyList<String>()

        fun enableFilter(completeMatch: Boolean, stacktrace : List<String>) {
            enabled = true
            this.completeMatch = completeMatch
            this.stacktrace = stacktrace
        }
    }
    private var threadFilter = ""

    val table = run {
        val initialModel = createModel(rawData)
        ReifiedJXTable(initialModel, initialModel.columns).apply {
            setSortOrder("Timestamp", SortOrder.ASCENDING)
            val densityDisplayAction = object : AbstractActionExt("Display Density") {
                init {
                    selectionModel.anchorSelectionIndex = 0
                    isSelected = showDensityDisplay
                    isStateAction = true
                }

                override fun actionPerformed(e: ActionEvent) {
                    showDensityDisplay = !showDensityDisplay
                    densityDisplay.repaint()
                }
            }
            actionMap.put("column.showLogDensity", densityDisplayAction)

//            fun createJMenuFilterItem(column: Column<out LogEvent, out Any?>): JCheckBoxMenuItem {
//                var name = ""
//                var condition = false
//                when (column) {
//                    is SystemLogsColumns<LogEvent> -> {
//
//                    }
//                    LogsColumns<LogEvent>::Level -> {
//
//                    }
//                    LogsColumns<LogEvent>::Logger -> {
//
//                    }
//                    LogsColumns<SystemLogsEvent>:: -> {
//
//                    }
//                }
//                return JCheckBoxMenuItem(name, !condition)
//            }
//
//            fun <R, C> Column<R, C>.condition(): Boolean {
//                when (this) {
//                    is SystemLogsColumns::Thread -> threadFilter.isNotEmpty()
//                    is LogsColumns::Logger -> {
//                        loggerNamesSidebar.loggerNamesList.isOnlySelected()
//                    }
//                }
//            }

            attachPopupMenu { event ->
                val currentRow = rowAtPoint(event.point)
                selectionModel.setSelectionInterval(currentRow, currentRow)
                JPopupMenu().apply {

                    val selectedLogEvent = this@attachPopupMenu.model.data[currentRow]
                    add(JMenu("Filter all with same...").apply {
                        val isOnlySelectedLevel = !loggerLevelsSidebar.isOnlySelected(selectedLogEvent.level!!.name)
                        add(
                            JCheckBoxMenuItem("Level", !isOnlySelectedLevel).apply {
                                addActionListener {
                                    if (isOnlySelectedLevel){
                                        loggerLevelsSidebar.select(this@attachPopupMenu.model.data[currentRow].level!!.name)
                                    } else {
                                        loggerLevelsSidebar.checkBoxListSelectionModel.setSelectionInterval(0, 0)
                                    }
                                    updateData()
                                }
                            }
                        )
                        if (this@attachPopupMenu.model.data[currentRow] is SystemLogsEvent) {
                            add(JCheckBoxMenuItem("Thread", threadFilter.isNotEmpty()).apply {
                                addActionListener {
                                    if (threadFilter.isEmpty()) {
                                        threadFilter = (this@attachPopupMenu.model.data[currentRow] as SystemLogsEvent).thread
                                    } else {
                                        threadFilter = ""
                                    }
                                    updateData()
                                }
                            })
                        }
                        add(JCheckBoxMenuItem("Logger", !loggerNamesSidebar.loggerNamesList.isOnlySelected(selectedLogEvent.logger)).apply {
                            addActionListener {
                                if (loggerNamesSidebar.loggerNamesList.isOnlySelected(this@attachPopupMenu.model.data[currentRow].logger)) {
                                    loggerNamesSidebar.loggerNamesList.select(this@attachPopupMenu.model.data[currentRow].logger)
                                } else {
                                    loggerNamesSidebar.loggerNamesList.checkBoxListSelectionModel.setSelectionInterval(0, 0)
                                }
                                updateData()
                            }
                        })
                        add(JCheckBoxMenuItem("Message", messageFilter.isNotEmpty()).apply {
                            addActionListener {
                                if (messageFilter.isEmpty()) {
                                    messageFilter = this@attachPopupMenu.model.data[currentRow].message
                                } else {
                                    messageFilter = ""
                                }
                                updateData()
                            }
                        })
                        add(JMenu("StackTrace").apply {
                            val eventStacktrace = this@attachPopupMenu.model.data[currentRow].stacktrace
                            add(JCheckBoxMenuItem("Complete Match", stackTraceFilter.enabled).apply {
                                if (eventStacktrace.isNotEmpty()) {
                                    addActionListener {
                                        if (stackTraceFilter.enabled) {
                                            stackTraceFilter.enabled = false
                                        } else {
                                            stackTraceFilter.enableFilter(true, eventStacktrace)
                                        }
                                        updateData()
                                    }
                                } else {
                                    this.isEnabled = false
                                }
                            })
                            add(JCheckBoxMenuItem("Partial Match", stackTraceFilter.enabled).apply {
                                if (eventStacktrace.isNotEmpty()) {
                                    addActionListener {
                                        if (stackTraceFilter.enabled) {
                                            stackTraceFilter.enabled = false
                                        } else {
                                            stackTraceFilter.enableFilter(false, eventStacktrace)
                                        }
                                        updateData()
                                    }
                                } else {
                                    this.isEnabled = false
                                }
                            })
                        })
                    }
                    )
                    add(JMenu("Mark/Unmark all with same...").apply {
                        val isLevelMarked = isMarked("level", this@attachPopupMenu.model.data[currentRow].level!!.name)
                        add(JCheckBoxMenuItem("Level", isLevelMarked).apply {
                            addActionListener {
                                this@attachPopupMenu.model.data.forEach {
                                    if (it.level?.name == this@attachPopupMenu.model.data[currentRow].level!!.name) {
                                        it.marked = !isLevelMarked
                                    }
                                }
                            }
                        })
                        if (this@attachPopupMenu.model.data[currentRow] is SystemLogsEvent) {
                            val isThreadMarked = isMarked("thread", (this@attachPopupMenu.model.data[currentRow] as SystemLogsEvent).thread)
                            add(JCheckBoxMenuItem("Thread", isThreadMarked).apply {
                                addActionListener {
                                    this@attachPopupMenu.model.data.forEach {
                                        if ((it as SystemLogsEvent).thread == (this@attachPopupMenu.model.data[currentRow] as SystemLogsEvent).thread) {
                                            it.marked = !isThreadMarked
                                        }
                                    }
                                }
                            })
                        }
                        val isNameMarked = isMarked("logger", this@attachPopupMenu.model.data[currentRow].logger)
                        add(JCheckBoxMenuItem("Logger", isNameMarked).apply {
                            addActionListener {
                                this@attachPopupMenu.model.data.forEach {
                                    if (it.logger == this@attachPopupMenu.model.data[currentRow].logger) {
                                        it.marked = !isNameMarked
                                    }
                                }
                            }
                        })
                        val isMessageMarked = isMarked("message", this@attachPopupMenu.model.data[currentRow].message)
                        add(JCheckBoxMenuItem("Message", isMessageMarked).apply {
                            addActionListener {
                                this@attachPopupMenu.model.data.forEach {
                                    if (it.message == this@attachPopupMenu.model.data[currentRow].message) {
                                        it.marked = !isMessageMarked
                                    }
                                }
                            }
                        })
                        val eventStacktrace = this@attachPopupMenu.model.data[currentRow].stacktrace
                        add(JMenu("StackTrace").apply {
                            val isFullStackMarked = isMarked("full-stacktrace", eventStacktrace.toString())
                            add(JCheckBoxMenuItem("Complete Match", isFullStackMarked).apply {
                                if (eventStacktrace.isNotEmpty()) {
                                    addActionListener {
                                        this@attachPopupMenu.model.data.forEach {
                                            if (it.stacktrace == eventStacktrace) {
                                                it.marked = !isFullStackMarked
                                            }
                                        }
                                    }
                                } else {
                                    this.isEnabled = false
                                }
                            })
                            val isPartialStackMarked = isMarked("partial-stacktrace", if (eventStacktrace.isNotEmpty()) { eventStacktrace[0] } else { "" })
                            add(JCheckBoxMenuItem("Partial Match", isPartialStackMarked).apply {
                                if (eventStacktrace.isNotEmpty()) {
                                    addActionListener {
                                        this@attachPopupMenu.model.data.forEach {
                                            if (it.stacktrace.isNotEmpty() && it.stacktrace[0] == this@attachPopupMenu.model.data[currentRow].stacktrace[0]) {
                                                it.marked = !isPartialStackMarked
                                            }
                                        }
                                    }
                                } else {
                                    this.isEnabled = false
                                }
                            })
                        })
                    })
                }
            }

        }
    }



    private fun isMarked(type: String, selectedValue: String) : Boolean {
        table.model.data.forEach {
            if (!it.marked) {
                val eventValue = when (type) {
                    "level" -> it.level?.name
                    "logger" -> it.logger
                    "thread" -> (it as SystemLogsEvent).thread
                    "message" ->it.message
                    "partial-stacktrace" ->
                        if (it.stacktrace.isNotEmpty()) { it.stacktrace[0] } else { "" }
                    "full-stacktrace" -> it.stacktrace.toString()
                    else -> ""
                }
                if (eventValue == selectedValue) return false
            }
        }
        return true
    }

    private val tableScrollPane = FlatScrollPane(table) {
        verticalScrollBar = densityDisplay
    }

    private val details = LogDetailsPane()
    private val loggerNamesSidebar = LoggerNamesPanel(rawData)

    private val loggerLevelsSidebar = FilterList("").apply {
        model = FilterModel(rawData.groupingBy { it.level?.name }.eachCount())
        selectAll()
    }
    private val loggerMDCsSidebar = if (rawData.first() is SystemLogsEvent) {LoggerMDCPanel(rawData as List<SystemLogsEvent>)} else {null}
    private val loggerTimesSidebar = LoggerTimesPanel(startTime, endTime)
    private val filterPane = JTabbedPane().apply {

        addTab("Loggers", loggerNamesSidebar)
        addTab("Levels", FlatScrollPane(loggerLevelsSidebar))
        if (loggerMDCsSidebar != null) {
            addTab("MDC", loggerMDCsSidebar)
        }
        addTab("Time", loggerTimesSidebar)
    }
    private val filters: List<(LogEvent) -> Boolean> = buildList {
        add { event ->
            event.logger in loggerNamesSidebar.loggerNamesList.checkBoxListSelectedValues
        }
        add { event ->
            loggerTimesSidebar.isValidLogEvent(event)
        }
        add { event ->
            event.level!!.name in loggerLevelsSidebar.checkBoxListSelectedValues
        }
        add { event ->
            loggerMDCsSidebar?.filterTable?.isValidLogEvent(event, false) ?: true
        }
        add { event ->
            loggerMDCsSidebar?.filterTable?.isValidLogEvent(event, true) ?: true
        }
        add { event ->
            event.marked || !header.isOnlyShowMarkedLogs
        }
        add { event ->
            val text = header.search.text
            if (text.isNullOrEmpty()) {
                true
            } else {
                when (event) {
                    is SystemLogsEvent -> {
                        text in event.message ||
                            event.logger.contains(text, ignoreCase = true) ||
                            event.thread.contains(text, ignoreCase = true) ||
                            event.stacktrace.any { stacktrace -> stacktrace.contains(text, ignoreCase = true) }
                    }

                    is WrapperLogEvent -> {
                        text in event.message ||
                            event.logger.contains(text, ignoreCase = true) ||
                            event.stacktrace.any { stacktrace -> stacktrace.contains(text, ignoreCase = true) }
                    }
                }
            }
        }
        add { event ->
            messageFilter.isEmpty() || messageFilter in event.message
        }
        add { event ->
            !stackTraceFilter.enabled ||
            (
                event.stacktrace.isNotEmpty() &&
                (
                    (stackTraceFilter.completeMatch && stackTraceFilter.stacktrace == event.stacktrace) ||
                    (!stackTraceFilter.completeMatch && stackTraceFilter.stacktrace[0] == event.stacktrace[0])
                )
            )
        }
        add { event ->
            event is WrapperLogEvent || threadFilter.isEmpty() || threadFilter == (event as SystemLogsEvent).thread
        }
    }

    private fun updateData() {
        BACKGROUND.launch {
            val filteredData = rawData.filter { event ->
                filters.all { filter -> filter(event) }
            }
            EDT_SCOPE.launch {
                table.model = createModel(filteredData)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createModel(rawData: List<LogEvent>) = when (rawData.firstOrNull()) {
        is WrapperLogEvent -> LogsModel(rawData as List<WrapperLogEvent>, WrapperLogColumns(this))
        is SystemLogsEvent -> LogsModel(rawData as List<SystemLogsEvent>, SystemLogsColumns(this))
        else -> LogsModel(rawData as List<WrapperLogEvent>, WrapperLogColumns(this))
    }

    init {
        add(header, "wrap, growx, spanx 2")
        add(
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                JPanel(MigLayout("ins 0, fill")).apply {
                    add(filterPane, "grow") },
                JSplitPane(
                    JSplitPane.VERTICAL_SPLIT,
                    tableScrollPane,
                    details
                ).apply {
                    resizeWeight = 0.6
                }
            ).apply {
                isOneTouchExpandable = true
                resizeWeight = 0.1
            },
            "push, grow"
        )

        table.selectionModel.apply {
            addListSelectionListener { selectionEvent ->
                if (!selectionEvent.valueIsAdjusting) {
                    details.events = selectedIndices
                        .filter { isSelectedIndex(it) }
                        .map { table.convertRowIndexToModel(it) }
                        .map { row -> table.model[row] }
                        .map { event ->
                            when (event) {
                                is SystemLogsEvent -> DetailEvent(
                                    title = "${dateFormatter.format(event.timestamp)} ${event.thread}",
                                    message = event.message,
                                    body = event.stacktrace,
                                    details = event.mdc
                                )

                                is WrapperLogEvent -> DetailEvent(
                                    title = event.level.toString() + "   -   " + dateFormatter.format(event.timestamp) + "   -   " + event.logger.substringAfterLast("."),
                                    message = event.message,
                                    body = event.stacktrace
                                )
                            }
                        }
                }
            }
        }

        table.addPropertyChangeListener("model") {
            header.displayedRows = table.model.rowCount
        }

        loggerNamesSidebar.loggerNamesList.checkBoxListSelectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateData()
            }
        }

        loggerLevelsSidebar.checkBoxListSelectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateData()
            }
        }

        loggerMDCsSidebar?.filterTable?.addTableModelListener {
            updateData()
        }

        loggerTimesSidebar.addTimeUpdateEventListener {
            updateData()
        }

        header.addPropertyChangeListener("minimumLevel") {
            updateData()
        }
        header.search.addActionListener { updateData() }

        header.addPropertyChangeListener("selectedTimeZone") {
            dateFormatter = dateFormatter.withZone(ZoneId.of(it.newValue as String))
            table.model.fireTableDataChanged()
        }

        header.addPropertyChangeListener("isShowFullLoggerName") {
            loggerNamesSidebar.loggerNamesList.isShowFullLoggerNames = !loggerNamesSidebar.loggerNamesList.isShowFullLoggerNames
            table.model.fireTableDataChanged()
        }

        header.addPropertyChangeListener("isOnlyShowMarkedLogs") {
            table.model.fireTableDataChanged()
            updateData()
        }
    }

    inner class GroupingScrollBar : JScrollBar() {
        private val density: Map<Instant, Int>
        private val rangex: Int

        init {
            val delta = Duration.between(rawData.first().timestamp, rawData.last().timestamp)
            val slice = delta.dividedBy((rawData.size.toLong() / 60).coerceAtLeast(1))
            val insertionPoint = DURATIONS.binarySearch { it.compareTo(slice) }
            val aggregate = DURATIONS[insertionPoint.absoluteValue - 1]

            toolTipText = aggregate.toString()

            density = rawData.groupingBy {
                it.timestamp.truncatedTo(DurationUnit(aggregate))
            }.eachCount()
            rangex = density.values.maxOf { it }
        }

        override fun getUnitIncrement(direction: Int): Int {
            return table.getScrollableUnitIncrement(tableScrollPane.viewport.viewRect, SwingConstants.VERTICAL, direction)
        }

        override fun getBlockIncrement(direction: Int): Int {
            return table.getScrollableBlockIncrement(tableScrollPane.viewport.viewRect, SwingConstants.VERTICAL, direction)
        }

        private val customUI = object : FlatScrollBarUI() {
            override fun paintTrack(g: Graphics, c: JComponent, trackBounds: Rectangle) {
                super.paintTrack(g, c, trackBounds)
                if (showDensityDisplay) {
                    g as Graphics2D
                    g.color = UIManager.getColor("Actions.Red")

                    val old = g.transform
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g.transform(
                        AffineTransform.getScaleInstance(
                            trackBounds.width / rangex.toDouble(),
                            trackBounds.height / density.size.toDouble()
                        )
                    )
                    density.values.forEachIndexed { index, count ->
                        g.drawLine(
                            trackBounds.x,
                            trackBounds.y + index,
                            trackBounds.x + count,
                            trackBounds.y + index
                        )
                    }
                    g.transform = old
                }
            }
        }

        init {
            preferredSize = Dimension(30, 100)
            setUI(customUI)
        }

        override fun updateUI() {
            setUI(customUI)
        }
    }

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)

        private val DURATIONS = listOf(
            Duration.ofMillis(100),
            Duration.ofMillis(500),
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
            Duration.ofMinutes(15),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofHours(2),
            Duration.ofHours(6),
            Duration.ofHours(12),
            Duration.ofDays(1)
        )

        private val DEFAULT_WRAPPER_LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        private val DEFAULT_WRAPPER_MESSAGE_FORMAT =
            "^[^|]+\\|(?<jvm>[^|]+)\\|(?<timestamp>[^|]+)\\|(?: (?<level>[TDIWE]) \\[(?<logger>[^]]++)] \\[(?<time>[^]]++)]: (?<message>.*)| (?<stack>.*))\$".toRegex()

        fun parseLogs(lines: Sequence<String>): List<WrapperLogEvent> {
            val events = mutableListOf<WrapperLogEvent>()
            val currentStack = mutableListOf<String>()
            var partialEvent: WrapperLogEvent? = null
            var lastEventTimestamp: Instant? = null

            fun WrapperLogEvent?.flush() {
                if (this != null) {
                    // flush our previously built event
                    events += this.copy(stacktrace = currentStack.toList())
                    currentStack.clear()
                    partialEvent = null
                }
            }
            for ((index, line) in lines.withIndex()) {
                if (line.isBlank()) {
                    continue
                }

                val match = DEFAULT_WRAPPER_MESSAGE_FORMAT.matchEntire(line)
                if (match != null) {
                    val timestamp by match.groups
                    val time = DEFAULT_WRAPPER_LOG_TIME_FORMAT.parse(timestamp.value.trim(), Instant::from)

                    // we hit an actual logged event
                    if (match.groups["level"] != null) {
                        partialEvent.flush()

                        // now build up a new partial (the next line(s) may have stacktrace)
                        val level by match.groups
                        val logger by match.groups
                        val message by match.groups
                        lastEventTimestamp = time
                        partialEvent = WrapperLogEvent(
                            timestamp = time,
                            message = message.value.trim(),
                            logger = logger.value.trim(),
                            level = Level.valueOf(level.value.single()),
                            marked = false
                        )
                    } else {
                        val stack by match.groups

                        if (lastEventTimestamp == time) {
                            // same timestamp - must be attached stacktrace
                            currentStack += stack.value
                        } else {
                            partialEvent.flush()
                            // different timestamp, but doesn't match our regex - just try to display it in a useful way
                            events += WrapperLogEvent(
                                timestamp = time,
                                message = stack.value,
                                level = Level.INFO,
                                marked = false
                            )
                        }
                    }
                } else {
                    throw IllegalArgumentException("Error parsing line $index, unparseable value: $line")
                }
            }
            partialEvent.flush()
            return events
        }
    }
}

// Credit to https://stackoverflow.com/a/66203968
class DurationUnit(private val duration: Duration) : TemporalUnit {
    init {
        require(!(duration.isZero || duration.isNegative)) { "Duration may not be zero or negative" }
    }

    override fun getDuration(): Duration = duration
    override fun isDurationEstimated(): Boolean = duration.seconds >= SECONDS_PER_DAY
    override fun isDateBased(): Boolean = duration.nano == 0 && duration.seconds % SECONDS_PER_DAY == 0L
    override fun isTimeBased(): Boolean = duration.seconds < SECONDS_PER_DAY && NANOS_PER_DAY % duration.toNanos() == 0L

    @Suppress("UNCHECKED_CAST")
    override fun <R : Temporal?> addTo(temporal: R, amount: Long): R =
            duration.multipliedBy(amount).addTo(temporal) as R

    override fun between(temporal1Inclusive: Temporal, temporal2Exclusive: Temporal): Long {
        return Duration.between(temporal1Inclusive, temporal2Exclusive).dividedBy(duration)
    }

    override fun toString(): String = duration.toString()

    companion object {
        private const val SECONDS_PER_DAY = 86400
        private const val NANOS_PER_SECOND = 1000000000L
        private const val NANOS_PER_DAY = NANOS_PER_SECOND * SECONDS_PER_DAY
    }
}
