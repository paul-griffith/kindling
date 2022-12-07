package io.github.paulgriffith.kindling.thread

import com.jidesoft.swing.CheckBoxList
import io.github.paulgriffith.kindling.utils.NoSelectionModel
import io.github.paulgriffith.kindling.utils.listCellRenderer
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.AbstractListModel
import javax.swing.JList
import javax.swing.ListModel
import javax.swing.ToolTipManager
import kotlin.io.path.name


class ThreadDumpListModel(private val values: List<Path>) : AbstractListModel<Any>() {
    override fun getSize(): Int = values.size + 1
    override fun getElementAt(index: Int): Any? = when (index) {
        0 -> CheckBoxList.ALL_ENTRY
        else -> values[index - 1]
    }
}

class ThreadDumpCheckboxList(data: List<Path>) : CheckBoxList(ThreadDumpListModel(data)) {

    private val defaultDismissTimeout = ToolTipManager.sharedInstance().dismissDelay

    init {

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(me: MouseEvent?) {
                ToolTipManager.sharedInstance().dismissDelay = 60000
            }

            override fun mouseExited(me: MouseEvent?) {
                ToolTipManager.sharedInstance().dismissDelay = defaultDismissTimeout
            }
        })

        layoutOrientation = JList.HORIZONTAL_WRAP
        visibleRowCount = -1

        isClickInCheckBoxOnly = false

        selectionModel = NoSelectionModel()

        cellRenderer = listCellRenderer<Any?> { _, value, index, _, _ ->
            text = when (index) {
                0 -> "All"
                else -> index.toString()
            }
            toolTipText = when (value) {
                is Path -> value.name
                else -> null
            }
            isOpaque = false
        }
        isOpaque = false

        selectAll()
    }


    override fun getModel() = super.getModel() as ThreadDumpListModel

    override fun setModel(model: ListModel<*>) {
        require(model is ThreadDumpListModel)
        val selection = checkBoxListSelectedValues
        checkBoxListSelectionModel.valueIsAdjusting = true
        super.setModel(model)
        addCheckBoxListSelectedValues(selection)
        checkBoxListSelectionModel.valueIsAdjusting = false
    }
}
