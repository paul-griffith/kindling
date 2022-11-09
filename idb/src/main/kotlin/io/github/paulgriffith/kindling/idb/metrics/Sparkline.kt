package io.github.paulgriffith.kindling.idb.metrics

import org.jfree.chart.JFreeChart
import org.jfree.chart.axis.DateAxis
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.StandardXYItemRenderer
import org.jfree.chart.ui.RectangleInsets
import org.jfree.data.time.Millisecond
import org.jfree.data.time.TimeSeries
import org.jfree.data.time.TimeSeriesCollection
import java.util.Date
import javax.swing.UIManager

class Sparkline(
    private val data: List<Pair<Double, Long>>,
    private val minimalPlot: XYPlot = XYPlot().apply {
        val series = TimeSeries("Series").apply {
            data.forEach {
                add(Millisecond(Date(it.second)), it.first)
            }
        }
        dataset = TimeSeriesCollection(series)
        domainAxis = DateAxis().apply {
            isTickLabelsVisible = true
            isTickMarksVisible = true
            isAxisLineVisible = true
            isNegativeArrowVisible = false
            isPositiveArrowVisible = true
            isVisible = true
        }
        rangeAxis = NumberAxis().apply {
            isTickLabelsVisible = true
            isTickMarksVisible = true
            isAxisLineVisible = true
            isNegativeArrowVisible = false
            isPositiveArrowVisible = true
            isVisible = true
        }
        isDomainCrosshairVisible = false
        isDomainGridlinesVisible = false
        isRangeCrosshairVisible = false
        isRangeGridlinesVisible = false
        isOutlineVisible = false
        renderer = StandardXYItemRenderer(StandardXYItemRenderer.LINES)
    },
) : JFreeChart(minimalPlot) {

    private fun setTheme() {
        with(minimalPlot) {
            backgroundPaint = UIManager.getColor("Panel.background")
            domainAxis.tickLabelPaint = UIManager.getColor("ColorChooser.foreground")
            rangeAxis.tickLabelPaint = UIManager.getColor("ColorChooser.foreground")
        }
        backgroundPaint = UIManager.getColor("Panel.background")
    }

    init {
        padding = RectangleInsets(10.0, 10.0, 10.0, 10.0)
        isBorderVisible = false
        removeLegend()
        setTheme()

        UIManager.addPropertyChangeListener { e ->
            if (e.propertyName == "lookAndFeel") {
                setTheme()
            }
        }
    }
}

