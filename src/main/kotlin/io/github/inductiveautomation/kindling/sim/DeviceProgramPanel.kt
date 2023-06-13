package io.github.inductiveautomation.kindling.sim

import io.github.inductiveautomation.kindling.sim.model.SimulatorProgram
import io.github.inductiveautomation.kindling.sim.model.exportToFile
import io.github.inductiveautomation.kindling.utils.Action
import io.github.inductiveautomation.kindling.utils.PopupMenuCustomizer
import net.miginfocom.swing.MigLayout
import javax.swing.BorderFactory
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane

class DeviceProgramPanel(
    val deviceName: String,
    val programItems: SimulatorProgram,
) : JScrollPane(), PopupMenuCustomizer {
    val numberOfTags by programItems::size

    init {
        viewport.view = JPanel(MigLayout("fillx, ins 0, gapy 10"))
        border = BorderFactory.createEmptyBorder()

        programItems.forEach {
            val itemPanel = ProgramItemPanel(it)
            itemPanel.addProgramItemDeletedListener {
                (viewport.view as JPanel).remove(itemPanel)

                val oldValue = numberOfTags
                val success = programItems.remove(it)
                val newValue = numberOfTags

                revalidate()
                repaint()

                if (success) firePropertyChange("numItems", oldValue, newValue)
            }

            (viewport.view as JPanel).add(itemPanel, "growx, span")
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
