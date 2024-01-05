package io.github.inductiveautomation.kindling.statistics

import io.github.inductiveautomation.kindling.statistics.categories.DatabaseStatistics
import io.github.inductiveautomation.kindling.statistics.categories.DatasourcesByDriverType
import io.github.inductiveautomation.kindling.statistics.categories.MetaStatistics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.Locale
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

@Suppress("unused")
class Statistics private constructor(private val statsMap: Map<String, StatisticCategory>) {
    val meta: MetaStatistics by statsMap
//    val gatewayNetworkStatistics: GatewayNetworkStatistics by statsMap
//    val tagConfig: TagConfigStatistics by statsMap
//    val devices: DeviceStatistics by statsMap
    val databases: DatabaseStatistics by statsMap
    val datasourcesByDriverType: DatasourcesByDriverType by statsMap
//    val opcServers: OpcServersStatistics by statsMap
//    val projects: GatewayProjectStatistics by statsMap

    val all by statsMap::values

    override fun toString(): String = statsMap.values.joinToString("\n\n")

    companion object {
        private val stats = listOf(
            ::MetaStatistics,
            ::DatabaseStatistics,
            ::DatasourcesByDriverType,
        )

        suspend fun create(gwbkPath: Path): Statistics {
            println("Create running on thread ${Thread.currentThread().name}")
            val gwbk = GatewayBackup(gwbkPath)

            val allStats = coroutineScope {
                stats.map { init ->
                    async { init(gwbk) }
                }.awaitAll()
            }.associateBy { stat ->
                stat.name.replaceFirstChar { it.lowercase(Locale.getDefault()) }
            }

            return Statistics(allStats)
        }
    }
}

abstract class StatisticCategory private constructor(
    @PublishedApi internal val list: MutableList<Statistic<*>>,
) : List<Statistic<*>> by list {
    constructor() : this(mutableListOf())

    abstract val name: String
    abstract val gwbk: GatewayBackup

    protected inline fun <reified T> statistic(
        noinline value: suspend CoroutineScope.(GatewayBackup) -> T,
    ): PropertyDelegateProvider<StatisticCategory, ReadOnlyProperty<StatisticCategory, Statistic<T>>> {
        return PropertyDelegateProvider { thisRef, prop ->
            val stat = Statistic(prop.name, value, thisRef.gwbk)
            thisRef.add(stat)
            ReadOnlyProperty { _, _ -> stat }
        }
    }

    fun add(stat: Statistic<*>) {
        list.add(stat)
    }

    override fun toString() = joinToString("\n", prefix = "$name:\n")
}

class Statistic<T>(
    val name: String,
    private val initializer: suspend CoroutineScope.(GatewayBackup) -> T,
    private val gwbk: GatewayBackup,
) {
    private var value: T? = null
    suspend fun getValue(): T = withContext(Dispatchers.IO) {
        if (value == null) value = initializer(gwbk)
        value!!
    }
}
