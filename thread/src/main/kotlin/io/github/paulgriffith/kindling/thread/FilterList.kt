package io.github.paulgriffith.kindling.thread

import com.jidesoft.swing.CheckBoxList
import io.github.paulgriffith.kindling.utils.NoSelectionModel
import io.github.paulgriffith.kindling.utils.listCellRenderer
import java.text.DecimalFormat
import javax.swing.AbstractListModel
import javax.swing.ListModel

class FilterModel(val rawData: Map<String?, Int>) : AbstractListModel<Any>() {
    private val comparator: Comparator<Map.Entry<String?, Int>> = compareByDescending(nullsFirst()) { it.value }
    private val values = rawData.entries.sortedWith(comparator).map { it.key }

    override fun getSize(): Int = values.size + 1
    override fun getElementAt(index: Int): Any? {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            values[index - 1]
        }
    }

    fun indexOf(value: String): Int {
        val indexOf = values.indexOf(value)
        return if (indexOf >= 0) {
            indexOf + 1
        } else {
            -1
        }
    }
}

class FilterList(private val emptyLabel: String) : CheckBoxList(FilterModel(emptyMap())) {
    private var total = 0
    private var percentages = emptyMap<String?, String>()

    init {
        selectionModel = NoSelectionModel()
        isClickInCheckBoxOnly = false

        cellRenderer = listCellRenderer<Any?> { _, value, _, _, _ ->
            text = when (value) {
                is String -> "$value - ${model.rawData[value]} (${percentages.getValue(value)})"
                null -> "$emptyLabel - ${model.rawData[null]} (${percentages.getValue(null)})"
                else -> value.toString()
            }
        }
    }

    fun select(value: String) {
        val rowToSelect = model.indexOf(value)
        checkBoxListSelectionModel.setSelectionInterval(rowToSelect, rowToSelect)
    }

    override fun getModel(): FilterModel = super.getModel() as FilterModel

    override fun setModel(model: ListModel<*>) {
        require(model is FilterModel)
        val selection = checkBoxListSelectedValues
        checkBoxListSelectionModel.valueIsAdjusting = true
        super.setModel(model)
        total = model.rawData.values.sum()
        percentages = model.rawData.mapValues { (_, count) ->
            val percentage = count.toFloat() / total
            DecimalFormat.getPercentInstance().format(percentage)
        }
        addCheckBoxListSelectedValues(selection)
        checkBoxListSelectionModel.valueIsAdjusting = false

        selectAll()
    }
}
