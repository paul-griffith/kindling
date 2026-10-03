package io.github.inductiveautomation.kindling.gatewaynetwork

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.inductiveautomation.kindling.core.ClipboardTool
import io.github.inductiveautomation.kindling.core.Detail
import io.github.inductiveautomation.kindling.core.DetailsPane
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.DefaultEncoding
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.core.ToolPanel
import io.github.inductiveautomation.kindling.utils.FileFilter
import io.github.inductiveautomation.kindling.utils.FlatScrollPane
import io.github.inductiveautomation.kindling.utils.HorizontalSplitPane
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.useLines

/**
 * Opens a file containing a gateway network diagram as JSON, and renders it as a laid-out diagram, with details of
 * the selected gateway or connection in a sidebar. The raw JSON is available in a separate tab.
 *
 * To get a JSON diagram in Ignition 8.1:
 * Set the 'gateway.routes.status.GanRoutes' logger set to DEBUG in an Ignition gateway to generate diagram JSON while
 * viewing the gateway network live diagram page.
 */

class GatewayNetworkViewer(tabName: String, tooltip: String, json: String) : ToolPanel() {
    override val icon = GatewayNetworkTool.icon

    private val textArea = JTextArea().apply {
        text = json
        isEditable = false
    }

    private val details = DetailsPane(listOf(NO_SELECTION))

    private val diagram = GanDiagramView(GanGraph.from(parseJson(json))).apply {
        onSelectionChanged = { selection ->
            details.events = listOf(
                when (selection) {
                    is DiagramSelection.Node -> selection.node.toDetail()
                    is DiagramSelection.Edge -> selection.edge.toDetail()
                    null -> NO_SELECTION
                },
            )
        }
    }

    init {
        name = tabName
        toolTipText = tooltip

        add(
            JTabbedPane().apply {
                addTab("Diagram", HorizontalSplitPane(FlatScrollPane(diagram), details, resizeWeight = 0.75))
                addTab("JSON", FlatScrollPane(textArea))
            },
            "push, grow",
        )
    }

    /**
     * Parses and verifies that the provided text is valid gateway network diagram JSON
     * @throws ToolOpeningException to abort opening the file early
     */
    private fun parseJson(json: String): DiagramModel {
        val model: DiagramModel = try {
            JSON.decodeFromString(serializer(), json)
        } catch (e: SerializationException) {
            throw ToolOpeningException("Error parsing GAN diagram JSON", e)
        }
        if (model.connections.isEmpty()) {
            throw ToolOpeningException("Incomplete GAN diagram - no connections")
        }
        return model
    }

    companion object {
        private val JSON = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        private val NO_SELECTION = Detail(
            title = "No selection",
            message = "Click on a gateway or connection to see its details.",
        )

        private fun GanNode.toDetail() = Detail(
            title = if (isLocal) "Local Gateway Properties" else "Remote Gateway Properties",
            body = detailLines(
                "Gateway Name" to gatewayName,
                "Redundancy Mode" to if (isRedundant) "Redundant-$redundantRole" else null,
                "Network Address" to if (isLocal) null else networkAddress ?: "N/A",
                "Version" to (version?.ifEmpty { null } ?: "N/A"),
                "Edition" to when (edition) {
                    null -> "N/A"
                    "" -> "standard"
                    else -> edition
                },
            ),
        )

        private fun GanEdge.toDetail(): Detail = if (status == EdgeStatus.Proxy) {
            Detail(
                title = "Connection Properties",
                body = detailLines(
                    "Server Status" to connection.connectionStatus,
                    "Proxy Connection" to "true",
                    "Routed Through" to source.gatewayName,
                    "Target" to target.gatewayName,
                ),
            )
        } else {
            val (origin, destination) = if (isIncoming) target to source else source to target
            Detail(
                title = "Connection Properties",
                body = detailLines(
                    "Connection Status" to connection.connectionStatus,
                    "Direction" to if (isIncoming) "Incoming" else "Outgoing",
                    "Origin" to origin.gatewayName,
                    "Target" to destination.gatewayName,
                    "Ping Time" to connection.pingTime.orNA(::formatPing),
                    "Outgoing Rate" to connection.outgoingRate.orNA(::formatRate),
                    "Incoming Rate" to connection.incomingRate.orNA(::formatRate),
                    "SSL Connection" to (connection.ssl?.toString() ?: "N/A"),
                    "Fault Count" to (connection.faultCount?.toString() ?: "N/A"),
                ),
            )
        }

        private fun Double?.orNA(format: (Double) -> String) = if (this == null || this < 0) "N/A" else format(this)

        /**
         * Aligned "key: value" lines; lines with a null value don't apply and are omitted.
         */
        private fun detailLines(vararg lines: Pair<String, String?>): List<String> {
            val present = lines.filter { (_, value) -> value != null }
            val width = present.maxOf { (key, _) -> key.length } + 2
            return present.map { (key, value) -> "$key:".padEnd(width) + value }
        }
    }
}

data object GatewayNetworkTool : ClipboardTool {
    override val serialKey = "gan-diagram"
    override val title = "Gateway Network Diagram"
    override val description = "GAN Diagram (.json, .txt)"
    override val icon = FlatSVGIcon("icons/bx-sitemap.svg")
    override val extensions: Array<String> = arrayOf("json", "txt")
    override val respectsEncoding = true

    override fun open(data: String) = GatewayNetworkViewer(
        tabName = "Paste at ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}",
        tooltip = "",
        json = data,
    )

    override fun open(path: Path): ToolPanel {
        val diagram: String = path.useLines(DefaultEncoding.currentValue) { lines ->
            lines.filter(String::isNotBlank).joinToString(separator = "\n")
        }

        return GatewayNetworkViewer(
            tabName = path.name,
            tooltip = path.toString(),
            json = diagram,
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    override val filter: FileFilter = FileFilter(description) { path ->
        (path.name.endsWith("json") || path.name.endsWith("txt")) &&
            runCatching {
                (Json.decodeFromStream<JsonElement>(path.inputStream()) as? JsonObject)?.containsKey("connections") == true
            }.getOrDefault(false)
    }
}
