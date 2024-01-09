package io.github.inductiveautomation.kindling.statistics

import io.github.inductiveautomation.kindling.statistics.categories.DatabaseStatistics
import io.github.inductiveautomation.kindling.statistics.categories.DatasourcesByDriverType
import io.github.inductiveautomation.kindling.statistics.categories.DeviceStatistics
import io.github.inductiveautomation.kindling.statistics.categories.DevicesByDriverType
import io.github.inductiveautomation.kindling.statistics.categories.GatewayNetworkStatistics
import io.github.inductiveautomation.kindling.statistics.categories.MetaStatistics
import io.github.inductiveautomation.kindling.statistics.categories.OpcServerStatistics
import io.github.inductiveautomation.kindling.statistics.categories.ProjectStatistics
import io.github.inductiveautomation.kindling.statistics.categories.StatisticCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.Locale

@Suppress("unused")
class Statistics private constructor(
    private val statsMap: Map<String, StatisticCategory>
) {
    val meta: MetaStatistics by statsMap
    val devices: DeviceStatistics by statsMap
    val devicesByDriverType: DevicesByDriverType by statsMap
    val databases: DatabaseStatistics by statsMap
    val datasourcesByDriverType: DatasourcesByDriverType by statsMap
    val projects: ProjectStatistics by statsMap
    val gatewayNetwork: GatewayNetworkStatistics by statsMap
    val opcServers: OpcServerStatistics by statsMap
//    val tagConfig: TagConfigStatistics by statsMap

    val all by statsMap::values

    override fun toString(): String = statsMap.values.joinToString("\n\n")

    companion object {
        val STATISTICS_IO by lazy { CoroutineScope(Dispatchers.IO) }

        suspend fun create(gwbkPath: Path): Statistics = withContext(Dispatchers.IO) {
            val gwbk = GatewayBackup(gwbkPath)

            val allStats = StatisticCategory.allCategories.map { init ->
                async { init(gwbk) }
            }.awaitAll().associateBy { stat ->
                stat.name.replaceFirstChar { it.lowercase(Locale.getDefault()) }
            }

            Statistics(allStats)
        }
    }
}
