package io.github.paulgriffith.kindling.idb.metrics

import io.github.paulgriffith.kindling.utils.toList
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXPanel
import org.jdesktop.swingx.border.DropShadowBorder
import org.jfree.chart.ChartPanel
import java.awt.Font
import java.sql.Connection
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JLabel
import javax.swing.SwingConstants

class MetricCard(val metric: Metric, connection: Connection) : JXPanel(MigLayout("fill, ins 10")) {

    private val data = connection
        .prepareStatement("SELECT VALUE, TIMESTAMP FROM SYSTEM_METRICS WHERE METRIC_NAME = '${metric.name}' ORDER BY TIMESTAMP ASC")
        .executeQuery()
        .toList { rs ->
            Pair(rs.getDouble(1), rs.getLong(2))
        }

    private val formatter: (Double) -> String = metric.name.lowercase().let {
        when {
            "heap" in it -> { value -> DecimalFormat("0.00").format(value / 1000000.0) + "MB" }
            "queue" in it -> { value -> NumberFormat.getInstance().format(value.toInt()) }
            "throughput" in it -> { value -> DecimalFormat("0.00").format(value) + " dp/s" }
            "cpu" in it -> { value -> DecimalFormat("0.00'%'").format(value) }
            else -> { value -> "$value" }
        }
    }


    private val sparkLine = ChartPanel(Sparkline(data))

    init {
        val avg = data.map { it.first }.average()
        val min = data.minOf { it.first }
        val max = data.maxOf { it.first }
        val dateFormat = SimpleDateFormat("MM/dd/yy HH:mm:ss")
        val minTimestamp = dateFormat.format(Date(data.minOf { it.second }))
        val maxTimestamp = dateFormat.format(Date(data.maxOf { it.second }))

        add(
            JLabel(metric.name, JLabel.CENTER).apply {
                font = font.deriveFont(Font.BOLD, 14.0F)
            },
            "span, pushx, growx",
        )
        add(JLabel("Min: ${formatter(min)}", SwingConstants.CENTER), "pushx, growx")
        add(JLabel("Avg: ${formatter(avg)}", SwingConstants.CENTER), "pushx, growx")
        add(JLabel("Max: ${formatter(max)}", SwingConstants.CENTER), "pushx, growx, wrap")
        add(sparkLine, "span, w 300, h 170, pushx, growx")
        add(JLabel("$minTimestamp - $maxTimestamp", SwingConstants.CENTER), "pushx, growx, span")

        border = DropShadowBorder().apply {
            isShowRightShadow = true
            isShowBottomShadow = true
            shadowSize = 10
        }
    }
}
