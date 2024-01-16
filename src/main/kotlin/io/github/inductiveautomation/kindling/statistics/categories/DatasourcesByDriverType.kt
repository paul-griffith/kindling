package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistics.Companion.STATISTICS_IO
import io.github.inductiveautomation.kindling.utils.getValue
import io.github.inductiveautomation.kindling.utils.toList
import kotlinx.coroutines.launch

@Suppress("unused")
class DatasourcesByDriverType(
    override val gwbk: GatewayBackup
) : PrecomputedStatisticCategory() {
    override val name = "DatasourcesByDriverType"

    init {
        STATISTICS_IO.launch {
            val map = mutableMapOf(
                "mariadb" to 0,
                "sqlite" to 0,
                "mysql" to 0,
                "sqlserver" to 0,
                "oracle" to 0,
                "postgresql" to 0,
                "other" to 0,
            )

            gwbk.configIDB.prepareStatement("SELECT CONNECTURL FROM DATASOURCES").executeQuery().toList {
                it.getString(1)
            }.forEach {
                val matchGroups = driverNameRegex.find(it)?.groups

                if (matchGroups != null) {
                    val driver by matchGroups

                    map.computeIfPresent(driver.value) { _, value ->
                        value + 1
                    } ?: run {
                        map["other"]!!.inc()
                    }
                }
            }

            dataMap.complete(map)
        }
    }

    val mariadb by statistic<Int>()
    val sqlite by statistic<Int>()
    val mysql by statistic<Int>()
    val sqlserver by statistic<Int>()
    val oracle by statistic<Int>()
    val postgresql by statistic<Int>()
    val other by statistic<Int>()

    companion object {
        private val driverNameRegex = """jdbc:(?<driver>.*?):.*""".toRegex()
    }
}