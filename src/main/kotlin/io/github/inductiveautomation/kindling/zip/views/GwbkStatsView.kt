package io.github.inductiveautomation.kindling.zip.views

import io.github.inductiveautomation.kindling.statistics.StatisticCategory
import io.github.inductiveautomation.kindling.statistics.Statistics
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jdesktop.swingx.JXTaskPane
import org.jdesktop.swingx.JXTaskPaneContainer
import java.beans.PropertyChangeListener
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.io.path.extension

class GwbkStatsView(override val provider: FileSystemProvider, override val path: Path) : SinglePathView() {
    override val icon: Icon? = null
    override val tabName: String = "Statistics"
    override fun customizePopupMenu(menu: JPopupMenu) = Unit

    init {
        EDT_SCOPE.launch {
            val stats = withContext(Dispatchers.Default) {
                Statistics.create(path)
            }
            add(
                JScrollPane(
                    JXTaskPaneContainer().apply {
                        stats.all.map { add(CategoryPane(it)) }
                    }
                ),
                "push, grow, span"
            )
        }
    }

    companion object {
        fun isGatewayBackup(path: Path): Boolean = path.extension.lowercase() == "gwbk"
    }

    class CategoryPane(
        private val statCategory: StatisticCategory
    ) : JXTaskPane(statCategory.name) {
        private var initialized = false
            set(value) {
                field = value
                removePropertyChangeListener(initializeListener)
            }

        private val initializeListener = PropertyChangeListener {
            if (it.newValue == false) {
                SwingUtilities.invokeLater {
                    CoroutineScope(Dispatchers.Default).launch {
                        statCategory.forEach { stat ->
                            launch {
                                val statDisplay = JLabel("${stat.name}: ${stat.getValue()}")
                                EDT_SCOPE.launch { add(statDisplay) }
                            }
                        }
                    }
                    initialized = true
                }
            }
        }

        init {
            isCollapsed = true
            addPropertyChangeListener("collapsed", initializeListener)
        }
    }
}