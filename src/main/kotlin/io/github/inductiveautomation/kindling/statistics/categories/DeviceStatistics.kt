package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup

@Suppress("unused")
class DeviceStatistics(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name = "Devices"

    val totalDevices by queryScalarStatistic<Int>("SELECT COUNT(*) FROM DEVICES")

    val enabledDevices by queryScalarStatistic<Int>("SELECT COUNT(*) FROM DEVICES WHERE ENABLED = 1")

    val disabledDevices by queryScalarStatistic<Int>("SELECT COUNT(*) FROM DEVICES WHERE ENABLED = 0")
}