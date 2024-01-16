package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.utils.toList

@Suppress("unused")
class DevicesByDriverType(override val gwbk: GatewayBackup) : PrecomputedStatisticCategory() {
    override val name = "DevicesByDriverType"

    init {
        val resultMap = gwbk.configIDB.prepareStatement(
            "SELECT TYPE, COUNT(TYPE) FROM DEVICESETTINGS GROUP BY TYPE"
        ).executeQuery().toList {
            val deviceName = deviceTypesToNames[it.getString(1)]

            if (deviceName == null) {
                null
            } else {
                deviceName to it.getInt(2)
            }
        }.filterNotNull().toMap().toMutableMap()

        deviceTypesToNames.forEach { (_, name) ->
            resultMap.putIfAbsent(name, 0)
        }

        dataMap.complete(resultMap)
    }

    val compactLogix by statistic<Int>()
    val controlLogix by statistic<Int>()
    val logixDriver by statistic<Int>()
    val microLogix by statistic<Int>()
    val plc5 by statistic<Int>()
    val slc by statistic<Int>()
    val bacnet by statistic<Int>()
    val dnp3 by statistic<Int>()
    val iec61850 by statistic<Int>()
    val mitsubishiTcp by statistic<Int>()
    val modbusRtu by statistic<Int>()
    val modbusRtuOverTcp by statistic<Int>()
    val modbusTcp by statistic<Int>()
    val finsTcp by statistic<Int>()
    val finsUdp by statistic<Int>()
    val omronNJ by statistic<Int>()
    val deviceSimulator by statistic<Int>()
    val s71200 by statistic<Int>()
    val s71500 by statistic<Int>()
    val s7300 by statistic<Int>()
    val s7400 by statistic<Int>()
    val tcp by statistic<Int>()
    val udp by statistic<Int>()

    companion object {
        private val deviceTypesToNames = mapOf(
            "CompactLogix" to "compactLogix",
            "ControlLogix" to "controlLogix",
            "LogixDriver" to "logixDriver",
            "MicroLogix" to "microLogix",
            "PLC5" to "plc5",
            "SLC" to "slc",
            "com.inductiveautomation.BacnetIpDeviceType" to "bacnet",
            "Dnp3Driver" to "dnp3",
            "com.inductiveautomation.Iec61850DeviceType" to "iec61850",
            "com.inductiveautomation.MitsubishiTcpDeviceType" to "mitsubishiTcp",
            "ModbusRtu" to "modbusRtu",
            "ModbusRtuOverTcp" to "modbusRtuOverTcp",
            "ModbusTcp" to "modbusTcp",
            "com.inductiveautomation.FinsTcpDeviceType" to "finsTcp",
            "com.inductiveautomation.FinsUdpDeviceType" to "finsUdp",
            "com.inductiveautomation.omron.NjDriver" to "omronNJ",
            "ProgrammableSimulatorDevice" to "deviceSimulator",
            "S71200" to "s71200",
            "S71500" to "s71500",
            "S7300" to "s7300",
            "S7400" to "s7400",
            "TCPDriver" to "tcp",
            "UDPDrive" to "udp",
        )
    }
}
