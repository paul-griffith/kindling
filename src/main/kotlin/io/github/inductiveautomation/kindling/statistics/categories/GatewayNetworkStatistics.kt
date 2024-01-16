package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup

@Suppress("unused")
class GatewayNetworkStatistics(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name = "GatewayNetwork"

    val totalConnections by statistic {
        outGoingConnections.getValue() + incomingConnections.getValue()
    }

    val outGoingConnections by queryScalarStatistic<Int>("SELECT COUNT(*) FROM WSCONNECTIONSETTINGS")

    val incomingConnections by queryScalarStatistic<Int>("SELECT COUNT(*) FROM WSINCOMINGCONNECTION")

    val enabled by queryScalarStatistic<Boolean>("SELECT ENABLED FROM WSCHANNELSETTINGS")

    val proxyHopsEnabled by queryScalarStatistic<Boolean>("SELECT ALLOWEDPROXYHOPS FROM WSCHANNELSETTINGS")
}