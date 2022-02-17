package io.github.paulgriffith.log

import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.time.ZoneOffset

class WrapperLogParsingTests : FunSpec({
    test("Simple case") {
        val case = """
            INFO   | jvm 1    | 2021/03/14 08:49:25 | I [t.h.q.PartitionManager        ] [07:49:25]: Ignition Created Tag history partition sqlt_data_1_20210314 
        """.trimIndent().lineSequence()

        LogPanel.parseLogs(case).asClue { events ->
            events.shouldHaveSize(1)
            events.single().asClue { event ->
                event.timestamp shouldBe LocalDateTime.of(2021, 3, 14, 8, 49, 25).toInstant(ZoneOffset.UTC)
                event.level shouldBe Event.Level.INFO
                event.logger shouldBe "t.h.q.PartitionManager"
                event.message shouldBe "[07:49:25]: Ignition Created Tag history partition sqlt_data_1_20210314"
                event.stacktrace.shouldBeEmpty()
                event.mdc.shouldBeEmpty()
            }
        }
    }

    test("Warning with attached exception") {
        val case = """
            INFO   | jvm 1    | 2021/03/12 06:33:01 | W [S.S.TagHistoryDatasourceSink  ] [05:33:01]: There is a problem checking the tag history database tables during initialization of the store and forward engine which could prevent tag history data from being forwarded properly. Trying again in 60 seconds. 
            INFO   | jvm 1    | 2021/03/12 06:33:01 | com.inductiveautomation.ignition.gateway.datasource.FaultedDatabaseConnectionException: The database connection 'IgnitionData' is FAULTED. See Gateway Status for details.
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at com.inductiveautomation.ignition.gateway.datasource.DatasourceManagerImpl.getConnectionImpl(DatasourceManagerImpl.java:201)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at com.inductiveautomation.ignition.gateway.datasource.DatasourceImpl.getConnection(DatasourceImpl.java:243)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at com.inductiveautomation.gateway.tags.history.storage.TagHistoryDatasourceSink.checkTables(TagHistoryDatasourceSink.java:1538)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at com.inductiveautomation.gateway.tags.history.storage.TagHistoryDatasourceSink.initialize(TagHistoryDatasourceSink.java:270)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at com.inductiveautomation.ignition.common.execution.impl.BasicExecutionEngine${"$"}ThrowableCatchingRunnable.run(BasicExecutionEngine.java:518)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at java.base/java.util.concurrent.Executors${"$"}RunnableAdapter.call(Unknown Source)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at java.base/java.util.concurrent.FutureTask.run(Unknown Source)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at java.base/java.util.concurrent.ScheduledThreadPoolExecutor${"$"}ScheduledFutureTask.run(Unknown Source)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at java.base/java.util.concurrent.ThreadPoolExecutor${"$"}Worker.run(Unknown Source)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at java.base/java.lang.Thread.run(Unknown Source)
        """.trimIndent().lineSequence()
        LogPanel.parseLogs(case).asClue { events ->
            events.shouldHaveSize(1)
        }
    }
})
