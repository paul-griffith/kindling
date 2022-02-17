package io.github.paulgriffith.log

import java.time.Instant

data class Event(
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
        ERROR;

        companion object {
            private val firstChars = values().associateBy { it.name.first() }

            fun valueOf(char: Char): Level = firstChars.getValue(char)
        }
    }
}
