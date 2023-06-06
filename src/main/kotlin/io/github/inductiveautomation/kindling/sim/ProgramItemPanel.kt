package io.github.inductiveautomation.kindling.sim

import com.formdev.flatlaf.extras.components.FlatComboBox
import io.github.inductiveautomation.kindling.sim.model.ProgramDataType
import io.github.inductiveautomation.kindling.sim.model.ProgramItem
import io.github.inductiveautomation.kindling.sim.model.QualityCodes
import io.github.inductiveautomation.kindling.sim.model.SimulatorFunction
import io.github.inductiveautomation.kindling.sim.model.SimulatorFunctionParameter
import io.github.inductiveautomation.kindling.utils.configureCellRenderer
import net.miginfocom.swing.MigLayout
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import kotlin.properties.Delegates
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

class ProgramItemPanel(val item: ProgramItem) : JPanel(MigLayout("fillx, ins 5, gap 5")) {

    private val timeIntervalEntry = JSpinner(
        SpinnerNumberModel(
            item.timeInterval,
            1,
            100000,
            1
        )
    )

    private val browsePathEntry = JTextField(item.browsePath, 25)

    private val functionDropdown = FlatComboBox<KClass<*>>().apply {
        val functions = SimulatorFunction::class.nestedClasses.filter { !it.isCompanion }
        functions.forEach(this::addItem)

        configureCellRenderer { _, value, _, _, _ ->
            text = value?.simpleName ?: ""
        }
        selectedIndex = -1
    }

    private val parameterEntry = FunctionPanel(item.valueSource)

    private val dataTypeEntry = FlatComboBox<ProgramDataType>().apply {
        ProgramDataType.values().forEach(this::addItem)
        
        configureCellRenderer { _, value, _, _, _ ->
            text = value?.name ?: ""
        }

        if (item.dataType != null) {
            selectedItem = item.dataType
        } else {
            selectedIndex = -1
        }
    }

    init {
        add(timeIntervalEntry, "growx, bottom")
        add(browsePathEntry, "growx, bottom")
        add(functionDropdown, "growx, bottom")
        add(parameterEntry, "growx, pushx, bottom")
        add(dataTypeEntry, "growx, bottom")

        functionDropdown.addActionListener {
            item.valueSource = run {
                val selectedItem = functionDropdown.selectedItem as KClass<*>
                selectedItem.createInstance() as SimulatorFunction
            }
            parameterEntry.function = item.valueSource
        }

        timeIntervalEntry.addChangeListener {
            val model = timeIntervalEntry.model as SpinnerNumberModel
            item.timeInterval = model.value as Int
        }

        browsePathEntry.addActionListener {
            item.browsePath = browsePathEntry.text
        }

        dataTypeEntry.addActionListener {
            item.dataType = dataTypeEntry.selectedItem as ProgramDataType
        }
    }

    class FunctionPanel(function: SimulatorFunction?) : JPanel(MigLayout("fill, ins 0, flowy, wrap 2")) {
        var function by Delegates.observable(function) { _, _, new ->
            removeAll()
            updateParams(new?.parameters)
        }

        init {
            updateParams(function?.parameters)
        }

        private fun updateParams(newParams: List<SimulatorFunctionParameter<*>>?) {
            newParams?.forEachIndexed { i, param ->
                add(JLabel(param.name), "grow")

                val component: JComponent = when (param) {
                    is SimulatorFunctionParameter.Min,
                    is SimulatorFunctionParameter.Max,
                    is SimulatorFunctionParameter.Period,
                    is SimulatorFunctionParameter.SetPoint -> {
                        JSpinner(
                            SpinnerNumberModel(
                                param.value as Int,
                                Integer.MIN_VALUE,
                                Integer.MAX_VALUE,
                                1)
                        ).apply {
                            addChangeListener {
                                assignParam(i, value)
                            }
                        }
                    }

                    is SimulatorFunctionParameter.Derivative,
                    is SimulatorFunctionParameter.Integral,
                    is SimulatorFunctionParameter.Proportion -> {
                        JTextField(param.value.toString()).apply {
                            addActionListener {
                                assignParam(i, text.toFloat())
                            }
                        }
                    }

                    is SimulatorFunctionParameter.List -> {
                        JTextField(param.value.joinToString(",")).apply {
                            addActionListener {
                                val newList = text.split(",")
                                assignParam(i, newList)
                            }
                        }
                    }
                    is SimulatorFunctionParameter.QualityCode -> {
                        JComboBox(QualityCodes.values()).apply {
                            addActionListener {
                                val newValue = model.selectedItem as QualityCodes
                                assignParam(i, newValue)
                            }
                        }
                    }
                    is SimulatorFunctionParameter.Repeat -> {
                        JCheckBox("Repeat", param.value).apply {
                            verticalTextPosition = JCheckBox.NORTH
                            addActionListener {
                                assignParam(i, isSelected)
                            }
                        }
                    }
                    is SimulatorFunctionParameter.Value -> {
                        JTextField(param.value).apply {
                            addActionListener {
                                assignParam(i, text)
                            }
                        }
                    }
                }
                add(component)
            }
            revalidate()
            repaint()
        }

        @Suppress("unchecked_cast")
        private fun <T> assignParam(index: Int, newValue: T) {
            (function?.parameters?.get(index) as SimulatorFunctionParameter<T>).value = newValue
        }
    }
}

