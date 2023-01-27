package io.github.paulgriffith.kindling.attach

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.sun.tools.attach.VirtualMachineDescriptor
import io.github.paulgriffith.kindling.core.JvmTool
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.utils.Action
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import io.github.paulgriffith.kindling.utils.ReifiedJXTable
import io.github.paulgriffith.kindling.utils.truncate
import java.util.Properties
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.table.AbstractTableModel

class AttachedPanel(val descriptor: VirtualMachineDescriptor) : ToolPanel() {
    private val virtualMachine = descriptor.provider().attachVirtualMachine(descriptor)

    private val connector = JMXConnectorFactory.connect(
        JMXServiceURL(virtualMachine.startLocalManagementAgent()),
    )

    private val connection = connector.mBeanServerConnection

    init {
        virtualMachine.loadAgent("/Users/pgriffith/Projects/kindling/build/libs/kindling-0.6.1-SNAPSHOT.jar")

        add(
            FlatScrollPane(ReifiedJXTable(PropertiesTableModel(virtualMachine.systemProperties))),
            "north",
        )

        for (tool in Tool.tools.filterIsInstance<JvmTool>()) {
            add(
                JButton(
                    Action(tool.title) {
                        add(tool.open(descriptor, connection))
                    },
                ),
            )
        }
    }

    override fun removeNotify() {
        super.removeNotify()
        connector.close()
        virtualMachine.detach()
    }

    override val icon: Icon = FlatSVGIcon("icons/bx-link.svg")
    override val tabName = descriptor.displayName().truncate()
    override val tabTooltip: String = descriptor.displayName()
}

private class PropertiesTableModel(properties: Properties) : AbstractTableModel() {
    private val data = properties.toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER, Any?::toString))
    private val keyList = data.keys.toList()

    override fun getRowCount(): Int = data.size
    override fun getColumnCount(): Int = 2
    override fun getColumnName(column: Int): String = when (column) {
        0 -> "Key"
        1 -> "Value"
        else -> throw ArrayIndexOutOfBoundsException(column)
    }

    override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val key = keyList[rowIndex]
        return when (columnIndex) {
            0 -> key
            1 -> data[key]
            else -> throw ArrayIndexOutOfBoundsException(columnIndex)
        }
    }
}
