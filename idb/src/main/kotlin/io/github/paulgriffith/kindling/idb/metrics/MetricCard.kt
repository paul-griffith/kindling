package io.github.paulgriffith.kindling.idb.metrics

import io.github.paulgriffith.kindling.idb.generic.QueryResult
import io.github.paulgriffith.kindling.utils.toList
import net.miginfocom.swing.MigLayout
import java.sql.Connection
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class MetricCard(val metric: Metric, connection: Connection) : JPanel(MigLayout("fill")) {

    private val data = connection
        .prepareStatement("SELECT VALUE, TIMESTAMP FROM SYSTEM_METRICS WHERE METRIC_NAME = '${metric.name}'")
        .executeQuery()
        .toList { rs ->
            Pair(rs.getDouble(1), rs.getLong(2))
        }

    private val avg = data.map { it.first }.average()
    private val min = data.minOf { it.first }
    private val max = data.maxOf { it.first }

    init {
        add(JLabel(metric.name, SwingConstants.CENTER), "span")
        add(JLabel("Avg: $avg", SwingConstants.CENTER), "wrap")
        add(JLabel("Min: $min", SwingConstants.CENTER))
        add(JLabel("Max: $max", SwingConstants.CENTER), "wrap")
        add(JLabel("Sparkline", SwingConstants.CENTER), "span")
    }
}