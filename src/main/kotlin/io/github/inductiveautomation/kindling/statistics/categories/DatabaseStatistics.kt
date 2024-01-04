package io.github.inductiveautomation.kindling.statistics.categories

import io.github.inductiveautomation.kindling.statistics.GatewayBackup
import io.github.inductiveautomation.kindling.statistics.StatisticCategory

class DatabaseStatistics(override val gwbk: GatewayBackup) : StatisticCategory() {
    override val name: String = "Databases"
//
//    val totalConnections by statistic {
//
//    }
//    val totalEnabled by statistic {
//
//    }
//    val totalDisabled by statistic {
//
//    }
//    val totalDiskCacheEnabled by statistic {
//
//    }
//    val largestMemoryBuffer by statistic {
//
//    }
//    val largestDiskCache by statistic {
//
//    }
//    val totalMemoryBuffer by statistic {
//
//    }
//    val totalDiskCache by statistic {
//
//    }
//    val connectionsByDriverType by statistic {
//
//    }
//
//    companion object : EntityClass<String, DatabaseStatistics>(Databases) {
//
//        suspend fun new(gwbk: io.github.inductiveautomation.support.gwbkstats.statistics.GatewayBackup): DatabaseStatistics {
//            // Create individual Driver Statistics:
//            val rawStats = DatasourcesService(gwbk.configIDB).getAll()
//
//            ConnectionsByDriverType.new(gwbk.UUID, rawStats)
//
//            return new(gwbk.UUID) {
//                totalConnections = rawStats.size
//                totalEnabled = rawStats.count { it.connectionEnabled }
//                totalDisabled = rawStats.count { !it.connectionEnabled }
//                totalDiskCacheEnabled = rawStats.count { it.storeAndForwardEnabled }
//                largestMemoryBuffer = rawStats.maxOfOrNull { it.bufferSize } ?: 0
//                largestDiskCache = rawStats.maxOfOrNull { it.diskCacheMaxRecords } ?: 0
//                totalMemoryBuffer = rawStats.sumOf { it.bufferSize }
//                totalDiskCache = rawStats.sumOf { it.diskCacheMaxRecords }
//            }
//        }
//    }
//
//    class ConnectionsByDriverType(id: EntityID<String>) : StatisticCategory("ConnectionsByDriverType", id) {
//        var mariadb by JdbcDriverTypes.mariadb
//        var sqlite by JdbcDriverTypes.sqlite
//        var mysql by JdbcDriverTypes.mysql
//        var sqlserver by JdbcDriverTypes.sqlserver
//        var oracle by JdbcDriverTypes.oracle
//        var postgresql by JdbcDriverTypes.postgresql
//        var other by JdbcDriverTypes.other
//
//        object JdbcDriverTypes : IdTable<String>("JdbcDriverTypes") {
//            override val id = reference("gateway_id", Databases).uniqueIndex()
//            override val primaryKey = PrimaryKey(id)
//            val mariadb = integer("mariadb")
//            val sqlite = integer("sqlite")
//            val mysql = integer("mysql")
//            val sqlserver = integer("sqlserver")
//            val oracle = integer("oracle")
//            val postgresql = integer("postgresql")
//            val other = integer("other")
//        }
//
//        enum class DatabaseDrivers {
//            MARIADB,
//            SQLITE,
//            MYSQL,
//            SQLSERVER,
//            ORACLE,
//            POSTGRESQL,
//            ;
//
//            val connectionPrefix: String
//                get() = "jdbc:${name.lowercase()}:"
//        }
//
//        companion object : EntityClass<String, ConnectionsByDriverType>(JdbcDriverTypes) {
//            internal fun new(
//                id: String,
//                rawStats: List<DatasourcesService.FulLDataSourceRecord>,
//            ): ConnectionsByDriverType {
//                val prefixRegEx = """jdbc:.*?:""".toRegex()
//
//                val dataSourceMap = rawStats.groupingBy {
//                    prefixRegEx.find(it.connectUrl)?.value
//                }.eachCount().toMutableMap()
//
//                return new(id) {
//                    mariadb = dataSourceMap.remove(DatabaseDrivers.MARIADB.connectionPrefix) ?: 0
//                    sqlite = dataSourceMap.remove(DatabaseDrivers.SQLITE.connectionPrefix) ?: 0
//                    mysql = dataSourceMap.remove(DatabaseDrivers.MYSQL.connectionPrefix) ?: 0
//                    sqlserver = dataSourceMap.remove(DatabaseDrivers.SQLSERVER.connectionPrefix) ?: 0
//                    oracle = dataSourceMap.remove(DatabaseDrivers.ORACLE.connectionPrefix) ?: 0
//                    postgresql = dataSourceMap.remove(DatabaseDrivers.POSTGRESQL.connectionPrefix) ?: 0
//                    other = dataSourceMap.values.sum()
//                }
//            }
//        }
//    }
}
