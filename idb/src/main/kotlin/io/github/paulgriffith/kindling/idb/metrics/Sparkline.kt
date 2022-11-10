package io.github.paulgriffith.kindling.idb.metrics

import org.jfree.chart.ChartFactory
import org.jfree.chart.JFreeChart
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.ui.RectangleInsets
import org.jfree.data.time.Millisecond
import org.jfree.data.time.TimeSeries
import org.jfree.data.time.TimeSeriesCollection
import java.text.NumberFormat
import javax.swing.UIManager

fun sparkline(data: List<MetricData>, formatter: NumberFormat): JFreeChart {
    return ChartFactory.createTimeSeriesChart(
        /* title = */ null,
        /* timeAxisLabel = */ null,
        /* valueAxisLabel = */ null,
        /* dataset = */
        TimeSeriesCollection(
            TimeSeries("Series").apply {
                for ((value, timestamp) in data) {
                    add(Millisecond(timestamp), value, false)
                }
            },
        ),
        /* legend = */ false,
        /* tooltips = */ false,
        /* urls = */ false,
    ).apply {
        xyPlot.apply {
            domainAxis.isPositiveArrowVisible = true
            rangeAxis.apply {
                isPositiveArrowVisible = true
                (this as NumberAxis).numberFormatOverride = formatter
            }
            isDomainGridlinesVisible = false
            isRangeGridlinesVisible = false
            isOutlineVisible = false
        }

        padding = RectangleInsets(10.0, 10.0, 10.0, 10.0)
        isBorderVisible = false

        applyTheme()

        UIManager.addPropertyChangeListener { e ->
            if (e.propertyName == "lookAndFeel") {
                applyTheme()
            }
        }
    }
}

private fun JFreeChart.applyTheme() {
    xyPlot.apply {
        backgroundPaint = UIManager.getColor("Panel.background")
        domainAxis.tickLabelPaint = UIManager.getColor("ColorChooser.foreground")
        rangeAxis.tickLabelPaint = UIManager.getColor("ColorChooser.foreground")
    }
    backgroundPaint = UIManager.getColor("Panel.background")
}
