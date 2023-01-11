package io.github.paulgriffith.kindling.log

import com.jidesoft.swing.CheckBoxList
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.NoSelectionModel
import io.github.paulgriffith.kindling.utils.installSearchable
import io.github.paulgriffith.kindling.utils.listCellRenderer
import net.miginfocom.swing.MigLayout
import javax.swing.AbstractListModel
import javax.swing.JPanel
import javax.swing.ListModel

data class LoggerLevel(
        val name: String,
        val eventCount: Int
)

class LoggerLevelsModel(val data: List<LoggerLevel>) : AbstractListModel<Any>() {
    override fun getSize(): Int = data.size + 1
    override fun getElementAt(index: Int): Any {
        return if (index == 0) {
            CheckBoxList.ALL_ENTRY
        } else {
            data[index - 1]
        }
    }
}

class LoggerLevelsList(model: LoggerLevelsModel) : CheckBoxList(model) {
    private fun displayValue(value: Any?): String {
        return when (value) {
            is LoggerLevel ->
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
        cellRenderer = listCellRenderer<Any> { _, value, _, _, _ ->
            when (value) {
                is LoggerLevel -> {
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

    override fun getModel(): LoggerLevelsModel = super.getModel() as LoggerLevelsModel

    override fun setModel(model: ListModel<*>) {
        require(model is LoggerLevelsModel)
        val selection = checkBoxListSelectedValues
        checkBoxListSelectionModel.valueIsAdjusting = true
        super.setModel(model)
        addCheckBoxListSelectedValues(selection)
        checkBoxListSelectionModel.valueIsAdjusting = false
    }
}

class LoggerLevelsPanel(events: List<LogEvent>) : JPanel(MigLayout("ins 0, fill, h 120")) {
    val list: LoggerLevelsList = run {
        val loggerLevels: List<LoggerLevel> = events.groupingBy { it.level!!.name }
                .eachCount()
                .entries
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
                .map { (key, value) -> LoggerLevel(key, value) }
        LoggerLevelsList(LoggerLevelsModel(loggerLevels))
    }

    init {
        add(FlatScrollPane(list), "newline, push, grow")
    }
}
