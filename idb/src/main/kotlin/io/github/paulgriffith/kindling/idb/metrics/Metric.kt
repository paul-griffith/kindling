package io.github.paulgriffith.kindling.idb.metrics

class Metric(val name: String) {
    val isLegacy: Boolean = name in legacyMetrics

    companion object {
        val legacyMetrics = listOf("PerformanceMonitor", "Gateway.Datasource")
        val newMetrics = listOf("databases.connection", "redundancy", "ignition.performance")
    }
}