package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup

@Suppress("unused", "MemberVisibilityCanBePrivate")
class OpcServerStatistics(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name = "OpcServers"

    val totalServers by statistic {
        uaServers.getValue() + daServers.getValue()
    }

    val uaServers by queryScalarStatistic<Int>(
        "SELECT COUNT(*) FROM OPCSERVERS WHERE TYPE = 'com.inductiveautomation.OpcUaServerType'"
    )

    val daServers by queryScalarStatistic<Int>("SELECT COUNT(*) FROM OPCSERVERS WHERE TYPE = 'OPC_COM_ServerType'")

    val enabledServers by queryScalarStatistic<Int>(
        """
            SELECT SUM(total) FROM (
                SELECT COUNT(*) AS total FROM OPCUACONNECTIONSETTINGS WHERE ENABLED = 1
                UNION
                SELECT COUNT(*) AS total FROM COMSERVERSETTINGSRECORD WHERE ENABLED = 1
            )
        """.trimIndent()
    )

    val disabledServers by queryScalarStatistic<Int>(
        """
            SELECT SUM(total) FROM (
                SELECT COUNT(*) AS total FROM OPCUACONNECTIONSETTINGS WHERE ENABLED = 0
                UNION
                SELECT COUNT(*) AS total FROM COMSERVERSETTINGSRECORD WHERE ENABLED = 0
            )
        """.trimIndent()
    )
}