package io.github.paulgriffith.kindling.idb.metrics

import io.github.paulgriffith.kindling.idb.metrics.MetricCard.Companion.MetricPresentation.Cpu
import io.github.paulgriffith.kindling.idb.metrics.MetricCard.Companion.MetricPresentation.Default
import io.github.paulgriffith.kindling.idb.metrics.MetricCard.Companion.MetricPresentation.Heap
import io.github.paulgriffith.kindling.idb.metrics.MetricCard.Companion.MetricPresentation.Queue
import io.github.paulgriffith.kindling.idb.metrics.MetricCard.Companion.MetricPresentation.Throughput
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.border.DropShadowBorder
import org.jfree.chart.ChartPanel
import org.jfree.chart.annotations.XYLineAnnotation
import org.jfree.data.statistics.Regression
import org.jfree.data.xy.XYDataset
import java.awt.BasicStroke
import java.awt.Font
import java.text.DecimalFormat
import java.text.FieldPosition
import java.text.NumberFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants.CENTER
import javax.swing.UIManager

class MetricCard(val metric: Metric, data: List<MetricData>) : JPanel(MigLayout("fill, ins 10")) {
    private val presentation = metric.presentation

    private val sparkLine = ChartPanel(
        /* chart = */ sparkline(data, presentation.formatter),
        /* properties = */ false,
        /* save = */ false,
        /* print = */ false,
        /* zoom = */ true,
        /* tooltips = */ true,
    )

    init {
        add(
            JLabel(metric.name, CENTER).apply {
                font = font.deriveFont(Font.BOLD, 14.0F)
            },
            "span, pushx, growx",
        )

        val aggregateData = DoubleArray(data.size) { i -> data[i].value }
        add(JLabel("Min: ${presentation.formatter.format(aggregateData.min())}", CENTER), "pushx, growx")
        add(JLabel("Avg: ${presentation.formatter.format(aggregateData.average())}", CENTER), "pushx, growx")
        add(JLabel("Max: ${presentation.formatter.format(aggregateData.max())}", CENTER), "pushx, growx, wrap")

        val minTimestamp = data.first().timestamp
        val maxTimestamp = data.last().timestamp

        if (presentation.isShowTrend) {
            val regression = regressionFunction(sparkLine.chart.xyPlot.dataset, 0)
            val minTimeDouble = minTimestamp.time.toDouble()
            val maxTimeDouble = maxTimestamp.time.toDouble()

            sparkLine.chart.xyPlot.addAnnotation(
                XYLineAnnotation(
                    minTimeDouble,
                    regression(minTimeDouble),
                    maxTimeDouble,
                    regression(maxTimeDouble),
                    BasicStroke(1.0f),
                    UIManager.getColor("Actions.Yellow"),
                ),
            )
        }

        add(sparkLine, "span, w 300, h 170, pushx, growx")
        add(JLabel("${DATE_FORMAT.format(minTimestamp)} - ${DATE_FORMAT.format(maxTimestamp)}", CENTER), "pushx, growx, span")

        border = DropShadowBorder().apply {
            isShowRightShadow = true
            isShowBottomShadow = true
            shadowSize = 10
        }
    }

    companion object {
        val DATE_FORMAT = SimpleDateFormat("MM/dd/yy HH:mm:ss")

        private val mbFormatter = DecimalFormat("0.0 'mB'")
        private val heapFormatter = object : NumberFormat() {
            override fun format(number: Double, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer {
                return mbFormatter.format(number / 1_000_000, toAppendTo, pos)
            }

            override fun format(number: Long, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer = mbFormatter.format(number, toAppendTo, pos)
            override fun parse(source: String, parsePosition: ParsePosition): Number = mbFormatter.parse(source, parsePosition)
        }

        enum class MetricPresentation(val formatter: NumberFormat, val isShowTrend: Boolean) {
            Heap(heapFormatter, true),
            Queue(NumberFormat.getIntegerInstance(), false),
            Throughput(DecimalFormat("0.00 'dp/s'"), true),
            Cpu(
                DecimalFormat("0.00%").apply {
                    multiplier = 1
                },
                true,
            ),
            Default(NumberFormat.getInstance(), false);
        }

        private val Metric.presentation: MetricPresentation
            get() = when {
                name.contains("heap", true) -> Heap
                name.contains("queue", true) -> Queue
                name.contains("throughput", true) -> Throughput
                name.contains("cpu", true) -> Cpu
                else -> Default
            }

        fun regressionFunction(dataset: XYDataset, series: Int): (Double) -> Double {
            val (a, b) = Regression.getOLSRegression(dataset, series)
            return { x ->
                a + b * x
            }
        }
    }
}
