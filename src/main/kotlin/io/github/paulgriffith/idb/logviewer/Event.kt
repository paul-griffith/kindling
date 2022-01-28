package io.github.paulgriffith.idb.logviewer

import java.time.Instant

data class Event(
    val eventId: Int,
    val timestamp: Instant,
    val message: String,
    val logger: String,
    val thread: String,
    val level: Level,
    val mdc: Map<String, String>,
    val stacktrace: List<String>,
) {
    @Suppress("unused")
    enum class Level {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }
}
