package io.github.paulgriffith.threadviewer

import com.jidesoft.swing.CheckBoxList
import io.github.paulgriffith.utils.EmptySelectionModel
import io.github.paulgriffith.utils.listCellRenderer
import java.text.DecimalFormat
import javax.swing.AbstractListModel
import javax.swing.ListModel

class StateModel(private val values: List<Thread.State>) : AbstractListModel<Any>() {
    override fun getSize(): Int = values.size + 1
    override fun getElementAt(index: Int): Any {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            values[index - 1]
        }
    }
}

class StateList(data: Map<Thread.State, Int>) :
    CheckBoxList(StateModel(data.entries.sortedByDescending { it.value }.map { it.key })) {
    init {
        selectionModel = EmptySelectionModel()

        val total = data.values.sum()
        val percentages = data.mapValues { (_, count) ->
            val percentage = count.toFloat() / total
            DecimalFormat.getPercentInstance().format(percentage)
        }

        cellRenderer = listCellRenderer<Any> { _, value, _, _, _ ->
            text = when (value) {
                is Thread.State -> {
                    "$value -  ${data[value]} (${percentages.getValue(value)})"
                }
                else -> {
                    value.toString()
                }
            }
        }

        selectAll()
    }

    override fun getModel(): StateModel = super.getModel() as StateModel

    override fun setModel(model: ListModel<*>) {
        require(model is StateModel)
        val selection = checkBoxListSelectedValues
        checkBoxListSelectionModel.valueIsAdjusting = true
        super.setModel(model)
        addCheckBoxListSelectedValues(selection)
        checkBoxListSelectionModel.valueIsAdjusting = false
    }
}
