package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import kotlinx.coroutines.CompletableDeferred
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
        val stat = Statistic(prop.name, value)
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
            ::TagConfigStatistics,
        )
    }
}

sealed class PrecomputedStatisticCategory(
    override val name: String,
    override val gwbk: GatewayBackup
) : StatisticCategory() {
    protected val dataMap = CompletableDeferred<Map<String, *>>()

    @Suppress("unchecked_cast")
    protected fun <T> statistic() = PropertyDelegateProvider { thisRef: PrecomputedStatisticCategory, prop ->
        val stat = Statistic(prop.name) { thisRef.dataMap.await()[prop.name] as T }
        thisRef.add(stat)
        ReadOnlyProperty { _: PrecomputedStatisticCategory, _ -> stat }
    }
}

data class Statistic<T>(
    val name: String,
    private val initializer: suspend () -> T,
    private val stringTransform: suspend (value: T) -> String,
) {
    constructor(
        name: String,
        initializer: suspend () -> T,
    ) : this(name, initializer, { it.toString() })

    private var value: T? = null

    suspend fun getValue(): T = withContext(Dispatchers.IO) {
        if (value == null) value = initializer()
        value!!
    }

    suspend fun valueAsString() = stringTransform(getValue())
}
