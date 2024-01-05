package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.StatisticCategory

class DatabaseStatistics(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name: String = "Databases"

    val totalConnections by statistic {
        val query = it.configIDB.prepareStatement("SELECT COUNT(*) FROM DATASOURCES")
        query.executeQuery().getInt(1)
    }
    val totalEnabled by statistic {
        val query = it.configIDB.prepareStatement(
            "SELECT COUNT(*) FROM DATASOURCES WHERE ENABLED = 1",
        )
        query.executeQuery().getInt(1)
    }
    val totalDisabled by statistic {
        val query = it.configIDB.prepareStatement(
            "SELECT COUNT(*) FROM DATASOURCES WHERE ENABLED = 0",
        )
        query.executeQuery().getInt(1)
    }
    val totalDiskCacheEnabled by statistic {
        val query = it.configIDB.prepareStatement(
            """
                SELECT COUNT(*) FROM DATASOURCES d
                JOIN STOREANDFORWARDSYSSETTINGS s ON d.DATASOURCES_ID = s.STOREANDFORWARDSYSSETTINGS_ID
                WHERE ENABLEDISKSTORE = 1
            """.trimIndent(),
        )
        query.executeQuery().getInt(1)
    }
    val largestMemoryBuffer by statistic {
        val query = it.configIDB.prepareStatement(
            "SELECT MAX(BUFFERSIZE) FROM STOREANDFORWARDSYSSETTINGS",
        )
        query.executeQuery().getInt(1)
    }
    val largestDiskCache by statistic {
        val query = it.configIDB.prepareStatement(
            "SELECT MAX(STOREMAXRECORDS) FROM STOREANDFORWARDSYSSETTINGS",
        )
        query.executeQuery().getLong(1)
    }
    val totalMemoryBuffer by statistic {
        val query = it.configIDB.prepareStatement(
            "SELECT SUM(BUFFERSIZE) FROM STOREANDFORWARDSYSSETTINGS",
        )
        query.executeQuery().getLong(1)
    }
    val totalDiskCache by statistic {
        val query = it.configIDB.prepareStatement(
            "SELECT SUM(STOREMAXRECORDS) FROM STOREANDFORWARDSYSSETTINGS WHERE ENABLEDISKSTORE = 1",
        )
        query.executeQuery().getLong(1)
    }
}
