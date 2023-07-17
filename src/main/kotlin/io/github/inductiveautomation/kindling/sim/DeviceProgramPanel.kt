package io.github.inductiveautomation.kindling.sim

import io.github.inductiveautomation.kindling.sim.model.SimulatorProgram
import io.github.inductiveautomation.kindling.sim.model.exportToFile
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import io.github.inductiveautomation.kindling.utils.add
import net.miginfocom.swing.MigLayout
import javax.swing.BorderFactory
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane

class DeviceProgramPanel(
    val deviceName: String,
    private val programItems: SimulatorProgram,
) : JScrollPane(), PopupMenuCustomizer {
    val numberOfTags by programItems::size

    private val itemPanels = programItems.map(::ProgramItemPanel).onEach { itemPanel ->
        itemPanel.addProgramItemDeletedListener {
            (viewport.view as JPanel).remove(itemPanel)

            val oldValue = numberOfTags
            val success = programItems.remove(itemPanel.item)
            val newValue = numberOfTags

            revalidate()
            repaint()

            if (success) firePropertyChange("numItems", oldValue, newValue)
        }
    }

    private fun addFunctionDataChangeListener(l: FunctionDataChangeListener) = listenerList.add(l)

    init {
        viewport.view = JPanel(MigLayout("fillx, ins 0, gapy 10")).apply {
            itemPanels.forEach { add(it, "growx, span") }
        }
        border = BorderFactory.createEmptyBorder()

        addFunctionDataChangeListener {
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
                if (SimulatorView.directoryChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    val outputFile = SimulatorView.directoryChooser.selectedFile.toPath().resolve("$deviceName-sim.csv")
                    programItems.exportToFile(outputFile)
                }
            },
        )
    }
}
