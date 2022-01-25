package io.github.paulgriffith.idb.logviewer

import com.formdev.flatlaf.extras.components.FlatProgressBar
import io.github.paulgriffith.idb.IdbPanel
import io.github.paulgriffith.utils.DetailsPane
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.toList
import java.sql.Connection
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.swing.JSplitPane
import io.github.paulgriffith.utils.Detail as DetailEvent

class LogView(connection: Connection) : IdbPanel() {
    private fun updateData(filter: (Event) -> Boolean) {
        val data = rawData.filter(filter)

        tableModel.data = data
        header.displayedRows = data.size
    }

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
            level = resultSet.getString("level_string"),
            mdc = mdcKeys[eventId].orEmpty(),
            stacktrace = stackTraces[eventId].orEmpty(),
        )
    }

    private val maxRows: Int = rawData.size
    private val tableModel: LogExportModel = LogExportModel(rawData)
    private val table = LogExportTable(tableModel)

    private val details = DetailsPane()

    private val loading = FlatProgressBar().apply {
        isIndeterminate = true
        isVisible = false
    }

    private val header = Header(maxRows)

    private val sidebar = LoggerNamesPanel(rawData).apply {
        list.checkBoxListSelectionModel.apply {
            addListSelectionListener { e ->
                if (!e.valueIsAdjusting && !lockout) {
                    selectedIndices
                        .map { list.model.getElementAt(it) }
                        .filterIsInstance<LoggerName>()
                        .let { checkedLoggers ->
                            updateData { event ->
                                event.logger in checkedLoggers.mapTo(mutableSetOf()) { it.name }
                            }
                        }
                }
            }
        }
    }

    private var lockout: Boolean = false

    init {
        add(loading, "hmax 10, hidemode 0, spanx 2, wrap")
        add(header, "wrap, spanx 2")
        add(sidebar, "growy, pushy, width 20%")
        add(
            JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                FlatScrollPane(table),
                details,
            ).apply {
                resizeWeight = 0.6
            },
            "push, grow"
        )

        table.selectionModel.apply {
            addListSelectionListener { e ->
                if (!e.valueIsAdjusting) {
                    details.events = selectedIndices
                        .filter { isSelectedIndex(it) }
                        .map { table.convertRowIndexToModel(it) }
                        .map { row -> tableModel[row] }
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

        lockout = true
        sidebar.list.selectAll()
        lockout = false
    }

    companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS")
            .withZone(ZoneId.from(ZoneOffset.UTC))
    }
}
