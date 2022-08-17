package io.github.paulgriffith.kindling.thread

import com.formdev.flatlaf.extras.components.FlatTextPane
import io.github.paulgriffith.kindling.thread.model.Thread
import io.github.paulgriffith.kindling.utils.ScrollableTextPane
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXTaskPane
import org.jdesktop.swingx.JXTaskPaneContainer
import java.text.DecimalFormat
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.text.html.HTMLEditorKit
import kotlin.properties.Delegates

class ThreadComparisonPane : JPanel(MigLayout("fill")) {

    // TODO: Hide specific threads
    // TODO: Add merge/
    private val percent = DecimalFormat("0.00%")
    var threads: List<Thread?> by Delegates.observable(emptyList()) { _, _, _ ->
        updateData()
    }

    private var headerText = FlatTextPane().apply {
        isEditable = false
        editorKit = ComparisonEditorKit()
    }

    private val nullCheckBox = JCheckBox("Show Null Threads", true).apply {
        horizontalTextPosition = SwingConstants.LEFT
        addActionListener {
            updateData()
        }
    }

    private val mergedCheckBox = JCheckBox("Merge", false).apply {
        horizontalTextPosition = SwingConstants.LEFT
        addActionListener {
            updateData()
        }
    }

    private val header = JPanel(MigLayout("fill")).apply {
        add(headerText, "west")
        add(nullCheckBox, "east")
        add(mergedCheckBox, "east")
    }

    private fun updateData() {
        removeAll()

        threads.run {
            headerText.text = getHeaderString(firstNotNullOf { it })

            add(header, "pushx, growx, span")
            if (nullCheckBox.isSelected) this else filterNotNull()
        }.also {
            if (mergedCheckBox.isSelected) buildMerged(it) else buildUnmerged(it)
        }

        revalidate()
        repaint()
    }

    private fun buildMerged(threads: List<Thread?>) {
        add(
            JXTaskPaneContainer().apply {

                // Add Panel with Thread Labels
                add(
                    JPanel(MigLayout("fill")).apply {
//                        background = Color(0, 0, 0, 0)
                        isOpaque = false
                        threads.forEach {
                            val threadText = "<b>${it?.state ?: "NO THREAD"}</b> - ${percent.format((it?.cpuUsage ?: 0.0) / 100.0)}"
                            add(JLabel("<html><div style='text-align: center;'>$threadText</div></html>", SwingConstants.CENTER), "growx")
                        }
                    },
                    "growx"
                )
                add(
                    JXTaskPane().apply {
                        layout = MigLayout("fill")
                        title = "Locked Monitors"
//                        add(JPanel(MigLayout("fill")).apply {
                        threads.forEach {
                            add(
                                ScrollableTextPane(
                                    buildList {
                                        it?.lockedMonitors?.forEach {
                                            add("lock: ${it.lock}")
                                            add("frame: ${it.frame}")
                                        }
                                    }
                                ),
                                "grow, width 0:100:"
                            )
                        }
                        isCollapsed = true
                    }
                )
                add(
                    JXTaskPane().apply {
                        layout = MigLayout("fill")
                        title = "Locked Synchronizers"
                        threads.forEach {
                            add(ScrollableTextPane(it?.lockedSynchronizers ?: emptyList()), "grow, width 0:100:")
                        }
                        isCollapsed = true
                    }
                )
                add(
                    JXTaskPane().apply {
                        layout = MigLayout("fill")
                        title = "Blocked by"
                        threads.forEach {
                            val blocker: String? = it?.blocker?.owner?.toString()
                            if (blocker != null) {
                                add(ScrollableTextPane(listOf(blocker)), "grow, width 0:100:")
                            } else {
                                add(ScrollableTextPane(emptyList()), "grow, width 0:100:")
                            }
                        }
                        isCollapsed = true
                    }
                )
                add(
                    JXTaskPane().apply {
                        layout = MigLayout("fill")
                        title = "Stack Trace"
                        threads.forEach {
                            add(ScrollableTextPane(it?.stacktrace ?: emptyList()), "grow, width 0:100:")
                        }
                        isCollapsed = true
                    }
                )
            },
            "push, grow"
        )
    }
    private fun buildUnmerged(threads: List<Thread?>) {
        threads.forEach {
            add(
                JXTaskPaneContainer().apply {
                    val threadText = "<b>${it?.state}</b> - ${percent.format((it?.cpuUsage ?: 0.0) / 100.0)}"
                    add(JLabel("<html><div>$threadText</div></html>", SwingConstants.CENTER), "span, grow, debug")
                    add(
                        JXTaskPane().apply {
                            val size = it?.lockedMonitors?.size ?: 0
                            title = "Locked Monitors ($size)"
                            if (size > 0) {
                                add(
                                    ScrollableTextPane(
                                        buildList {
                                            it?.lockedMonitors?.forEach {
                                                add("lock: ${it.lock}")
                                                add("frame: ${it.frame}")
                                            }
                                        }
                                    )
                                )
                            }
                            isCollapsed = true
                        },
                        "span"
                    )
                    add(
                        JXTaskPane().apply {
                            val size = it?.lockedSynchronizers?.size ?: 0
                            title = "Locked Synchronizers ($size)"
                            if (size > 0) add(ScrollableTextPane(it!!.lockedSynchronizers))
                            isCollapsed = true
                        },
                        "span"
                    )
                    add(
                        JXTaskPane().apply {
                            title = "Blocked by"
                            it?.blocker?.owner?.toString()?.let {
                                add(ScrollableTextPane(listOf(it)))
                            }
//                    if (owner != null) add(ScrollableTextPane(listOf(owner)))
                            isCollapsed = true
                        },
                        "span"
                    )
                    add(
                        JXTaskPane().apply {
                            val size = it?.stacktrace?.size ?: 0
                            title = "Stack Trace (${it?.stacktrace?.size ?: 0})"
                            if (size > 0) add(ScrollableTextPane(it!!.stacktrace))
                            isCollapsed = true
                        },
                        "span"
                    )
                },
                "push, grow"
            )
        }
    }

    private fun getHeaderString(thread: Thread): String {
        return buildString {
            // Thread name
            append("<html>")
            append("<b>${thread.name}</b>")
            append("<br>")

            append("<b>ID:</b> ${thread.id}<b> | </b>")
            append("<b>Daemon:</b> ${thread.isDaemon}<b> | </b>")
            thread.system?.let { append("<b>System:</b> $it<b> | </b>") }
            thread.scope?.let { append("<b>Scope:</b> $it") }
            append("</html>")
        }
    }
}

class ComparisonEditorKit : HTMLEditorKit() {
    init {
        styleSheet.apply {
            //language=CSS
            addRule(
                """
                b {
                    font-size: larger;
                }
                pre { 
                    font-size: 10px; 
                }
                object { 
                    padding-left: 16px; 
                }
                """.trimIndent()
            )
        }
    }
}
