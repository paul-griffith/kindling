package io.github.paulgriffith.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTextPane
import io.github.paulgriffith.kindling.thread.model.Thread
import io.github.paulgriffith.kindling.utils.ScrollableTextPane
import io.github.paulgriffith.kindling.utils.add
import io.github.paulgriffith.kindling.utils.firstNotNull
import io.github.paulgriffith.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXTaskPane
import org.jdesktop.swingx.JXTaskPaneContainer
import java.awt.Image
import java.text.DecimalFormat
import java.util.*
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.event.EventListenerList
import javax.swing.text.html.HTMLEditorKit
import kotlin.properties.Delegates

class ThreadComparisonPane(numThreadDumps: Int) : JPanel(MigLayout("fill")) {

    // TODO: Hide specific threads
    private val percent = DecimalFormat("0.00%")
    private val listeners = EventListenerList()

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
        add(headerText, "push, grow")
        add(nullCheckBox)
        add(mergedCheckBox)
    }

    private val expansionMatrix = Array(numThreadDumps) { BooleanArray(3) { true } }

    private fun updateData() {
        removeAll()

        threads.run {
            headerText.text = getHeaderString(firstNotNull())
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
                            val threadText =
                                "<b>${it?.state ?: "NO THREAD"}</b> - ${percent.format((it?.cpuUsage ?: 0.0) / 100.0)}"
                            add(
                                JLabel(
                                    "<html><div style='text-align: center;'>$threadText</div></html>",
                                    SwingConstants.CENTER
                                ), "growx"
                            )
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
        val img: Image = FlatSVGIcon("icons/bx-block.svg").image
        val newImg = img.getScaledInstance(12, 12, Image.SCALE_REPLICATE)
        threads.forEachIndexed { index, thread ->
            add(
                JXTaskPaneContainer().apply {
                    val threadText =
                        "<b>${thread?.state ?: "NO THREAD"}</b> - ${percent.format((thread?.cpuUsage ?: 0.0) / 100.0)}"
                    add(
                        JPanel(MigLayout("fill")).apply {
                            add(
                                JLabel("<html><div>$threadText</div></html>"),
                                "push, grow, height 22"
                            )
                            if (thread?.blocker?.owner != null) {
                                add(JButton().apply {
                                    text = thread.blocker.owner.toString()
                                    icon = ImageIcon(newImg)
                                    addActionListener {
                                        fireBlockerSelectedEvent(text.toInt())
                                    }
                                }, "grow, shrink")
                            }
                        }
                    )

                    add(
                        JXTaskPane().apply {
                            val threadPropIndex = 0
                            val size = thread?.lockedMonitors?.size ?: 0
                            title = "Locked Monitors ($size)"
                            if (size > 0) {
                                add(
                                    ScrollableTextPane(
                                        buildList {
                                            thread?.lockedMonitors?.forEach {
                                                add("lock: ${it.lock}")
                                                add("frame: ${it.frame}")
                                            }
                                        }
                                    )
                                )
                            }
                            isCollapsed = expansionMatrix[index][threadPropIndex]
                            addPropertyChangeListener("collapsed") { event ->
                                expansionMatrix[index][threadPropIndex] = event.newValue as Boolean
                            }
                        },
                        "span"
                    )

                    add(
                        JXTaskPane().apply {
                            val threadPropIndex = 1
                            val size = thread?.lockedSynchronizers?.size ?: 0
                            title = "Locked Synchronizers ($size)"
                            if (size > 0) add(ScrollableTextPane(thread!!.lockedSynchronizers))
                            isCollapsed = expansionMatrix[index][threadPropIndex]
                            addPropertyChangeListener("collapsed") { event ->
                                expansionMatrix[index][threadPropIndex] = event.newValue as Boolean
                            }
                        },
                        "span"
                    )

                    add(
                        JXTaskPane().apply {
                            val threadPropIndex = 2
                            val size = thread?.stacktrace?.size ?: 0
                            title = "Stack Trace (${thread?.stacktrace?.size ?: 0})"
                            if (size > 0) add(ScrollableTextPane(thread!!.stacktrace))
                            isCollapsed = expansionMatrix[index][threadPropIndex]
                            addPropertyChangeListener("collapsed") { event ->
                                expansionMatrix[index][threadPropIndex] = event.newValue as Boolean
                            }
                        },
                        "span"
                    )
                },
                "push, grow, sizegroup"
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

    fun addBlockSelectedListener(listener: BlockSelectedEventListener) {
        listeners.add(listener)
    }

    private fun fireBlockerSelectedEvent(threadID: Int) {
        for (listener in listeners.getAll<BlockSelectedEventListener>()) {
            listener.onBlockSelected(threadID)
        }
    }
}

fun interface BlockSelectedEventListener : EventListener {
    fun onBlockSelected(threadID: Int)
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
