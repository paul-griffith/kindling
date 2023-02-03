package io.github.paulgriffith.kindling.log

import com.formdev.flatlaf.extras.components.FlatScrollPane
import com.jidesoft.swing.CheckBoxList
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.NoSelectionModel
import io.github.paulgriffith.kindling.utils.installSearchable
import io.github.paulgriffith.kindling.utils.listCellRenderer
import net.miginfocom.swing.MigLayout
import javax.swing.AbstractListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.ListModel

data class LoggerMDC(
        val name: String,
        val eventCount: Int
)

class LoggerMDCsModel(val data: List<LoggerMDC>) : AbstractListModel<Any>() {
    override fun getSize(): Int = data.size + 1
    override fun getElementAt(index: Int): Any {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            data[index - 1]
        }
    }
}

class LoggerMDCsList(model: LoggerMDCsModel) : CheckBoxList(model) {
    private fun displayValue(value: Any?): String {
        return when (value) {
            is LoggerMDC ->
                value.name
            else -> value.toString()
        }
    }

    init {
        installSearchable(
                setup = {
                    isCaseSensitive = false
                    isRepeats = true
                    isCountMatch = true
                },
                conversion = ::displayValue
        )
        selectionModel = NoSelectionModel()
        isClickInCheckBoxOnly = false
        cellRenderer = listCellRenderer<Any> { _, value, _, _, _ ->
            when (value) {
                is LoggerMDC -> {
                    text = "${displayValue(value)} - [${value.eventCount}]"
                    toolTipText = value.name
                }

                else -> {
                    text = value.toString()
                }
            }
        }

        selectAll()
    }

    override fun getModel(): LoggerMDCsModel = super.getModel() as LoggerMDCsModel

    override fun setModel(model: ListModel<*>) {
        require(model is LoggerMDCsModel)
        val selection = checkBoxListSelectedValues
        checkBoxListSelectionModel.valueIsAdjusting = true
        super.setModel(model)
        addCheckBoxListSelectedValues(selection)
        checkBoxListSelectionModel.valueIsAdjusting = false
    }
}

class LoggerMDCsPanel(events: List<SystemLogsEvent>) : JPanel(MigLayout("ins 0, fill, h 120")) {

    private val keyMenu = JComboBox<String>()
    private val valueMenu = JComboBox<String>()
    private val addButton = JButton("Add Filter")
    private val removeButton = JButton("Remove")

    fun select(mdc: String) {
        list.model.data.listIterator().forEach { if (it.name == mdc)  {
            val index = list.model.data.indexOf(it) + 1
            list.checkBoxListSelectionModel.setSelectionInterval(index, index)
        } }
    }

    fun isOnlySelected(mdc: String): Boolean {
        if (list.checkBoxListSelectionModel.model.getElementAt(0) == 0) { return false }
        for (i in 1 until list.checkBoxListSelectionModel.model.size) {
            if (mdc != (list.checkBoxListSelectionModel.model.getElementAt(i) as LoggerMDC).name
                    && i in list.checkBoxListSelectionModel.selectedIndices) {
                return false
            }
        }
        return true
    }

    val list: LoggerMDCsList = run {
        val loggerMDCs: List<LoggerMDC> = events.groupingBy {
                    it.mdc.toString()
                }
                        .eachCount()
                        .entries
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
                        .map { (key, value) -> LoggerMDC(key, value) }
                LoggerMDCsList(LoggerMDCsModel(loggerMDCs))
            }

    init {
        add(keyMenu,"span")
        add(valueMenu, "span, wrap")
        add(removeButton)
        add(addButton, "wrap")
        add(FlatScrollPane(list), "spanx 2, push, grow")
    }
}
