package ma7moud3ly.fast.compose.interpreter.jvm

import ma7moud3ly.fast.compose.interpreter.ErrorValue
import ma7moud3ly.fast.compose.interpreter.InterpretedObject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * [value] for a JVM parameter of [type]: an [InterpretedObject] as the object itself when it fits, for an interface a
 * proxy forwarding to its members (see [interfaceProxy]), else null; other values unchanged.
 */
internal fun fromInterpreted(value: Any?, type: Class<*>): Any? {
    if (value !is InterpretedObject) return value
    val obj = value.value
    return when {
        type.isInstance(obj) -> obj
        type.isInterface -> interfaceProxy(value, type)
        else -> null
    }
}

/**
 * [obj] as an implementation of interface [type], e.g. a project's `class FakeSettings : Settings` passed to library
 * code. Getters and setters (`getKeys()`, `setName(…)`) use its properties, other methods its functions; a method it
 * doesn't declare runs the interface's default body when there is one.
 */
internal fun interfaceProxy(obj: InterpretedObject, type: Class<*>): Any =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
        val arguments = args?.toList().orEmpty()
        if (method.declaringClass == Any::class.java) {
            when (method.name) {
                "equals" -> proxy === arguments.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                else -> obj.toString()
            }
        } else {
            returned(forward(obj, proxy, method, arguments), method.returnType)
        }
    }

private fun forward(obj: InterpretedObject, proxy: Any, method: Method, arguments: List<Any?>): Any? {
    val name = method.name
    if (!obj.hasFunction(name)) {
        val property = propertyName(name, arguments.size)
        if (property != null && obj.hasProperty(property)) {
            return if (arguments.isEmpty()) obj.read(property) else obj.write(property, arguments[0])
        }
        if (method.isDefault) return InvocationHandler.invokeDefault(proxy, method, *arguments.toTypedArray())
    }
    return obj.call(name, arguments)
}

/** The property a getter or setter named [method] accesses: `keys` for `getKeys()`, `isDark` for `isDark()`. */
private fun propertyName(method: String, argumentCount: Int): String? {
    fun String.decapitalized() = replaceFirstChar { it.lowercaseChar() }
    return when {
        argumentCount == 0 && method.startsWith("get") && method.length > 3 -> method.substring(3).decapitalized()
        argumentCount == 0 && method.startsWith("is") && method.length > 2 -> method
        argumentCount == 1 && method.startsWith("set") && method.length > 3 -> method.substring(3).decapitalized()
        else -> null
    }
}

/** [value] as a method returning [type] gives it: numbers converted, and zero for a primitive without a value. */
private fun returned(value: Any?, type: Class<*>): Any? {
    if (type == Void.TYPE) return null
    val boxed = if (type.isPrimitive) type.kotlin.javaObjectType else type
    if (boxed.isInstance(value)) return value
    if (value is Number) {
        when (boxed) {
            Int::class.javaObjectType -> return value.toInt()
            Long::class.javaObjectType -> return value.toLong()
            Double::class.javaObjectType -> return value.toDouble()
            Float::class.javaObjectType -> return value.toFloat()
            Short::class.javaObjectType -> return value.toShort()
            Byte::class.javaObjectType -> return value.toByte()
        }
    }
    if (!type.isPrimitive) return value.takeUnless { it is ErrorValue }
    return when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Character.TYPE -> '\u0000'
        else -> 0.let { returned(it, type) }
    }
}
