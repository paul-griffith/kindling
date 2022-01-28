package io.github.paulgriffith.threadviewer

import com.formdev.flatlaf.extras.components.FlatTextField
import io.github.paulgriffith.threadviewer.model.ThreadDump
import io.github.paulgriffith.threadviewer.model.ThreadInfo
import io.github.paulgriffith.utils.Detail
import io.github.paulgriffith.utils.DetailsPane
import io.github.paulgriffith.utils.EDT_SCOPE
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.Tool
import io.github.paulgriffith.utils.ToolPanel
import io.github.paulgriffith.utils.debounce
import io.github.paulgriffith.utils.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.miginfocom.swing.MigLayout
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
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
    private val mainTable = ThreadsTable(ThreadModel(threadDump.threads))
    private val stateList = StateList(threadDump.threads.groupingBy(ThreadInfo::state).eachCount())
    private val systemList = SystemList(threadDump.threads.groupingBy(ThreadInfo::system).eachCount())

    private val searchField = FlatTextField().apply {
        placeholderText = "Search"
    }

    private val filters: List<(ThreadInfo) -> Boolean> = listOf(
        { thread ->
            thread.state in stateList.checkBoxListSelectedValues
        },
        { thread ->
            thread.system in systemList.checkBoxListSelectedValues
        },
        { thread ->
            val search = searchField.text
            search != null &&
                thread.name.contains(search, ignoreCase = true) ||
                thread.system != null && thread.system.contains(search, ignoreCase = true) ||
                thread.scope != null && thread.scope.contains(search, ignoreCase = true) ||
                thread.state.name.contains(search, ignoreCase = true) ||
                thread.stacktrace.any { stack -> stack.contains(search, ignoreCase = true) }
        }
    )

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

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = search.invoke(e.document.text)
            override fun removeUpdate(e: DocumentEvent) = search.invoke(e.document.text)
            override fun changedUpdate(e: DocumentEvent) = search.invoke(e.document.text)

            val search = debounce<String?>(waitMs = 100L) {
                updateData()
            }
        })

        add(JLabel("Version: ${threadDump.version}"))
        add(searchField, "align right, width 25%, wrap")
        add(
            JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                JPanel(MigLayout("ins 0, fill", "[shrink][fill]", "fill")).apply {
                    add(FlatScrollPane(systemList), "width 215")
                    add(FlatScrollPane(mainTable), "wrap, spany 2, push")
                    add(FlatScrollPane(stateList), "width 215")
                },
                details,
            ).apply {
                resizeWeight = 0.6
            },
            "dock center, span 3"
        )
    }

    override val icon: Icon = Tool.ThreadViewer.icon

    companion object {
        private val BACKGROUND = CoroutineScope(Dispatchers.Default)

        internal val JSON = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}
