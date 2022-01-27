package io.github.paulgriffith.idb.logviewer

import com.formdev.flatlaf.extras.components.FlatProgressBar
import io.github.paulgriffith.idb.IdbPanel
import io.github.paulgriffith.utils.DetailsPane
import io.github.paulgriffith.utils.EDT_SCOPE
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.debounce
import io.github.paulgriffith.utils.toList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.sql.Connection
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.swing.JSplitPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import io.github.paulgriffith.utils.Detail as DetailEvent

class LogView(connection: Connection) : IdbPanel() {
    private val stackTraces: Map<Int, List<String>> = connection.prepareStatement(
        //language=sql
        """
        SELECT
            event_id,
            i,
            trace_line
        FROM 
            logging_event_exception
        ORDER BY
            event_id,
            i
        """.trimIndent()
    ).executeQuery()
        .toList { resultSet ->
            Pair(
                resultSet.getInt("event_id"),
                resultSet.getString("trace_line")
            )
        }.groupBy(keySelector = { it.first }, valueTransform = { it.second })

    private val mdcKeys: Map<Int, Map<String, String>> = connection.prepareStatement(
        //language=sql
        """
        SELECT 
            event_id,
            mapped_key,
            mapped_value
        FROM 
            logging_event_property
        ORDER BY 
            event_id
        """.trimIndent()
    ).executeQuery()
        .toList { resultSet ->
            Triple(
                resultSet.getInt("event_id"),
                resultSet.getString("mapped_key"),
                resultSet.getString("mapped_value"),
            )
        }.groupingBy { it.first }
        // TODO I bet this can be improved
        .aggregateTo(mutableMapOf<Int, MutableMap<String, String>>()) { _, accumulator, element, _ ->
            val acc = accumulator ?: mutableMapOf()
            acc[element.second] = element.third
            acc
        }

    // Run an initial query (blocking) so if this isn't a log export we bail out
    // This is unfortunate (it can be a little slow) but better UX overall
    private val rawData: List<Event> = connection.prepareStatement(
        //language=sql
        """
            SELECT
                   event_id,
                   timestmp,
                   formatted_message,
                   logger_name,
                   level_string,
                   thread_name
            FROM 
                logging_event
            ORDER BY
                event_id
        """.trimIndent()
    ).executeQuery().toList { resultSet ->
        val eventId = resultSet.getInt("event_id")
        Event(
            eventId = eventId,
            timestamp = Instant.ofEpochMilli(resultSet.getLong("timestmp")),
            message = resultSet.getString("formatted_message"),
            logger = resultSet.getString("logger_name"),
            thread = resultSet.getString("thread_name"),
            level = Event.Level.valueOf(resultSet.getString("level_string")),
            mdc = mdcKeys[eventId].orEmpty(),
            stacktrace = stackTraces[eventId].orEmpty(),
        )
    }

    private val maxRows: Int = rawData.size
    private val table = LogExportTable(LogExportModel(rawData))

    private val details = DetailsPane()

    private val loading = FlatProgressBar().apply {
        isIndeterminate = true
        isVisible = false
    }

    private val header = Header(maxRows)

    private val sidebar = LoggerNamesPanel(rawData)

    private var lockout: Boolean = false

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
        add(loading, "hmax 10, hidemode 0, spanx 2, wrap")
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

        sidebar.list.checkBoxListSelectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting && !lockout) {
                updateData()
            }
        }

        header.levels.addActionListener {
            updateData()
        }

        header.search.document.addDocumentListener(object : DocumentListener {
            val search = debounce<DocumentEvent>(waitMs = 100L) { updateData() }

            override fun insertUpdate(e: DocumentEvent) = search(e)
            override fun removeUpdate(e: DocumentEvent) = search(e)
            override fun changedUpdate(e: DocumentEvent) = search(e)
        })

        lockout = true
        sidebar.list.selectAll()
        lockout = false
    }

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)

        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS")
            .withZone(ZoneId.from(ZoneOffset.UTC))
    }
}
