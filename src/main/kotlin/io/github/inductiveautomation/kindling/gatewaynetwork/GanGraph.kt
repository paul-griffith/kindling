package io.github.inductiveautomation.kindling.gatewaynetwork

import io.github.inductiveautomation.kindling.gatewaynetwork.DiagramModel.Connection
import kotlin.math.roundToLong

enum class Edition(val iconName: String) {
    Standard("standard"),
    Edge("edge"),
    Maker("maker"),
    Cloud("cloud"),
    ;

    companion object {
        fun of(edition: String?): Edition = entries.find { it.iconName.equals(edition, ignoreCase = true) } ?: Standard
    }
}

class GanNode(
    val id: String,
    val label: String,
    val gatewayName: String,
    val isLocal: Boolean,
    val redundantRole: String?,
    val edition: String?,
    val version: String?,
    val networkAddress: String?,
) {
    val isRedundant: Boolean = isRedundant(redundantRole)
}

enum class EdgeStatus { Running, Faulted, Proxy }

/**
 * A gateway network connection. [source] is always the end closer to the local gateway, regardless of the actual
 * direction of the connection; [isIncoming] carries the real direction.
 */
class GanEdge(
    val source: GanNode,
    val target: GanNode,
    val connection: Connection,
) {
    val isIncoming: Boolean = connection.direction.equals("Incoming", ignoreCase = true)

    private val pingTime: Double? = connection.pingTime?.takeIf { it >= 0 }

    val status: EdgeStatus = when {
        !isRunning(connection.connectionStatus) -> EdgeStatus.Faulted
        pingTime != null -> EdgeStatus.Running
        connection.isProxy -> EdgeStatus.Proxy
        else -> EdgeStatus.Running
    }

    val label: String = when {
        !isRunning(connection.connectionStatus) -> "(${connection.connectionStatus})"
        pingTime != null -> buildString {
            append(formatPing(pingTime))
            if (connection.incomingRate != null && connection.outgoingRate != null) {
                append('\n').append(formatRate(connection.incomingRate + connection.outgoingRate))
            }
        }
        connection.isProxy -> "(proxy)"
        else -> "N/A"
    }
}

class GanGraph(val nodes: List<GanNode>, val edges: List<GanEdge>) {
    companion object {
        fun from(model: DiagramModel): GanGraph {
            val local = GanNode(
                id = LOCAL_ID,
                label = withRole(model.localGatewayName, model.redundantRole),
                gatewayName = model.localGatewayName,
                isLocal = true,
                redundantRole = model.redundantRole,
                edition = model.edition,
                version = model.version,
                networkAddress = null,
            )

            // the same remote gateway can be reached along multiple paths; nodes are unique by connection ID
            val nodes = linkedMapOf(local.id to local)
            val edges = mutableListOf<GanEdge>()

            fun addConnections(parent: GanNode, connections: List<Connection>) {
                for (connection in connections) {
                    val node = nodes.getOrPut(connection.connectionId) { remoteNode(connection) }
                    edges += GanEdge(parent, node, connection)
                    addConnections(node, connection.connections)
                }
            }
            addConnections(local, model.connections)

            return GanGraph(nodes.values.toList(), edges)
        }

        private fun remoteNode(connection: Connection): GanNode {
            val name = if (connection.systemName == "N/A" && connection.networkAddress != null) {
                connection.networkAddress
            } else {
                connection.systemName
            }
            return GanNode(
                id = connection.connectionId,
                label = withRole(name, connection.redundantRole),
                gatewayName = connection.systemName,
                isLocal = false,
                redundantRole = connection.redundantRole,
                edition = connection.edition,
                version = connection.version,
                networkAddress = connection.networkAddress,
            )
        }

        // connection IDs are "name|uuid", so this can't collide with a remote gateway
        private const val LOCAL_ID = "<local>"
    }
}

fun isRedundant(role: String?) = role == "Master" || role == "Backup"

fun isRunning(status: String) = status == "Running" || status == "Connected"

private fun withRole(name: String, role: String?) = if (isRedundant(role)) "$name\n($role)" else name

fun formatPing(pingTime: Double): String = "${pingTime.roundToLong()} ms"

/**
 * Formats a rate in decimal byte units, e.g. "12 KB/sec".
 */
fun formatRate(bytesPerSecond: Double): String {
    var value = bytesPerSecond
    var unit = 0
    while (value >= 1000 && unit < BYTE_UNITS.lastIndex) {
        value /= 1000
        unit++
    }
    return "${value.roundToLong()} ${BYTE_UNITS[unit]}/sec"
}

private val BYTE_UNITS = listOf("B", "KB", "MB", "GB", "TB")
