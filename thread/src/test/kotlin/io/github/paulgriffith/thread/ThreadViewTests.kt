package io.github.paulgriffith.thread

import io.github.paulgriffith.kindling.thread.model.Thread.Companion.extractPool
import io.github.paulgriffith.kindling.thread.model.ThreadDump
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.blocking.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream

@OptIn(ExperimentalSerializationApi::class)
class ThreadViewTests : FunSpec({
    test("Thread JSON deserialization") {
        val stream = requireNotNull(ThreadViewTests::class.java.getResourceAsStream("threadDump.json"))
        ThreadDump.JSON.decodeFromStream(ThreadDump.serializer(), stream)
            .asClue { (version, threads) ->
                version shouldBe "Dev"
                threads.size shouldBe 2
            }
    }
    context("Legacy parsing") {
        test("From webpage") {
            ThreadDump.fromInputStream(requireNotNull(ThreadViewTests::class.java.getResourceAsStream("legacyWebThreadDump.txt")))
                .asClue { (version, threads) ->
                    version shouldBe "7.9.14 (b2020042813)"
                    threads.size shouldBe 4
                }
        }
        test("From scripting") {
            ThreadDump.fromInputStream(requireNotNull(ThreadViewTests::class.java.getResourceAsStream("legacyScriptThreadDump.txt")))
                .asClue { (version, threads) ->
                    version shouldBe "8.1.1 (b2020120808)"
                    threads.size shouldBe 3
                }
        }
    }

    test("Thread Pool Parsing Tests") {
        forAll(
            row("gateway-logging-sqlite-appender", "gateway-logging-sqlite-appender"),
            row("gateway-performance-metric-history-1", "gateway-performance-metric-history"),
            row("gateway-performance-metric-history-2", "gateway-performance-metric-history"),
            row("gateway-shared-exec-engine-11", "gateway-shared-exec-engine"),
            row("gateway-storeforward-pipeline[postgres]-engine[PrimarySFEngine]", "gateway-storeforward-pipeline[postgres]-engine[PrimarySFEngine]"),
            row("gateway.tags.subscriptionmodel-1", "gateway.tags.subscriptionmodel"),
            row("HSQLDB Timer @32c91059", "HSQLDB Timer @32c91059"),
            row("HttpClient-1-SelectorManager", "HttpClient-1-SelectorManager"),
            row("HttpClient@25d4330e-1129", "HttpClient@25d4330e"),
            row("HttpClient@25d4330e-1315", "HttpClient@25d4330e"),
            row("milo-netty-event-loop-0", "milo-netty-event-loop"),
            row("opc-ua-executor-18", "opc-ua-executor"),
            row("opc-ua-executor-19", "opc-ua-executor"),
            row("Session-Scheduler-782a4fff-1", "Session-Scheduler-782a4fff"),
            row("webserver-1114", "webserver"),
            row( // maybe someday
                "webserver-43-acceptor-0@25cd7918-ServerConnector@1d7f7be7{SSL, (ssl, http/1.1)}{0.0.0.0:8060}",
                "webserver-43-acceptor-0@25cd7918-ServerConnector@1d7f7be7{SSL, (ssl, http/1.1)}{0.0.0.0:8060}"
            ),
            row("AsyncSocketIOSession[I/O]-1", "AsyncSocketIOSession[I/O]")
        ) { name, pool ->
            extractPool(name) shouldBe pool
        }
    }
})
