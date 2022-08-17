package io.github.paulgriffith.kindling.thread.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.lang.Thread.State

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
    val stacktrace: List<String> = emptyList(),
) {
    var marked: Boolean = false
    val pool: String = with(name) {
        val temp = name.split("[")[0].split("@")[0].split("-")
        if (temp.last().all { c: Char -> c.isDigit() }) {
            temp.subList(0, temp.size - 1).joinToString("-")
        } else {
            temp.joinToString("-")
        }
    }

    @Serializable
    data class Monitors(
        val lock: String,
        val frame: String,
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
        fun createFromLegacyWebPage(trace: MutableList<String>): Thread {
            val threadParts = trace[0].split(" [", "] id=", ", (", ")")
            val isDaemon = threadParts[0] != "Thread"
            val name = threadParts[1]
            val id = threadParts[2]
            val state = threadParts[3]
            val lockedMonitors: MutableList<Monitors> = mutableListOf()
            val lockedSynchronizers: MutableList<String> = mutableListOf()
            var blocker: Blocker? = null
            val stacktrace: MutableList<String> = mutableListOf()
            for (i in 1 until trace.size) {
                when(trace[i].split(":")[0]) {
                    "waiting for" -> {
                        val waiting = trace[i].split("waiting for: ", " (owned by ", ")")
                        blocker = if (waiting.size > 2) {
                            Blocker(lock = waiting[1], owner = waiting[2].toInt())
                        } else {
                            Blocker(lock = waiting[1])
                        }
                    }
                    "owns synchronizer" -> lockedSynchronizers.add(trace[i].split("owns synchronizer: ")[1])
                    "owns monitor" -> lockedMonitors.add(Monitors(lock = trace[i].split("owns monitor: ")[1], frame = ""))
                    else -> stacktrace.add(trace[i])
                }
            }
            return Thread(id.toInt(), name, State.valueOf(state), isDaemon, null, null, null, lockedMonitors, lockedSynchronizers, blocker, stacktrace)
        }

        fun createFromLegacyScript(trace: MutableList<String>): Thread {
            val name = trace[0].replace("\"", "")
            val cpu = trace[1].split("CPU: ", "%")[1].toDouble()
            val state = trace[2].replace("java.lang.Thread.State: ", "")
            val stacktrace: List<String> = if (trace.size > 3) { trace.slice(3 until trace.size) } else { emptyList() }
            return Thread(0, name, State.valueOf(state), false, null, null, cpu, emptyList(), emptyList(), null, stacktrace)
        }
    }
}
