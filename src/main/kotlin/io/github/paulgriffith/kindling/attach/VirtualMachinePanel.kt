package io.github.paulgriffith.kindling.attach

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.sun.management.HotSpotDiagnosticMXBean
import com.sun.tools.attach.VirtualMachineDescriptor
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.thread.MultiThreadView
import io.github.paulgriffith.kindling.thread.model.Thread
import io.github.paulgriffith.kindling.thread.model.ThreadDump
import io.github.paulgriffith.kindling.utils.Action
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean
import java.time.LocalTime
import java.util.Properties
import javax.management.JMX
import javax.management.ObjectName
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.table.AbstractTableModel
import kotlin.properties.Delegates

class VirtualMachinePanel(val descriptor: VirtualMachineDescriptor) : ToolPanel() {
    private val virtualMachine = descriptor.provider().attachVirtualMachine(descriptor)
    private val connector = JMXConnectorFactory.connect(
        JMXServiceURL(virtualMachine.startLocalManagementAgent()),
    )
    private val connection = connector.mBeanServerConnection

    private val threadMXBean = JMX.newMXBeanProxy(connection, threadBeanName, ThreadMXBean::class.java)
    private val threadDumps = mutableMapOf(now() to threadMXBean.dumpThreads())
    private var threadView by Delegates.observable(threadDumps.toView()) { _, oldValue, newValue ->
        oldValue.isVisible = false
        remove(oldValue)
        add(newValue, "newline, push, grow, span")
    }

    private val captureThreadDump = Action("Capture Thread Dump") {
        threadDumps[now()] = threadMXBean.dumpThreads()
        threadView = threadDumps.toView()
    }

    private val dumpHeap = Action("Dump Memory") {
        val diagnosticBean = JMX.newMXBeanProxy(connection, diagnosticBeanName, HotSpotDiagnosticMXBean::class.java)
        exportFileChooser.apply {
            selectedFile = File("${descriptor.displayName()}.hprof")
            if (showSaveDialog(this@apply) == JFileChooser.APPROVE_OPTION) {
                diagnosticBean.dumpHeap(selectedFile.absolutePath, false)
            }
        }
    }

    private val detach = Action("Detach") {
        captureThreadDump.isEnabled = false
        dumpHeap.isEnabled = false
        (it.source as JButton).isEnabled = false
        connector.close()
        virtualMachine.detach()
    }

    init {
        add(JLabel(descriptor.displayName()), "wrap")

        add(JButton(captureThreadDump), "split 3")
        add(JButton(dumpHeap))
        add(JButton(detach))

        add(threadView, "newline, push, grow, span")
    }

    override fun removeNotify() {
        super.removeNotify()
        connector.close()
        virtualMachine.detach()
    }

    override val icon: Icon = FlatSVGIcon("icons/bx-link.svg")
    override val tabName = "PID ${descriptor.id()}"
    override val tabTooltip: String = descriptor.displayName()

    companion object {
        private val threadBeanName = ObjectName.getInstance(ManagementFactory.THREAD_MXBEAN_NAME)
        private val diagnosticBeanName = ObjectName.getInstance("com.sun.management:type=HotSpotDiagnostic")

        private fun ThreadMXBean.dumpThreads() = ThreadDump(
            version = "(Unknown)",
            threads = dumpAllThreads(true, true).mapNotNull { info ->
                val name = info.threadName
                // filter out JMX bookkeeping threads, because their IDS aren't stable and could cause issues with the
                // de-duplication logic in MultiThreadView
                if (name.startsWith("RMI") || name.startsWith("JDWP") || name.startsWith("JMX")) {
                    null
                } else {
                    Thread(info)
                }
            },
            deadlockIds = findDeadlockedThreads()?.map(Long::toInt).orEmpty(),
        )

        private fun now(): String = LocalTime.now().toString()
        private fun Map<String, ThreadDump>.toView() = MultiThreadView(keys.toList(), values.toList())
    }
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
