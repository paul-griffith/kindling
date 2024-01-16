package io.github.inductiveautomation.kindling.idb.tagconfig.model

import io.github.inductiveautomation.kindling.utils.splitCamelCase
import java.util.Locale
import kotlin.reflect.KProperty

@Suppress("MemberVisibilityCanBePrivate")
class ProviderStatistics {
    val orphanedTags = ListStatistic<Node>("orphanedTags")

    private val statsMap = mapOf(
        "totalAtomicTags" to QuantitativeStatistic("totalAtomicTags"),
        "dataTypes" to MappedStatistic("dataTypes"),
        "valueSources" to MappedStatistic("valueSources"),
        "totalFolders" to QuantitativeStatistic("totalFolders"),
        "totalUdtInstances" to QuantitativeStatistic("totalUdtInstances"),
        "totalUdtDefinitions" to QuantitativeStatistic("totalUdtDefinitions"),
        "totalTagsWithAlarms" to QuantitativeStatistic("totalTagsWithAlarms"),
        "totalAlarms" to QuantitativeStatistic("totalAlarms"),
        "totalTagsWithHistory" to QuantitativeStatistic("totalTagsWithHistory"),
        "totalTagsWithEnabledScripts" to QuantitativeStatistic("totalTagsWithEnabledScripts"),
        "totalEnabledScripts" to QuantitativeStatistic("totalEnabledScripts"),
        "totalOrphanedTags" to DependentStatistic("totalOrphanedTags", orphanedTags) { it.size },
        "totalReadOnlyTags" to QuantitativeStatistic("totalReadOnlyTags")
    )

    val all by statsMap::values

    val totalAtomicTags: QuantitativeStatistic by statsMap
    val totalFolders: QuantitativeStatistic by statsMap
    val totalUdtInstances: QuantitativeStatistic by statsMap
    val totalTagsWithAlarms: QuantitativeStatistic by statsMap
    val totalUdtDefinitions: QuantitativeStatistic by statsMap
    val totalAlarms: QuantitativeStatistic by statsMap
    val totalTagsWithHistory: QuantitativeStatistic by statsMap
    val totalTagsWithEnabledScripts: QuantitativeStatistic by statsMap
    val totalEnabledScripts: QuantitativeStatistic by statsMap
    val dataTypes: MappedStatistic by statsMap
    val valueSources: MappedStatistic by statsMap
    val totalOrphanedTags: DependentStatistic<Int, MutableList<Node>> by statsMap
    val totalReadOnlyTags: QuantitativeStatistic by statsMap

    fun processNodeForStatistics(node: Node) {
        if (node.statistics.isUdtDefinition) {
            totalUdtDefinitions.value++
            return
        }

        when {
            node.statistics.isAtomicTag -> {
                totalAtomicTags.value++

                dataTypes.value.compute(node.statistics.dataType ?: DEFAULT_DATA_TYPE) { _, value ->
                    if (value == null) 1 else value + 1
                }

                valueSources.value.compute(node.statistics.dataSource ?: DEFAULT_VALUE_SOURCE) { _, value ->
                    if (value == null) 1 else value + 1
                }
            }

            node.statistics.isFolder -> totalFolders.value++
            node.statistics.isUdtInstance -> totalUdtInstances.value++
            node.statistics.isReadOnly == true -> totalReadOnlyTags.value++
        }

        if (node.statistics.historyEnabled == true) totalTagsWithHistory.value++

        if (node.statistics.hasAlarms) {
            totalTagsWithAlarms.value++
            totalAlarms.value += node.statistics.numAlarms
        }

        if (node.statistics.hasScripts) {
            totalTagsWithEnabledScripts.value++
            totalEnabledScripts.value += node.statistics.numScripts
        }
    }

    override fun toString(): String = buildString {
        appendLine("Atomic Tags: $totalAtomicTags")
        appendLine("- By Data Source")
        valueSources.value.entries.joinTo(this, "\n|- ", prefix = "\n|- ") { (key, value) -> "$key: $value" }
        appendLine("- By Data Type")
        dataTypes.value.entries.joinTo(this, "\n|- ", prefix = "\n|- ") { (key, value) -> "$key: $value" }
        appendLine("Folders: $totalFolders")
        appendLine("Udt Instances: $totalUdtInstances")
        appendLine("Udt Definition: $totalUdtDefinitions")
        appendLine("Tags with History Enabled: $totalTagsWithHistory")
        appendLine("Scripts:")
        appendLine("- $totalEnabledScripts scripts found on $totalTagsWithEnabledScripts tags")
        appendLine("Alarms:")
        appendLine("- $totalAlarms alarms found on $totalTagsWithAlarms tags")
        appendLine("Orphaned Tags: $totalOrphanedTags")
    }

    companion object {
        private const val DEFAULT_DATA_TYPE = "Int4"
        private const val DEFAULT_VALUE_SOURCE = "memory"
    }

    sealed class ProviderStatistic<T>(val name: String) {
        abstract val value: T
        val humanReadableName = name.splitCamelCase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

        override fun toString(): String = value.toString()
    }

    class QuantitativeStatistic(
        name: String,
        override var value: Int = 0,
    ) : ProviderStatistic<Int>(name)

    class MappedStatistic(
        name: String,
        override val value: MutableMap<String, Int> = mutableMapOf(),
    ) : ProviderStatistic<MutableMap<String, Int>>(name)

    class ListStatistic<T>(
        name: String,
        override val value: MutableList<T> = mutableListOf(),
    ) : ProviderStatistic<MutableList<T>>(name)

    class DependentStatistic<T, P>(
        name: String,
        private val dependentStatistic: ProviderStatistic<P>,
        private val transform: (P) -> T,
    ) : ProviderStatistic<T>(name) {
        override val value: T
            get() = transform(dependentStatistic.value)
    }
}
