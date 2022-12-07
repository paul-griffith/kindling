package io.github.paulgriffith.kindling.thread

import com.jidesoft.swing.CheckBoxList
import io.github.paulgriffith.kindling.utils.NoSelectionModel
import io.github.paulgriffith.kindling.utils.listCellRenderer
import java.lang.Thread.State
import java.text.DecimalFormat
import javax.swing.AbstractListModel
import javax.swing.ListModel
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

class StateModel(private val values: List<State>) : AbstractListModel<Any>() {
    override fun getSize(): Int = values.size + 1
    override fun getElementAt(index: Int): Any {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            values[index - 1]
        }
    }

    fun indexOf(state: State): Int {
        val indexOf = values.indexOf(state)
        return if (indexOf >= 0) {
            indexOf + 1
        } else {
            -1
        }
    }
}

class StateList(var data: Map<State, Int>) :
    CheckBoxList(StateModel(data.entries.sortedByDescending { it.value }.map { it.key })) {

    private var total = 0
    private var percentages = emptyMap<Thread.State, String>()

    init {
        selectionModel = NoSelectionModel()
        setModel(data)

        cellRenderer = listCellRenderer<Any> { _, value, _, _, _ ->
            text = when (value) {
                is State -> "$value -  ${data[value]} (${percentages.getValue(value)})"
                else -> value.toString()
            }
        }

        selectAll()
    }

    fun select(state: State) {
        val rowToSelect = model.indexOf(state)
        checkBoxListSelectionModel.setSelectionInterval(rowToSelect, rowToSelect)
    }

    override fun getModel(): StateModel = super.getModel() as StateModel

    override fun setModel(model: ListModel<*>) {
        require(model is StateModel)
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

    fun setModel(data: Map<State, Int>) {
        this.data = data
        model = StateModel(data.entries.sortedByDescending { it.value }.map { it.key })
    }
}
