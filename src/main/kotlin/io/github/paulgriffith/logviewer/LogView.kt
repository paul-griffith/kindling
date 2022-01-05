package io.github.paulgriffith.logviewer

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatProgressBar
import io.github.paulgriffith.utils.DetailsPane
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.ToolPanel
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.swing.Icon
import javax.swing.JSplitPane
import io.github.paulgriffith.utils.Detail as DetailEvent

class LogView(override val path: Path) : ToolPanel() {
    private val connection = SQLiteDataSource().apply {
        url = "jdbc:sqlite:file:$path"
        setReadOnly(true)
    }.connection

    private fun updateData(filter: (Event) -> Boolean) {
        val data = rawData.filter(filter)

        tableModel.data = data
        header.displayedRows = data.size
    }

    private val stackTraces: Map<Int, List<String>> = connection.prepareStatement(
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
    ).executeQuery().use { resultSet ->
        sequence {
            while (resultSet.next()) {
                yield(
                    Pair(
                        resultSet.getInt("event_id"),
                        resultSet.getString("trace_line")
                    )
                )
            }
        }.groupBy(keySelector = { it.first }, valueTransform = { it.second })
    }

    private val mdcKeys: Map<Int, Map<String, String>> = connection.prepareStatement(
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
    ).executeQuery().use { resultSet ->
        sequence {
            while (resultSet.next()) {
                yield(
                    Triple(
                        resultSet.getInt("event_id"),
                        resultSet.getString("mapped_key"),
                        resultSet.getString("mapped_value"),
                    )
                )
            }
        }.groupingBy { it.first }
            // TODO I bet this can be improved
            .aggregateTo(mutableMapOf<Int, MutableMap<String, String>>()) { _, accumulator, element, _ ->
                val acc = accumulator ?: mutableMapOf()
                acc[element.second] = element.third
                acc
            }
    }

    // Run an initial query (blocking) so if this isn't a log export we bail out
    // This is unfortunate (it can be a little slow) but better UX overall
    private val rawData: List<Event> = connection.prepareStatement(
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
    ).executeQuery().use { resultSet ->
        buildList {
            while (resultSet.next()) {
                val eventId = resultSet.getInt("event_id")
                this.add(
                    Event(
                        eventId = eventId,
                        timestamp = Instant.ofEpochMilli(resultSet.getLong("timestmp")),
                        message = resultSet.getString("formatted_message"),
                        logger = resultSet.getString("logger_name"),
                        thread = resultSet.getString("thread_name"),
                        level = resultSet.getString("level_string"),
                        mdc = this@LogView.mdcKeys[eventId].orEmpty(),
                        stacktrace = this@LogView.stackTraces[eventId].orEmpty(),
                    )
                )
            }
        }
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
        connection.close() // all data is held locally in memory, close the connection so we don't lock the file
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

    override val icon: Icon = FlatSVGIcon("icons/bx-hdd.svg")

    companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss:SSS")
            .withZone(ZoneId.from(ZoneOffset.UTC))
    }
}
