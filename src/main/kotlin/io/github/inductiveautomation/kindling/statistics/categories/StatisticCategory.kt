package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

sealed class StatisticCategory private constructor(
    @PublishedApi internal val list: MutableList<Statistic<*>>,
) : List<Statistic<*>> by list {
    constructor() : this(mutableListOf())

    abstract val name: String
    abstract val gwbk: GatewayBackup

    protected fun <T> statistic(
        value: suspend () -> T,
    ) = PropertyDelegateProvider { thisRef: StatisticCategory, prop ->
        val stat = Statistic(prop.name, value, thisRef.gwbk)
        thisRef.add(stat)
        ReadOnlyProperty { _: StatisticCategory, _ -> stat }
    }

    protected fun <T> queryStatistic(
        query: String,
        value: suspend ResultSet.() -> T,
    ) = statistic {
        gwbk.configIDB.prepareStatement(query).executeQuery().value()
    }

    fun add(stat: Statistic<*>) {
        list.add(stat)
    }

    override fun toString() = joinToString("\n", prefix = "$name:\n")

    companion object {
        val allCategories = listOf(
            ::MetaStatistics,
            ::DatabaseStatistics,
            ::DatasourcesByDriverType,
            ::ProjectStatistics,
            ::GatewayNetworkStatistics,
            ::DeviceStatistics,
            ::DevicesByDriverType,
            ::OpcServerStatistics,
        )
    }
}

data class Statistic<T>(
    val name: String,
    private val initializer: suspend () -> T,
    private val gwbk: GatewayBackup,
) {
    private var value: T? = null
    suspend fun getValue(): T = withContext(Dispatchers.IO) {
        if (value == null) value = initializer()
        value!!
    }
}
