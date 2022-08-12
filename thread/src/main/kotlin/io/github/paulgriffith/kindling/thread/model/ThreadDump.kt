package io.github.paulgriffith.kindling.thread.model

import kotlinx.serialization.Serializable

@Serializable
data class ThreadDump(
    val version: String,
    val threads: List<Thread>
)
