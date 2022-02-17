package io.github.paulgriffith.log

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.idb.IdbPanel
import io.github.paulgriffith.log.LogExportModel.EventColumns.Timestamp
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

class LogPanel(private val rawData: List<Event>) : IdbPanel() {
    private val totalRows: Int = rawData.size
    private val table = ReifiedJXTable(LogExportModel(rawData), LogExportModel).apply {
        setSortOrder(LogExportModel[Timestamp], SortOrder.ASCENDING)
    }

    private val details = DetailsPane()
    private val header = Header(totalRows)
    private val sidebar = LoggerNamesPanel(rawData)

    private val filters: List<(Event) -> Boolean> = listOf(
        { event ->
            event.logger in sidebar.list.checkBoxListSelectedIndices
                .map { sidebar.list.model.getElementAt(it) }
                .filterIsInstance<LoggerName>()
                .mapTo(mutableSetOf()) { it.name }
        },
        { event ->
            event.level >= header.levels.selectedItem as Event.Level
        },
        { event ->
            val text = header.search.text
            if (text.isNullOrEmpty()) {
                true
            } else {
                text in event.message ||
                    text in event.logger ||
                    text in event.thread ||
                    event.stacktrace.any { stacktrace -> text in stacktrace }
            }
        }
    )

    private fun updateData() {
        BACKGROUND.launch {
            val data = rawData.filter { event ->
                filters.all { filter -> filter(event) }
            }
            EDT_SCOPE.launch {
                table.model = LogExportModel(data)
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
                        .map { event ->
                            DetailEvent(
                                title = "${DATE_FORMAT.format(event.timestamp)} ${event.thread}",
                                message = event.message,
                                body = event.stacktrace,
                                details = event.mdc,
                            )
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
        private val DEFAULT_WRAPPER_MESSAGE_FORMAT = " (?<level>[TDIWE]) (?<logger>\\[.+?]) (?<message>.*)".toRegex()

        fun parseLogs(lines: Sequence<String>): List<Event> {
            val events = mutableListOf<Event>()
            val currentStack = mutableListOf<String>()
            for (line in lines) {
                val elements = line.split('|')
                if (elements.size == 4) { // assume we're properly formatted
                    val time = DEFAULT_WRAPPER_LOG_TIME_FORMAT.parse(elements[2].trim(), Instant::from)
                    val formatMatch = DEFAULT_WRAPPER_MESSAGE_FORMAT.find(elements[3])
                    if (formatMatch == null) { // it's not in the expected format; maybe it's a stacktrace?
                        currentStack += elements[3]
                    } else {
                        val level by formatMatch.groups
                        val logger by formatMatch.groups
                        val message by formatMatch.groups
                        events += Event(
                            timestamp = time,
                            message = message.value,
                            logger = logger.value,
                            thread = "",
                            level = Event.Level.valueOf(level.value.single()),
                            mdc = emptyMap(),
                            stacktrace = currentStack,
                        )
                        currentStack.clear()
                    }
                } else {
                    throw IllegalArgumentException(line)
                }
            }
            return events
        }
    }

    // INFO   | jvm 1    | 2022/02/03 12:53:35 | W [c.i.x.s.d.s.SynchronousRead   ] [19:53:35]: ReadItem did not receive value before timeout: Global.KAS002.Latched

    class LogView(override val path: Path) : ToolPanel() {
        init {
            val events = path.useLines(block = ::parseLogs)
            add(LogPanel(events), "push, grow")
        }

        override val icon: Icon = FlatSVGIcon("icons/bx-hdd.svg")
    }
}
