package io.github.paulgriffith.kindling.idb

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.idb.generic.GenericView
import io.github.paulgriffith.kindling.idb.metrics.MetricsView
import io.github.paulgriffith.kindling.log.Level
import io.github.paulgriffith.kindling.log.LogPanel
import io.github.paulgriffith.kindling.log.SystemLogsEvent
import io.github.paulgriffith.kindling.utils.toList
import java.nio.file.Path
import java.sql.Connection
import java.time.Instant
import javax.swing.JLabel
import javax.swing.JPanel

enum class IdbTool {
    Log {
        override fun openPanel(connection: Connection): JPanel {
            val stackTraces: Map<Int, List<String>> = connection.prepareStatement(
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

            val mdcKeys: Map<Int, Map<String, String>> = connection.prepareStatement(
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
                        resultSet.getString("mapped_value")
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
            val events = connection.prepareStatement(
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
                SystemLogsEvent(
                    timestamp = Instant.ofEpochMilli(resultSet.getLong("timestmp")),
                    message = resultSet.getString("formatted_message"),
                    logger = resultSet.getString("logger_name"),
                    thread = resultSet.getString("thread_name"),
                    level = Level.valueOf(resultSet.getString("level_string")),
                    mdc = mdcKeys[eventId].orEmpty(),
                    stacktrace = stackTraces[eventId].orEmpty()
                )
            }
            return LogPanel(events)
        }
    },
    Generic {
        override fun openPanel(connection: Connection): JPanel = GenericView(connection)
    },
    Metrics {
        override fun openPanel(connection: Connection): JPanel = MetricsView(connection)

    };

    abstract fun openPanel(connection: Connection): JPanel
}

object IdbViewer : Tool {
    override val title = "Idb File"
    override val description = ".idb (SQLite3) files"
    override val icon = FlatSVGIcon("icons/bx-hdd.svg")
    override val extensions = listOf("idb")
    override fun open(path: Path): ToolPanel = IdbView(path)
}

class IdbViewerProxy : Tool by IdbViewer // https://youtrack.jetbrains.com/issue/KT-25892
