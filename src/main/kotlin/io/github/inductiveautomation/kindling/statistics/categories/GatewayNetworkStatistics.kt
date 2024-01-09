package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup

class GatewayNetworkStatistics(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name = "GatewayNetwork"

    val totalConnections by statistic {
        outGoingConnections.getValue() + incomingConnections.getValue()
    }

    val outGoingConnections by queryStatistic("SELECT COUNT(*) FROM WSCONNECTIONSETTINGS") {
        getInt(1)
    }

    val incomingConnections by queryStatistic("SELECT COUNT(*) FROM WSINCOMINGCONNECTION") {
        getInt(1)
    }

    val enabled by queryStatistic("SELECT ENABLED FROM WSCHANNELSETTINGS") {
        getBoolean(1)
    }

    val proxyHopsEnabled by queryStatistic("SELECT ALLOWEDPROXYHOPS FROM WSCHANNELSETTINGS") {
        getBoolean(1)
    }
}