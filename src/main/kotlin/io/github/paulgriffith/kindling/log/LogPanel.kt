package io.github.paulgriffith.kindling.log

import com.formdev.flatlaf.ui.FlatScrollBarUI
import io.github.paulgriffith.kindling.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.action.AbstractActionExt
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.geom.AffineTransform
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import java.time.temporal.TemporalUnit
import javax.swing.*
import io.github.paulgriffith.kindling.utils.Action
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
    private val startTime = rawData.first().timestamp.toEpochMilli()
    private val endTime = rawData.last().timestamp.toEpochMilli()
    private var messageFilter = ""
    private var stackTraceFilter = Pair<Short, List<String>>(0, emptyList())
    private var threadFilter = ""
    private var mdcFilter : Map<String, String> = emptyMap()
    private val timeline = TimelineSelector(startTime, endTime).apply {
        listeners.add(::updateData)
        isVisible = false
    }

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
        }
    }

    private val tableScrollPane = FlatScrollPane(table) {
        verticalScrollBar = densityDisplay
    }

    private val details = LogDetailsPane()

    private val loggerNamesSidebar = LoggerNamesPanel(rawData)
    private val loggerLevelsSidebar = LoggerLevelsPanel(rawData)
    private val loggerMDCsSidebar = if (rawData.first() is SystemLogsEvent) {LoggerMDCPanel(rawData as List<SystemLogsEvent>)} else {null}
    private val filterPane = JTabbedPane().apply {
        addTab("Loggers", loggerNamesSidebar)
        addTab("Levels", loggerLevelsSidebar)
        if (loggerMDCsSidebar != null) {
            addTab("MDC", loggerMDCsSidebar)
        }
    }
    private val filters: List<(LogEvent) -> Boolean> = buildList {
        add { event ->
            event.logger in loggerNamesSidebar.list.checkBoxListSelectedIndices
                .map { loggerNamesSidebar.list.model.getElementAt(it) }
                .filterIsInstance<LoggerName>()
                .mapTo(mutableSetOf()) { it.name }
        }
        add { event ->
            event.timestamp.toEpochMilli() in timeline.lowerSelectedTime ..  timeline.upperSelectedTime
        }
        add { event ->
            event.level!!.name in loggerLevelsSidebar.list.checkBoxListSelectedIndices
                    .map { loggerLevelsSidebar.list.model.getElementAt(it) }
                    .filterIsInstance<LoggerLevel>()
                    .mapTo(mutableSetOf()) { it.name }
        }
        add { event ->
            loggerMDCsSidebar?.filterTable?.isValidLogEvent(event, false) ?: true
        }
        add { event ->
            loggerMDCsSidebar?.filterTable?.isValidLogEvent(event, true) ?: true
        }
        add { event ->
            if (header.isOnlyShowMarkedLogs) {
                event.marked
            } else {
                true
            }
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
            if (messageFilter.isEmpty()) {
                true
            } else {
                when (event) {
                    is SystemLogsEvent -> {
                        messageFilter in event.message
                    }
                    is WrapperLogEvent -> {
                        messageFilter in event.message
                    }
                }
            }
        }
        add { event ->
            if (stackTraceFilter.first.toInt() == 0) {
                true
            } else if (stackTraceFilter.first.toInt() == 1) {
                if (event.stacktrace.isEmpty()) {
                    false
                } else {
                    stackTraceFilter.second == event.stacktrace
                }
            } else {
                if (event.stacktrace.isEmpty()) {
                    false
                } else {
                    stackTraceFilter.second[0] == event.stacktrace[0]
                }
            }
        }
        add { event ->
            when (event) {
                is SystemLogsEvent -> {
                    if (threadFilter.isEmpty()) {
                        true
                    } else {
                        threadFilter == event.thread
                    }
                }
                is WrapperLogEvent -> {
                    true
                }
            }
        }
        add { event ->
            when (event) {
                is SystemLogsEvent -> {
                    if (mdcFilter.isEmpty()) {
                        true
                    } else {
                        mdcFilter == event.mdc
                    }
                }
                is WrapperLogEvent -> {
                    true
                }
            }
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
        add(timeline, "wrap, spanx 2, w 620!")
        add(
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                JPanel(MigLayout("ins 0, fill")).apply { add(filterPane, "grow") },
                JSplitPane(
                    JSplitPane.VERTICAL_SPLIT,
                    tableScrollPane,
                    details
                ).apply {
                    resizeWeight = 0.6
                }
            ).apply {
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

        fun isLoggerLevelMarked(logger: String) : Boolean {
            table.model.data.forEach {
                if (it.level?.name == logger && !it.marked) {
                    return false
                }
            }
            return true
        }

        fun isLoggerNameMarked(name: String) : Boolean {
            table.model.data.forEach {
                if (it.logger == name && !it.marked) {
                    return false
                }
            }
            return true
        }

        fun isLoggerThreadMarked(name: String) : Boolean {
            table.model.data.forEach {
                if ((it as SystemLogsEvent).thread == name && !it.marked) {
                    return false
                }
            }
            return true
        }

        fun isMDCMarked(mdc: Map<String, String>) : Boolean {
            table.model.data.forEach {
                if ((it as SystemLogsEvent).mdc == mdc && !it.marked) {
                    return false
                }
            }
            return true
        }

        fun isLoggerMessageMarked(message: String) : Boolean {
            table.model.data.forEach {
                if (it.message == message && !it.marked) {
                    return false
                }
            }
            return true
        }

        fun isPartialStackTraceMarked(stackTrace: String) : Boolean {
            table.model.data.forEach {
                if (it.stacktrace.isNotEmpty() && it.stacktrace[0] == stackTrace && !it.marked) {
                    return false
                }
            }
            return true
        }

        fun isFullStackTraceMarked(stackTrace: List<String>) : Boolean {
            table.model.data.forEach {
                if (it.stacktrace == stackTrace && !it.marked) {
                    return false
                }
            }
            return true
        }
        table.attachPopupMenu table@{
            val rowAtPoint = if (selectedRowIndices().isNotEmpty()) {selectedRowIndices().first()} else {0}
            selectionModel.setSelectionInterval(rowAtPoint, rowAtPoint)
            JPopupMenu().apply {
                add(
                        JMenu("Filter all with same...").apply {
                            if (!loggerLevelsSidebar.isOnlySelected(this@table.model.data[rowAtPoint].level!!.name)) {
                                add(
                                        Action("Level") {
                                            loggerLevelsSidebar.select(this@table.model.data[rowAtPoint].level!!.name)
                                        }
                                )
                            } else {
                                add(
                                        JCheckBoxMenuItem("Level", true).apply {
                                            toolTipText = "Remove Filter"
                                            addActionListener {
                                                loggerLevelsSidebar.list.checkBoxListSelectionModel.setSelectionInterval(0, 0)
                                                updateData()
                                            }
                                        }
                                )
                            }
                            if (this@table.model.data[rowAtPoint] is SystemLogsEvent) {
                                if (threadFilter.isEmpty()) {
                                    add(
                                            Action("Thread") {
                                                threadFilter = (this@table.model.data[rowAtPoint] as SystemLogsEvent).thread
                                                updateData()
                                            }
                                    )
                                } else {
                                    add(
                                            JCheckBoxMenuItem("Thread", true).apply {
                                                toolTipText = "Remove Filter"
                                                addActionListener {
                                                    threadFilter = ""
                                                    updateData()
                                                }
                                            }
                                    )
                                }
                            }
                            if (!loggerNamesSidebar.isOnlySelected(this@table.model.data[rowAtPoint].logger)) {
                                add(
                                        Action("Logger") {
                                            loggerNamesSidebar.select(this@table.model.data[rowAtPoint].logger)
                                        }
                                )
                            } else {
                                add(
                                        JCheckBoxMenuItem("Logger", true).apply {
                                            toolTipText = "Remove Filter"
                                            addActionListener {
                                                loggerNamesSidebar.list.checkBoxListSelectionModel.setSelectionInterval(0, 0)
                                                updateData()
                                            }
                                        }
                                )
                            }
                            if (messageFilter.isEmpty()) {
                                add(
                                        Action("Message") {
                                            messageFilter = this@table.model.data[rowAtPoint].message
                                            updateData()
                                        }
                                )
                            } else {
                                add(
                                        JCheckBoxMenuItem("Message", true).apply {
                                            toolTipText = "Remove Filter"
                                            addActionListener {
                                                messageFilter = ""
                                                updateData()
                                            }
                                        }
                                )
                            }
                            add(
                                    JMenu("StackTrace").apply {
                                        if (stackTraceFilter.first.toInt() == 0) {
                                            add(
                                                    Action("Complete Match") {
                                                        stackTraceFilter = stackTraceFilter.copy(1, this@table.model.data[rowAtPoint].stacktrace)
                                                        updateData()
                                                    }
                                            )
                                        } else if (stackTraceFilter.first.toInt() == 1) {
                                            add(
                                                    JCheckBoxMenuItem("Complete Match", true).apply {
                                                        toolTipText = "Remove Filter"
                                                        addActionListener {
                                                            stackTraceFilter = stackTraceFilter.copy(0, emptyList())
                                                            updateData()
                                                        }
                                                    }
                                            )
                                        }
                                        if (stackTraceFilter.first.toInt() == 0) {
                                            add(
                                                    Action("Partial Match") {
                                                        stackTraceFilter = stackTraceFilter.copy(2, this@table.model.data[rowAtPoint].stacktrace)
                                                        updateData()
                                                    }
                                            )
                                        } else if (stackTraceFilter.first.toInt() == 2) {
                                            add(
                                                    JCheckBoxMenuItem("Partial Match", true).apply {
                                                        toolTipText = "Remove Filter"
                                                        addActionListener {
                                                            stackTraceFilter = stackTraceFilter.copy(0, emptyList())
                                                            updateData()
                                                        }
                                                    }
                                            )
                                        }
                                    }
                            )
                        }
                )
                add(
                        JMenu("Mark/Unmark all with same...").apply {
                            if (!isLoggerLevelMarked(this@table.model.data[rowAtPoint].level!!.name)) {
                                add(
                                        Action("Level") {
                                            table.model.data.forEach {
                                                if (it.level?.name == this@table.model.data[rowAtPoint].level!!.name) {
                                                    it.marked = true
                                                }
                                            }
                                        }
                                )
                            } else {
                                add(
                                        JCheckBoxMenuItem("Level", true).apply {
                                            addActionListener {
                                                table.model.data.forEach {
                                                    if (it.level?.name == this@table.model.data[rowAtPoint].level!!.name) {
                                                        it.marked = false
                                                    }
                                                }
                                            }
                                        }
                                )
                            }
                            if (this@table.model.data[rowAtPoint] is SystemLogsEvent) {
                                if (!(isLoggerThreadMarked((this@table.model.data[rowAtPoint] as SystemLogsEvent).thread))) {
                                    add(
                                            Action("Thread") {
                                                table.model.data.forEach {
                                                    if ((it as SystemLogsEvent).thread == (this@table.model.data[rowAtPoint] as SystemLogsEvent).thread) {
                                                        it.marked = true
                                                    }
                                                }
                                            }
                                    )
                                } else {
                                    add(
                                            JCheckBoxMenuItem("Thread", true).apply {
                                                addActionListener {
                                                    table.model.data.forEach {
                                                        if ((it as SystemLogsEvent).thread == (this@table.model.data[rowAtPoint] as SystemLogsEvent).thread) {
                                                            it.marked = false
                                                        }
                                                    }
                                                }
                                            }
                                    )
                                }
                                if (!isMDCMarked((this@table.model.data[rowAtPoint] as SystemLogsEvent).mdc)) {
                                    add(
                                            Action("MDC Key") {
                                                table.model.data.forEach {
                                                    if ((it as SystemLogsEvent).mdc == (this@table.model.data[rowAtPoint] as SystemLogsEvent).mdc) {
                                                        it.marked = true
                                                    }
                                                }
                                            }
                                    )
                                } else {
                                    add(
                                            JCheckBoxMenuItem("MDC Key", true).apply {
                                                addActionListener {
                                                    table.model.data.forEach {
                                                        if ((it as SystemLogsEvent).mdc == (this@table.model.data[rowAtPoint] as SystemLogsEvent).mdc) {
                                                            it.marked = false
                                                        }
                                                    }
                                                }
                                            }
                                    )
                                }
                            }
                            if (!isLoggerNameMarked(this@table.model.data[rowAtPoint].logger)) {
                                add(
                                        Action("Logger") {
                                            table.model.data.forEach {
                                                if (it.logger == this@table.model.data[rowAtPoint].logger) {
                                                    it.marked = true
                                                }
                                            }
                                        }
                                )
                            } else {
                                add(
                                        JCheckBoxMenuItem("Logger", true).apply {
                                            addActionListener {
                                                table.model.data.forEach {
                                                    if (it.logger == this@table.model.data[rowAtPoint].logger) {
                                                        it.marked = false
                                                    }
                                                }
                                            }
                                        }
                                )
                            }
                            if (!isLoggerMessageMarked(this@table.model.data[rowAtPoint].message)) {
                                add(
                                        Action("Message") {
                                            table.model.data.forEach {
                                                if (it.message == this@table.model.data[rowAtPoint].message) {
                                                    it.marked = true
                                                }
                                            }
                                        }
                                )
                            } else {
                                add(
                                        JCheckBoxMenuItem("Message", true).apply {
                                            addActionListener {
                                                table.model.data.forEach {
                                                    if (it.message == this@table.model.data[rowAtPoint].message) {
                                                        it.marked = false
                                                    }
                                                }
                                            }
                                        }
                                )
                            }
                            add(
                                    JMenu("StackTrace").apply {
                                        if (!isFullStackTraceMarked(this@table.model.data[rowAtPoint].stacktrace)) {
                                            add(
                                                    Action("Complete Match") {
                                                        table.model.data.forEach {
                                                            if (it.stacktrace == this@table.model.data[rowAtPoint].stacktrace) {
                                                                it.marked = true
                                                            }
                                                        }
                                                    }
                                            )
                                        } else {
                                            add(
                                                    JCheckBoxMenuItem("Complete Match", true).apply {
                                                        addActionListener {
                                                            table.model.data.forEach {
                                                                if (it.stacktrace == this@table.model.data[rowAtPoint].stacktrace) {
                                                                    it.marked = false
                                                                }
                                                            }
                                                        }
                                                    }
                                            )
                                        }
                                        if (this@table.model.data[rowAtPoint].stacktrace.isNotEmpty() && !isPartialStackTraceMarked(this@table.model.data[rowAtPoint].stacktrace[0])) {
                                            add(
                                                    Action("Partial Match") {
                                                        table.model.data.forEach {
                                                            if (it.stacktrace.isNotEmpty() && it.stacktrace[0] == this@table.model.data[rowAtPoint].stacktrace[0]) {
                                                                it.marked = true
                                                            }
                                                        }
                                                    }
                                            )
                                        } else {
                                            add(
                                                    JCheckBoxMenuItem("Partial Match", true).apply {
                                                        addActionListener {
                                                            table.model.data.forEach {
                                                                if (it.stacktrace.isNotEmpty() && it.stacktrace[0] == this@table.model.data[rowAtPoint].stacktrace[0]) {
                                                                    it.marked = false
                                                                }
                                                            }
                                                        }
                                                    }
                                            )
                                        }
                                    }
                            )
                        }
                )
            }
        }

        table.addPropertyChangeListener("model") {
            header.displayedRows = table.model.rowCount
        }

        loggerNamesSidebar.list.checkBoxListSelectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateData()
            }
        }

        loggerLevelsSidebar.list.checkBoxListSelectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateData()
            }
        }

        loggerMDCsSidebar?.filterTable?.addTableModelListener {
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
            table.model.fireTableDataChanged()
            loggerNamesSidebar.list.isShowFullLoggerName = it.newValue as Boolean
            loggerNamesSidebar.sortByFullName(it.newValue as Boolean)
        }

        header.addPropertyChangeListener("isShowTimeFilter") {
            table.model.fireTableDataChanged()
            timeline.isVisible = it.newValue as Boolean
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
