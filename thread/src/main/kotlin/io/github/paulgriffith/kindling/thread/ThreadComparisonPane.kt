package io.github.paulgriffith.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.extras.components.FlatLabel
import com.formdev.flatlaf.extras.components.FlatTextPane
import com.jidesoft.swing.JideButton
import com.jidesoft.swing.JidePopupMenu
import io.github.paulgriffith.kindling.core.DetailsPane
import io.github.paulgriffith.kindling.thread.MultiThreadView.Companion.linkify
import io.github.paulgriffith.kindling.thread.MultiThreadView.Companion.toDetail
import io.github.paulgriffith.kindling.thread.model.Thread
import io.github.paulgriffith.kindling.thread.model.ThreadLifespan
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.ScrollingTextPane
import io.github.paulgriffith.kindling.utils.add
import io.github.paulgriffith.kindling.utils.escapeHtml
import io.github.paulgriffith.kindling.utils.getAll
import io.github.paulgriffith.kindling.utils.tag
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXTaskPane
import org.jdesktop.swingx.JXTaskPaneContainer
import java.awt.Desktop
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.NumberFormat
import java.util.EventListener
import javax.swing.JCheckBoxMenuItem
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.event.EventListenerList
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit
import kotlin.properties.Delegates

class ThreadComparisonPane(
    totalThreadDumps: Int,
    private val version: String,
) : JPanel(MigLayout("fill, ins 0")) {
    private val listeners = EventListenerList()

    var threads: ThreadLifespan by Delegates.observable(emptyList()) { _, _, _ ->
        updateData()
    }

    private val threadContainers: List<ThreadContainer> = List(totalThreadDumps) {
        ThreadContainer(version).apply {
            blockerButton.addActionListener {
                val blocker = blockerButton.blocker
                if (blocker != null) {
                    fireBlockerSelectedEvent(blocker)
                }
            }
        }
    }

    private val header = HeaderPanel()

    init {
        header.addPropertyChangeListener("showNullThreads") { event ->
            for (container in threadContainers) {
                container.isShowNulls = event.newValue as Boolean
            }
        }
        header.addPropertyChangeListener("showEmptyValues") { event ->
            for (container in threadContainers) {
                container.isShowEmptyValues = event.newValue as Boolean
            }
        }

        add(header, "growx, spanx")
        add(
            FlatScrollPane(
                JPanel(MigLayout("fill, hidemode 3, ins 0")).apply {
                    for (container in threadContainers) {
                        add(container, "push, grow, sizegroup")
                    }
                },
            ),
            "push, grow",
        )
    }

    private fun updateData() {
        threads.firstOrNull { it != null }?.let {
            header.setText(it)
        }
        for ((container, thread) in threadContainers.zip(threads)) {
            container.thread = thread
        }
    }

    fun addBlockerSelectedListener(listener: BlockerSelectedEventListener) {
        listeners.add(listener)
    }

    private fun fireBlockerSelectedEvent(threadID: Int) {
        for (listener in listeners.getAll<BlockerSelectedEventListener>()) {
            listener.onBlockerSelected(threadID)
        }
    }

    fun interface BlockerSelectedEventListener : EventListener {
        fun onBlockerSelected(threadId: Int)
    }

    private class ComparisonEditorKit : HTMLEditorKit() {
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

    private class HeaderPanel : JPanel(MigLayout("fill, ins 3")) {
        private val nameLabel = FlatTextPane().apply {
            editorKit = ComparisonEditorKit()
            isEditable = false
        }

        private val settingsMenu = JidePopupMenu().apply {
            add(
                JCheckBoxMenuItem("Show Null Threads", SHOW_NULL_THREADS_DEFAULT).apply {
                    addActionListener {
                        this@HeaderPanel.firePropertyChange("showNullThreads", !isSelected, isSelected)
                    }
                },
            )
            add(
                JCheckBoxMenuItem("Show Empty Values", SHOW_EMPTY_VALUES_DEFAULT).apply {
                    addActionListener {
                        this@HeaderPanel.firePropertyChange("showEmptyValues", !isSelected, isSelected)
                    }
                },
            )
        }
        private val settings = JideButton(FlatSVGIcon("icons/bx-cog.svg")).apply {
            addMouseListener(
                object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        settingsMenu.show(this@apply, e.x, e.y)
                    }
                },
            )
        }

        init {
            add(nameLabel, "pushx, growx")
            add(settings, "east")
        }

        fun setText(thread: Thread) {
            nameLabel.text = buildString {
                tag("html") {
                    tag("b", thread.name)
                    append("<br>")

                    tag("b", "ID: ")
                    append(thread.id)
                    tag("b", " | ")

                    tag("b", "Daemon: ")
                    append(thread.isDaemon)
                    tag("b", " | ")

                    if (thread.system != null) {
                        tag("b", "System: ")
                        append(thread.system)
                        tag("b", " | ")
                    }
                    if (thread.scope != null) {
                        tag("b", "Scope: ")
                        append(thread.scope)
                    }
                }
            }
        }
    }

    private class BlockerButton : FlatButton() {
        var blocker: Int? = null
            set(value) {
                isVisible = value != null
                text = value?.toString()
                field = value
            }

        init {
            icon = blockedIcon
            toolTipText = "Jump to blocking thread"
            isVisible = false
        }
    }

    private class DetailContainer(val prefix: String) : JXTaskPane() {
        var itemCount = 0
            set(value) {
                title = "$prefix: $value"
                field = value
            }

        private val scrollingTextPane = ScrollingTextPane().apply {
            addHyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    val desktop = Desktop.getDesktop()
                    desktop.browse(event.url.toURI())
                }
            }
        }

        var text: String? by scrollingTextPane::text

        init {
            isCollapsed = true
            isAnimated = false

            add(scrollingTextPane)
        }
    }

    private class ThreadContainer(private val version: String) : JXTaskPaneContainer() {
        var thread: Thread? by Delegates.observable(null) { _, _, _ ->
            updateThreadInfo()
        }

        var isShowNulls: Boolean = SHOW_NULL_THREADS_DEFAULT
            set(value) {
                field = value
                updateThreadInfo()
            }

        var isShowEmptyValues: Boolean = SHOW_EMPTY_VALUES_DEFAULT
            set(value) {
                field = value
                updateThreadInfo()
            }

        private val titleLabel = FlatLabel()
        private val detailsButton = FlatButton().apply {
            icon = detailIcon
            toolTipText = "Open in details popup"
            addActionListener {
                thread?.let {
                    JFrame("Thread ${it.id} Details").apply {
                        setSize(900, 500)
                        isResizable = true
                        setLocationRelativeTo(null)
                        add(
                            DetailsPane().apply {
                                events = listOf(it.toDetail(version))
                            },
                        )
                        isVisible = true
                    }
                }
            }
        }

        val blockerButton = BlockerButton()

        private val monitors = DetailContainer("Locked Monitors")
        private val synchronizers = DetailContainer("Synchronizers")
        private val stacktrace = DetailContainer("Stacktrace").apply {
            isCollapsed = false
        }

        init {
            add(
                JPanel(MigLayout("fill, ins 5, hidemode 3")).apply {
                    add(detailsButton)
                    add(titleLabel, "push, grow, gapleft 8")
                    add(blockerButton)
                },
            )

            add(monitors)
            add(synchronizers)
            add(stacktrace)
        }

        private fun updateThreadInfo() {
            isVisible = thread != null || isShowNulls

            // Update label text
            titleLabel.text = buildString {
                tag("html") {
                    tag("div") {
                        tag("b", (thread?.state?.toString() ?: "NO THREAD"))
                        append(" - ")
                        append(percent.format((thread?.cpuUsage ?: 0.0) / 100))
                    }
                }
            }

            blockerButton.blocker = thread?.blocker?.owner
            detailsButton.isVisible = thread != null

            monitors.apply {
                isVisible = isShowEmptyValues || thread?.lockedMonitors?.isNotEmpty() == true
                if (isVisible) {
                    itemCount = thread?.lockedMonitors?.size ?: 0
                    text = thread?.lockedMonitors
                        ?.joinToString(
                            separator = "\n",
                            prefix = "<html><pre>",
                            postfix = "</pre></html>",
                        ) { monitor ->
                            if (monitor.frame == null) {
                                "lock: ${monitor.lock}"
                            } else {
                                "lock: ${monitor.lock}\n${monitor.frame}"
                            }
                        }
                }
            }

            synchronizers.apply {
                isVisible = isShowEmptyValues || thread?.lockedSynchronizers?.isNotEmpty() == true
                if (isVisible) {
                    itemCount = thread?.lockedSynchronizers?.size ?: 0
                    text = thread?.lockedSynchronizers
                        ?.joinToString(
                            separator = "\n",
                            prefix = "<html><pre>",
                            postfix = "</pre></html>",
                            transform = String::escapeHtml,
                        )
                }
            }

            stacktrace.apply {
                isVisible = isShowEmptyValues || thread?.stacktrace?.isNotEmpty() == true
                if (isVisible) {
                    itemCount = thread?.stacktrace?.size ?: 0
                    text = thread?.stacktrace
                        ?.linkify(version)
                        ?.joinToString(
                            separator = "\n",
                            prefix = "<html><pre>",
                            postfix = "</pre></html>",
                        ) { (text, link) ->
                            if (link != null) {
                                """<a href="$link">$text</a>"""
                            } else {
                                text
                            }
                        }
                }
            }
        }
    }

    companion object {
        private val blockedIcon = FlatSVGIcon("icons/bx-block.svg").derive(12, 12)
        private val detailIcon = FlatSVGIcon("icons/bx-link-external.svg").derive(12, 12)

        private val percent = NumberFormat.getPercentInstance()

        private const val SHOW_NULL_THREADS_DEFAULT = true
        private const val SHOW_EMPTY_VALUES_DEFAULT = false
    }
}
