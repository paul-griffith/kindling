package io.github.paulgriffith.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.core.Detail
import io.github.paulgriffith.kindling.core.DetailsPane
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.thread.model.Thread
import io.github.paulgriffith.kindling.thread.model.ThreadDump
import io.github.paulgriffith.kindling.thread.model.ThreadModel
import io.github.paulgriffith.kindling.thread.model.ThreadModel.ThreadColumns.Id
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.EDT_SCOPE
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.ReifiedJXTable
import io.github.paulgriffith.kindling.utils.exportMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import java.awt.Desktop
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSplitPane
import javax.swing.SortOrder
import kotlin.io.path.inputStream
import kotlin.io.path.name

@OptIn(ExperimentalSerializationApi::class)
class ThreadView(private val path: Path) : ToolPanel() {
    private val threadDump = JSON.decodeFromStream<ThreadDump>(path.inputStream())

    private val details = DetailsPane()
    private val mainTable = ReifiedJXTable(ThreadModel(threadDump.threads), ThreadModel).apply {
        setSortOrder(ThreadModel[Id], SortOrder.ASCENDING)
    }

    private val stateList = StateList(threadDump.threads.groupingBy(Thread::state).eachCount())
    private val systemList = SystemList(threadDump.threads.groupingBy(Thread::system).eachCount())

    private val searchField = JXSearchField("Search")

    private val filters: List<(thread: Thread) -> Boolean> = buildList {
        add { thread ->
            thread.state in stateList.checkBoxListSelectedValues
        }
        add { thread ->
            thread.system in systemList.checkBoxListSelectedValues
        }
        add { thread ->
            val query = searchField.text
            query != null &&
                thread.name.contains(query, ignoreCase = true) ||
                thread.system != null && thread.system.contains(query, ignoreCase = true) ||
                thread.scope != null && thread.scope.contains(query, ignoreCase = true) ||
                thread.state.name.contains(query, ignoreCase = true) ||
                thread.stacktrace.any { stack -> stack.contains(query, ignoreCase = true) }
        }
    }

    private fun updateData() {
        BACKGROUND.launch {
            val data = threadDump.threads.filter { thread ->
                filters.all { filter -> filter(thread) }
            }
            EDT_SCOPE.launch {
                mainTable.model = ThreadModel(data)
            }
        }
    }

    init {
        name = path.name
        toolTipText = path.toString()

        mainTable.selectionModel.apply {
            addListSelectionListener {
                if (!it.valueIsAdjusting) {
                    details.events = selectedIndices
                        .map { viewRow -> mainTable.convertRowIndexToModel(viewRow) }
                        .map { modelRow -> mainTable.model.threads[modelRow] }
                        .map { thread -> thread.toDetail() }
                }
            }
        }

        systemList.checkBoxListSelectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateData()
            }
        }

        stateList.checkBoxListSelectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateData()
            }
        }

        searchField.addActionListener {
            updateData()
        }

        add(JLabel("Version: ${threadDump.version}"))

        add(
            JMenuBar().apply {
                add(exportMenu { mainTable.model })
            },
            "align right, gapright 8"
        )

        add(searchField, "align right, wmin 300, wrap")
        add(
            JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                JPanel(MigLayout("ins 0, fill")).apply {
                    add(FlatScrollPane(stateList), "push, grow, width 200!")
                    add(FlatScrollPane(mainTable), "wrap, spany 2, push, grow")
                    add(FlatScrollPane(systemList), "push, grow, width 200!")
                },
                details
            ).apply {
                resizeWeight = 0.5
            },
            "push, grow, span"
        )
    }

    private fun Thread.toDetail(): Detail = Detail(
        title = name,
        details = mapOf(
            "id" to id.toString()
        ),
        body = buildList {
            if (blocker != null) {
                add("waiting for:")
                add(blocker.toString())
            }

            if (lockedMonitors.isNotEmpty()) {
                add("locked monitors:")
                lockedMonitors.forEach { monitor ->
                    add(monitor.frame)
                    add(monitor.lock)
                }
            }

            if (lockedSynchronizers.isNotEmpty()) {
                add("locked synchronizers:")
                addAll(lockedSynchronizers)
            }

            if (stacktrace.isNotEmpty()) {
                add("stacktrace:")
                addAll(stacktrace)
            }
        }
    )

    override val icon: Icon = ThreadViewer.icon

    override fun customizePopupMenu(menu: JPopupMenu) {
        menu.addSeparator()
        menu.add(
            Action(name = "Open in External Editor") {
                Desktop.getDesktop().open(path.toFile())
            }
        )
        menu.add(
            exportMenu { mainTable.model }
        )
    }

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)

        internal val JSON = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}

object ThreadViewer : Tool {
    override val title = "Thread Dump"
    override val description = "Thread dump .json files"
    override val icon = FlatSVGIcon("icons/bx-chip.svg")
    override val extensions = listOf("json")
    override fun open(path: Path): ToolPanel = ThreadView(path)
}

class ThreadViewerProxy : Tool by ThreadViewer
