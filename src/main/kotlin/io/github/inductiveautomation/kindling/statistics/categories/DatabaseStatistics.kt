package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup

class DatabaseStatistics(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name: String = "Databases"

    val totalConnections by queryStatistic("SELECT COUNT(*) FROM DATASOURCES") {
        getInt(1)
    }

    val totalEnabled by queryStatistic("SELECT COUNT(*) FROM DATASOURCES WHERE ENABLED = 1") {
        getInt(1)
    }

    val totalDisabled by queryStatistic("SELECT COUNT(*) FROM DATASOURCES WHERE ENABLED = 0") {
        getInt(1)
    }

    val totalDiskCacheEnabled by queryStatistic(
        """
            SELECT COUNT(*) FROM DATASOURCES d
            JOIN STOREANDFORWARDSYSSETTINGS s ON d.DATASOURCES_ID = s.STOREANDFORWARDSYSSETTINGS_ID
            WHERE ENABLEDISKSTORE = 1
        """.trimIndent(),
    ) {
        getInt(1)
    }

    val largestMemoryBuffer by queryStatistic("SELECT MAX(BUFFERSIZE) FROM STOREANDFORWARDSYSSETTINGS") {
        getInt(1)
    }

    val largestDiskCache by queryStatistic("SELECT MAX(STOREMAXRECORDS) FROM STOREANDFORWARDSYSSETTINGS") {
        getLong(1)
    }

    val totalMemoryBuffer by queryStatistic("SELECT SUM(BUFFERSIZE) FROM STOREANDFORWARDSYSSETTINGS") {
        getLong(1)
    }

    val totalDiskCache by queryStatistic(
        "SELECT SUM(STOREMAXRECORDS) FROM STOREANDFORWARDSYSSETTINGS WHERE ENABLEDISKSTORE = 1",
    ) {
        getLong(1)
    }
}
