package io.github.paulgriffith.thread

import io.github.paulgriffith.kindling.thread.model.Thread.Companion.extractPool
import io.github.paulgriffith.kindling.thread.model.ThreadDump
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.blocking.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe

class ThreadViewTests : FunSpec({
    test("Thread JSON deserialization") {
        ThreadDump.fromModernInputStream(requireNotNull(ThreadViewTests::class.java.getResourceAsStream("threadDump.json")))
            .asClue { (version, threads) ->
                version shouldBe "Dev"
                threads.size shouldBe 2
            }
    }
    context("Legacy parsing") {
        test("From webpage") {
            ThreadDump.fromLegacyInputStream(requireNotNull(ThreadViewTests::class.java.getResourceAsStream("legacyWebThreadDump.txt")))
                .asClue { (version, threads) ->
                    version shouldBe "7.9.14 (b2020042813)"
                    threads.size shouldBe 4
                }
        }
        test("From scripting") {
            ThreadDump.fromLegacyInputStream(requireNotNull(ThreadViewTests::class.java.getResourceAsStream("legacyScriptThreadDump.txt")))
                .asClue { (version, threads) ->
                    version shouldBe "8.1.1 (b2020120808)"
                    threads.size shouldBe 3
                }
        }
    }

    test("Thread Pool Parsing Tests") {
        forAll(
            row("gateway-logging-sqlite-appender", null),
            row("gateway-performance-metric-history-1", "gateway-performance-metric-history"),
            row("gateway-performance-metric-history-2", "gateway-performance-metric-history"),
            row("gateway-shared-exec-engine-11", "gateway-shared-exec-engine"),
            row("gateway-storeforward-pipeline[postgres]-engine[PrimarySFEngine]", null),
            row("gateway.tags.subscriptionmodel-1", "gateway.tags.subscriptionmodel"),
            row("HSQLDB Timer @32c91059", null),
            row("HttpClient-1-SelectorManager", null),
            row("HttpClient@25d4330e-1129", "HttpClient@25d4330e"),
            row("HttpClient@25d4330e-1315", "HttpClient@25d4330e"),
            row("milo-netty-event-loop-0", "milo-netty-event-loop"),
            row("opc-ua-executor-18", "opc-ua-executor"),
            row("opc-ua-executor-19", "opc-ua-executor"),
            row("Session-Scheduler-782a4fff-1", "Session-Scheduler-782a4fff"),
            row("webserver-1114", "webserver"),
            row( // maybe someday
                "webserver-43-acceptor-0@25cd7918-ServerConnector@1d7f7be7{SSL, (ssl, http/1.1)}{0.0.0.0:8060}",
                null
            ),
            row("AsyncSocketIOSession[I/O]-1", "AsyncSocketIOSession[I/O]")
        ) { name, pool ->
            extractPool(name) shouldBe pool
        }
    }
})
