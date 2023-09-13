package io.github.inductiveautomation.kindling.sim

import io.github.inductiveautomation.kindling.sim.model.SimulatorProgram
import io.github.inductiveautomation.kindling.sim.model.exportToFile
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.Pager
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import io.github.inductiveautomation.kindling.utils.add
import net.miginfocom.swing.MigLayout
import javax.swing.BorderFactory
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import kotlin.math.min

class DeviceProgramPanel(
    val deviceName: String,
    private val programItems: SimulatorProgram,
) : JPanel(MigLayout("fill, ins 0, flowy")), PopupMenuCustomizer {
    val numberOfTags by programItems::size

    private val scrollPane = JScrollPane()

    private val itemsPerPage = 20

    private var itemPanels = createItemPanels(0, min(itemsPerPage, numberOfTags))

    private val pager = Pager(numberOfTags, itemsPerPage).apply {
        addPageSelectedListener { start, end, _ ->
            (scrollPane.viewport.view as JPanel).apply {
                removeAll()
                createItemPanels(start, end).forEach {
                    add(it, "growx, span")
                }
                revalidate()
                repaint()
            }
        }
    }

    private fun createItemPanels(startIndex: Int, endIndex: Int): List<ProgramItemPanel> {
        return programItems.subList(startIndex, endIndex).map(::ProgramItemPanel).onEach { itemPanel ->
            itemPanel.addProgramItemDeletedListener {

                val viewportView = (scrollPane.viewport.view as JPanel).also {
                    it.remove(itemPanel)
                }

                val oldValue = numberOfTags
                val success = programItems.remove(itemPanel.item)
                val newValue = numberOfTags

                if (success) {
                    firePropertyChange("numItems", oldValue, newValue)

                    if (numberOfTags == 0) return@addProgramItemDeletedListener

                    pager.numberOfElements = numberOfTags

                    if (viewportView.componentCount == 0) {
                        // Kind of janky. This forces the onPageSelected event to fire which then causes the listener
                        // above to fire. We don't have the start and end indices readily availably so this saves us
                        // doing the calculation here.
                        pager.selectedPage = pager.selectedPage
                    }
                }
            }
        }
    }

    private fun addFunctionDataChangeListener(l: FunctionDataChangeListener) = listenerList.add(l)

    init {
        scrollPane.viewport.view = JPanel(MigLayout("fillx, ins 0, gapy 10")).apply {
            itemPanels.forEach {
                add(it, "growx, span")
            }
            border = BorderFactory.createEmptyBorder()
        }

        border = BorderFactory.createEmptyBorder()

        scrollPane.border = BorderFactory.createEmptyBorder()

        add(scrollPane, "push, grow")
        add(pager, "align center")

        addFunctionDataChangeListener {
            repaint()
            itemPanels.forEach { panel ->
                panel.getListeners(FunctionDataChangeListener::class.java).forEach {
                    it.functionDataChange()
                }
            }
        }
    }

    override fun customizePopupMenu(menu: JPopupMenu) {
        menu.add(
            Action("Export to CSV") {
                if (SimulatorView.directoryChooser.showSaveDialog(this@DeviceProgramPanel) == JFileChooser.APPROVE_OPTION) {
                    val outputFile = SimulatorView.directoryChooser.selectedFile.toPath().resolve("$deviceName-sim.csv")
                    programItems.exportToFile(outputFile)
                }
            },
        )
    }
}
