package io.github.paulgriffith.kindling.cache

import com.jidesoft.swing.CheckBoxList
import io.github.paulgriffith.kindling.utils.NoSelectionModel
import io.github.paulgriffith.kindling.utils.listCellRenderer
import io.github.paulgriffith.kindling.utils.tag
import java.awt.Font
import java.awt.Font.MONOSPACED
import javax.swing.AbstractListModel
import javax.swing.JLabel
import javax.swing.UIManager

class SchemaModel(private val data: List<SchemaRecord>) : AbstractListModel<Any>() {
    private val comparator: Comparator<SchemaRecord> = compareBy(nullsFirst()) { it.id }
    private val values = data.sortedWith(comparator)

    override fun getSize(): Int {
        return values.size + 1
    }

    override fun getElementAt(index: Int): Any {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            values[index - 1]
        }
    }

    fun getSchemaRecordAt(index: Int): SchemaRecord {
        require(index > 0)
        return values[index - 1]
    }

    fun indexOf(value: SchemaRecord): Int {
        val indexOf = values.indexOf(value)
        return if (indexOf >= 0) {
            indexOf + 1
        } else {
            -1
        }
    }
}

class SchemaFilterList(modelData: List<SchemaRecord>) : CheckBoxList(SchemaModel(modelData)) {

    init {
        selectionModel = NoSelectionModel()
        isClickInCheckBoxOnly = false

        cellRenderer = listCellRenderer<Any?> { _, value, _, _, _ ->
            text = when (value) {
//                is SchemaRecord -> "${"%4d".format(value.id)}: ${value.name}"
                is SchemaRecord -> {
                    buildString {
                        tag("html") {
                            append("${"%4d".format(value.id)}: ${value.name}")
                            if (value.errors.isNotEmpty()) {
                                val color = UIManager.getColor("Component.warning.focusedBorderColor")
                                val colorString = "rgb(${color.red},${color.green},${color.blue})"
                                append("<br>errors:")
                                append("<span style=\"color: $colorString;\">")
                                value.errors.forEach { error ->
                                    append("<br>$error")
                                }
                                append("</span>")
                            }
                        }
                    }
                }
                else -> value.toString()
            }
            verticalTextPosition = JLabel.BOTTOM
            font = Font.decode(MONOSPACED).deriveFont(14.0F)
        }
        selectAll()
    }

    fun select(value: SchemaRecord) {
        val rowToSelect = model.indexOf(value)
        checkBoxListSelectionModel.setSelectionInterval(rowToSelect, rowToSelect)
    }

    override fun getModel() = super.getModel() as SchemaModel
}