package io.github.inductiveautomation.kindling.zip.views

import io.github.inductiveautomation.kindling.statistics.Statistics
import io.github.inductiveautomation.kindling.statistics.Statistics.Companion.STATISTICS_IO
import io.github.inductiveautomation.kindling.statistics.categories.Statistic
import io.github.inductiveautomation.kindling.statistics.categories.StatisticCategory
import io.github.inductiveautomation.kindling.utils.EDT_SCOPE
import io.github.inductiveautomation.kindling.utils.splitCamelCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import org.jdesktop.swingx.JXTaskPane
import org.jdesktop.swingx.JXTaskPaneContainer
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.Locale
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextArea
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
                    JPanel(MigLayout("ins 0, fillx, wrap 3, gap 0")).apply {
                        stats.all.map { this@apply.add(CategoryPane(it), "grow, sgx") }
                    }
                ),
                "push, grow, span"
            )
            revalidate()
        }
    }

    companion object {
        fun isGatewayBackup(path: Path): Boolean = path.extension.lowercase() == "gwbk"

        fun Statistic<*>.nameAsHumanReadable(): String {
            return name.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }.splitCamelCase()
        }
    }

    class CategoryPane(
        private val statCategory: StatisticCategory
    ) : JXTaskPaneContainer() {
        private var initialized = false

        private val taskPane = JXTaskPane(statCategory.name.splitCamelCase()).apply {
            isCollapsed = true

            addPropertyChangeListener("collapsed") {
                if (!initialized && it.newValue == false) {
                    val originalText = title
                    STATISTICS_IO.launch {
                        withContext(Dispatchers.Swing) {
                            title = "$originalText (Loading...)"
                        }
                        statCategory.forEach { stat ->
                            launch(Dispatchers.Default) {
                                val name = stat.nameAsHumanReadable()
                                val value = stat.getValue()
                                if (value != 0 && value != null) {
                                    val statDisplay = JTextArea("$name: ${stat.valueAsString()}")
                                    EDT_SCOPE.launch {
                                        this@apply.add(statDisplay)
                                    }
                                }
                            }
                        }
                        initialized = true
                    }.invokeOnCompletion {
                        EDT_SCOPE.launch {
                            this@apply.title = originalText
                        }
                    }
                }
            }
        }

        init {
            add(taskPane)
        }
    }
}