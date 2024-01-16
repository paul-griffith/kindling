package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.idb.tagconfig.model.TagProviderRecord
import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistics.Companion.STATISTICS_IO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Suppress("unused")
class TagConfigStatistics(override val gwbk: GatewayBackup) : PrecomputedStatisticCategory() {
    override val name = "TagConfig"

    init {
        STATISTICS_IO.launch {
            val allProviderStats = TagProviderRecord.getProvidersFromDB(gwbk.configIDB).map {
                async(Dispatchers.Default) {
                    it.initProviderNode()
                    it.providerStatistics
                }
            }.awaitAll()

            val totals = allProviderStats.reduce { acc, providerStatistics ->
                acc.totalAtomicTags.value += providerStatistics.totalAtomicTags.value
                providerStatistics.valueSources.value.entries.forEach { (valueSource, count) ->
                    acc.valueSources.value.merge(valueSource, count) { int1, int2 ->
                        int1 + int2
                    }
                }
                providerStatistics.dataTypes.value.entries.forEach { (dataType, count) ->
                    acc.dataTypes.value.merge(dataType, count) { int1, int2 ->
                        int1 + int2
                    }
                }

                acc.apply {
                    totalFolders.value += providerStatistics.totalFolders.value
                    totalUdtDefinitions.value += providerStatistics.totalUdtDefinitions.value
                    totalUdtInstances.value += providerStatistics.totalUdtInstances.value
                    totalTagsWithHistory.value += providerStatistics.totalTagsWithHistory.value
                    totalTagsWithAlarms.value += providerStatistics.totalTagsWithAlarms.value
                    totalTagsWithEnabledScripts.value += providerStatistics.totalTagsWithEnabledScripts.value
                    orphanedTags.value.addAll(providerStatistics.orphanedTags.value)
                }

                acc
            }

            dataMap.complete(
                mapOf(
                    "totalAtomicTags" to totals.totalAtomicTags.value,
                    "atomicTagsByDataType" to totals.dataTypes.value,
                    "atomicTagsByValueSource" to totals.valueSources.value,
                    "totalFolders" to totals.totalFolders.value,
                    "totalUdtInstances" to totals.totalUdtInstances.value,
                    "totalUdtDefinitions" to totals.totalUdtDefinitions.value,
                    "totalTagsWithHistory" to totals.totalTagsWithHistory.value,
                    "totalTagsWithAlarms" to totals.totalTagsWithAlarms.value,
                    "totalTagsWithScripts" to totals.totalTagsWithEnabledScripts.value,
                    "numberOfTagProviders" to allProviderStats.size,
                ),
            )
        }
    }

    val totalAtomicTags by statistic<Int>()

    @Suppress("unchecked_cast")
    val atomicTagsByDataType = Statistic(
        name = "atomicTagsByDataType",
        initializer = { dataMap.await()["atomicTagsByDataType"] as Map<String, Int> },
        stringTransform = { value -> JsonFormat.encodeToString(value) },
    ).also { list.add(it) }

    @Suppress("unchecked_cast")
    val atomicTagsByValueSource = Statistic(
        name = "atomicTagsByValueSource",
        initializer = { dataMap.await()["atomicTagsByValueSource"] as Map<String, Int> },
        stringTransform = { value -> JsonFormat.encodeToString(value) },
    ).also { list.add(it) }

    val totalFolders by statistic<Int>()
    val totalUdtInstances by statistic<Int>()
    val totalUdtDefinitions by statistic<Int>()
    val totalTagsWithHistory by statistic<Int>()
    val totalTagsWithAlarms by statistic<Int>()
    val totalTagsWithScripts by statistic<Int>()
    val numberOfTagProviders by statistic<Int>()

    companion object {
        private val JsonFormat = Json { prettyPrint = true }
    }
}
