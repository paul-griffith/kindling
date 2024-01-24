package io.github.inductiveautomation.kindling.zip.views.gwbk

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.Kindling.SECONDARY_ACTION_ICON_SCALE
import io.github.inductiveautomation.kindling.statistics.categories.OpcServerStatistics
import io.github.inductiveautomation.kindling.utils.ColumnList
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.ReifiedJXTable
import io.github.inductiveautomation.kindling.utils.ReifiedLabelProvider.Companion.setDefaultRenderer
import io.github.inductiveautomation.kindling.utils.ReifiedListTableModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SortOrder

class OpcConnectionsStatisticsRenderer : StatisticRenderer<OpcServerStatistics> {
    override val title: String = "OPC Server Connections"
    override val icon: Icon = FlatSVGIcon("icons/bx-purchase-tag.svg").derive(SECONDARY_ACTION_ICON_SCALE)

    override fun OpcServerStatistics.subtitle(): String {
        return "$uaServers UA, $comServers COM"
    }

    override fun OpcServerStatistics.render(): JComponent {
        return FlatScrollPane(
            ReifiedJXTable(ReifiedListTableModel(servers, GanColumns)).apply {
                setDefaultRenderer<OpcServerStatistics.OpcServer>(
                    getText = { it?.name },
                    getTooltip = { it?.description ?: it?.name },
                )
                setSortOrder(Name, SortOrder.ASCENDING)
            },
        )
    }

    companion object GanColumns : ColumnList<OpcServerStatistics.OpcServer>() {
        val Name by column { it }
        val Type by column {
            when (it.type) {
                OpcServerStatistics.UA_SERVER_TYPE -> "OPC UA"
                OpcServerStatistics.COM_SERVER_TYPE -> "OPC COM"
                else -> it.type
            }
        }
        val Enabled by column(value = OpcServerStatistics.OpcServer::enabled)
    }
}
