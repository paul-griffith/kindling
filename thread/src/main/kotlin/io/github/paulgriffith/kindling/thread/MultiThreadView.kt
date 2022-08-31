package io.github.paulgriffith.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.CheckBoxListSelectionModel
import io.github.paulgriffith.kindling.core.MultiTool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.thread.model.MultiThreadModel
import io.github.paulgriffith.kindling.thread.model.Thread
import io.github.paulgriffith.kindling.thread.model.ThreadDump
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.EDT_SCOPE
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.ReifiedJXTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import java.awt.Desktop
import java.awt.Rectangle
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.SortOrder
import kotlin.io.path.inputStream
import kotlin.io.path.name

class MultiThreadView(
    private val paths: List<Path>
) : ToolPanel() {

    // List of thread dumps instead of single thread dump
    private val threadDumps = paths.mapNotNull { path ->
        ThreadDump.fromStream(path.inputStream())
    }.toMutableList()

    private val mainTable =
        ReifiedJXTable(MultiThreadModel(createGroupedThreadList(threadDumps)), MultiThreadModel.ThreadColumns).apply {
            setSortOrder(MultiThreadModel[MultiThreadModel.Id], SortOrder.ASCENDING)
        }

    // Details pane replaced with comparison pane
    private var comparison = ThreadComparisonPane(threadDumps.size)

    // TODO: Look into optimizing this so that allThreads doesn't sit in memory forever.
    private val allThreads: List<Thread> = buildList { threadDumps.forEach { addAll(it.threads) } }

    private var poolList = PoolList(allThreads.groupingBy(Thread::pool).eachCount())
    private var stateList = StateList(allThreads.groupingBy(Thread::state).eachCount())

    private val threadDumpList = ThreadDumpList(paths)

    private val searchField = JXSearchField("Search")

    private val filters: List<(thread: Thread) -> Boolean> = buildList {
        add { thread ->
            thread.pool in poolList.checkBoxListSelectedValues
        }
        add { thread ->
            thread.state in stateList.checkBoxListSelectedValues
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

//    private fun threadDumpFilter: Boolean = ()

    private fun updateData() {
        BACKGROUND.launch {
            val selectedThreadDumps = threadDumps.filterIndexed { index, _ ->
                index + 1 in threadDumpList.checkBoxListSelectedIndices
            }
            val filteredThreadDumps: List<ThreadDump> = buildList {
                selectedThreadDumps.forEach {
                    add(
                        ThreadDump(
                            it.version,
                            it.threads.filter { thread ->
                                filters.all { filter -> filter(thread) }
                            }
                        )
                    )
                }
            }
            val data = createGroupedThreadList(filteredThreadDumps)
            EDT_SCOPE.launch {
                if (mainTable.selectionModel.isSelectionEmpty) {
                    mainTable.model = MultiThreadModel(data)
                    comparison.removeAll()
                    comparison.revalidate()
                } else {
                    val selectedIndex = mainTable.convertRowIndexToModel(mainTable.selectedRow)
                    val selectedID = mainTable.model[selectedIndex, MultiThreadModel.Id]

                    mainTable.model = MultiThreadModel(data)

                    val index = mainTable.model.threadData.indexOfFirst { threads ->
                        selectedID in threads.mapNotNull { thread -> thread?.id }
                    }
                    if (index != -1) {
                        val viewIndex = mainTable.convertRowIndexToView(index)
                        mainTable.selectionModel.setSelectionInterval(0, viewIndex)
                        mainTable.scrollRectToVisible(Rectangle(mainTable.getCellRect(viewIndex, 0, true)))
                    } else {
                        comparison.removeAll()
                        comparison.revalidate()
                        comparison.repaint()
                    }
                }
            }
        }
    }

    init {
        name = "${threadDumps.size} thread dumps"
        toolTipText = paths.joinToString("\n") { path -> path.name }

        mainTable.selectionMode = ListSelectionModel.SINGLE_SELECTION
        mainTable.selectionModel.apply {
            addListSelectionListener {
                if (!it.valueIsAdjusting && !isSelectionEmpty) {
                    val viewIndex = selectedIndices[0]
                    val modelIndex = mainTable.convertRowIndexToModel(viewIndex)
                    comparison.threads = mainTable.model.threadData[modelIndex]
                }
            }
        }

        poolList.checkBoxListSelectionModel.bind()
        stateList.checkBoxListSelectionModel.bind()
        threadDumpList.checkBoxListSelectionModel.bind()

        searchField.addActionListener {
            updateData()
        }

        comparison.addBlockSelectedListener { selectedID ->
            for (i in 0 until mainTable.model.rowCount) {
                if (selectedID == mainTable.model[i, MultiThreadModel.Id]) {
                    val rowIndex = mainTable.convertRowIndexToView(i)
                    mainTable.selectionModel.setSelectionInterval(0, rowIndex)
                    mainTable.scrollRectToVisible(Rectangle(mainTable.getCellRect(rowIndex, 0, true)))
                    break
                }
            }

        }

        add(JLabel("Version: ${threadDumps[0].version}"))
        add(threadDumpList, "growx, pushx")
        add(searchField, "align right, wmin 300, wrap")
        add(
            JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                JPanel(MigLayout("ins 0, fill")).apply {
                    add(FlatScrollPane(stateList), "grow, w 200::25%, height 100!")
                    add(FlatScrollPane(mainTable), "wrap, spany 3, push, grow 100 100")
                    add(FlatScrollPane(poolList), "grow, w 200::25%, h 1500")
                },
                comparison,
            ).apply {
                resizeWeight = 0.5
                isOneTouchExpandable = true
            },
            "push, grow, span"
        )
    }

    override val icon: Icon = MultiThreadViewer.icon
    private fun createGroupedThreadList(dumps: List<ThreadDump>): List<List<Thread?>> {
        val map: MutableMap<Int, MutableList<Thread?>> = mutableMapOf()
        dumps.forEachIndexed { index, threadDump ->
            threadDump.threads.forEach {
                if (!map.contains(it.id)) {
                    map[it.id] = mutableListOf<Thread?>().apply {
                        for (i in 1..dumps.size) add(null)
                    }
                }
                map[it.id]!![index] = it
                val condition = map[it.id]!!.distinctBy { thread -> thread?.name }.filterNotNull().size == 1
                require(condition) { "Thread dumps must be from the same gateway and runtime instance." }
            }
        }
        return map.values.toList()
    }

    override fun customizePopupMenu(menu: JPopupMenu) {
        menu.addSeparator()
        menu.add(
            Action(name = "Open in External Editors") {
                val desktop = Desktop.getDesktop()
                paths.forEach { desktop.open(it.toFile()) }
            }
        )
    }

    private fun CheckBoxListSelectionModel.bind() {
        addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                updateData()
            }
        }
    }

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)
    }
}

object MultiThreadViewer : MultiTool {
    override val title = "Multi Thread Viewer"
    override val description = "multiple thread(.json or .txt) files"
    override val icon = FlatSVGIcon("icons/bx-file.svg")
    override val extensions = listOf("json", "txt")
    override fun open(path: Path): ToolPanel = open(listOf(path))
    override fun open(paths: List<Path>): ToolPanel {
        return MultiThreadView(paths.sorted())
    }
}

class MultiThreadViewerProxy : MultiTool by MultiThreadViewer