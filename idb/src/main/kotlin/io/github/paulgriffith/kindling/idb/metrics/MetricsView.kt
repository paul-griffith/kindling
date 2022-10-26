package io.github.paulgriffith.kindling.idb.metrics

import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.idb.IdbPanel
import io.github.paulgriffith.kindling.idb.IdbViewer
import io.github.paulgriffith.kindling.utils.EDT_SCOPE
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.ReifiedListModel
import io.github.paulgriffith.kindling.utils.addAll
import io.github.paulgriffith.kindling.utils.toList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.sql.Connection
import java.sql.ResultSet
import java.util.*
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableModel

class MetricsView(connection: Connection) : IdbPanel() {

    private val metricCards: List<MetricCard> = connection.prepareStatement(
            """
            SELECT DISTINCT METRIC_NAME FROM SYSTEM_METRICS
            ORDER BY METRIC_NAME
            """.trimIndent()).executeQuery().toList { rs -> MetricCard(rs.getString(1), connection) }

    private val nameModel = ReifiedListModel(metricCards.map { it.metricName })
    private val nameList = JList(nameModel).apply {
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    }

    private val cardPanel = JPanel(MigLayout("fill, wrap 3"))


    init {
        cardPanel.addAll(metricCards)
        add(
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                FlatScrollPane(nameList),
                cardPanel
            ).apply {
                resizeWeight = 0.5
            },
            "push, grow"
        )

        nameList.addListSelectionListener {event ->
            if (!event.valueIsAdjusting) {
                updateData()
            }
        }
    }

    private fun updateData() {
        CoroutineScope(Dispatchers.Default).launch {
            val cards = metricCards.filter {
                it.metricName in nameList.selectedValuesList
            }
            EDT_SCOPE.launch {
                cardPanel.removeAll()
                cardPanel.addAll(cards)
                this@MetricsView.revalidate()
                this@MetricsView.repaint()
            }
        }
    }

}