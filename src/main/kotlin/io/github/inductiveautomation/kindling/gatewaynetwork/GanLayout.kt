package io.github.inductiveautomation.kindling.gatewaynetwork

import org.eclipse.elk.alg.layered.options.LayeredOptions
import org.eclipse.elk.alg.layered.options.OrderingStrategy
import org.eclipse.elk.core.RecursiveGraphLayoutEngine
import org.eclipse.elk.core.math.ElkPadding
import org.eclipse.elk.core.options.CoreOptions
import org.eclipse.elk.core.options.Direction
import org.eclipse.elk.core.options.EdgeLabelPlacement
import org.eclipse.elk.core.options.EdgeRouting
import org.eclipse.elk.core.util.BasicProgressMonitor
import org.eclipse.elk.graph.ElkEdgeSection
import org.eclipse.elk.graph.util.ElkGraphUtil
import java.awt.Dimension
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

class EdgeRoute(val path: Path2D, val labelBounds: Rectangle2D)

/**
 * Positions for every node and edge of a [GanGraph], computed by ELK's layered (Sugiyama-style) algorithm.
 */
class GanLayout(
    val width: Int,
    val height: Int,
    val nodes: Map<GanNode, Rectangle2D>,
    val edges: Map<GanEdge, EdgeRoute>,
) {
    companion object {
        fun compute(
            graph: GanGraph,
            nodeSize: (GanNode) -> Dimension,
            labelSize: (GanEdge) -> Dimension,
        ): GanLayout {
            val root = ElkGraphUtil.createGraph().apply {
                setProperty(CoreOptions.ALGORITHM, LayeredOptions.ALGORITHM_ID)
                setProperty(CoreOptions.DIRECTION, Direction.DOWN)
                setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.SPLINES)
                setProperty(CoreOptions.PADDING, ElkPadding(20.0))
                setProperty(LayeredOptions.SPACING_NODE_NODE, 40.0)
                setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, 40.0)
                setProperty(LayeredOptions.SPACING_EDGE_EDGE, 20.0)
                setProperty(LayeredOptions.SPACING_EDGE_LABEL, 4.0)
                // keep the diagram stable relative to the order of the source JSON
                setProperty(LayeredOptions.CONSIDER_MODEL_ORDER_STRATEGY, OrderingStrategy.NODES_AND_EDGES)
            }

            val elkNodes = graph.nodes.associateWith { node ->
                ElkGraphUtil.createNode(root).apply {
                    val size = nodeSize(node)
                    width = size.width.toDouble()
                    height = size.height.toDouble()
                }
            }
            val elkEdges = graph.edges.associateWith { edge ->
                ElkGraphUtil.createSimpleEdge(elkNodes.getValue(edge.source), elkNodes.getValue(edge.target)).apply {
                    ElkGraphUtil.createLabel(edge.label, this).apply {
                        val size = labelSize(edge)
                        width = size.width.toDouble()
                        height = size.height.toDouble()
                        setProperty(CoreOptions.EDGE_LABELS_PLACEMENT, EdgeLabelPlacement.CENTER)
                    }
                }
            }

            RecursiveGraphLayoutEngine().layout(root, BasicProgressMonitor())

            return GanLayout(
                width = root.width.toInt(),
                height = root.height.toInt(),
                nodes = elkNodes.mapValues { (_, node) ->
                    Rectangle2D.Double(node.x, node.y, node.width, node.height)
                },
                edges = elkEdges.mapValues { (_, edge) ->
                    val label = edge.labels.single()
                    EdgeRoute(
                        path = edge.sections.single().toPath(),
                        labelBounds = Rectangle2D.Double(label.x, label.y, label.width, label.height),
                    )
                },
            )
        }

        /**
         * With spline routing, ELK's bend points are cubic Bézier control points: (c1, c2, end) triples following
         * the start point.
         */
        private fun ElkEdgeSection.toPath(): Path2D = Path2D.Double().apply {
            moveTo(startX, startY)
            val points = bendPoints.map { it.x to it.y } + (endX to endY)
            if (points.size % 3 == 0) {
                for ((c1, c2, end) in points.chunked(3)) {
                    curveTo(c1.first, c1.second, c2.first, c2.second, end.first, end.second)
                }
            } else {
                for ((x, y) in points) {
                    lineTo(x, y)
                }
            }
        }
    }
}
