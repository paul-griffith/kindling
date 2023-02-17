package io.github.paulgriffith.kindling.sim

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.core.Tool
import io.github.paulgriffith.kindling.core.ToolPanel
import io.github.paulgriffith.kindling.sim.model.TagProviderStructure
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JScrollPane
import javax.swing.JTextArea
import kotlin.io.path.inputStream

@OptIn(ExperimentalSerializationApi::class)
class SimulatorView(path: Path) : ToolPanel() {
    override val icon: Icon = FlatSVGIcon("icons/bx-archive.svg")

    //val tagConfig: MutableList<TagConfiguration> = TagUtilities.toTagConfiguration(Files.readString(path), null)

    private val tagProvider: TagProviderStructure = path.inputStream().use(JSON::decodeFromStream)

    init {
        name = "Sim"

        tagProvider.resolveOpcTags()

        add(
            JScrollPane(
                JTextArea("Check the logs").apply {
                    lineWrap = true
                }
            ), "push, grow, span"
        )
    }

    companion object {
        private val JSON = Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

object SimulatorViewer : Tool {
    override val extensions = listOf("json")
    override val description = "Opens a tag export."
    override val icon: FlatSVGIcon = FlatSVGIcon("Placeholder")
    override val title = "Tag Export"
    override fun open(path: Path): ToolPanel {
        return SimulatorView(path)
    }
}

class SimulatorViewerProxy : Tool by SimulatorViewer