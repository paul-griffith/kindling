package io.github.paulgriffith.kindling.thread

import com.jidesoft.swing.CheckBoxList
import io.github.paulgriffith.kindling.utils.NoSelectionModel
import io.github.paulgriffith.kindling.utils.listCellRenderer
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

    fun indexOf(system: String): Int {
        val indexOf = values.indexOf(system)
        return if (indexOf >= 0) {
            indexOf + 1
        } else {
            -1
        }
    }
}

class SystemList(var data: Map<String?, Int>) :
    CheckBoxList(SystemModel(data.entries.sortedByDescending { it.value }.map { it.key })) {

    private var total = 0
    private var percentages = emptyMap<String?, String>()

    init {
        selectionModel = NoSelectionModel()
        setModel(data)

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

    fun select(system: String) {
        val rowToSelect = model.indexOf(system)
        checkBoxListSelectionModel.setSelectionInterval(rowToSelect, rowToSelect)
    }

    override fun getModel(): SystemModel = super.getModel() as SystemModel

    override fun setModel(model: ListModel<*>) {
        require(model is SystemModel)
        val selection = checkBoxListSelectedValues
        checkBoxListSelectionModel.valueIsAdjusting = true
        super.setModel(model)
        total = data.values.sum()
        percentages = data.mapValues { (_, count) ->
            val percentage = count.toFloat() / total
            DecimalFormat.getPercentInstance().format(percentage)
        }
        addCheckBoxListSelectedValues(selection)
        checkBoxListSelectionModel.valueIsAdjusting = false
    }

    fun setModel(data: Map<String?, Int>) {
        this.data = data
        model = SystemModel(data.entries.sortedByDescending { it.value }.map { it.key })
    }
}
