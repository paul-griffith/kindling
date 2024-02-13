package io.github.inductiveautomation.kindling.utils

import java.text.NumberFormat
import java.util.EventListener
import javax.swing.InputVerifier
import javax.swing.JComponent
import javax.swing.JFormattedTextField
import javax.swing.SwingConstants
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.NumberFormatter

fun interface NumericChangeListener : EventListener {
    fun valueChanged()
}

class NumericEntryField(inputValue: Long?) : JFormattedTextField(inputValue) {
    private val format = NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }
//    private var previousValue = inputValue.toString()

    fun addNumericChangeListener(listener: NumericChangeListener) {
        listenerList.add(listener)
    }

    fun fireListeners() {
        listenerList.getAll<NumericChangeListener>().forEach(NumericChangeListener::valueChanged)
    }

    // Clean this up.... eventually.
//    private var isValidating = 2
//
//    fun validateTextField(): Boolean {
//        return if (text.all { it.isDigit() } && text.length < 19) {
//            previousValue = text
//            if (isValidating > 0) {
//                isValidating -= 1
//                false
//            } else {
//                true
//            }
//        } else {
//            isValidating = 2
//            text = previousValue
//            false
//        }
//    }

    init {
        formatterFactory = DefaultFormatterFactory(NumberFormatter(format))
        horizontalAlignment = SwingConstants.CENTER
        inputVerifier =
            object : InputVerifier() {
                override fun verify(input: JComponent): Boolean {
                    return (input as JFormattedTextField).text.let { input ->
                        input.all { it.isDigit() } && input.length < 19
                    }
                }
            }
//        document.addDocumentListener(
//            object : DocumentListener {
//                override fun insertUpdate(e: DocumentEvent) {
//                    SwingUtilities.invokeLater {
//                        if (validateTextField()) {
//                            fireListeners()
//                        }
//                    }
//                }
//
//                override fun removeUpdate(e: DocumentEvent) {
//                    SwingUtilities.invokeLater {
//                        if (validateTextField()) {
//                            fireListeners()
//                        }
//                    }
//                }
//
//                override fun changedUpdate(e: DocumentEvent) {}
//            },
//        )
    }
}
