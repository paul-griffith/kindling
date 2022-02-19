package io.github.paulgriffith.thread.model

import kotlinx.serialization.Serializable

@Serializable
data class ThreadDump(
    val version: String,
    val threads: List<Thread>,
)
