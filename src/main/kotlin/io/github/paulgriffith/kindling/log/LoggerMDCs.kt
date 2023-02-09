package io.github.paulgriffith.kindling.log

import io.github.paulgriffith.kindling.utils.FlatScrollPane
import net.miginfocom.swing.MigLayout
import javax.swing.*
import javax.swing.table.AbstractTableModel
import kotlin.Any
import kotlin.Array
import kotlin.Int
import kotlin.String
import kotlin.apply
import kotlin.arrayOf
import kotlin.run


data class MDCDisplay(
        val mdc: Pair<String, String>,
        var eventCount: Int,
) {
    fun displayValue(valIndex: Int) : String {
        if (valIndex == 0) return "${mdc.first} - [${eventCount}]"
        return "${mdc.second} - [${eventCount}]"
    }
}

class FilterTable(val columns: Array<String>, var data: MutableList<MutableList<Any>>) : AbstractTableModel() {

    var selectedRow = 0

    override fun getRowCount(): Int {
        return data.size
    }

    override fun getColumnCount(): Int {
        return columns.size
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        return data[rowIndex][columnIndex]
    }

    override fun getColumnName(column: Int): String {
        return columns[column]
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return data[0][columnIndex].javaClass
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        selectedRow = rowIndex
        return columnIndex in arrayOf(0, 3)
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (aValue != null) {
            data[rowIndex][columnIndex] = aValue
        }
        fireTableCellUpdated(rowIndex, columnIndex)
    }

    fun isValidLogEvent(logEvent : LogEvent, inverted : Boolean) : Boolean {
        val mdcList : MutableList<List<Any>> = mutableListOf()
        data.forEach {
            if (it[0] == true && it[3] == inverted) {
                mdcList.add(listOf(it[1], it[2]))
            }
        }
        if (logEvent is SystemLogsEvent) {
            var exists = inverted
            return if (mdcList.size == 0) {
                true
            } else {
                logEvent.mdc.forEach { eventMDC ->
                    mdcList.forEach { filterMDC ->
                        if (eventMDC.key == filterMDC[0] && eventMDC.value == filterMDC[1]) {
                            exists = !inverted
                        }
                    }
                }
                exists
            }
        } else {
            return true
        }
    }
}

class LoggerMDCPanel(events: List<SystemLogsEvent>) : JPanel(MigLayout("ins 0, fill")) {

    private val fullMCDList : List<MDCDisplay> = run {
        val list : MutableList<Pair<String, String>> = mutableListOf()
        events.forEach { event ->
            event.mdc.forEach { mdc ->
                list.add(Pair(mdc.key, mdc.value))
            }
        }
        val condensedList : List<MDCDisplay> = list.groupingBy {
            it
        }
                .eachCount()
                .entries
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key.first})
                .map { (key, value) -> MDCDisplay(key, value) }
        condensedList
    }

    private val keyList : List<MDCDisplay> = run {
        val list : MutableList<MDCDisplay> = mutableListOf()
        fullMCDList.forEach { loggerMDC ->
            var found = false
            list.forEach breaking@{key ->
                if (key.mdc.first == loggerMDC.mdc.first) {
                    key.eventCount += loggerMDC.eventCount
                    found = true
                    return@breaking
                }
            }
            if (!found) {
                list.add(MDCDisplay(loggerMDC.mdc, loggerMDC.eventCount))
            }

        }
        list
    }
    private val keyMenu = JComboBox<String>().apply {
        addItem(" - key - ")
        keyList.forEach{
            addItem(it.displayValue(0))
        }
        addActionListener {
            valueMenu.removeAllItems()
            valueMenu.addItem(" - value - ")
            fullMCDList.forEach { loggerMDC ->
                if (loggerMDC.mdc.first == selectedItem?.toString()?.substringBefore(" - [")) {
                    valueMenu.addItem(loggerMDC.displayValue(1))
                }
            }

        }
    }
    private val valueMenu = JComboBox<String>().apply { addItem(" - value - ") }
    private val addButton = JButton("+").apply {
        border = null
        background = null
        addActionListener {
            val key = keyMenu.selectedItem?.toString()!!.substringBefore(" - [")
            val value = valueMenu.selectedItem?.toString()!!.substringBefore(" - [")
            if (key != " - key - " && value != " - value - ") {
                var exists = false
                filterTable.data.forEach {
                    if (it[1] == key && it[2] == value) {
                        exists = true
                    }
                }
                if (!exists) {
                    filterTable.data.add(mutableListOf(java.lang.Boolean.TRUE, key, value, java.lang.Boolean.FALSE))
                    filterTable.fireTableDataChanged()
                }
            }
        }
    }
    private val removeButton = JButton("-").apply {
        border = null
        background = null
        addActionListener {
            if (filterTable.selectedRow < filterTable.data.size && filterTable.data.size > 0) {
                filterTable.data.removeAt(filterTable.selectedRow)
                filterTable.fireTableDataChanged()

            }

        }
    }
    val filterTable = FilterTable(arrayOf("Enable", "Key", "Value", "Invert"), mutableListOf())

    init {
        add(JLabel("MDC Key Filtering"), "align center, span 2, wrap")
        add(keyMenu, "spanx 2, pushx, growx, wrap")
        add(valueMenu, "spanx 2, pushx, growx, wrap")
        add(removeButton, "cell 1 3, align right")
        add(addButton, "cell 1 3, align right, wrap")
        add(FlatScrollPane(JTable(filterTable)), "spanx 2, pushy, grow")
    }
}
