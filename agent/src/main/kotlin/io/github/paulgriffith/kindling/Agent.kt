package io.github.paulgriffith.kindling

import com.inductiveautomation.ignition.gateway.model.GatewayContext

object Agent {
    @JvmStatic
    fun agentmain(agentArgs: String) {
        val gatewayClass = Class.forName("com.inductiveautomation.ignition.gateway.IgnitionGateway")
        val gatewayInstance = gatewayClass.getMethod("get").invoke(null) as GatewayContext
        println(gatewayInstance.telemetryManager)
    }
}
