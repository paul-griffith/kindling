package io.github.paulgriffith.idb

import io.github.paulgriffith.idb.generic.GenericView
import io.github.paulgriffith.log.Event
import io.github.paulgriffith.log.LogPanel
import io.github.paulgriffith.utils.toList
import java.sql.Connection
import java.time.Instant

enum class IdbTool {
    Log {
        override fun openPanel(connection: Connection): IdbPanel {
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
                Event(
                    timestamp = Instant.ofEpochMilli(resultSet.getLong("timestmp")),
                    message = resultSet.getString("formatted_message"),
                    logger = resultSet.getString("logger_name"),
                    thread = resultSet.getString("thread_name"),
                    level = Event.Level.valueOf(resultSet.getString("level_string")),
                    mdc = mdcKeys[eventId].orEmpty(),
                    stacktrace = stackTraces[eventId].orEmpty(),
                )
            }
            return LogPanel(events)
        }
    },
    Generic {
        override fun openPanel(connection: Connection): IdbPanel = GenericView(connection)
    };

    abstract fun openPanel(connection: Connection): IdbPanel
}
