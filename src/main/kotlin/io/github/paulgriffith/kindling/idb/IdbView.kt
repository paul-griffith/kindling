package io.github.paulgriffith.kindling.idb

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.inductiveautomation.ignition.gateway.images.ImageFormat
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.idb.generic.GenericView
import io.github.paulgriffith.kindling.idb.metrics.MetricsView
import io.github.paulgriffith.kindling.log.Level
import io.github.paulgriffith.kindling.log.LogPanel
import io.github.paulgriffith.kindling.log.SystemLogsEvent
import io.github.paulgriffith.kindling.utils.AbstractTreeNode
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.SQLiteConnection
import io.github.paulgriffith.kindling.utils.TabStrip
import io.github.paulgriffith.kindling.utils.TypedTreeNode
import io.github.paulgriffith.kindling.utils.toList
import java.awt.Dimension
import java.nio.file.Path
import java.sql.Connection
import java.time.Instant
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel
import kotlin.io.path.name

class IdbView(path: Path) : ToolPanel() {
    private val connection = SQLiteConnection(path)

    private val tables: List<String> = connection.metaData.getTables("", "", "", null).toList { rs ->
        rs.getString(3)
    }

    private val tabs = TabStrip().apply {
        trailingComponent = null
        isTabsClosable = false
    }

    init {
        name = path.name
        toolTipText = path.toString()

        tabs.addTab(
            tabName = "Tables",
            component = GenericView(connection),
            tabTooltip = null,
            select = true,
        )

        for (tool in IdbTool.values()) {
            if (tool.supports(tables)) {
                tabs.addLazyTab(
                    tabName = tool.name,
                ) {
                    tool.open(connection)
                }
            }
        }

        add(tabs, "push, grow")
    }

    override val icon = IdbViewer.icon

    override fun removeNotify() {
        super.removeNotify()
        connection.close()
    }
}

enum class IdbTool {
    @Suppress("SqlResolve")
    Logs {
        override fun supports(tables: List<String>): Boolean = "logging_event" in tables
        override fun open(connection: Connection): ToolPanel {
            val stackTraces: Map<Int, List<String>> = connection.prepareStatement(
                //language=sql
                """
                                SELECT
                                    event_id,
                                    i,
                                    trace_line
                                FROM 
                                    logging_event_exception
                                ORDER BY
                                    event_id,
                                    i
                                """.trimIndent(),
            ).executeQuery()
                .toList { resultSet ->
                    Pair(
                        resultSet.getInt("event_id"),
                        resultSet.getString("trace_line"),
                    )
                }.groupBy(keySelector = { it.first }, valueTransform = { it.second })

            val mdcKeys: Map<Int, Map<String, String>> = connection.prepareStatement(
                //language=sql
                """
                                SELECT 
                                    event_id,
                                    mapped_key,
                                    mapped_value
                                FROM 
                                    logging_event_property
                                ORDER BY 
                                    event_id
                                """.trimIndent(),
            ).executeQuery()
                .toList { resultSet ->
                    Triple(
                        resultSet.getInt("event_id"),
                        resultSet.getString("mapped_key"),
                        resultSet.getString("mapped_value"),
                    )
                }.groupingBy { it.first }
                .aggregateTo(mutableMapOf<Int, MutableMap<String, String>>()) { _, accumulator, element, _ ->
                    val acc = accumulator ?: mutableMapOf()
                    acc[element.second] = element.third ?: "null"
                    acc
                }

            val events = connection.prepareStatement(
                //language=sql
                """
                                SELECT
                                       event_id,
                                       timestmp,
                                       formatted_message,
                                       logger_name,
                                       level_string,
                                       thread_name
                                FROM 
                                    logging_event
                                ORDER BY
                                    event_id
                                """.trimIndent(),
            ).executeQuery()
                .toList { resultSet ->
                    val eventId = resultSet.getInt("event_id")
                    SystemLogsEvent(
                        timestamp = Instant.ofEpochMilli(resultSet.getLong("timestmp")),
                        message = resultSet.getString("formatted_message"),
                        logger = resultSet.getString("logger_name"),
                        thread = resultSet.getString("thread_name"),
                        level = Level.valueOf(resultSet.getString("level_string")),
                        mdc = mdcKeys[eventId].orEmpty(),
                        stacktrace = stackTraces[eventId].orEmpty(),
                    )
                }
            return LogPanel(events)
        }
    },
    Metrics {
        override fun supports(tables: List<String>): Boolean = "SYSTEM_METRICS" in tables
        override fun open(connection: Connection): ToolPanel = MetricsView(connection)
    },
    Images {
        override fun supports(tables: List<String>): Boolean = "IMAGES" in tables
        override fun open(connection: Connection): ToolPanel = ImagesPanel(connection)
    };

    abstract fun supports(tables: List<String>): Boolean

    abstract fun open(connection: Connection): ToolPanel
}

object IdbViewer : Tool {
    override val title = "Idb File"
    override val description = ".idb (SQLite3) files"
    override val icon = FlatSVGIcon("icons/bx-hdd.svg")
    override val extensions = listOf("idb")
    override fun open(path: Path): ToolPanel = IdbView(path)
}

class ImagesPanel(connection: Connection) : ToolPanel("ins 0, fill, hidemode 3") {
    override val icon: Icon? = null

    init {
        val tree = JTree(DefaultTreeModel(RootImageNode(connection)))
        add(FlatScrollPane(tree), "push, grow, w 30%!")
        val imageDisplay = JLabel()
        add(FlatScrollPane(imageDisplay), "push, grow")

        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.addTreeSelectionListener {
            val node = it.newLeadSelectionPath.lastPathComponent as AbstractTreeNode
            if (node is ImageNode) {
                val data = node.userObject.data
                val icon: Icon? = when (node.userObject.type) {
                    ImageFormat.SVG -> FlatSVGIcon(data.inputStream())

                    else -> {
                        val readers = ImageIO.getImageReadersByFormatName(node.userObject.type.name)
                        val image = readers.asSequence().firstNotNullOfOrNull { reader ->
                            ImageIO.createImageInputStream(data.inputStream())?.use { iis ->
                                reader.input = iis
                                reader.read(
                                    0,
                                    reader.defaultReadParam.apply {
                                        sourceRenderSize = Dimension(200, 200)
                                    },
                                )
                            }
                        }
                        image?.let(::ImageIcon)
                    }
                }
                imageDisplay.icon = icon
            } else {
                imageDisplay.icon = null
            }
        }
    }
}

private data class ImageNode(override val userObject: ImageRow) : TypedTreeNode<ImageRow>()

private data class ImageRow(
    val path: String,
    val type: ImageFormat,
    val description: String?,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageRow

        if (path != other.path) return false
        if (type != other.type) return false
        if (description != other.description) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + data.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "ImageRow(path='$path', type=$type, description=$description)"
    }
}

private data class ImageFolderNode(override val userObject: String) : TypedTreeNode<String>()

class RootImageNode(connection: Connection) : AbstractTreeNode() {
    private val listAll = connection.prepareStatement(
        """
            SELECT path, type, description, data
            FROM IMAGES
            WHERE type IS NOT NULL
            ORDER BY path
        """.trimIndent(),
    )

    init {
        val images = listAll.use {
            it.executeQuery().toList { rs ->
                ImageRow(
                    rs.getString("path"),
                    rs.getString("type").let(ImageFormat::valueOf),
                    rs.getString("description"),
                    rs.getBytes("data"),
                )
            }
        }

        val seen = mutableMapOf<List<String>, AbstractTreeNode>()
        for (row in images) {
            if (row.type != null) {
                var lastSeen: AbstractTreeNode = this
                val currentLeadingPath = mutableListOf<String>()
                for (pathPart in row.path.split('/')) {
                    currentLeadingPath.add(pathPart)
                    val next = seen.getOrPut(currentLeadingPath.toList()) {
                        val newChild = if (pathPart.contains('.')) {
                            ImageNode(row)
                        } else {
                            ImageFolderNode(currentLeadingPath.joinToString("/"))
                        }
                        lastSeen.children.add(newChild)
                        newChild
                    }
                    lastSeen = next
                }
            }
        }
    }
}
