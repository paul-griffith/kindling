package io.github.paulgriffith.idb

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTree
import com.inductiveautomation.ignition.common.util.csv.CSVWriter
import com.jidesoft.swing.SearchableUtils
import com.jidesoft.swing.StyledLabelBuilder
import com.jidesoft.tree.StyledTreeCellRenderer
import io.github.paulgriffith.utils.Action
import io.github.paulgriffith.utils.FlatScrollPane
import io.github.paulgriffith.utils.attachPopupMenu
import io.github.paulgriffith.utils.javaType
import io.github.paulgriffith.utils.setDefaultRenderer
import io.github.paulgriffith.utils.toList
import net.miginfocom.swing.MigLayout
import java.awt.Font
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.io.File
import java.sql.Connection
import java.sql.JDBCType
import java.util.Collections
import java.util.Enumeration
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.TransferHandler
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.AbstractTableModel
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreeSelectionModel

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

    private val tree = FlatTree().apply {
        model = DefaultTreeModel(root)
        isRootVisible = false
        showsRootHandles = true
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        cellRenderer = object : StyledTreeCellRenderer() {
            override fun customizeStyledLabel(
                tree: JTree,
                value: Any?,
                sel: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean,
            ) {
                clearStyleRanges()
                when (value) {
                    is Table -> {
                        text = value.name
                        icon = if (sel) TABLE_ICON_SELECTED else TABLE_ICON
                    }
                    is Column -> {
                        StyledLabelBuilder()
                            .add(value.name)
                            .add("   ")
                            .add(value.type.takeIf { it.isNotEmpty() } ?: "UNKNOWN", Font.ITALIC)
                            .configure(this)
                        icon = if (sel) COLUMN_ICON_SELECTED else COLUMN_ICON
                    }
                    else -> super.customizeStyledLabel(tree, value, sel, expanded, leaf, row, hasFocus)
                }
            }
        }

        attachPopupMenu { e ->
            val path = getClosestPathForLocation(e.x, e.y)
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
    }

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
                            // SQLite stores booleans as ints, we'll store actual booleans to make things easier
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

    private data class Table(
        val name: String,
        val columns: List<Column>,
        val _parent: () -> TreeNode,
    ) : TreeNode {
        override fun getChildAt(childIndex: Int): TreeNode = columns[childIndex]
        override fun getChildCount(): Int = columns.size
        override fun getParent(): TreeNode = _parent()
        override fun getIndex(node: TreeNode): Int = columns.indexOf(node)
        override fun getAllowsChildren(): Boolean = true
        override fun isLeaf(): Boolean = false
        override fun children(): Enumeration<out TreeNode> = Collections.enumeration(columns)
    }

    private data class Column(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val primaryKey: Boolean,
        val hidden: Boolean,
        val _parent: () -> TreeNode,
    ) : TreeNode {
        override fun getChildAt(childIndex: Int): TreeNode? = null
        override fun getChildCount(): Int = 0
        override fun getParent(): TreeNode = _parent()
        override fun getIndex(node: TreeNode?): Int = -1
        override fun getAllowsChildren(): Boolean = false
        override fun isLeaf(): Boolean = true
        override fun children(): Enumeration<out TreeNode> = Collections.emptyEnumeration()
    }

    companion object {
        private val TABLE_ICON = FlatSVGIcon("icons/bx-table.svg").derive(0.75F)
        private val TABLE_ICON_SELECTED = FlatSVGIcon("icons/bx-table.svg").derive(0.75F).apply {
            colorFilter = FlatSVGIcon.ColorFilter { UIManager.getColor("Tree.selectionForeground") }
        }
        private val COLUMN_ICON = FlatSVGIcon("icons/bx-column.svg").derive(0.75F)
        private val COLUMN_ICON_SELECTED = FlatSVGIcon("icons/bx-column.svg").derive(0.75F).apply {
            colorFilter = FlatSVGIcon.ColorFilter { UIManager.getColor("Tree.selectionForeground") }
        }
    }
}

class ResultModel(
    val columnNames: List<String>,
    private val columnTypes: List<Class<*>>,
    val data: List<List<*>>,
) : AbstractTableModel() {
    constructor() : this(emptyList(), emptyList(), emptyList())

    init {
        require(columnNames.size == columnTypes.size)
    }

    override fun getRowCount(): Int = data.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(columnIndex: Int): String = columnNames[columnIndex]
    override fun getColumnClass(columnIndex: Int): Class<*> = columnTypes[columnIndex]
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = data[rowIndex][columnIndex]
}

class ResultsPanel : JPanel(MigLayout("ins 0, fill, hidemode 3")) {
    private val table = ResultsTable().apply {
        addPropertyChangeListener("model") { e ->
            val newValue = e.newValue as ResultModel
            if (newValue.rowCount == 0 && newValue.columnCount == 0) {
                isVisible = false
                noResults.isVisible = true
            } else {
                isVisible = true
                noResults.isVisible = false
            }
        }
    }

    private val noResults = JLabel("No results - run a query in the text area above").apply {
        isVisible = false
    }

    var model: ResultModel by table::model

    private val copy = Action(
        description = "Copy to Clipboard",
        icon = FlatSVGIcon("icons/bx-clipboard.svg"),
    ) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        table.transferHandler.exportToClipboard(table, clipboard, TransferHandler.COPY)
    }

    private val save = Action(
        description = "Save to File",
        icon = FlatSVGIcon("icons/bx-save.svg")
    ) {
        JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("CSV File", "csv")
            selectedFile = File("query results.csv")
            val save = showSaveDialog(this@ResultsPanel)
            if (save == JFileChooser.APPROVE_OPTION) {
                CSVWriter(selectedFile.writer()).use { csv ->
                    csv.writeNext(table.model.columnNames)
                    table.model.data.forEach { line ->
                        csv.writeNext(line.map { it?.toString() })
                    }
                }
            }
        }
    }

    init {
        table.addPropertyChangeListener("model") {
            copy.isEnabled = model.rowCount > 0
            save.isEnabled = model.rowCount > 0
        }

        add(noResults, "cell 0 0")
        add(FlatScrollPane(table), "cell 0 0, push, grow")
        add(JButton(copy), "cell 1 0, top, flowy")
        add(JButton(save), "cell 1 0")
    }
}

class ResultsTable : JTable(ResultModel()) {
    init {
        autoCreateRowSorter = true
        SearchableUtils.installSearchable(this).apply {
            isCaseSensitive = false
            isRepeats = true
        }
        setDefaultRenderer<String> { _, value, _, _, _, _ ->
            text = value
            toolTipText = value
        }

        tableHeader = JTableHeader(columnModel).apply {
            val original = defaultRenderer
            defaultRenderer = TableCellRenderer { table, value, isSelected, hasFocus, row, column ->
                original.getTableCellRendererComponent(
                    table,
                    value,
                    isSelected,
                    hasFocus,
                    row,
                    column
                ).apply {
                    toolTipText = value?.toString()
                }
            }
        }
    }

    override fun getModel(): ResultModel = super.getModel() as ResultModel
}
