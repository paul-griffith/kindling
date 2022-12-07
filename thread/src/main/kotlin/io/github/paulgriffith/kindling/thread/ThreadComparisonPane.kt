package io.github.paulgriffith.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.extras.components.FlatLabel
import com.formdev.flatlaf.extras.components.FlatTextPane
import io.github.paulgriffith.kindling.core.DetailsPane
import io.github.paulgriffith.kindling.thread.MultiThreadView.Companion.linkify
import io.github.paulgriffith.kindling.thread.model.Thread
import io.github.paulgriffith.kindling.thread.MultiThreadView.Companion.toDetail
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.ScrollingTextPane
import io.github.paulgriffith.kindling.utils.add
import io.github.paulgriffith.kindling.utils.attachPopupMenu
import io.github.paulgriffith.kindling.utils.escapeHtml
import io.github.paulgriffith.kindling.utils.firstNotNull
import io.github.paulgriffith.kindling.utils.getAll
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXTaskPane
import org.jdesktop.swingx.JXTaskPaneContainer
import java.awt.Desktop
import java.text.DecimalFormat
import java.util.EventListener
import javax.swing.JCheckBox
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingConstants
import javax.swing.event.EventListenerList
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit
import kotlin.properties.Delegates


class ThreadComparisonPane(private val numThreadDumps: Int, private val version: String) : JPanel(MigLayout("fill, ins 0")) {

    private val percent = DecimalFormat("0.00'%'")
    private val listeners = EventListenerList()

    var threads: List<Thread?> by Delegates.observable(emptyList()) { _, _, _ ->
        updateData()
    }

    val threadContainers: List<ThreadContainer> = List(numThreadDumps) { ThreadContainer() }

    // Only need one instance of Header for our one instance of comparison pane
    private val header = object : JPanel(MigLayout("fill, ins 3")) {
        private val nameLabel = FlatTextPane().apply {
            editorKit = ComparisonEditorKit()
            isEditable = false
        }
        val nullCheckBox = JCheckBox("Show Null Threads", true).apply {
            horizontalTextPosition = SwingConstants.LEFT
            addActionListener {
                threadContainers.forEach {
                    if (it.thread == null) it.isVisible = isSelected
                }
            }
        }

        init {
            add(nameLabel, "pushx, growx")
            add(nullCheckBox, "east")
        }

        fun setText(thread: Thread) {
            nameLabel.text = buildString {
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

    init {
        add(header, "growx, spanx")
        add(
            FlatScrollPane(
                JPanel(MigLayout("fill, hidemode 3, ins 0")).apply {
                    threadContainers.forEach { add(it, "push, grow, sizegroup") }
                }
            ), "push, grow"
        )
    }

    private fun updateData() {
        header.setText(threads.firstNotNull())
        threads.forEachIndexed { index, thread ->
            threadContainers[index].thread = thread
        }
        if (threads.size < numThreadDumps) {
            (threads.size until numThreadDumps).forEach {
                threadContainers[it].isVisible = false
            }
        }
    }

    inner class ThreadContainer(t: Thread? = null) : JXTaskPaneContainer() {
        var thread: Thread? by Delegates.observable(t) { _, _, _ ->
            updateThreadInfo()
        }

        private val titleLabel = FlatLabel()
        private val detailsButton = FlatButton().apply {
            icon = detailImage
            isVisible = thread != null
            toolTipText = "Open in details popup"
            addActionListener {
                thread?.let {
                    JFrame().apply {
                        title = "Thread Details"
                        setSize(900, 500)
                        isResizable = true
                        setLocationRelativeTo(null)
                        add(
                            DetailsPane().apply {
                                events = listOf(it.toDetail(version))
                            }
                        )
                        isVisible = true
                    }
                }
            }
        }
        private val blockerButton = FlatButton().apply {
            text = "null"
            icon = blockedImage
            toolTipText = "Jump to blocking thread"
            isVisible = false
            addActionListener {
                fireBlockerSelectedEvent(text.toInt())
            }
        }

        private val monitorsTaskPane = JXTaskPane().apply { isCollapsed = true }
        private val synchronizersTaskPane = JXTaskPane().apply { isCollapsed = true }
        private val stackTaskPane = JXTaskPane().apply { isCollapsed = true }

        private val monitors = ScrollingTextPane()
        private val synchronizers = ScrollingTextPane()
        private val stackTrace = ScrollingTextPane().apply {
            textPane.addHyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    val desktop = Desktop.getDesktop()
                    desktop.browse(event.url.toURI())
                }
            }
        }

        init {
            add(
                JPanel(MigLayout("fill, ins 5, hidemode 3")).apply {
                    add(detailsButton)
                    add(titleLabel, "push, grow, gapleft 8")
                    add(blockerButton)
                },
            )

            add(monitorsTaskPane.apply { add(monitors) })
            add(synchronizersTaskPane.apply { add(synchronizers) })
            add(stackTaskPane.apply { add(stackTrace) })
        }

        private fun updateThreadInfo() {
            isVisible = header.nullCheckBox.isSelected || thread != null

            // Update label text
            titleLabel.text =
                """<html><div>
                   <b>${thread?.state ?: "NO THREAD"}</b> - ${percent.format(thread?.cpuUsage ?: 0.0)}
                   </div></html>""".trimIndent()

            if (thread?.blocker?.owner != null) {
                blockerButton.isVisible = true
                blockerButton.text = thread!!.blocker!!.owner.toString()
            } else {
                blockerButton.isVisible = false
            }
            detailsButton.isVisible = thread != null

            // Update text for thread props
            monitorsTaskPane.title = "Locked Monitors: ${thread?.lockedMonitors?.size ?: 0}"
            monitors.text =
                thread
                ?.lockedMonitors
                ?.joinToString("\n", "<html><pre>", "</pre></html>") { monitor ->
                    if (monitor.frame == null) {
                        "lock: ${monitor.lock}"
                    } else {
                        "lock: ${monitor.lock}\n${monitor.frame}"
                    }
            }

            synchronizersTaskPane.title = "Synchronizers: ${thread?.lockedSynchronizers?.size ?: 0}"
            synchronizers.text =
                thread
                ?.lockedSynchronizers
                ?.joinToString("\n", "<html><pre>", "</pre></html>", transform = String::escapeHtml)

            stackTaskPane.title = "Stacktrace: ${thread?.stacktrace?.size ?: 0}"
            stackTrace.text =
                thread?.stacktrace?.linkify(version)
                ?.joinToString("\n", "<html><pre>", "</pre></html>") { (text, link) ->
                    if (link != null) {
                        """<a href="$link">$text</a>"""
                    } else {
                        text
                    }
                }
        }
    }

    companion object {
        private val blockedImage = FlatSVGIcon("icons/bx-block.svg").derive(12, 12)
        private val detailImage = FlatSVGIcon("icons/bx-link-external.svg").derive(12, 12)
    }

    fun addBlockerSelectedListener(listener: BlockSelectedEventListener) {
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
                """.trimIndent(),
            )
        }
    }
}
