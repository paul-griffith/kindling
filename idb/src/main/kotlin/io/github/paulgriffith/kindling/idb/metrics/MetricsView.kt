package io.github.paulgriffith.kindling.idb.metrics

import com.jidesoft.swing.CheckBoxTree
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
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

class MetricsView(connection: Connection) : IdbPanel() {

    private val metrics: List<Metric> = connection.prepareStatement(
        """
            SELECT DISTINCT METRIC_NAME FROM SYSTEM_METRICS
            ORDER BY METRIC_NAME
            """.trimIndent(),
    ).executeQuery().toList { rs -> Metric(rs.getString(1)) }

    // Create a tree to hold all metric choices

    private val cardPanel = JPanel(MigLayout("fill, wrap 3"))


    init {
        val m = Metric("gateway.performance.cpu")
        val root = DefaultMutableTreeNode("ROOT").apply {
            add(
                DefaultMutableTreeNode("Modern").apply {
                    add(
                        DefaultMutableTreeNode("ignition").apply {
                            add(
                                DefaultMutableTreeNode("performance").apply {
                                    add(DefaultMutableTreeNode("cpu"))
                                },
                            )
                        },
                    )
                },
            )
            add(
                DefaultMutableTreeNode("Legacy").apply {
                    add(
                        DefaultMutableTreeNode("PerformanceMonitor"),
                    )
                },
            )
        }
        val tree = CheckBoxTree(root).apply {
            isRootVisible = false
        }

        cardPanel.addAll(metrics.map { MetricCard(it, connection) })
        add(FlatScrollPane(tree), "grow, w 200!")
        add(FlatScrollPane(cardPanel), "push, grow")

    }
}