package io.github.paulgriffith.kindling.idb.metrics

import io.github.paulgriffith.kindling.utils.EDT_SCOPE
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.addAll
import io.github.paulgriffith.kindling.utils.toList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import java.sql.Connection
import javax.swing.JPanel

class MetricsView(connection: Connection) : JPanel(MigLayout("ins 0, fill, hidemode 3")) {

    private val metrics: List<Metric> = connection.prepareStatement(
        """
            SELECT DISTINCT METRIC_NAME FROM SYSTEM_METRICS
            ORDER BY METRIC_NAME
        """.trimIndent(),
    ).executeQuery().toList { rs -> Metric(rs.getString(1)) }

    private val metricCards: List<MetricCard> = metrics.map { MetricCard(it, connection) }

    private val metricTree: MetricTree = MetricTree(metrics)

    private val cardPanel = JPanel(MigLayout("wrap 3, fillx"))

    private fun updateData() {
        CoroutineScope(Dispatchers.Default).launch {
            val selectedMetricNames = metricTree.selectedLeafNodes.map { it.metricName }
            EDT_SCOPE.launch {
                cardPanel.removeAll()
                cardPanel.addAll(
                    metricCards.filter { card ->
                        card.metric.name in selectedMetricNames
                    },
                    "pushx, growx",
                )
                cardPanel.revalidate()
                repaint()
            }
        }
    }

    init {
        cardPanel.addAll(metricCards, "pushx, growx")
        add(FlatScrollPane(metricTree), "grow, w 200::20%")
        add(FlatScrollPane(cardPanel), "push, grow, span")

        metricTree.checkBoxTreeSelectionModel.addTreeSelectionListener { updateData() }
    }
}