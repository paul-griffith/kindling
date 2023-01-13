package io.github.paulgriffith.kindling.cache

import com.jidesoft.swing.CheckBoxList
import io.github.paulgriffith.kindling.utils.listCellRenderer
import java.awt.Font
import java.awt.Font.MONOSPACED
import javax.swing.AbstractListModel
import javax.swing.DefaultListSelectionModel

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
        selectionModel = DefaultListSelectionModel()
        isClickInCheckBoxOnly = true
        visibleRowCount = 0

        cellRenderer = listCellRenderer<Any?> { _, schemaEntry, _, _, _ ->
            text = when (schemaEntry) {
                is SchemaRecord -> {
                    val txGroupRegex = """(.*)\{.*\}""".toRegex()
                    buildString {
                        val name = txGroupRegex.find(schemaEntry.name)?.groups?.get(1)?.value ?: schemaEntry.name
                        val schemaIdAndName = "${"%4d".format(schemaEntry.id)}: $name"

                        append(schemaIdAndName)

                        when (val size = schemaEntry.errors.size) {
                            0 -> return@buildString
                            1 -> append(" ($size error. Click to view.)")
                            else -> append(" ($size errors. Click to view.)")
                        }
                    }
                }
                else -> schemaEntry.toString()
            }
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