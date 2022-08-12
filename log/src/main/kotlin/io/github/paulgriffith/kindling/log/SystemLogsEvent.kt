package io.github.paulgriffith.kindling.log

import java.time.Instant

sealed interface LogEvent {
    val timestamp: Instant
    val message: String
    val logger: String
}

data class WrapperLogEvent(
    override val timestamp: Instant,
    override val message: String,
    override val logger: String = STDOUT,
    val level: Level? = null,
    val stacktrace: List<String> = emptyList()
) : LogEvent {
    companion object {
        const val STDOUT = "STDOUT"
    }
}

data class SystemLogsEvent(
    override val timestamp: Instant,
    override val message: String,
    override val logger: String,
    val thread: String,
    val level: Level,
    val mdc: Map<String, String>,
    val stacktrace: List<String>
) : LogEvent

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
