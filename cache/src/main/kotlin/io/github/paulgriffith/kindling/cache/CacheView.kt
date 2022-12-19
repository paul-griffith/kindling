package io.github.paulgriffith.kindling.cache

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.inductiveautomation.ignition.gateway.history.BasicHistoricalRecord
import com.inductiveautomation.ignition.gateway.history.ScanclassHistorySet
import io.github.paulgriffith.kindling.core.Detail
import io.github.paulgriffith.kindling.core.DetailsPane
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.ReifiedJXTable
import io.github.paulgriffith.kindling.utils.getLogger
import io.github.paulgriffith.kindling.utils.selectedRowIndices
import io.github.paulgriffith.kindling.utils.toList
import net.lingala.zip4j.ZipFile
import org.hsqldb.jdbc.JDBCDataSource
import org.intellij.lang.annotations.Language
import java.io.ObjectInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.sql.PreparedStatement
import java.util.zip.GZIPInputStream
import javax.swing.Icon
import javax.swing.JSplitPane
import javax.swing.SwingConstants
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

class CacheView(private val path: Path) : ToolPanel() {
    private val tempDirectory: Path = Files.createTempDirectory(path.nameWithoutExtension)

    private val dbName: String = run {
        LOGGER.debug("Exploding to $tempDirectory")
        ZipFile(path.toFile()).run {
            extractAll(tempDirectory.toString())
            fileHeaders.first { !it.isDirectory }.fileName.substringBeforeLast('.')
        }
    }.also { dbName ->
        LOGGER.trace("dbName: $dbName")
    }

    private val connection = JDBCDataSource().apply {
        setUrl(
            buildString {
                append("jdbc:hsqldb:file:")
                append(tempDirectory).append("/").append(dbName).append(";")
                append("create=").append(false).append(";")
                append("shutdown=").append(true).append(";")
            }.also { url ->
                LOGGER.trace("JDBC URL: {}", url)
            }
        )
        user = "SA"
        setPassword("dstorepass")
    }.connection

    override fun removeNotify() = super.removeNotify().also {
        connection.close()
    }

    @Suppress("SqlNoDataSourceInspection", "SqlResolve")
    @Language("HSQLDB")
    private val dataQuery: PreparedStatement = connection.prepareStatement(
        "SELECT data FROM datastore_data WHERE id = ?"
    )

    @Suppress("SqlNoDataSourceInspection", "SqlResolve")
    @Language("HSQLDB")
    private val tableQuery: PreparedStatement = connection.prepareStatement(
        "SELECT id, schemaid, t_stamp, attemptcount, data_count FROM datastore_data"
    )

    private fun queryForData(id: Int): Detail {
        dataQuery.apply {
            setInt(1, id)
            executeQuery().use { resultSet ->
                resultSet.next()
                resultSet.getBytes(1).let { binary ->
                    val decompressed = GZIPInputStream(binary.inputStream()).readAllBytes()
                    return deserialize(decompressed)
                }
            }
        }
    }

    private val data: List<CacheEntry> = tableQuery.use { statement ->
        statement.executeQuery().toList { resultSet ->
            CacheEntry(
                id = resultSet.getInt("ID"),
                schemaId = resultSet.getInt("SCHEMAID"),
                timestamp = resultSet.getString("T_STAMP"),
                attemptCount = resultSet.getInt("ATTEMPTCOUNT"),
                dataCount = resultSet.getInt("DATA_COUNT")
            )
        }
    }

    private val details = DetailsPane()
    private val deserializedCache = mutableMapOf<Int, Detail>()
    private val model = CacheModel(data)
    private val table = ReifiedJXTable(model, CacheModel)

    private fun deserialize(data: ByteArray): Detail {
        return try {
            // Try to decode the thing directly
            when (val obj = ObjectInputStream(data.inputStream()).readObject()) {
                is BasicHistoricalRecord -> obj.toDetail()
                is ScanclassHistorySet -> obj.toDetail()
                is Array<*> -> {
                    // 2D array
                    if (obj.firstOrNull()?.javaClass?.isArray == true) {
                        Detail(
                            title = "Java 2D Array",
                            body = obj.map { row ->
                                (row as Array<*>).contentToString()
                            }
                        )
                    } else {
                        Detail(
                            title = "Java Array",
                            body = obj.map(Any?::toString)
                        )
                    }
                }

                else -> Detail(
                    title = obj::class.java.name,
                    message = obj.toString()
                )
            }
        } catch (e: ClassNotFoundException) {
            // It's not serialized with a class in the public API, or some other problem;
            // give up, and try to just dump the serialized data in a friendlier format
            val serializationDumper = deser.SerializationDumper(data)

            Detail(
                title = "Serialization dump of ${data.size} bytes:",
                body = serializationDumper.parseStream().lines()
            )
        }
    }

    private fun ScanclassHistorySet.toDetail(): Detail {
        return Detail(
            title = this::class.java.simpleName,
            body = this.map { historicalTagValue ->
                buildString {
                    append(historicalTagValue.source.toStringFull())
                    append(", ")
                    append(historicalTagValue.typeClass.name)
                    append(", ")
                    append(historicalTagValue.value)
                    append(", ")
                    append(historicalTagValue.interpolationMode.name)
                    append(", ")
                    append(historicalTagValue.timestampSource.name)
                }
            },
            details = mapOf(
                "gatewayName" to gatewayName,
                "provider" to providerName,
                "setName" to setName,
                "execRate" to execRate.toString(),
                "execTime" to executionTime.time.toString() // TODO date format?
            )
        )
    }

    private fun BasicHistoricalRecord.toDetail(): Detail {
        return Detail(
            title = this::class.java.simpleName,
            message = "INSERT INTO $tablename",
            body = columns.map { column ->
                buildString {
                    append(column.name).append(": ")
                    (0..dataCount).joinTo(buffer = this, prefix = "(", postfix = ")") { row ->
                        column.getValue(row).toString()
                    }
                }
            },
            details = mapOf(
                "quoteColumnNames" to quoteColumnNames().toString()
            )
        )
    }

    init {
        name = path.name
        toolTipText = path.toString()

        add(
            JSplitPane(
                SwingConstants.VERTICAL,
                FlatScrollPane(table),
                details
            ).apply {
                resizeWeight = 0.3
            },
            "dock center"
        )

        table.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                details.events = table.selectedRowIndices()
                    .map { index -> data[index].id }
                    .map { id ->
                        deserializedCache.getOrPut(id) {
                            queryForData(id)
                        }
                    }
            }
        }
    }

    override val icon: Icon = CacheViewer.icon

    companion object {
        val LOGGER = getLogger<CacheView>()
    }
}

object CacheViewer : Tool {
    override val title = "Cache Dump"
    override val description = "S&F Cache data/script files"
    override val icon = FlatSVGIcon("icons/bx-data.svg")
    override val extensions = listOf("data", "script")
    override fun open(path: Path): ToolPanel = CacheView(path)
}

class CacheViewerProxy : Tool by CacheViewer
