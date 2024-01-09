package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup

class DevicesByDriverType(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name = "DevicesByDriverType"

    val compactLogix by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'CompactLogix'") {
        getInt(1)
    }

    val controlLogix by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'ControlLogix'") {
        getInt(1)
    }

    val logixDriver by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'LogixDriver'") {
        getInt(1)
    }

    val microLogix by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'MicroLogix'") {
        getInt(1)
    }

    val plc5 by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'PLC5'") {
        getInt(1)
    }

    val slc by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'SLC'") {
        getInt(1)
    }

    val bacnet by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'com.inductiveautomation.BacnetIpDeviceType'") {
        getInt(1)
    }

    val dnp3 by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'Dnp3Driver'") {
        getInt(1)
    }

    val iec61850 by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'com.inductiveautomation.Iec61850DeviceType'") {
        getInt(1)
    }

    val mitsubishiTcp by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'com.inductiveautomation.MitsubishiTcpDeviceType'") {
        getInt(1)
    }

    val modbusRtu by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'ModbusRtu'") {
        getInt(1)
    }

    val modbusRtuOverTcp by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'ModbusRtuOverTcp'") {
        getInt(1)
    }

    val modbusTcp by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'ModbusTcp'") {
        getInt(1)
    }

    val finsTcpDeviceType by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'com.inductiveautomation.FinsTcpDeviceType'") {
        getInt(1)
    }

    val finsUdpDeviceType by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'com.inductiveautomation.FinsUdpDeviceType'") {
        getInt(1)
    }

    val omronNJ by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'com.inductiveautomation.omron.NjDriver'") {
        getInt(1)
    }

    val deviceSimulator by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'ProgrammableSimulatorDevice'") {
        getInt(1)
    }

    val s71200 by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'S71200'") {
        getInt(1)
    }

    val s71500 by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'S71500'") {
        getInt(1)
    }

    val s7300 by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'S7300'") {
        getInt(1)
    }

    val s7400 by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'S7400'") {
        getInt(1)
    }

    val tcp by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'TCPDriver'") {
        getInt(1)
    }

    val udp by queryStatistic(" SELECT COUNT(*) FROM DEVICESETTINGS WHERE TYPE = 'UDPDriver'") {
        getInt(1)
    }
}
