package io.github.inductiveautomation.kindling.utils

import net.miginfocom.swing.MigLayout
import java.util.EventListener
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import kotlin.math.ceil
import kotlin.math.min

class Pager(
    numberOfElements: Int,
    elementsPerPage: Int,
) : JPanel(MigLayout("ins 0, fillx, align center")) {

    var numberOfElements = numberOfElements
        set(value) {
            val oldNumberOfPages = numberOfPages
            field = value
            val newNumberOfPages = calcNumberOfPages()
            if (oldNumberOfPages != newNumberOfPages) {
                numberOfPages = newNumberOfPages
            }
        }

    var elementsPerPage = elementsPerPage
        set(value) {
            val oldNumberOfPages = numberOfPages
            field = value
            val newNumberOfPages = calcNumberOfPages()
            if (oldNumberOfPages != newNumberOfPages) {
                numberOfPages = newNumberOfPages
            }
        }

    var selectedPage = 1
        set(value) {
            if (value !in 1..numberOfPages) throw IndexOutOfBoundsException("Page $value not valid in range 1..$numberOfPages")
            field = value

            firstButton.isEnabled = value != 1
            backButton.isEnabled = value != 1
            forwardButton.isEnabled = value != numberOfPages
            lastButton.isEnabled = value != numberOfPages

            if (changedViaButton) {
                pageNumberSelector.value = value
            }

            val startIndex = (value - 1) * elementsPerPage
            val endIndex = min(startIndex + elementsPerPage, numberOfElements)

            firePageSelectedEvent(startIndex, endIndex, value)
        }

    private var numberOfPages = calcNumberOfPages()
        set(value) {
            field = value
            val currentPage = pageNumberSelector.value as Int
            if (currentPage > value) { // We are on the last page and the number of pages has reduced
                pageNumberSelector.model = SpinnerNumberModel(value, 1, value, 1)
                // the page has changed, so we fire the listener
                val startIndex = (value - 1) * elementsPerPage
                val endIndex = min(startIndex + elementsPerPage, numberOfElements)

                firePageSelectedEvent(startIndex, endIndex, value)
            } else {
                pageNumberSelector.model = SpinnerNumberModel(currentPage, 1, value, 1)
            }

            numPagesLabel.text = "of $value"
        }

    private val firstButton = JButton("<<").apply { isEnabled = false }
    private val backButton = JButton("<").apply { isEnabled = false }
    private val forwardButton = JButton(">").apply {
        isEnabled = numberOfPages > 1
    }
    private val lastButton = JButton(">>").apply {
        isEnabled = numberOfPages > 1
    }

    private val numPagesLabel = JLabel("of $numberOfPages")

    private val pageNumberSelector = JSpinner(SpinnerNumberModel(1, 1, numberOfPages, 1))

    private var changedViaButton = false

    init {
        firstButton.addActionListener {
            changedViaButton = true
            selectedPage = 1
            changedViaButton = false
        }
        backButton.addActionListener {
            changedViaButton = true
            selectedPage -= 1
            changedViaButton = false
        }
        forwardButton.addActionListener {
            changedViaButton = true
            selectedPage += 1
            changedViaButton = false
        }
        lastButton.addActionListener {
            changedViaButton = true
            selectedPage = numberOfPages
            changedViaButton = false
        }

        pageNumberSelector.addChangeListener {
            if (!changedViaButton) selectedPage = (it.source as JSpinner).value as Int
        }

        add(firstButton)
        add(backButton, "gapright 10")
        add(pageNumberSelector, "gapright 5")
        add(numPagesLabel, "gapright 10")
        add(forwardButton)
        add(lastButton)
    }

    private fun calcNumberOfPages() = ceil(numberOfElements.toDouble() / elementsPerPage).toInt()

    private fun firePageSelectedEvent(start: Int, end: Int, page: Int) {
        listenerList.getAll<PageSelectedListener>().forEach {
            it.onPageSelected(start, end, page)
        }
    }

    fun addPageSelectedListener(l: PageSelectedListener) = listenerList.add(l)

    fun interface PageSelectedListener : EventListener {
        fun onPageSelected(startIndex: Int, endIndex: Int, page: Int)
    }

}