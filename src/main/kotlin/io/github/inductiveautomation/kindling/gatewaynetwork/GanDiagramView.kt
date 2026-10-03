package io.github.inductiveautomation.kindling.gatewaynetwork

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatSVGIcon
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.FlatteningPathIterator
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.UIManager
import kotlin.math.abs
import kotlin.math.atan2

sealed interface DiagramSelection {
    data class Node(val node: GanNode) : DiagramSelection
    data class Edge(val edge: GanEdge) : DiagramSelection
}

/**
 * Paints a laid-out [GanGraph] at its natural size. Intended to be the view of a scroll pane; dragging pans the
 * viewport and clicking selects a node or edge.
 */
class GanDiagramView(private val graph: GanGraph) :
    JComponent(),
    Scrollable {
    var selection: DiagramSelection? = null
        set(value) {
            if (field != value) {
                field = value
                repaint()
                onSelectionChanged(value)
            }
        }

    var onSelectionChanged: (DiagramSelection?) -> Unit = {}

    private lateinit var diagramLayout: GanLayout
    private lateinit var edgeHitAreas: Map<GanEdge, Shape>
    private lateinit var labelFont: Font
    private lateinit var localLabelFont: Font
    private lateinit var edgeLabelFont: Font

    init {
        isOpaque = true
        updateUI()

        val mouseHandler = DragScrollHandler()
        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
    }

    override fun updateUI() {
        super.updateUI()
        val base = UIManager.getFont("Label.font")
        labelFont = base.deriveFont(base.size2D - 1)
        localLabelFont = labelFont.deriveFont(Font.BOLD)
        edgeLabelFont = base.deriveFont(base.size2D - 2)
        background = UIManager.getColor("Panel.background")
        foreground = UIManager.getColor("Label.foreground")
        computeLayout()
    }

    private fun computeLayout() {
        diagramLayout = GanLayout.compute(
            graph,
            nodeSize = { node ->
                val label = textSize(node.label, node.labelFont)
                Dimension(maxOf(NODE_DIAMETER, label.width), NODE_DIAMETER + LABEL_GAP + label.height)
            },
            labelSize = { edge ->
                val label = textSize(edge.label, edgeLabelFont)
                Dimension(label.width + 2 * EDGE_LABEL_PADDING, label.height + 2 * EDGE_LABEL_PADDING)
            },
        )
        val hitStroke = BasicStroke(EDGE_HIT_WIDTH)
        edgeHitAreas = diagramLayout.edges.mapValues { (_, route) -> hitStroke.createStrokedShape(route.path) }
        revalidate()
        repaint()
    }

    private val GanNode.labelFont: Font
        get() = if (isLocal) localLabelFont else this@GanDiagramView.labelFont

    private fun textSize(text: String, font: Font): Dimension {
        val metrics = getFontMetrics(font)
        val lines = text.lines()
        return Dimension(lines.maxOf(metrics::stringWidth), lines.size * metrics.height)
    }

    /**
     * The diagram is centered when the viewport is larger than it.
     */
    private val origin: Point
        get() = Point(
            maxOf(0, (width - diagramLayout.width) / 2),
            maxOf(0, (height - diagramLayout.height) / 2),
        )

    fun hitTest(point: Point): DiagramSelection? {
        val p = Point2D.Double((point.x - origin.x).toDouble(), (point.y - origin.y).toDouble())
        diagramLayout.nodes.entries.find { (_, bounds) -> bounds.contains(p) }?.let { (node, _) ->
            return DiagramSelection.Node(node)
        }
        diagramLayout.edges.entries.find { (edge, route) ->
            route.labelBounds.contains(p) || edgeHitAreas.getValue(edge).contains(p)
        }?.let { (edge, _) ->
            return DiagramSelection.Edge(edge)
        }
        return null
    }

    override fun getPreferredSize() = Dimension(diagramLayout.width, diagramLayout.height)

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.color = background
            g2.fillRect(0, 0, width, height)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.translate(origin.x, origin.y)

            for ((edge, route) in diagramLayout.edges) {
                paintEdge(g2, edge, route)
            }
            for ((edge, route) in diagramLayout.edges) {
                paintEdgeLabel(g2, edge, route.labelBounds)
            }
            for ((node, bounds) in diagramLayout.nodes) {
                paintNode(g2, node, bounds)
            }
        } finally {
            g2.dispose()
        }
    }

    private fun paintEdge(g: Graphics2D, edge: GanEdge, route: EdgeRoute) {
        val selected = (selection as? DiagramSelection.Edge)?.edge == edge
        val width = if (selected) 3F else 1F
        g.color = edge.color
        g.stroke = if (edge.status == EdgeStatus.Faulted) {
            BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10F, floatArrayOf(6F, 4F), 0F)
        } else {
            BasicStroke(width)
        }
        g.draw(route.path)

        if (edge.status == EdgeStatus.Proxy) return

        // arrows point at the end of the connection that was connected to
        val points = route.path.flatten()
        val reversed = if (edge.isIncoming) points.asReversed() else points
        g.fillArrow(reversed.last(), reversed[reversed.lastIndex - 1])
        val (mid, beforeMid) = reversed.pointAtHalfLength()
        g.fillArrow(mid, beforeMid)
    }

    private fun paintEdgeLabel(g: Graphics2D, edge: GanEdge, bounds: Rectangle2D) {
        g.color = background
        g.fill(RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, 4.0, 4.0))
        g.font = edgeLabelFont
        g.color = if (edge.status == EdgeStatus.Faulted) edge.color else foreground
        g.drawCenteredLines(edge.label, bounds.centerX, bounds.y + EDGE_LABEL_PADDING)
    }

    private fun paintNode(g: Graphics2D, node: GanNode, bounds: Rectangle2D) {
        val centerX = bounds.centerX
        val circle = Ellipse2D.Double(centerX - NODE_DIAMETER / 2.0, bounds.y, NODE_DIAMETER.toDouble(), NODE_DIAMETER.toDouble())
        val selected = (selection as? DiagramSelection.Node)?.node == node

        if (selected) {
            g.color = UIManager.getColor("Component.focusColor") ?: Color.GRAY
            g.stroke = BasicStroke(3F)
            g.draw(circle)
        } else {
            // double border, as in the web viewer
            g.color = if (node.isRedundant) REDUNDANT_COLOR else UIManager.getColor("Component.borderColor") ?: Color.LIGHT_GRAY
            g.stroke = BasicStroke(1F)
            g.draw(circle)
            g.draw(circle.inset(2.5))
        }

        val icon = iconFor(node)
        icon.paintIcon(this, g, (centerX - ICON_SIZE / 2.0).toInt(), (circle.centerY - ICON_SIZE / 2.0).toInt())

        g.font = node.labelFont
        g.color = foreground
        g.drawCenteredLines(node.label, centerX, bounds.y + NODE_DIAMETER + LABEL_GAP)
    }

    private fun Graphics2D.drawCenteredLines(text: String, centerX: Double, top: Double) {
        val metrics = fontMetrics
        for ((i, line) in text.lines().withIndex()) {
            val x = centerX - metrics.stringWidth(line) / 2.0
            val y = top + i * metrics.height + metrics.ascent
            drawString(line, x.toFloat(), y.toFloat())
        }
    }

    private fun Graphics2D.fillArrow(tip: Point2D, from: Point2D) {
        val angle = atan2(tip.y - from.y, tip.x - from.x)
        val arrow = Path2D.Double().apply {
            moveTo(0.0, 0.0)
            lineTo(-ARROW_LENGTH, -ARROW_WIDTH / 2)
            lineTo(-ARROW_LENGTH, ARROW_WIDTH / 2)
            closePath()
        }
        val g = create() as Graphics2D
        try {
            g.translate(tip.x, tip.y)
            g.rotate(angle)
            g.fill(arrow)
        } finally {
            g.dispose()
        }
    }

    private val GanEdge.color: Color
        get() = when (status) {
            EdgeStatus.Running -> if (FlatLaf.isLafDark()) RUNNING_DARK else RUNNING_LIGHT
            EdgeStatus.Faulted -> if (FlatLaf.isLafDark()) FAULTED_DARK else FAULTED_LIGHT
            EdgeStatus.Proxy -> UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 16

    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = if (orientation == SwingConstants.VERTICAL) {
        visibleRect.height
    } else {
        visibleRect.width
    }

    // stretch to fill the viewport when smaller than it, so the diagram can be centered
    override fun getScrollableTracksViewportWidth() = (parent as? JViewport)?.let { it.width > preferredSize.width } ?: false

    override fun getScrollableTracksViewportHeight() = (parent as? JViewport)?.let { it.height > preferredSize.height } ?: false

    private inner class DragScrollHandler : MouseAdapter() {
        private var pressedOnScreen: Point? = null
        private var pressedViewPosition: Point? = null
        private var dragging = false

        override fun mousePressed(e: MouseEvent) {
            pressedOnScreen = e.locationOnScreen
            pressedViewPosition = (parent as? JViewport)?.viewPosition
            dragging = false
        }

        override fun mouseDragged(e: MouseEvent) {
            val start = pressedOnScreen ?: return
            val dx = e.locationOnScreen.x - start.x
            val dy = e.locationOnScreen.y - start.y
            if (!dragging && abs(dx) + abs(dy) < DRAG_THRESHOLD) return
            dragging = true
            cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)

            val viewport = parent as? JViewport ?: return
            val initial = pressedViewPosition ?: return
            val maxX = maxOf(0, width - viewport.width)
            val maxY = maxOf(0, height - viewport.height)
            viewport.viewPosition = Point(
                (initial.x - dx).coerceIn(0, maxX),
                (initial.y - dy).coerceIn(0, maxY),
            )
        }

        override fun mouseReleased(e: MouseEvent) {
            if (!dragging) {
                selection = hitTest(e.point)
            }
            pressedOnScreen = null
            dragging = false
            updateCursor(e.point)
        }

        override fun mouseMoved(e: MouseEvent) = updateCursor(e.point)

        private fun updateCursor(point: Point) {
            cursor = if (hitTest(point) != null) {
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            } else {
                Cursor.getDefaultCursor()
            }
        }
    }

    companion object {
        private const val ICON_SIZE = 32
        private const val NODE_DIAMETER = 40
        private const val LABEL_GAP = 4
        private const val EDGE_LABEL_PADDING = 2
        private const val EDGE_HIT_WIDTH = 8F
        private const val ARROW_LENGTH = 8.0
        private const val ARROW_WIDTH = 7.0
        private const val DRAG_THRESHOLD = 4

        private val REDUNDANT_COLOR = Color(0xF7901E)
        private val RUNNING_LIGHT = Color(0x2E8B3A)
        private val RUNNING_DARK = Color(0x5CB85C)
        private val FAULTED_LIGHT = Color(0xD32F2F)
        private val FAULTED_DARK = Color(0xE5534B)

        private val icons = mutableMapOf<String, FlatSVGIcon>()

        private fun iconFor(node: GanNode): FlatSVGIcon {
            val name = (if (node.isLocal) "local-" else "") + Edition.of(node.edition).iconName
            return icons.getOrPut(name) {
                FlatSVGIcon("io/github/inductiveautomation/kindling/gatewaynetwork/icons/$name.svg", ICON_SIZE, ICON_SIZE)
            }
        }

        private fun Ellipse2D.inset(amount: Double) = Ellipse2D.Double(x + amount, y + amount, width - 2 * amount, height - 2 * amount)

        private fun Path2D.flatten(): List<Point2D> = buildList {
            val iterator = FlatteningPathIterator(getPathIterator(null), 0.5)
            val coords = DoubleArray(6)
            while (!iterator.isDone) {
                iterator.currentSegment(coords)
                add(Point2D.Double(coords[0], coords[1]))
                iterator.next()
            }
        }

        /**
         * Returns the point halfway along a polyline, and the point just before it (for the direction of travel).
         */
        private fun List<Point2D>.pointAtHalfLength(): Pair<Point2D, Point2D> {
            val segments = zipWithNext()
            val half = segments.sumOf { (a, b) -> a.distance(b) } / 2
            var travelled = 0.0
            for ((a, b) in segments) {
                val length = a.distance(b)
                if (travelled + length >= half && length > 0) {
                    val t = (half - travelled) / length
                    val mid = Point2D.Double(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                    return mid to a
                }
                travelled += length
            }
            return last() to this[lastIndex - 1]
        }
    }
}
