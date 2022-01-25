package io.github.paulgriffith.idb.generic

import io.github.paulgriffith.idb.IdbPanel
import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.attachPopupMenu
import io.github.paulgriffith.utils.javaType
import io.github.paulgriffith.utils.toList
import net.miginfocom.swing.MigLayout
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.sql.Connection
import java.sql.JDBCType
import java.util.Collections
import java.util.Enumeration
import javax.swing.JButton
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.tree.TreeNode

class GenericView(connection: Connection) : IdbPanel() {
    private val tables: List<Table> = connection
        .prepareStatement("SELECT name FROM main.sqlite_schema WHERE type = \"table\" ORDER BY name")
        .executeQuery()
        .toList { resultSet ->
            resultSet.getString(1)
        }.mapIndexed { i, tableName ->
            Table(
                name = tableName,
                _parent = { root },
                columns = connection
                    .prepareStatement("PRAGMA table_xinfo(\"$tableName\");")
                    .executeQuery()
                    .toList { resultSet ->
                        Column(
                            name = resultSet.getString("name"),
                            type = resultSet.getString("type"),
                            notNull = resultSet.getInt("notnull") == 1,
                            defaultValue = resultSet.getString("dflt_value"),
                            primaryKey = resultSet.getInt("pk") == 1,
                            hidden = resultSet.getInt("hidden") == 1,
                            _parent = { root.getChildAt(i) }
                        )
                    },
            )
        }

    private val root: TreeNode = object : TreeNode {
        override fun getChildAt(childIndex: Int): TreeNode = tables[childIndex]
        override fun getChildCount(): Int = tables.size
        override fun getParent(): TreeNode? = null
        override fun getIndex(node: TreeNode): Int = tables.indexOf(node)
        override fun getAllowsChildren(): Boolean = true
        override fun isLeaf(): Boolean = false
        override fun children(): Enumeration<out TreeNode> = Collections.enumeration(tables)
    }

    private val tree = DBMetaDataTree(root)

    private val query = JTextArea(0, 0)

    private val execute = Action(name = "Execute") {
        if (!query.text.isNullOrEmpty()) {
            connection.prepareStatement(query.text)
                .executeQuery()
                .use { resultSet ->
                    val columnCount = resultSet.metaData.columnCount
                    val names = List(columnCount) { i -> resultSet.metaData.getColumnName(i + 1) }
                    val types = List(columnCount) { i ->
                        val sqlType = resultSet.metaData.getColumnType(i + 1)
                        val jdbcType = JDBCType.valueOf(sqlType)
                        jdbcType.javaType
                    }

                    val data = resultSet.toList {
                        List(columnCount) { i ->
                            // SQLite stores booleans as ints, we'll use actual booleans to make things easier
                            if (types[i] == Boolean::class.javaObjectType) {
                                resultSet.getObject(i + 1) == 1
                            } else {
                                resultSet.getObject(i + 1)
                            }
                        }
                    }

                    results.model = ResultModel(names, types, data)
                }
        } else {
            results.model = ResultModel()
        }
    }

    private val queryPanel = JPanel(MigLayout("ins 0, fill")).apply {
        add(JButton(execute), "wrap")
        add(query, "push, grow")
    }

    private val results = ResultsPanel()

    init {
        val ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(ctrlEnter, "execute")
        actionMap.put("execute", execute)

        tree.attachPopupMenu { event ->
            val path = tree.getClosestPathForLocation(event.x, event.y)
            when (val node = path?.lastPathComponent) {
                is Table -> JPopupMenu().apply {
                    add(
                        JMenuItem(
                            Action("SELECT * FROM ${node.name}") {
                                query.text = "SELECT * FROM ${node.name};"
                            }
                        )
                    )
                }
                is Column -> JPopupMenu().apply {
                    val table = path.parentPath.lastPathComponent as Table
                    add(
                        JMenuItem(
                            Action("SELECT ${node.name} FROM ${table.name}") {
                                query.text = "SELECT ${node.name} FROM ${table.name}"
                            }
                        )
                    )
                }
                else -> null
            }
        }

        add(
            JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                FlatScrollPane(tree),
                JSplitPane(
                    JSplitPane.VERTICAL_SPLIT,
                    FlatScrollPane(queryPanel),
                    results,
                ).apply {
                    resizeWeight = 0.5
                }
            ).apply {
                resizeWeight = 0.2
            },
            "push, grow"
        )
    }
}
