package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup

@Suppress("unused")
class DatabaseStatistics(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name: String = "Databases"

    val totalConnections by queryScalarStatistic<Int>("SELECT COUNT(*) FROM DATASOURCES")

    val totalEnabled by queryScalarStatistic<Int>("SELECT COUNT(*) FROM DATASOURCES WHERE ENABLED = 1")

    val totalDisabled by queryScalarStatistic<Int>("SELECT COUNT(*) FROM DATASOURCES WHERE ENABLED = 0")

    val totalDiskCacheEnabled by queryScalarStatistic<Int>(
        """
            SELECT COUNT(*) FROM DATASOURCES d
            JOIN STOREANDFORWARDSYSSETTINGS s ON d.DATASOURCES_ID = s.STOREANDFORWARDSYSSETTINGS_ID
            WHERE ENABLEDISKSTORE = 1
        """.trimIndent(),
    )

    val largestMemoryBuffer by queryScalarStatistic<Int>(
        "SELECT MAX(BUFFERSIZE) FROM STOREANDFORWARDSYSSETTINGS"
    )

    val largestDiskCache by queryScalarStatistic<Long>(
        "SELECT MAX(STOREMAXRECORDS) FROM STOREANDFORWARDSYSSETTINGS"
    )

    val totalMemoryBuffer by queryScalarStatistic<Long>(
        "SELECT SUM(BUFFERSIZE) FROM STOREANDFORWARDSYSSETTINGS"
    )

    val totalDiskCache by queryScalarStatistic<Long>(
        "SELECT SUM(STOREMAXRECORDS) FROM STOREANDFORWARDSYSSETTINGS WHERE ENABLEDISKSTORE = 1",
    )
}
