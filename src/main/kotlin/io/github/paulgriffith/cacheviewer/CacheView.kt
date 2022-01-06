package io.github.paulgriffith.cacheviewer

import com.inductiveautomation.ignition.gateway.history.BasicHistoricalRecord
import com.inductiveautomation.ignition.gateway.history.ScanclassHistorySet
import io.github.paulgriffith.utils.Detail
import io.github.paulgriffith.utils.DetailsPane
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.Tool
import io.github.paulgriffith.utils.ToolPanel
import io.github.paulgriffith.utils.getLogger
import nb.deser.SerializationDumper
import net.lingala.zip4j.ZipFile
import org.hsqldb.jdbc.JDBCDataSource
import org.intellij.lang.annotations.Language
import java.awt.EventQueue
import java.awt.Rectangle
import java.io.ObjectInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.sql.PreparedStatement
import java.util.zip.GZIPInputStream
import javax.swing.Icon
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.SwingConstants
import kotlin.io.path.nameWithoutExtension

class CacheView(override val path: Path) : ToolPanel() {
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
                append("readonly=").append(true).append(";")
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
        statement.executeQuery().use { resultSet ->
            buildList {
                while (resultSet.next()) {
                    add(
                        CacheEntry(
                            id = resultSet.getInt("ID"),
                            schemaId = resultSet.getInt("SCHEMAID"),
                            timestamp = resultSet.getString("T_STAMP"),
                            attemptCount = resultSet.getInt("ATTEMPTCOUNT"),
                            dataCount = resultSet.getInt("DATA_COUNT"),
                        )
                    )
                }
            }
        }
    }

    private val details = DetailsPane()

    private val deserializeCache = mutableMapOf<Int, Detail>()

    private val model = CacheModel(data)
    private val table = JTable(model).apply {
        autoCreateRowSorter = true
        selectionModel.apply {
            addListSelectionListener { e ->
                if (!e.valueIsAdjusting) {
                    details.events = selectedIndices.filter { isSelectedIndex(it) }
                        .map { index -> data[convertRowIndexToModel(index)].id }
                        .map { id ->
                            deserializeCache.getOrPut(id) {
                                queryForData(id)
                            }
                        }

                    EventQueue.invokeLater {
                        details.scrollRectToVisible(Rectangle(0, 10))
                    }
                }
            }
        }
    }

    private fun deserialize(data: ByteArray): Detail {
        return try {
            // Try to decode the thing directly
            when (val obj = ObjectInputStream(data.inputStream()).readObject()) {
                is BasicHistoricalRecord -> obj.toDetail()
                is ScanclassHistorySet -> obj.toDetail()
                else -> Detail(
                    title = obj::class.java.name,
                    message = obj.toString(),
                )
            }
        } catch (e: ClassNotFoundException) {
            // It's not serialized with a class in the public API, or some other problem;
            // give up, and try to just dump the serialized data in a friendlier format
            val serializationDumper = SerializationDumper(data)

            Detail(
                title = "Serialization dump of ${data.size} bytes:",
                body = serializationDumper.parseStream().lines(),
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
                "execTime" to executionTime.time.toString(), // TODO date format?
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
                "quoteColumnNames" to quoteColumnNames().toString(),
            )
        )
    }

    init {
        add(
            JSplitPane(
                SwingConstants.HORIZONTAL,
                FlatScrollPane(table),
                details
            ).apply {
                resizeWeight = 0.6
            },
            "dock center"
        )
    }

    override val icon: Icon = Tool.CacheViewer.icon

    companion object {
        val LOGGER = getLogger<CacheView>()
    }
}
