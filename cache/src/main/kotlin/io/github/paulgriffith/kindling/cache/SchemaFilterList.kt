package io.github.paulgriffith.kindling.cache

import com.jidesoft.swing.CheckBoxList
import io.github.paulgriffith.kindling.utils.NoSelectionModel
import io.github.paulgriffith.kindling.utils.escapeHtml
import io.github.paulgriffith.kindling.utils.listCellRenderer
import io.github.paulgriffith.kindling.utils.tag
import javax.swing.AbstractListModel

class SchemaModel(val data: Map<Int, String>) : AbstractListModel<Any>() {
    private val comparator: Comparator<Map.Entry<Int, String>> = compareBy(nullsFirst()) { it.key }
    private val values = data.entries.sortedWith(comparator)

    override fun getSize(): Int {
        return values.size + 1
    }

    override fun getElementAt(index: Int): Any {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            values[index - 1].key
        }
    }

    fun indexOf(value: Map.Entry<Int, String>): Int {
        val indexOf = values.indexOf(value)
        return if (indexOf >= 0) {
            indexOf + 1
        } else {
            -1
        }
    }
}

class SchemaFilterList(modelData: Map<Int, String>) : CheckBoxList(SchemaModel(modelData)) {

    init {
        selectionModel = NoSelectionModel()
        isClickInCheckBoxOnly = false

        cellRenderer = listCellRenderer<Any?> { _, value, _, _, _ ->
            text = when (value) {
                is Int -> "ID: $value, Name: ${model.data[value]}"
                else -> value.toString()
            }
        }
    }

    fun select(value: Map.Entry<Int, String>) {
        val rowToSelect = model.indexOf(value)
        checkBoxListSelectionModel.setSelectionInterval(rowToSelect, rowToSelect)
    }

    override fun getModel() = super.getModel() as SchemaModel
}