package io.github.inductiveautomation.kindling.gatewaynetwork

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A gateway network diagram, as seen from the local gateway. Each connection's nested [Connection.connections] are
 * the connections that remote gateway reported, so the structure is a tree rooted at the local gateway.
 */
@Serializable
data class DiagramModel(
    val localGatewayName: String,
    val redundantRole: String,
    val version: String,
    val edition: String,
    val connections: List<Connection> = emptyList(),
) {
    @Serializable
    data class Connection(
        val systemName: String,
        val connectionId: String,
        val connectionStatus: String,
        val redundantRole: String,
        val direction: String? = null,
        val isProxy: Boolean = false,
        val ssl: Boolean? = null,
        val pingTime: Double? = null,
        @SerialName("incoming_rate")
        val incomingRate: Double? = null,
        @SerialName("outgoing_rate")
        val outgoingRate: Double? = null,
        val faultCount: Long? = null,
        val networkAddress: String? = null,
        val version: String? = null,
        val edition: String? = null,
        val connections: List<Connection> = emptyList(),
    )
}
