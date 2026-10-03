package io.github.inductiveautomation.kindling.gatewaynetwork

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import java.awt.Dimension

class GanGraphTests :
    FunSpec(
        {
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
            val model = json.decodeFromString<DiagramModel>(
                GanGraphTests::class.java.getResource("sample-diagram.json")!!.readText(),
            )
            val graph = GanGraph.from(model)

            fun node(label: String) = graph.nodes.single { it.label.lines().first() == label }
            fun edges(from: String, to: String) = graph.edges.filter { it.source == node(from) && it.target == node(to) }

            test("Nodes are unique by connection ID") {
                graph.nodes.map { it.label.lines().first() } shouldContainExactly listOf(
                    "Central",
                    "Site-A",
                    "Shared",
                    "Site-B",
                    "Proxied",
                    "10.0.0.9",
                    "Cloud-1",
                )
            }

            test("Every connection is an edge, including parallel and cross connections") {
                graph.edges shouldHaveSize 8
                edges("Central", "Site-A") shouldHaveSize 2
                edges("Site-A", "Shared") shouldHaveSize 1
                edges("Site-B", "Shared") shouldHaveSize 1
            }

            test("Node labels") {
                node("Central").apply {
                    isLocal shouldBe true
                    label shouldBe "Central\n(Master)"
                }
                node("Site-B").label shouldBe "Site-B\n(Backup)"
                // unnamed faulted gateways fall back to their address
                node("10.0.0.9").gatewayName shouldBe "N/A"
            }

            test("Edge status and labels") {
                edges("Central", "Site-A").map { it.label } shouldContainExactly listOf("12 ms\n4 KB/sec", "13 ms\n1 KB/sec")
                edges("Central", "Site-B").single().apply {
                    status shouldBe EdgeStatus.Running
                    isIncoming shouldBe true
                    label shouldBe "5 ms\n2 MB/sec"
                }
                edges("Site-B", "Proxied").single().apply {
                    status shouldBe EdgeStatus.Proxy
                    label shouldBe "(proxy)"
                }
                edges("Central", "10.0.0.9").single().apply {
                    status shouldBe EdgeStatus.Faulted
                    label shouldBe "(Faulted)"
                }
                edges("Central", "Cloud-1").single().label shouldBe "80 ms\n0 B/sec"
            }

            test("Rate formatting") {
                formatRate(999.0) shouldBe "999 B/sec"
                formatRate(1000.0) shouldBe "1 KB/sec"
                formatRate(2_500_000_000.0) shouldBe "3 GB/sec"
            }

            test("Layered layout") {
                val layout = GanLayout.compute(
                    graph,
                    nodeSize = { Dimension(60, 60) },
                    labelSize = { Dimension(40, 24) },
                )

                layout.nodes.keys shouldBe graph.nodes.toSet()
                layout.edges.keys shouldBe graph.edges.toSet()

                val central = layout.nodes.getValue(node("Central"))
                val siteA = layout.nodes.getValue(node("Site-A"))
                val shared = layout.nodes.getValue(node("Shared"))
                siteA.y shouldBeGreaterThan central.y
                shared.y shouldBeGreaterThan siteA.y

                for (bounds in layout.nodes.values) {
                    (bounds.maxX <= layout.width) shouldBe true
                    (bounds.maxY <= layout.height) shouldBe true
                }

                val (first, second) = edges("Central", "Site-A").map { layout.edges.getValue(it).path.bounds2D }
                first shouldNotBe second
            }
        },
    )
