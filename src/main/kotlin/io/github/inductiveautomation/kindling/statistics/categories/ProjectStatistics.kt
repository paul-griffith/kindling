package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.Statistics.Companion.STATISTICS_IO
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.walk

/*
This statistic involves walking a potentially massive file tree.
Most IO operations for stats are relatively lightweight, but this stat isn't
 */
@OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
@Suppress("unused")
class ProjectStatistics(gwbk: GatewayBackup) : PrecomputedStatisticCategory("Projects", gwbk) {

    init {
        STATISTICS_IO.launch {
            val map = (projectJsonStats.keys + filenameRegexes.keys + directoryRegexes.keys).associateWith { 0 }.toMutableMap()

            val allPaths =
                gwbk.root.walk(PathWalkOption.FOLLOW_LINKS, PathWalkOption.INCLUDE_DIRECTORIES)

            allPaths.forEach { path ->
                for ((projectStatName, regex) in filenameRegexes) {
                    if (regex.matches(path.absolutePathString())) {
                        map.merge(projectStatName, 1, Int::plus)
                    }
                }

                if (path.name == "project.json") {
                    path.inputStream().use { stream ->
                        val projectJson: ProjectJson = JSON.decodeFromStream(stream)
                        for ((name, condition) in projectJsonStats) {
                            map.merge(name, if (condition(projectJson)) 1 else 0, Int::plus)
                        }
                    }
                }

                if (path.isDirectory()) {
                    for ((projectStatName, regex) in directoryRegexes) {
                        if (regex.matches(path.absolutePathString())) {
                            map.compute(projectStatName) { _, value: Int? ->
                                (value ?: 0) + 1
                            }
                        }
                    }
                }
            }

            dataMap.complete(map)
        }
    }

    val totalProjects by statistic<Int>()
    val totalPerspectiveProjects by statistic<Int>()
    val totalVisionProjects by statistic<Int>()
    val totalPerspectiveViews by statistic<Int>()
    val totalVisionWindows by statistic<Int>()
    val totalEnabled by statistic<Int>()
    val totalInheritable by statistic<Int>()
    val totalInheriting by statistic<Int>()
    val totalReports by statistic<Int>()
    val totalSfcs by statistic<Int>()
    val totalTransactionGroups by statistic<Int>()
    val totalAlarmPipelines by statistic<Int>()

    companion object {
        private val JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        private val projectJsonStats: Map<String, (ProjectJson) -> Boolean> = mapOf(
            "totalInheriting" to { it.parent != null },
            "totalEnabled" to { it.enabled },
            "totalInheritable" to { it.parent == null },
        )

        private val filenameRegexes = mapOf(
            "totalVisionWindows" to ".*com\\.inductiveautomation\\.vision/windows/.*resource\\.json$".toRegex(),
            "totalPerspectiveViews" to ".*com\\.inductiveautomation\\.perspective/views/.*view\\.json$".toRegex(),
            "totalReports" to ".*com\\.inductiveautomation\\.reporting/reports/.*resource\\.json$".toRegex(),
            "totalTransactionGroups" to ".*com\\.inductiveautomation\\.sqlbridge/transaction-groups/.*resource\\.json$".toRegex(),
            "totalSfcs" to ".*com\\.inductiveautomation\\.sfc/charts/.*resource\\.json$".toRegex(),
            "totalAlarmPipelines" to ".*com\\.inductiveautomation\\.alarm-notification/alarm-pipelines/.*resource\\.json$".toRegex(),
        )

        private val directoryRegexes = mapOf(
            "totalPerspectiveProjects" to "/projects/[^/]+/com\\.inductiveautomation\\.perspective$".toRegex(),
            "totalVisionProjects" to "/projects/[^/]+/com\\.inductiveautomation\\.vision$".toRegex(),
            "totalProjects" to "^/projects/[^/]+".toRegex(),
        )
    }
}

@Serializable
data class ProjectJson internal constructor(
    val parent: String?,
    val enabled: Boolean,
    val inheritable: Boolean,
)
