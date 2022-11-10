package io.github.paulgriffith.kindling.idb.metrics

import io.github.paulgriffith.kindling.utils.EDT_SCOPE
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.toList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import java.sql.Connection
import javax.swing.JPanel

class MetricsView(connection: Connection) : JPanel(MigLayout("ins 0, fill, hidemode 3")) {
    private val metrics: List<Metric> = connection.prepareStatement(
        //language=sql
        """
        SELECT DISTINCT
            METRIC_NAME
        FROM SYSTEM_METRICS
        """,
    ).executeQuery().toList { rs ->
        Metric(rs.getString(1))
    }

    private val metricDataQuery = connection.prepareStatement(
        //language=sql
        """
        SELECT 
            VALUE,
            TIMESTAMP 
        FROM SYSTEM_METRICS
        WHERE METRIC_NAME = ?
        ORDER BY TIMESTAMP ASC
        """,
    )

    private val metricCards: List<MetricCard> = metrics.map { metric ->
        val metricData = metricDataQuery.apply {
            setString(1, metric.name)
        }
            .executeQuery()
            .toList { rs ->
                MetricData(rs.getDouble(1), rs.getDate(2))
            }

        MetricCard(metric, metricData)
    }

    private val metricTree = MetricTree(metrics)

    private val cardPanel = JPanel(MigLayout("wrap 3, fillx, hidemode 3"))

    private fun updateData() {
        CoroutineScope(Dispatchers.Default).launch {
            val selectedMetricNames = metricTree.selectedLeafNodes.map { it.userObject }
            EDT_SCOPE.launch {
                for (card in metricCards) {
                    card.isVisible = card.metric.name in selectedMetricNames
                }
            }
        }
    }

    init {
        for (card in metricCards) {
            cardPanel.add(card, "pushx, growx")
        }

        add(FlatScrollPane(metricTree), "grow, w 200::20%")
        add(FlatScrollPane(cardPanel), "push, grow, span")

        metricTree.checkBoxTreeSelectionModel.addTreeSelectionListener { updateData() }
    }
}
