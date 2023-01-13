package io.github.paulgriffith.kindling.cache

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.inductiveautomation.ignition.gateway.history.BasicHistoricalRecord
import com.inductiveautomation.ignition.gateway.history.ScanclassHistorySet
import com.jidesoft.swing.JideButton
import io.github.paulgriffith.kindling.core.Detail
import io.github.paulgriffith.kindling.core.DetailsPane
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolOpeningException
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.EDT_SCOPE
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.ReifiedJXTable
import io.github.paulgriffith.kindling.utils.getLogger
import io.github.paulgriffith.kindling.utils.getValue
import io.github.paulgriffith.kindling.utils.selectedRowIndices
import io.github.paulgriffith.kindling.utils.toList
import io.github.paulgriffith.kindling.utils.transpose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.lingala.zip4j.ZipFile
import org.hsqldb.jdbc.JDBCDataSource
import org.intellij.lang.annotations.Language
import org.jdesktop.swingx.JXTable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.ObjectInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.sql.PreparedStatement
import java.util.zip.GZIPInputStream
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JSplitPane
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel
import kotlin.io.path.CopyActionResult
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

@OptIn(ExperimentalPathApi::class)
class CacheView(private val path: Path) : ToolPanel() {
    private val tempDirectory: Path = Files.createTempDirectory(path.nameWithoutExtension)

    private val dbName = when (path.extension) {
        "zip" -> run {
            LOGGER.debug("Exploding to $tempDirectory")
            ZipFile(path.toFile()).run {
                extractAll(tempDirectory.toString())
                fileHeaders.first { !it.isDirectory }.fileName.substringBeforeLast('.')
            }
        }

        in CacheViewer.extensions -> run {
            path.parent.copyToRecursively(
                target = tempDirectory,
                followLinks = false,
                copyAction = { source: Path, target: Path ->
                    if (source.extension in cacheFileExtensions) {
                        source.copyToIgnoringExistingDirectory(target, false)
                    }
                    CopyActionResult.CONTINUE
                },
            )
            path.nameWithoutExtension
        }

        else -> throw ToolOpeningException(".${path.extension} files not supported.")
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
            },
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
        "SELECT data FROM datastore_data WHERE id = ?",
    )

    @Suppress("SqlNoDataSourceInspection", "SqlResolve")
    @Language("HSQLDB")
    private val tableQuery: PreparedStatement = connection.prepareStatement(
        "SELECT id, schemaid, t_stamp, attemptcount, data_count FROM datastore_data",
    )

    @Suppress("SqlNoDataSourceInspection", "SqlResolve")
    @Language("HSQLDB")
    private val schemaQuery: PreparedStatement = connection.prepareStatement(
        "SELECT id, signature FROM datastore_schema"
    )

    @Suppress("SqlNoDataSourceInspection", "SqlResolve")
    @Language("HSQLDB")
    private val errorQuery: PreparedStatement = connection.prepareStatement(
        "SELECT schemaid, message FROM datastore_errors"
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

    private val schemaRecords = run {
        val schemata = schemaQuery.use { statement ->
            statement.executeQuery().toList { rs ->
                Pair(
                    rs.getInt("ID"),
                    rs.getString("SIGNATURE")
                )
            }
        }
        val schemaErrors: List<Pair<Int, String>> = errorQuery.use { statement ->
            statement.executeQuery().toList { rs ->
                Pair(
                    rs.getInt("SCHEMAID"),
                    rs.getString("MESSAGE")
                )
            }
        }

        schemata.map { (id, name) ->
            SchemaRecord(
                id,
                name,
                schemaErrors.filter { (errId, _) ->
                    errId == id
                }.map {(_, message) -> message }
            )
        }
    }

    private val data: List<CacheEntry> = tableQuery.use { statement ->
        statement.executeQuery().toList { resultSet ->
            CacheEntry(
                id = resultSet.getInt("ID"),
                schemaId = resultSet.getInt("SCHEMAID"),
                schemaName = schemaRecords.find {
                    it.id == resultSet.getInt("SCHEMAID")
                }?.name ?: "null",
                timestamp = resultSet.getString("T_STAMP"),
                attemptCount = resultSet.getInt("ATTEMPTCOUNT"),
                dataCount = resultSet.getInt("DATA_COUNT"),
            )
        }
    }

    private fun tableSchemaFilter(entry: CacheEntry): Boolean {
        return schemaRecords.find { it.id == entry.schemaId } in schemaList.checkBoxListSelectedValues
    }

    private fun SchemaRecord.toDetail(): Detail {
        return Detail(
            title = name,
            body = errors
        )
    }

    private val details = DetailsPane().apply {
        isAllExtraButtonsEnabled = false
    }
    private val deserializedCache = mutableMapOf<Int, Detail>()
    private val model = CacheModel(data)
    private val table = ReifiedJXTable(model, CacheModel)
    private val schemaList = SchemaFilterList(schemaRecords).apply {
        selectionModel.addListSelectionListener {
            details.events = selectedValuesList.filterIsInstance<SchemaRecord>().map { it.toDetail() }
            details.isAllExtraButtonsEnabled = false
        }
    }

    private val settingsMenu = FlatPopupMenu().apply {
        add(
            Action("Toggle show Schema Records") {
                val isVisible = mainSplitPane.bottomComponent.isVisible
                mainSplitPane.bottomComponent.isVisible = !isVisible
                if (!isVisible) {
                    mainSplitPane.setDividerLocation(0.75)
                }
            }
        )
    }
    private val settings = JideButton(FlatSVGIcon("icons/bx-cog.svg")).apply {
        addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    settingsMenu.show(this@apply, e.x, e.y)
                }
            },
        )
    }

    private val mainSplitPane = JSplitPane(
        SwingConstants.HORIZONTAL,
        JSplitPane(
            SwingConstants.VERTICAL,
            FlatScrollPane(table),
            details
        ).apply {
            resizeWeight = 0.5
        },
        FlatScrollPane(schemaList)
    ).apply {
        resizeWeight = 0.75
    }

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
                            },
                        )
                    } else {
                        Detail(
                            title = "Java Array",
                            body = obj.map(Any?::toString),
                        )
                    }
                }

                else -> Detail(
                    title = obj::class.java.name,
                    message = obj.toString(),
                )
            }
        } catch (e: ClassNotFoundException) {
            // It's not serialized with a class in the public API, or some other problem;
            // give up, and try to just dump the serialized data in a friendlier format
            val serializationDumper = deser.SerializationDumper(data)

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
            ),
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
            ),
        )
    }

    init {
        name = path.name
        toolTipText = path.toString()
        val numEntriesLabel = JLabel("${data.size} ${if (data.size == 1) "entry" else "entries"}")

        add(numEntriesLabel)
        add(settings, "right, wrap")

        add(mainSplitPane, "push, grow, span")

        details.addButton("2dArray", FlatSVGIcon("icons/bx-detail.svg")) {
            val columnNameRegex = """(?<tableName>.*)\{(?<columnsString>.*)}""".toRegex()
            /*
            * A few assumptions are made:
            * 1. The currently selected table row matches the entry in the Details pane.
            * 2. There is only row selected
            * 3. The entry is already in the cache, since it's been selected.
            *
            * We need the ID to get the table data and the schemaName to get the table columns and table name

            Get data with ID.
            Data is parsed from the array string stored in the Detail object.
            We could alternatively get it from the DB and deserialize it again, but the existing functions
            don't allow that.
            */
            val id = table.model[table.selectedRow, CacheModel.Id]
            val lines = deserializedCache[id]?.body ?: return@addButton
            val data = transpose(
                Array(lines.size) { i ->
                    val rowString = lines[i].text
                    val rowList = rowString.substring(1, rowString.length - 1).split(", ")
                    rowList.toTypedArray()
                }
            )

            // Get table name and column names with schemaName
            val schemaName = table.model[table.selectedRow, CacheModel.SchemaName]
            val matcher = columnNameRegex.find(schemaName) ?: return@addButton
            val tableName by matcher.groups
            val columnsString by matcher.groups
            val columns = columnsString.value.split(",").toTypedArray()

            // Use data and columns to create a simple table model
            val model = DefaultTableModel(data, columns) // TODO: Add export functionality? Not sure if useful

            JFrame(tableName.value).apply {
                setSize(900, 500)
                isResizable = true
                setLocationRelativeTo(null)
                contentPane.add(
                    FlatScrollPane(JXTable(model))
                )
                isVisible = true
            }
        }

        table.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                details.events = table.selectedRowIndices()
                    .map { index -> data[index].id }
                    .map { id ->
                        deserializedCache.getOrPut(id) {
                            queryForData(id)
                        }
                    }
                details.isAllExtraButtonsEnabled = details.events.size == 1 &&
                                                details.events.first().title == "Java 2D Array"
            }
        }

        schemaList.checkBoxListSelectionModel.addListSelectionListener {
            updateData()
        }
    }

    private fun updateData() {
        BACKGROUND.launch {
            val filteredData = data.filter(::tableSchemaFilter)
            EDT_SCOPE.launch {
                table.model = CacheModel(filteredData)
            }
        }
    }

    override val icon: Icon = CacheViewer.icon

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)
        val LOGGER = getLogger<CacheView>()
        val cacheFileExtensions = listOf("data", "script", "log", "backup", "properties")
    }
}

object CacheViewer : Tool {
    override val title = "Cache Dump"
    override val description = "S&F Cache data/script files"
    override val icon = FlatSVGIcon("icons/bx-data.svg")
    override val extensions = listOf("data", "script", "zip")
    override fun open(path: Path): ToolPanel = CacheView(path)
}

class CacheViewerProxy : Tool by CacheViewer
