package io.github.paulgriffith.kindling.idb.metrics

import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.border.DropShadowBorder
import org.jfree.chart.ChartPanel
import java.awt.Font
import java.text.DecimalFormat
import java.text.FieldPosition
import java.text.NumberFormat
import java.text.SimpleDateFormat
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants.CENTER

class MetricCard(val metric: Metric, data: List<MetricData>) : JPanel(MigLayout("fill, ins 10")) {
    companion object {
        private val heapFormatter = object : DecimalFormat("0 'MB'") {
            override fun format(number: Double, toAppendTo: StringBuffer?, pos: FieldPosition?): StringBuffer {
                return super.format(number / 1_000_000, toAppendTo, pos)
            }
        }
        private val queueFormatter = NumberFormat.getIntegerInstance()
        private val throughputFormatter = DecimalFormat("0.00 'dp/s'")
        private val cpuFormatter = NumberFormat.getPercentInstance()

        private val Metric.formatter: NumberFormat
            get() = when {
                name.contains("heap", true) -> heapFormatter
                name.contains("queue", true) -> queueFormatter
                name.contains("throughput", true) -> throughputFormatter
                name.contains("cpu", true) -> cpuFormatter
                else -> NumberFormat.getInstance()
            }
    }

    private val sparkLine = ChartPanel(
        /* chart = */ sparkline(data, metric.formatter),
        /* properties = */ false,
        /* save = */ false,
        /* print = */ false,
        /* zoom = */ false,
        /* tooltips = */ false,
    )

    init {
        val dateFormat = SimpleDateFormat("MM/dd/yy HH:mm:ss")
        val minTimestamp = dateFormat.format(data.first().timestamp)
        val maxTimestamp = dateFormat.format(data.last().timestamp)

        add(
            JLabel(metric.name, CENTER).apply {
                font = font.deriveFont(Font.BOLD, 14.0F)
            },
            "span, pushx, growx",
        )

        val aggregateData = DoubleArray(data.size) { i -> data[i].value }
        add(
            JLabel(
                "Min: ${metric.formatter.format(aggregateData.min())}",
                CENTER,
            ),
            "pushx, growx",
        )
        add(
            JLabel(
                "Avg: ${metric.formatter.format(aggregateData.average())}",
                CENTER,
            ),
            "pushx, growx",
        )
        add(
            JLabel(
                "Max: ${metric.formatter.format(aggregateData.max())}",
                CENTER,
            ),
            "pushx, growx, wrap",
        )

        add(sparkLine, "span, w 300, h 170, pushx, growx")
        add(JLabel("$minTimestamp - $maxTimestamp", CENTER), "pushx, growx, span")

        border = DropShadowBorder().apply {
            isShowRightShadow = true
            isShowBottomShadow = true
            shadowSize = 10
        }
    }
}
