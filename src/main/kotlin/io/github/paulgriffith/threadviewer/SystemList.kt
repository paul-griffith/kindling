package io.github.paulgriffith.threadviewer

import com.jidesoft.swing.CheckBoxList
import io.github.paulgriffith.utils.NoSelectionModel
import io.github.paulgriffith.utils.listCellRenderer
import java.text.DecimalFormat
import javax.swing.AbstractListModel
import javax.swing.ListModel

class SystemModel(private val values: List<String?>) : AbstractListModel<Any>() {
    override fun getSize(): Int = values.size + 1
    override fun getElementAt(index: Int): Any? {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            values[index - 1]
        }
    }
}

class SystemList(data: Map<String?, Int>) :
    CheckBoxList(SystemModel(data.entries.sortedByDescending { it.value }.map { it.key })) {
    init {
        selectionModel = NoSelectionModel()

        val total = data.values.sum()
        val percentages = data.mapValues { (_, count) ->
            val percentage = count.toFloat() / total
            DecimalFormat.getPercentInstance().format(percentage)
        }

        cellRenderer = listCellRenderer<Any?> { _, value, _, _, _ ->
            text = when (value) {
                is String? -> {
                    "${value ?: "Unassigned"} - ${data[value]} (${percentages.getValue(value)})"
                }
                else -> {
                    value.toString()
                }
            }
        }

        selectAll()
    }

    override fun getModel(): SystemModel = super.getModel() as SystemModel

    override fun setModel(model: ListModel<*>) {
        require(model is SystemModel)
        val selection = checkBoxListSelectedValues
        checkBoxListSelectionModel.valueIsAdjusting = true
        super.setModel(model)
        addCheckBoxListSelectedValues(selection)
        checkBoxListSelectionModel.valueIsAdjusting = false
    }
}
