package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup

@Suppress("unused")
class DeviceStatistics(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name = "Devices"

    val totalDevices by queryStatistic("SELECT COUNT(*) FROM DEVICES") { getInt(1) }

    val enabledDevices by queryStatistic("SELECT COUNT(*) FROM DEVICES WHERE ENABLED = 1") {
        getInt(1)
    }

    val disabledDevices by queryStatistic("SELECT COUNT(*) FROM DEVICES WHERE ENABLED = 0") {
        getInt(1)
    }
}