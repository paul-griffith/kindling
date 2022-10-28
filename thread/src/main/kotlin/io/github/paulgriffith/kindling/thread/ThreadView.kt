package io.github.paulgriffith.kindling.thread

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.jidesoft.swing.CheckBoxListSelectionModel
import io.github.paulgriffith.kindling.core.ClipboardTool
import io.github.paulgriffith.kindling.core.Detail
import io.github.paulgriffith.kindling.core.DetailsPane
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.thread.model.Stacktrace
import io.github.paulgriffith.kindling.thread.model.Thread
import io.github.paulgriffith.kindling.thread.model.ThreadDump
import io.github.paulgriffith.kindling.thread.model.ThreadModel
import io.github.paulgriffith.kindling.thread.model.ThreadModel.ThreadColumns.Id
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.Column
import io.github.paulgriffith.kindling.utils.EDT_SCOPE
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.ReifiedJXTable
import io.github.paulgriffith.kindling.utils.attachPopupMenu
import io.github.paulgriffith.kindling.utils.escapeHtml
import io.github.paulgriffith.kindling.utils.getValue
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
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSplitPane
import javax.swing.SortOrder
import javax.swing.UIManager
import kotlin.io.path.inputStream
import kotlin.io.path.name

class ThreadView(
    private val tabName: String,
    data: InputStream,
    private val fromFile: Boolean,
) : ToolPanel() {
    private val threadDump: ThreadDump = requireNotNull(ThreadDump.fromStream(data))

    private val details = DetailsPane()
    private val mainTable = ReifiedJXTable(ThreadModel(threadDump.threads), ThreadModel).apply {
        setSortOrder(ThreadModel[Id], SortOrder.ASCENDING)
        addHighlighter(
            ColorHighlighter(
                { _, adapter ->
                    threadDump.deadlockIds.contains(adapter.getValue(ThreadModel.ThreadColumns[Id]))
                },
                UIManager.getColor("Actions.Red"),
                null,
            ),
        )

        fun markAllWithSameValue(property: Column<Thread, *>) {
            val selectedPropertyValue = model[selectedRowIndices().first(), property]

            for (thread in model.threads) {
                if (property.getValue(thread) == selectedPropertyValue) {
                    thread.marked = true
                }
            }

            model.fireTableDataChanged()
        }

        fun filterAllWithSameValue(property: Column<Thread, *>) {
            val firstRow = selectedRowIndices().first()
            when (property) {
                ThreadModel.State -> {
                    val state = model[firstRow, ThreadModel.State]
                    stateList.select(state)
                }

                ThreadModel.System -> {
                    val system = model[firstRow, ThreadModel.System]
                    if (system != null) {
                        systemList.select(system)
                    }
                }

                ThreadModel.Pool -> {
                    val pool = model[firstRow, ThreadModel.Pool]
                    if (pool != null) {
                        poolList.select(pool)
                    }
                }
            }
        }

        actionMap.put(
            "${ColumnControlButton.COLUMN_CONTROL_MARKER}.clearAllMarks",
            Action(name = "Clear All Marks") {
                for (thread in model.threads) {
                    thread.marked = false
                }
            },
        )

        attachPopupMenu { event ->
            val rowAtPoint = rowAtPoint(event.point)
            selectionModel.setSelectionInterval(rowAtPoint, rowAtPoint)
            JPopupMenu().apply {
                add(
                    JMenu("Filter all with same...").apply {
                        for (column in ThreadModel.filterableColumns) {
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
                        for (column in ThreadModel.markableColumns) {
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

    private val stateList = StateList(threadDump.threads.groupingBy(Thread::state).eachCount())
    private val systemList = SystemList(threadDump.threads.groupingBy(Thread::system).eachCount())
    private val poolList = PoolList(threadDump.threads.groupingBy(Thread::pool).eachCount())

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
        name = tabName
        toolTipText = tabName

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

        stateList.checkBoxListSelectionModel.bind()
        systemList.checkBoxListSelectionModel.bind()
        poolList.checkBoxListSelectionModel.bind()

        searchField.addActionListener {
            updateData()
        }

        add(JLabel("Version: ${threadDump.version}"))

        add(
            JMenuBar().apply {
                val fileName = "threaddump_${threadDump.version}_${threadDump.hashCode()}"
                add(exportMenu(fileName) { mainTable.model })
            },
            "align right, gapright 8",
        )

        add(searchField, "align right, wmin 300, wrap")
        add(
            JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                JPanel(MigLayout("ins 0, fill")).apply {
                    add(FlatScrollPane(stateList), "grow, w 200::25%, hmin 100")
                    add(FlatScrollPane(mainTable), "push, grow 200, spany 3, wrap")
                    add(FlatScrollPane(systemList), "grow, w 200::25%, hmin 100, wrap")
                    add(FlatScrollPane(poolList), "grow, w 200::25%, hmin 100")
                },
                details,
            ).apply {
                resizeWeight = 0.5
            },
            "push, grow, span",
        )
    }

    private fun Thread.toDetail(): Detail = Detail(
        title = name,
        details = mapOf(
            "id" to id.toString(),
        ),
        body = buildList {
            if (blocker != null) {
                add("waiting for:")
                add(blocker.toString())
            }

            if (lockedMonitors.isNotEmpty()) {
                add("locked monitors:")
                lockedMonitors.forEach { monitor ->
                    if (monitor.frame != null) {
                        add(monitor.frame)
                    }
                    add(monitor.lock.escapeHtml())
                }
            }

            if (lockedSynchronizers.isNotEmpty()) {
                add("locked synchronizers:")
                addAll(lockedSynchronizers.map { it.escapeHtml() })
            }

            if (stacktrace.isNotEmpty()) {
                add("stacktrace:")
                addAll(stacktrace.linkify(threadDump.version))
            }
        },
    )

    override val icon: Icon = ThreadViewer.icon

    override fun customizePopupMenu(menu: JPopupMenu) {
        menu.addSeparator()
        if (fromFile) {
            menu.add(
                Action(name = "Open in External Editor") {
                    Desktop.getDesktop().open(File(tabName))
                },
            )
        }
        menu.add(
            exportMenu { mainTable.model },
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

object ThreadViewer : ClipboardTool {
    override val title = "Thread Dump"
    override val description = "Thread dump .json or .txt files"
    override val icon = FlatSVGIcon("icons/bx-chip.svg")
    override val extensions = listOf("json", "txt")

    override fun open(path: Path): ToolPanel {
        return ThreadView(
            tabName = path.name,
            data = path.inputStream(),
            fromFile = true,
        )
    }

    override fun open(data: String): ToolPanel {
        return ThreadView(
            tabName = "Paste at ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}",
            data = data.byteInputStream(),
            fromFile = false,
        )
    }
}

class ThreadViewerProxy : ClipboardTool by ThreadViewer
