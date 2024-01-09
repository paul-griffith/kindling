package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class DatasourcesByDriverType(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name = "DatasourcesByDriverType"

    val mariadb by statistic { getDriverCount(DatabaseDriver.MARIADB) }
    val sqlite by statistic { getDriverCount(DatabaseDriver.SQLITE) }
    val mysql by statistic { getDriverCount(DatabaseDriver.MYSQL) }
    val sqlserver by statistic { getDriverCount(DatabaseDriver.SQLSERVER) }
    val oracle by statistic { getDriverCount(DatabaseDriver.ORACLE) }
    val postgresql by statistic { getDriverCount(DatabaseDriver.POSTGRESQL) }

    val other by statistic {
        coroutineScope {
            val total = gwbk.configIDB.prepareStatement(
                "SELECT COUNT(1) FROM DATASOURCES"
            ).executeQuery().getInt(1)

            total - map {
                async {
                    it.getValue() as Int
                }
            }.awaitAll().sum()
        }
    }

    private fun getDriverCount(driver: DatabaseDriver): Int {
        val query = gwbk.configIDB.prepareStatement(
            """
                SELECT COUNT(*) FROM DATASOURCES
                WHERE CONNECTURL LIKE '${driver.connectionPrefix}%'
            """.trimIndent()
        )
        return query.executeQuery().getInt(1)
    }

    enum class DatabaseDriver {
        MARIADB,
        SQLITE,
        MYSQL,
        SQLSERVER,
        ORACLE,
        POSTGRESQL,
        ;

        val connectionPrefix: String
            get() = "jdbc:${name.lowercase()}:"
    }
}