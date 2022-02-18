package io.github.paulgriffith.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.idb.IdbPanel
import io.github.paulgriffith.utils.DetailsPane
import io.github.paulgriffith.utils.EDT_SCOPE
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.ReifiedJXTable
import io.github.paulgriffith.utils.ToolPanel
import io.github.paulgriffith.utils.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.swing.Icon
import javax.swing.JSplitPane
import javax.swing.SortOrder
import kotlin.io.path.useLines
import io.github.paulgriffith.utils.Detail as DetailEvent

class LogPanel<T : LogEvent>(
    rawData: List<T>,
    private val modelFn: (List<T>) -> LogsModel<T>,
) : IdbPanel() {
    private val model = modelFn(rawData)
    private val totalRows: Int = rawData.size
    private val table = ReifiedJXTable(model, model.columns).apply {
        // TODO avoid the hardcoded 1 here, somehow
        setSortOrder(model.columns[1], SortOrder.ASCENDING)
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
                is WrapperLogEvent -> TODO()
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
                            text in event.logger ||
                            text in event.thread ||
                            event.stacktrace.any { stacktrace -> text in stacktrace }
                    }
                    is WrapperLogEvent -> {
                        text in event.message ||
                            event.logger?.contains(text) ?: true ||
                            event.stacktrace?.any { stacktrace -> text in stacktrace } ?: true
                    }
                }
            }
        }
    }

    private fun updateData() {
        BACKGROUND.launch {
            val data = model.data.filter { event ->
                filters.all { filter -> filter(event) }
            }
            EDT_SCOPE.launch {
                table.model = modelFn(data)
            }
        }
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
                        .map { event: T ->
                            when (event) {
                                is SystemLogsEvent -> DetailEvent(
                                    title = "${DATE_FORMAT.format(event.timestamp)} ${event.thread}",
                                    message = event.message,
                                    body = event.stacktrace,
                                    details = event.mdc,
                                )
                                is WrapperLogEvent -> DetailEvent(
                                    title = DATE_FORMAT.format(event.timestamp),
                                    message = event.message,
                                    body = event.stacktrace.orEmpty(),
                                )
                                else -> throw IllegalStateException("impossible")
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
    }

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)

        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS")
            .withZone(ZoneId.from(ZoneOffset.UTC))

        private val DEFAULT_WRAPPER_LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        private val DEFAULT_WRAPPER_MESSAGE_FORMAT =
            "^[^|]+\\|(?<jvm>[^|]+)\\|(?<timestamp>[^|]+)\\|(?: (?<level>[TDIWE]) \\[(?<logger>[^]]++)] (?<message>.*)|(?<stack>.*))\$".toRegex()

        fun parseLogs(lines: Sequence<String>): List<WrapperLogEvent> {
            val events = mutableListOf<WrapperLogEvent>()
            val currentStack = mutableListOf<String>()
            var partialEvent: WrapperLogEvent? = null
            var lastEventTimestamp: Instant? = null

            fun WrapperLogEvent?.flush() {
                if (this != null) {
                    // flush our previously built event
                    events += this.copy(
                        stacktrace = currentStack.toList(),
                    )
                    currentStack.clear()
                    partialEvent = null
                }
            }

            for (line in lines) {
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
                            stacktrace = emptyList(),
                        )
                    } else {
                        val stack by match.groups
                        // same timestamp - must be attached stacktrace
                        if (lastEventTimestamp == time) {
                            currentStack += stack.value
                            // different timestamp, but doesn't match our regex - just try to display it in a useful way
                        } else {
                            events += WrapperLogEvent(
                                timestamp = time,
                                message = stack.value,
                                logger = "",
                                level = Level.INFO,
                                stacktrace = emptyList(),
                            )
                            partialEvent.flush()
                        }
                    }
                } else {
                    throw IllegalArgumentException(line)
                }
            }
            partialEvent.flush()
            return events
        }
    }

    class LogView(override val path: Path) : ToolPanel() {
        init {
            val events = path.useLines(block = ::parseLogs)
            add(LogPanel(events) { list -> LogsModel(list, WrapperLogColumns) }, "push, grow")
        }

        override val icon: Icon = FlatSVGIcon("icons/bx-hdd.svg")
    }
}
