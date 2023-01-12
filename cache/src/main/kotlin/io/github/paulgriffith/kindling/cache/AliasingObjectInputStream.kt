package io.github.paulgriffith.kindling.cache

import io.github.paulgriffith.kindling.cache.model.SerializationAlias
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

class AliasingObjectInputStream(inputStream: InputStream) : ObjectInputStream(inputStream) {
    val aliases: MutableMap<String, Class<*>> = mutableMapOf()

    inline fun <reified T> registerAlias() {
        val aliasAnnotation = requireNotNull(T::class.java.getAnnotation(SerializationAlias::class.java))
        aliases[aliasAnnotation.forClass] = T::class.java
    }

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
