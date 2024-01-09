package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup

class OpcServerStatistics(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name = "OpcServers"

    val totalServers by statistic {
        uaServers.getValue() + daServers.getValue()
    }

    val uaServers by queryStatistic(
        "SELECT COUNT(*) FROM OPCSERVERS WHERE TYPE = 'com.inductiveautomation.OpcUaServerType'"
    ) {
        getInt(1)
    }

    val daServers by queryStatistic("SELECT COUNT(*) FROM OPCSERVERS WHERE TYPE = 'OPC_COM_ServerType'") {
        getInt(1)
    }

    val enabledServers by queryStatistic(
        """
            SELECT SUM(total) FROM (
                SELECT COUNT(*) AS total FROM OPCUACONNECTIONSETTINGS WHERE ENABLED = 1
                UNION
                SELECT COUNT(*) AS total FROM COMSERVERSETTINGSRECORD WHERE ENABLED = 1
            )
        """.trimIndent()
    ) {
        getInt(1)
    }

    val disabledServers by queryStatistic(
        """
            SELECT SUM(total) FROM (
                SELECT COUNT(*) AS total FROM OPCUACONNECTIONSETTINGS WHERE ENABLED = 0
                UNION
                SELECT COUNT(*) AS total FROM COMSERVERSETTINGSRECORD WHERE ENABLED = 0
            )
        """.trimIndent()
    ) {
        getInt(1)
    }
}