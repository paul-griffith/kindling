package io.github.paulgriffith.kindling.thread.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.lang.Thread.State
import java.lang.management.ThreadInfo

typealias Stacktrace = List<String>

@Serializable
data class Thread(
    val id: Int,
    val name: String,
    val state: State,
    @SerialName("daemon")
    val isDaemon: Boolean,
    @Serializable(with = NoneAsNullStringSerializer::class)
    val system: String? = null,
    val scope: String? = null,
    val cpuUsage: Double? = null,
    val lockedMonitors: List<Monitors> = emptyList(),
    val lockedSynchronizers: List<String> = emptyList(),
    @SerialName("waitingFor")
    val blocker: Blocker? = null,
    val stacktrace: Stacktrace = emptyList(),
) {
    constructor(data: ThreadInfo) : this(
        id = data.threadId.toInt(),
        name = data.threadName,
        state = data.threadState,
        isDaemon = data.isDaemon,
//        cpuUsage = null,
//        lockedMonitors = data.lockedMonitors.map {
//            Monitors(
//                lock = it.toString(),
//                frame = it.lockedStackFrame?.toString(),
//            )
//        },
//        lockedSynchronizers = data.lockedSynchronizers.map(LockInfo::toString),
        stacktrace = data.stackTrace.map(StackTraceElement::toString),
        blocker = data.lockInfo?.let { lockInfo ->
            Blocker(
                lock = lockInfo.toString(),
                owner = data.lockOwnerId.takeIf { it != -1L }?.toInt(),
            )
        },
    )

    var marked: Boolean = false

    val pool: String? = extractPool(name)

    @Serializable
    data class Monitors(
        val lock: String,
        val frame: String? = null,
    )

    @Serializable
    data class Blocker(
        val lock: String,
        val owner: Int? = null,
    ) {
        override fun toString(): String = if (owner != null) {
            "$lock (owned by $owner)"
        } else {
            lock
        }
    }

    companion object {
        private val threadPoolRegex = "(?<pool>.+)-\\d+\$".toRegex()

        internal fun extractPool(name: String): String? {
            return threadPoolRegex.find(name)?.groups?.get("pool")?.value
        }
    }
}
