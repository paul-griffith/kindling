package io.github.paulgriffith.kindling.thread

import com.jidesoft.swing.CheckBoxList
import io.github.paulgriffith.kindling.utils.NoSelectionModel
import io.github.paulgriffith.kindling.utils.listCellRenderer
import java.text.DecimalFormat
import javax.swing.AbstractListModel
import javax.swing.ListModel

class PoolModel(private val values: List<String?>) : AbstractListModel<Any>() {
    override fun getSize(): Int = values.size + 1
    override fun getElementAt(index: Int): Any? = when (index) {
        0 -> CheckBoxList.ALL_ENTRY
        else -> values[index - 1]
    }

    fun indexOf(pool: String): Int {
        val indexOf = values.indexOf(pool)
        return if (indexOf >= 0) {
            indexOf + 1
        } else {
            -1
        }
    }
}

class PoolList(var data: Map<String?, Int>) :
    CheckBoxList(PoolModel(data.entries.sortedWith(compareByDescending(nullsFirst()) { it.value }).map { it.key })) {

    private var total = 0
    private var percentages = emptyMap<String?, String>()
    init {
        selectionModel = NoSelectionModel()
        refreshData(data)

        cellRenderer = listCellRenderer<Any?> { _, value, _, _, _ ->
            text = when (value) {
                is String -> "$value - ${data[value]} (${percentages.getValue(value)})"
                null -> "(No Pool) - ${data[null]} (${percentages.getValue(null)})"
                else -> value.toString()
            }
        }

        selectAll()
    }

    fun select(pool: String) {
        val rowToSelect = model.indexOf(pool)
        checkBoxListSelectionModel.setSelectionInterval(rowToSelect, rowToSelect)
    }

    override fun getModel(): PoolModel = super.getModel() as PoolModel

    override fun setModel(model: ListModel<*>) {
        require(model is PoolModel)
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

    fun refreshData(data: Map<String?, Int>) {
        this.data = data
        model = PoolModel(data.entries.sortedWith(compareByDescending(nullsFirst()) { it.value }).map { it.key })
    }
}
