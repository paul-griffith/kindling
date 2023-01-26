package io.github.paulgriffith.kindling.cache

import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

class AliasingObjectInputStream private constructor(
    inputStream: InputStream,
    private val aliases: MutableMap<String, Class<*>>,
) : ObjectInputStream(inputStream), MutableMap<String, Class<*>> by aliases {
    constructor(inputStream: InputStream) : this(inputStream, mutableMapOf())

    override fun readClassDescriptor(): ObjectStreamClass {
        val baseDescriptor = super.readClassDescriptor()

        return if (aliases.containsKey(baseDescriptor.name)) {
            val aliasClassDescriptor = ObjectStreamClass.lookup(aliases[baseDescriptor.name])
            val aliasUid = aliasClassDescriptor.serialVersionUID
            val expectedUid = baseDescriptor.serialVersionUID

            require(aliasUid == expectedUid) { "serialVersionUID mismatch; expected $expectedUid but got $aliasUid" }

            aliasClassDescriptor
        } else {
            baseDescriptor
        }
    }
}
