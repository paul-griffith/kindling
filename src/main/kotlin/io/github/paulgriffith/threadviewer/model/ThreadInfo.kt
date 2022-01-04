package io.github.paulgriffith.threadviewer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThreadInfo(
    val id: Int,
    val name: String,
    val state: Thread.State,
    @SerialName("daemon")
    val isDaemon: Boolean,
    @Serializable(with = NoneAsNullStringSerializer::class)
    val system: String? = null,
    val scope: String? = null,
    val cpuUsage: Double? = null,
    val lockedMonitors: List<Monitors> = emptyList(),
    val stacktrace: List<String> = emptyList(),
)
