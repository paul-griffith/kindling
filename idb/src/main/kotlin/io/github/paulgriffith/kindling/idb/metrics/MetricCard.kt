package io.github.paulgriffith.kindling.idb.metrics

import io.github.paulgriffith.kindling.idb.generic.QueryResult
import io.github.paulgriffith.kindling.utils.toList
import net.miginfocom.swing.MigLayout
import org.jfree.chart.JFreeChart
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.sql.Connection
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.roundToInt

class MetricCard(val metric: Metric, connection: Connection) : JPanel(MigLayout("ins 0, fill")) {

    private val data = connection
        .prepareStatement("SELECT VALUE, TIMESTAMP FROM SYSTEM_METRICS WHERE METRIC_NAME = '${metric.name}' ORDER BY TIMESTAMP ASC")
        .executeQuery()
        .toList { rs ->
            Pair(rs.getDouble(1), rs.getLong(2))
        }

    private val avg = data.map { it.first }.average()
    private val min = data.minOf { it.first }
    private val max = data.maxOf { it.first }

    init {
        add(JLabel(metric.name, SwingConstants.CENTER), "wrap")
        add(JLabel("Avg: $avg", SwingConstants.CENTER), "wrap")
        add(JLabel("Min: $min", SwingConstants.CENTER))
        add(JLabel("Max: $max", SwingConstants.CENTER), "wrap")
    }
}
