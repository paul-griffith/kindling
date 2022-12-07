package io.github.paulgriffith.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.CheckBoxListSelectionModel
import io.github.paulgriffith.kindling.core.MultiTool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.thread.model.Stacktrace
import io.github.paulgriffith.kindling.thread.model.Thread
import io.github.paulgriffith.kindling.thread.model.ThreadDump
import io.github.paulgriffith.kindling.thread.model.ThreadLifespan
import io.github.paulgriffith.kindling.thread.model.ThreadModel
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.Column
import io.github.paulgriffith.kindling.utils.EDT_SCOPE
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.ReifiedJXTable
import io.github.paulgriffith.kindling.utils.attachPopupMenu
import io.github.paulgriffith.kindling.utils.escapeHtml
import io.github.paulgriffith.kindling.utils.getValue
import io.github.paulgriffith.kindling.utils.installColumnFactory
import io.github.paulgriffith.kindling.utils.selectedRowIndices
import io.github.paulgriffith.kindling.utils.toHtmlLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXSearchField
import org.jdesktop.swingx.decorator.ColorHighlighter
import org.jdesktop.swingx.table.ColumnControlButton
import java.awt.Desktop
import java.awt.Rectangle
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.SortOrder
import javax.swing.UIManager
import kotlin.io.path.inputStream
import kotlin.io.path.name

class MultiThreadView(
    private val paths: List<Path>
) : ToolPanel() {
    private val threadDumps = paths.mapNotNull { path -> ThreadDump.fromStream(path.inputStream()) }.toList()

    private val mainTable = run {
        val initialModel = ThreadModel(threadDumps.toLifespanList())
        ReifiedJXTable(initialModel, initialModel.columns).apply {
            setSortOrder(ThreadModel.idIndex, SortOrder.ASCENDING)
            addHighlighter(
                ColorHighlighter(
                    { _, adapter ->
                        threadDumps.any { threadDump ->
                            threadDump.deadlockIds.contains(adapter.getValue(ThreadModel.idIndex))
                        }
                    },
                    UIManager.getColor("Actions.Red"),
                    null,
                ),
            )

            fun markAllWithSameValue(property: Column<ThreadLifespan, *>) {
                val selectedPropertyValue = model[selectedRowIndices().first(), property]
                for (lifespan: ThreadLifespan in model.threadData) {
                    if (property.getValue(lifespan) == selectedPropertyValue) {
                        lifespan.forEach { it?.marked = true }
                    }
                }

                model.fireTableDataChanged()
            }

            fun filterAllWithSameValue(property: Column<ThreadLifespan, *>) {
                val firstRow = selectedRowIndices().first()
                when (property) {
                    ThreadModel.SingleThreadColumns.State -> {
                        val state = model[firstRow, ThreadModel.SingleThreadColumns.State]
                        stateList.select(state)
                    }

                    ThreadModel.SingleThreadColumns.System -> {
                        val system = model[firstRow, ThreadModel.SingleThreadColumns.System]
                        if (system != null) {
                            systemList.select(system)
                        }
                    }

                    ThreadModel.SingleThreadColumns.Pool -> {
                        val pool = model[firstRow, ThreadModel.SingleThreadColumns.Pool]
                        if (pool != null) {
                            poolList.select(pool)
                        }
                    }

                    ThreadModel.MultiThreadColumns.System -> {
                        val system = model[firstRow, ThreadModel.MultiThreadColumns.System]
                        if (system != null) {
                            systemList.select(system)
                        }
                    }

                    ThreadModel.MultiThreadColumns.Pool -> {
                        val pool = model[firstRow, ThreadModel.MultiThreadColumns.Pool]
                        if (pool != null) {
                            poolList.select(pool)
                        }
                    }
                }
            }

            actionMap.put(
                "${ColumnControlButton.COLUMN_CONTROL_MARKER}.clearAllMarks",
                Action(name = "Clear All Marks") {
                    for (lifespan in model.threadData) {
                        lifespan.forEach { thread ->
                            thread?.marked = false
                        }
                    }
                },
            )

            attachPopupMenu { event ->
                val rowAtPoint = rowAtPoint(event.point)
                selectionModel.setSelectionInterval(rowAtPoint, rowAtPoint)

                val filterable = when(model.isSingleContext) {
                    true -> ThreadModel.SingleThreadColumns.filterableColumns
                    false -> ThreadModel.MultiThreadColumns.filterableColumns
                }

                val markable = when(model.isSingleContext) {
                    true -> ThreadModel.SingleThreadColumns.markableColumns
                    false -> ThreadModel.MultiThreadColumns.markableColumns
                }

                JPopupMenu().apply {
                    add(
                        JMenu("Filter all with same...").apply {
                            for (column in filterable) {
                                add(
                                    Action(column.header) {
                                        filterAllWithSameValue(column)
                                    },
                                )
                            }
                        },
                    )
                    add(
                        JMenu("Mark all with same...").apply {
                            for (column in markable) {
                                add(
                                    Action(column.header) {
                                        markAllWithSameValue(column)
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    // Details pane replaced with comparison pane
    private var comparison = ThreadComparisonPane(threadDumps.size)

    private var allThreads: List<Thread> = threadDumps.flatMap { it.threads }

    private var poolList = PoolList(allThreads.groupingBy(Thread::pool).eachCount())
    private var systemList = SystemList(allThreads.groupingBy(Thread::system).eachCount())
    private var stateList = StateList(allThreads.groupingBy(Thread::state).eachCount())
    private val threadDumpCheckboxList = ThreadDumpCheckboxList(paths)
    private var listModelsAdjusting = false

    private val exportButton = JMenuBar().apply {
        val fileName = "threaddump_${threadDumps[0].version}_${threadDumps[0].hashCode()}"
        add(exportMenu(fileName) { mainTable.model })
        isVisible = mainTable.model.isSingleContext
    }

    private val searchField = JXSearchField("Search")

    private val filters: List<(thread: Thread) -> Boolean> = buildList {
        add { thread ->
            thread.state in stateList.checkBoxListSelectedValues
        }
        add { thread ->
            thread.system in systemList.checkBoxListSelectedValues
        }
        add { thread ->
            thread.pool in poolList.checkBoxListSelectedValues
        }
        add { thread ->
            val query = searchField.text ?: return@add true

            val idMatches = thread.id.toString().contains(query)
            val nameMatches = thread.name.contains(query, ignoreCase = true)
            val systemMatches = thread.system != null && thread.system.contains(query, ignoreCase = true)
            val scopeMatches = thread.scope != null && thread.scope.contains(query, ignoreCase = true)
            val stateMatches = thread.state.name.contains(query, ignoreCase = true)
            val stackMatches = thread.stacktrace.any { stack -> stack.contains(query, ignoreCase = true) }

            idMatches || nameMatches || systemMatches || scopeMatches || stateMatches || stackMatches
        }
    }

    private fun updateData() {
        println("Update data called")
        BACKGROUND.launch {
            val selectedThreadDumps = threadDumps.filterIndexed { index, _ ->
                index + 1 in threadDumpCheckboxList.checkBoxListSelectedIndices
            }

            val filteredThreadDumps = selectedThreadDumps.map {
                ThreadDump(
                    it.version,
                    it.threads.filter { thread ->
                        filters.all { filter -> filter(thread) }
                    }
                )
            }
            val data = filteredThreadDumps.toLifespanList()

            EDT_SCOPE.launch {
                if (mainTable.selectionModel.isSelectionEmpty) {
                    val newModel = ThreadModel(data)
                    mainTable.installColumnFactory(newModel.columns, false)
                    mainTable.model = newModel
                    mainTable.createDefaultColumnsFromModel()
                } else {
                    /* Maintain selection when model changes */
                    val previousSelectedIndex = mainTable.convertRowIndexToModel(mainTable.selectedRow)
                    val selectedID = mainTable.model.getValueAt(previousSelectedIndex, ThreadModel.idIndex)

                    val newModel = ThreadModel(data)
                    mainTable.installColumnFactory(newModel.columns, false)
                    mainTable.model = newModel
                    mainTable.createDefaultColumnsFromModel()

                    val newSelectedIndex = mainTable.model.threadData.indexOfFirst { lifespan ->
                        selectedID in lifespan.mapNotNull { thread -> thread?.id }
                    }
                    if (newSelectedIndex > -1) {
                        val newSelectedViewIndex = mainTable.convertRowIndexToView(newSelectedIndex)
                        mainTable.selectionModel.setSelectionInterval(0, newSelectedViewIndex)
                        mainTable.scrollRectToVisible(Rectangle(mainTable.getCellRect(newSelectedViewIndex, 0, true)))
                    }
                }

                exportButton.isVisible = mainTable.model.isSingleContext
            }
        }
    }

    init {
        name = if (mainTable.model.isSingleContext) {
            paths[0].fileName.toString()
        } else {
            "${threadDumps.size} thread dumps"
        }

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
        threadDumpCheckboxList.checkBoxListSelectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                listModelsAdjusting = true

                val selectedThreadDumps = threadDumps.filterIndexed { index, _ ->
                    index + 1 in (event.source as CheckBoxListSelectionModel).selectedIndices
                }
                allThreads = selectedThreadDumps.flatMap { it.threads }

                poolList.refreshData(allThreads.groupingBy(Thread::pool).eachCount())
                stateList.refreshData(allThreads.groupingBy(Thread::state).eachCount())

                listModelsAdjusting = false
                updateData()
            }
        }

        searchField.addActionListener {
            updateData()
        }

        comparison.addBlockerSelectedListener { selectedID ->
            for (i in 0 until mainTable.model.rowCount) {
                if (selectedID == mainTable.model.getValueAt(i, ThreadModel.idIndex)) {
                    val rowIndex = mainTable.convertRowIndexToView(i)
                    mainTable.selectionModel.setSelectionInterval(0, rowIndex)
                    mainTable.scrollRectToVisible(Rectangle(mainTable.getCellRect(rowIndex, 0, true)))
                    break
                }
            }
        }

        add(JLabel("Version: ${threadDumps[0].version}"))

        if (!mainTable.model.isSingleContext) {
            add(threadDumpCheckboxList, "growx, pushx")
        }

        add(exportButton, "align right, gapright 8")

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
            if (!event.valueIsAdjusting && !listModelsAdjusting) {
                println("${event.source} calling update data...")
                updateData()
            }
        }
    }
    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)

        private fun List<ThreadDump>.toLifespanList(): List<ThreadLifespan> {
            val map: MutableMap<Int, MutableList<Thread?>> = mutableMapOf()
            forEachIndexed { index, threadDump ->
                threadDump.threads.forEach {
                    if (!map.contains(it.id)) {
                        map[it.id] = MutableList(this.size) { null }
                    }
                    map[it.id]!![index] = it
                    val condition = map[it.id]!!.distinctBy { thread -> thread?.name }.filterNotNull().size == 1
                    require(condition) { "Thread dumps must be from the same gateway and runtime instance." }
                }
            }
            return map.values.toList()
        }

        private val classnameRegex = """(.*/)?(?<path>.*)\..*\(.*\)""".toRegex()
        private fun Stacktrace.linkify(version: String): Stacktrace {
            val foundEntry = classMapsByVersion.entries.find { (classMapVersion, _) ->
                classMapVersion in version
            } ?: return this

            return stack.map { line ->
                val escapedLine = line.escapeHtml()
                val matchResult = classnameRegex.find(line)

                if (matchResult != null) {
                    val path by matchResult.groups
                    val url = foundEntry.value[path.value] as String?
                    if (url != null) {
                        return@map escapedLine.toHtmlLink(url)
                    }
                }
                escapedLine
            }.let(::Stacktrace)
        }
    }
}

object MultiThreadViewer : MultiTool {
    override val title = "Thread Viewer"
    override val description = "Thread dump (.json or .txt) files"
    override val icon = FlatSVGIcon("icons/bx-file.svg")
    override val extensions = listOf("json", "txt")
    override fun open(path: Path): ToolPanel = open(listOf(path))
    override fun open(paths: List<Path>): ToolPanel {
        return MultiThreadView(paths.sorted())
    }
//    override fun open(data: String): ToolPanel {
//        return MultiThreadView(
//            tabName = "Paste at ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}",
//            data = data.byteInputStream(),
//            fromFile = false,
//        )
//    }
}

class ThreadViewerProxy : MultiTool by MultiThreadViewer