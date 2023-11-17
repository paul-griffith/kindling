package io.github.inductiveautomation.kindling.sim.model

import io.github.inductiveautomation.kindling.sim.model.SimulatorFunction.Companion.functions
import io.github.inductiveautomation.kindling.sim.model.SimulatorFunction.Companion.generateRandomParametersForFunction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TagParser(tagProvider: NodeStructure) {
    private val udtPathsToStructures = buildMap {
        val definitions = tagProvider.tags.find { node -> node.name == "_types_" } ?: return@buildMap
        val nodeStack = ArrayDeque(listOf(definitions))
        val pathStack = ArrayDeque(listOf(""))

        while (nodeStack.isNotEmpty() && pathStack.isNotEmpty()) {
            val folder = nodeStack.removeLast()
            val relativePath = pathStack.removeLast()

            for (tag in folder.tags) {
                if (tag.isFolder()) {
                    pathStack.addLast("$relativePath${tag.name}/")
                    nodeStack.addLast(tag)
                } else {
                    this["$relativePath${tag.name}"] = tag
                }
            }
        }
    }

    private val opcTags: MutableList<NodeStructure> = mutableListOf()

    val missingDefinitions = mutableSetOf<String>()

    val unsupportedDataTypes = mutableMapOf<TagDataType, Int>()

    init {
        tagProvider.tags.forEach(::resolveOpcTags)
    }

    val programItems: SimulatorProgram = mutableListOf<ProgramItem>().apply {
        opcTags.forEach { tag ->
            val actualPath = when (val path = tag.opcItemPath) {
                is JsonObject -> (path["binding"] as JsonPrimitive).content // Case when binding couldn't be resolved
                is JsonPrimitive -> path.content
                else -> null
            }

            if (actualPath != null && actualPath.isIgnitionOpcItemPath()) {
                val type = when (val path = tag.dataType) {
                    is JsonObject -> (path["binding"] as JsonPrimitive).content
                    is JsonPrimitive -> path.content
                    null -> "Int4"
                    else -> null
                }?.let(TagDataType::valueOf)?.let {
                    tagToSimDataType[it] ?: run {
                        unsupportedDataTypes.merge(it, 1) { _, v -> v + 1 }
                        null
                    }
                }

                if (type != null) {
                    add(
                        ProgramItem(
                            ignitionBrowsePath = actualPath,
                            dataType = type,
                            valueSource = run {
                                val clazz = SimulatorFunction.compatibleTypes.filter { (_, dataTypes) ->
                                    type in dataTypes
                                }.keys.random()

                                functions[clazz]!!().apply {
                                    generateRandomParametersForFunction(type)
                                }
                            },
                        ),
                    )
                }
            }
        }
    }.distinctBy(ProgramItem::browsePath).toMutableList()

    private fun resolveOpcTags(struct: NodeStructure) {
        when {
            struct.isFolder() -> {
                struct.tags.forEach(::resolveOpcTags)
            }
            struct.isUdtInstance() -> {
                zipInstanceWithDefinition(struct)
            }
            struct.isOpcTag() -> {
                resolveDataType(struct)
                resolveOpcItemPath(struct)
                opcTags.add(struct)
            }
        }
    }

    private fun zipInstanceWithDefinition(struct: NodeStructure) { // struct is a UdtInstance
        val definitionOfInstance = udtPathsToStructures[struct.typeId]

        if (definitionOfInstance == null) {
            missingDefinitions.add(struct.typeId!!) // Nullity is checked previously
            return
        }

        if (definitionOfInstance.isInherited()) {
            resolveDefinitionInheritence(definitionOfInstance)
        }

        // Zip all child tags/UDTs/Folders
        struct.zipWith(definitionOfInstance)

        // Resolve these parameters so that OPC tags are ready to grab the values
        resolveParameters(struct)

        // Now we repeate this process with all child UDT instances
        struct.tags.forEach {
            resolveOpcTags(it)
        }
    }

    private fun NodeStructure.zipWith(parent: NodeStructure) {
        when {
            this.isFolder() -> {
                parent.tags.forEach { parentTag ->
                    val childTag = tags.find { parentTag.name == it.name }!!
                    childTag.zipWith(parentTag)
                }
            }
            this.isUdtInstance() -> {
                parameters.addInheritedParameters(parent.parameters)
                typeId = parent.typeId
                parent.tags.forEach { parentTag ->
                    val childTag = tags.find { parentTag.name == it.name }!!
                    childTag.zipWith(parentTag)
                }
            }
            this.isUdtDefinition() -> {
                parameters.addInheritedParameters(parent.parameters)
                parent.tags.forEach { parentTag ->
                    val childTag = tags.find { parentTag.name == it.name }!!
                    childTag.zipWith(parentTag)
                }
            }
            this.isAtomicTag() -> {
                opcItemPath = opcItemPath ?: parent.opcItemPath
                opcServer = opcServer ?: parent.opcServer
                dataType = dataType ?: parent.dataType
                valueSource = valueSource ?: parent.valueSource
            }
        }
    }

    private fun resolveDefinitionInheritence(struct: NodeStructure) { // struct is a UdtDefiniion
        val parentUdt = udtPathsToStructures[struct.typeId]

        if (parentUdt == null) {
            missingDefinitions.add(struct.typeId!!) // Nullity is checked previously
            return
        }
        if (parentUdt.isInherited()) {
            resolveDefinitionInheritence(parentUdt)
        }

        // Add all parameters and tags which the child doesn't explicitly have already.
        struct.zipWith(parentUdt)
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val JSON = Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        val tagToSimDataType = mapOf(
            TagDataType.Short to ProgramDataType.INT16,
            TagDataType.Integer to ProgramDataType.INT32,
            TagDataType.Int1 to ProgramDataType.INT16,
            TagDataType.Int2 to ProgramDataType.INT16,
            TagDataType.Int4 to ProgramDataType.INT32,
            TagDataType.Int8 to ProgramDataType.INT64,
            TagDataType.Long to ProgramDataType.INT64,
            TagDataType.Float4 to ProgramDataType.FLOAT,
            TagDataType.Float8 to ProgramDataType.DOUBLE,
            TagDataType.Boolean to ProgramDataType.BOOLEAN,
            TagDataType.String to ProgramDataType.STRING,
            TagDataType.DateTime to ProgramDataType.DATETIME,
            TagDataType.Text to ProgramDataType.STRING,
            TagDataType.Document to ProgramDataType.STRING,
        )

        // https://docs.inductiveautomation.com/display/DOC81/UDT+Parameters#UDTParameters-Pre-DefinedParameters
        private val builtInParameters = mapOf<String, (NodeStructure) -> String>(
            "InstanceName" to { if (it.isUdtInstance()) it.name else it.getUdtParent()!!.name },
            "ParentInstanceName" to { it.getUdtParent()?.name ?: "" },
            "PathToParentFolder" to { it.getBrowsableParent()!!.getPath() },
            "TagName" to { it.name },
            "PathToTag" to { it.getPath() },
            "RootInstanceName" to {
                var parentInstance = it.getUdtParent()
                while (parentInstance!!.getUdtParent() != null) {
                    parentInstance = parentInstance.getUdtParent()
                }
                parentInstance.name
            },
        )

        private val PARAM_REGEX = """\{(.*?)}""".toRegex()

        fun resolveParameters(struct: NodeStructure) {
            struct.parameters.forEach { param ->
                if (param.value.isBoundValue()) {
                    param.value = resolveBoundValue(struct, param.value)
                }
            }
        }

        fun resolveOpcItemPath(struct: NodeStructure) {
            require(struct.isOpcTag())
            if (struct.opcItemPath.isBoundValue()) {
                struct.opcItemPath = resolveBoundValue(struct, struct.opcItemPath)
            }
        }

        fun resolveDataType(struct: NodeStructure) {
            if (struct.dataType.isBoundValue()) {
                struct.dataType = resolveBoundValue(struct, struct.dataType)
            }
        }

        private fun resolveBoundValue(struct: NodeStructure, valueToResolve: JsonElement?): JsonPrimitive {
            // This value is bound to a parent UDT parameter. Go to the parent and get the value
            var boundStringValue = valueToResolve
                ?.jsonObject
                ?.get("binding")
                ?.jsonPrimitive
                ?.content!!

            val boundParamNames = parseBoundValueNames(boundStringValue)

            boundParamNames.forEach { boundParamName ->
                val boundParamValue = if (boundParamName in builtInParameters) {
                    resolveBuiltInParamBinding(struct, boundParamName)
                } else {
                    findParentParameterValue(struct, boundParamName)
                }

                boundStringValue = boundStringValue.replace(
                    oldValue = "{$boundParamName}",
                    newValue = boundParamValue,
                )
            }
            return JsonPrimitive(boundStringValue)
        }

        private fun resolveBuiltInParamBinding(struct: NodeStructure, boundParamName: String): String {
            return builtInParameters[boundParamName]!!.invoke(struct)
        }

        private fun findParentParameterValue(struct: NodeStructure, parameterName: String): String {
            val parentUdt = struct.getUdtParent() ?: return "{$parameterName}"
            val boundParamValue = parentUdt.parameters.find { it.name == parameterName }?.value?.jsonPrimitive?.content
            return boundParamValue ?: findParentParameterValue(parentUdt, parameterName)
        }

        fun ParameterList.addInheritedParameters(parent: ParameterList) {
            val parameterNames = map(UdtParameter::name)
            addAll(
                parent.filter {
                    it.name !in parameterNames
                }.map { it.copy() },
            )
        }

        private fun parseBoundValueNames(boundValue: String): List<String> {
            return PARAM_REGEX.findAll(boundValue).map { result ->
                result.groupValues[1]
            }.toList()
        }

        private fun NodeStructure.getPath(): String {
            return buildString {
                var p = parent
                while (!p.isTagProvider) {
                    insert(0, "${p.name}/")
                    p = p.parent
                }
            } + name
        }
        private fun NodeStructure.isAtomicTag(): Boolean = tagType == "AtomicTag"
        private fun NodeStructure.isFolder(): Boolean = tagType == "Folder"
        private fun NodeStructure.isUdtInstance(): Boolean = tagType == "UdtInstance"
        private fun NodeStructure.isUdtDefinition(): Boolean = tagType == "UdtType"
        private fun NodeStructure.isInherited(): Boolean = isUdtDefinition() && !typeId.isNullOrEmpty()
        private fun NodeStructure.isUdtStructure(): Boolean = isUdtInstance() || isUdtDefinition()
        private fun NodeStructure.isOpcTag(): Boolean = valueSource == "opc"
        private fun NodeStructure.getUdtParent(): NodeStructure? {
            var p = parent
            while (!p.isUdtStructure()) {
                if (p.isTagProvider) return null
                p = p.parent
            }
            return p
        }

        private fun NodeStructure.getBrowsableParent(): NodeStructure? {
            var p = parent
            while (!p.isUdtStructure() && !p.isFolder()) {
                if (p.isTagProvider) return null
                p = p.parent
            }
            return p
        }

        private fun String.isIgnitionOpcItemPath(): Boolean {
            val regex = """ns=[0-9];s=\[.*].*""".toRegex()
            return regex.matches(this)
        }

        private fun JsonElement?.isBoundValue(): Boolean = this != null && this is JsonObject && get("binding") != null
    }
}
