package io.github.paulgriffith.log

import io.github.paulgriffith.idb.IdbPanel
import io.github.paulgriffith.utils.DetailsPane
import io.github.paulgriffith.utils.EDT_SCOPE
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.ReifiedJXTable
import io.github.paulgriffith.utils.getLogger
import io.github.paulgriffith.utils.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JSplitPane
import javax.swing.SortOrder
import io.github.paulgriffith.utils.Detail as DetailEvent

class LogPanel(
    private val rawData: List<LogEvent>,
) : IdbPanel() {
    private val totalRows: Int = rawData.size

    var dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS")
        .withZone(ZoneId.systemDefault())

    private val table = run {
        val initialModel = createModel(rawData)
        ReifiedJXTable(initialModel, initialModel.columns).apply {
            setSortOrder("Timestamp", SortOrder.ASCENDING)
        }
    }

    private val details = DetailsPane()
    private val header = Header(totalRows)
    private val sidebar = LoggerNamesPanel(rawData)

    private val filters: List<(LogEvent) -> Boolean> = buildList {
        add { event ->
            event.logger in sidebar.list.checkBoxListSelectedIndices
                .map { sidebar.list.model.getElementAt(it) }
                .filterIsInstance<LoggerName>()
                .mapTo(mutableSetOf()) { it.name }
        }
        add { event ->
            when (event) {
                is SystemLogsEvent -> {
                    event.level >= header.levels.selectedItem as Level
                }
                is WrapperLogEvent -> true
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
                            event.stacktrace.any { stacktrace ->
                                stacktrace.contains(text, ignoreCase = true)
                            }
                    }
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
        add(
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                sidebar,
                JSplitPane(
                    JSplitPane.VERTICAL_SPLIT,
                    FlatScrollPane(table),
                    details,
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
                                    details = event.mdc,
                                )
                                is WrapperLogEvent -> DetailEvent(
                                    title = dateFormatter.format(event.timestamp),
                                    message = event.message,
                                    body = event.stacktrace,
                                )
                            }
                        }
                }
            }
        }

        table.addPropertyChangeListener("model") {
            header.displayedRows = table.model.rowCount
        }

        sidebar.list.checkBoxListSelectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateData()
            }
        }

        header.levels.addActionListener {
            updateData()
        }

        header.search.addActionListener {
            updateData()
        }

        header.timezone.addActionListener {
            dateFormatter = dateFormatter.withZone(ZoneId.of(header.timezone.selectedItem))
        }
    }

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)

        val LOGGER = getLogger<LogPanel>()

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
