package io.github.paulgriffith.logviewer

import java.time.Instant

data class Event(
    val eventId: Int,
    val timestamp: Instant,
    val message: String,
    val logger: String,
    val thread: String,
    val level: String,
    val mdc: Map<String, String>,
    val stacktrace: List<String>,
)
