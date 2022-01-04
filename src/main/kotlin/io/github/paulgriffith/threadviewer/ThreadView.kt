package io.github.paulgriffith.threadviewer

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.threadviewer.model.ThreadDump
import io.github.paulgriffith.threadviewer.model.ThreadInfo
import io.github.paulgriffith.utils.Detail
import io.github.paulgriffith.utils.DetailsPane
import io.github.paulgriffith.utils.EDT_SCOPE
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.ToolPanel
import io.github.paulgriffith.utils.debounce
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.miginfocom.swing.MigLayout
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.io.path.extension
import kotlin.io.path.inputStream

@OptIn(ExperimentalSerializationApi::class)
class ThreadView(override val path: Path) : ToolPanel() {
    private val threadDump = run {
        when {
            path.extension.equals("json", ignoreCase = true) -> {
                JSON.decodeFromStream<ThreadDump>(path.inputStream())
            }
            else -> throw IllegalArgumentException("Unable to parse $path; unexpected file type")
        }
    }

    private val details = DetailsPane()
    private val mainTable = ThreadsTable(threadDump.threads)
    private val stateTable = StateTable(threadDump.threads.groupingBy(ThreadInfo::state).eachCount())
    private val systemTable = SystemTable(threadDump.threads.groupingBy(ThreadInfo::system).eachCount())

    private val searchField = JTextField(30).apply {
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = search.invoke(text)
            override fun removeUpdate(e: DocumentEvent) = search.invoke(text)
            override fun changedUpdate(e: DocumentEvent) = search.invoke(text)

            val search = debounce<String?>(
                waitMs = 100L,
                coroutineScope = EDT_SCOPE,
            ) { text ->
                if (text.isNullOrEmpty()) {
                    mainTable.rowSorter.rowFilter = null
                } else {
                    mainTable.rowSorter.rowFilter = RowFilter.regexFilter(text)
                }
            }
        })
    }

    init {
        mainTable.selectionModel.apply {
            addListSelectionListener {
                if (!it.valueIsAdjusting) {
                    selectedIndices
                        .map { row ->
                            mainTable.model.getValueAt(mainTable.convertRowIndexToModel(row), 0) as Int
                        }
                        .mapNotNull { id -> threadDump.threadsById[id] }
                        .let { threads ->
                            details.events = threads.map { thread ->
                                Detail(
                                    title = thread.name,
                                    details = mapOf(
                                        "id" to thread.id.toString(),
                                    ),
                                    body = thread.stacktrace,
                                )
                            }
                        }
                }
            }
        }

        fun <T> List<String?>.toRowFilter(): RowFilter<T, Int> {
            return RowFilter.regexFilter(
                this.joinToString(separator = "|", prefix = "^", postfix = "$", transform = String?::orEmpty)
            )
        }

        stateTable.selectionModel.apply {
            addListSelectionListener { selectionEvent ->
                if (!selectionEvent.valueIsAdjusting) {
                    if (isSelectionEmpty) {
                        mainTable.rowSorter.rowFilter = null
                    } else {
                        systemTable.clearSelection()
                        selectedIndices
                            .map { row -> stateTable.model[row, StateModel.State] }
                            .let { states ->
                                mainTable.rowSorter.rowFilter = states.map(Thread.State::name).toRowFilter()
                            }
                    }
                }
            }
        }

        systemTable.selectionModel.apply {
            addListSelectionListener { selectionEvent ->
                if (!selectionEvent.valueIsAdjusting) {
                    if (isSelectionEmpty) {
                        mainTable.rowSorter.rowFilter = null
                    } else {
                        stateTable.clearSelection()
                        selectedIndices
                            .map { row -> systemTable.model[row, SystemModel.System] }
                            .let { systems ->
                                mainTable.rowSorter.rowFilter = systems.toRowFilter()
                            }
                    }
                }
            }
        }

        add(JLabel("Version: ${threadDump.version}"))
        add(JLabel("Search:"), "align right, gap related")
        add(searchField, "align right, width 25%, wrap")
        add(
            JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                JPanel(MigLayout("ins 0, fill", "[shrink][fill]", "fill")).apply {
                    add(FlatScrollPane(systemTable), "width 215")
                    add(FlatScrollPane(mainTable), "wrap, spany 2, push")
                    add(FlatScrollPane(stateTable), "width 215")
                },
                details,
            ).apply {
                resizeWeight = 0.6
            },
            "dock center, span 3"
        )
    }

    override val icon: Icon = FlatSVGIcon("icons/bx-chip.svg")

    companion object {
        internal val JSON = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}
