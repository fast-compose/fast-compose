package ma7moud3ly.fast.compose.interpreter.jvm

import ma7moud3ly.fast.compose.interpreter.ExternalResolver
import ma7moud3ly.fast.compose.interpreter.ExternalSymbol
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.kotlinFunction

/**
 * Runs library code through reflection: classes, objects and top-level functions and properties of the libraries in
 * [loader], and members of the values they return. Kotlin reflection supplies parameter names, default arguments and
 * extension receivers; plain Java reflection is the fallback.
 *
 * Top-level declarations are found in the package's file facades (`DateTimeKt`) listed by [packageClasses].
 */
internal class ReflectionResolver(
    private val loader: ClassLoader,
    private val packageClasses: (packageName: String) -> List<String>,
) : ExternalResolver {

    private val found = ConcurrentHashMap<Pair<String, String>, Lookup>()
    private val members = ConcurrentHashMap<Pair<Class<*>, String>, Lookup>()

    /** A lookup result, cached also when nothing was found. */
    private class Lookup(val symbol: ExternalSymbol?)

    override fun find(packageName: String, name: String): ExternalSymbol? =
        found.getOrPut(packageName to name) { Lookup(findNow(packageName, name)) }.symbol

    override fun member(receiver: Any, name: String): ExternalSymbol? {
        val type = if (receiver is StaticScope) receiver.type else receiver.javaClass
        return members.getOrPut(type to name) { Lookup(memberNow(receiver, name)) }.symbol
    }

    private fun findNow(packageName: String, name: String): ExternalSymbol? {
        loadClass(if (packageName.isEmpty()) name else "$packageName.$name")?.let { return ClassSymbol(it) }
        val prefix = if (packageName.isEmpty()) "" else "$packageName."
        val facades = packageClasses(packageName).filter { it.endsWith("Kt") }.mapNotNull { loadClass(prefix + it) }
        val functions = facades.flatMap { facade -> facade.methods.filter { Modifier.isStatic(it.modifiers) && sameName(it, name) } }
        if (functions.isNotEmpty()) return functionSymbol(functions, static = true)
        val getter = getterName(name)
        val property = facades.firstNotNullOfOrNull { facade ->
            facade.methods.firstOrNull { Modifier.isStatic(it.modifiers) && it.name == getter && it.parameterCount == 0 }
        }
        return property?.let { PropertySymbol { _ -> invoke(it, null, emptyArray()) } }
    }

    private fun memberNow(receiver: Any, name: String): ExternalSymbol? {
        if (receiver is StaticScope) {
            val type = receiver.type
            val methods = type.methods.filter { Modifier.isStatic(it.modifiers) && sameName(it, name) }
            if (methods.isNotEmpty()) return functionSymbol(methods, static = true)
            val field = type.fields.firstOrNull { Modifier.isStatic(it.modifiers) && it.name == name } ?: return null
            return PropertySymbol { field.get(null) }
        }
        val type = receiver.javaClass
        val methods = type.methods.filter { !Modifier.isStatic(it.modifiers) && sameName(it, name) }
        val property = runCatching { type.kotlin.memberProperties.firstOrNull { it.name == name } }.getOrNull()
        if (property != null) {
            return object : ExternalSymbol {
                override fun read(receiver: Any?): Any? = property.apply { isAccessible = true }.getter.call(receiver)
                override fun call(receiver: Any?, positional: List<Any?>, named: Map<String, Any?>): Any? =
                    FunctionSymbol(methods, static = false).call(receiver, positional, named)
            }
        }
        if (methods.isNotEmpty()) return functionSymbol(methods, static = false)
        val getter = getterName(name)
        val javaGetter = type.methods.firstOrNull { (it.name == getter || it.name == "is${name.capitalized()}") && it.parameterCount == 0 }
        if (javaGetter != null) return PropertySymbol { invoke(javaGetter, it, emptyArray()) }
        val field = type.fields.firstOrNull { it.name == name } ?: return null
        return PropertySymbol { field.get(it) }
    }

    /** Plain functions, or composables when the overloads are compiled composables (see [ComposableSymbol]). */
    private fun functionSymbol(methods: List<Method>, static: Boolean): ExternalSymbol {
        val composables = methods.filter(::isComposable)
        return if (composables.isNotEmpty()) ComposableSymbol(composables, static) else FunctionSymbol(methods, static)
    }

    private fun loadClass(name: String): Class<*>? = try {
        Class.forName(name, false, loader)
    } catch (_: ClassNotFoundException) {
        null
    } catch (_: LinkageError) {
        null
    }

    /** A class: called, its constructor; read, its companion object, `object` instance, or its static members. */
    private inner class ClassSymbol(private val type: Class<*>) : ExternalSymbol {
        override fun read(receiver: Any?): Any? {
            staticField(type, "INSTANCE")?.let { return it }
            staticField(type, "Companion")?.let { return it }
            return StaticScope(type)
        }

        override fun call(receiver: Any?, positional: List<Any?>, named: Map<String, Any?>): Any? {
            val kotlinClass: KClass<*>? = runCatching { type.kotlin.also { it.constructors } }.getOrNull()
            kotlinClass?.constructors?.let { constructors ->
                for (constructor in constructors) bind(constructor, null, positional, named)?.let { return callBy(constructor, it) }
            }
            for (constructor in type.constructors.filter { it.parameterCount == positional.size }) {
                val args = convert(constructor.parameterTypes, positional) ?: continue
                return try {
                    constructor.newInstance(*args)
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            }
            throw IllegalArgumentException("No constructor of ${type.simpleName} takes these arguments")
        }
    }

    /** Overloads of a function or method; the first whose parameters fit the arguments runs. */
    private inner class FunctionSymbol(private val methods: List<Method>, private val static: Boolean) : ExternalSymbol {
        override fun read(receiver: Any?): Any? = throw UnsupportedOperationException("'${methods.first().name}' is a function")

        override fun call(receiver: Any?, positional: List<Any?>, named: Map<String, Any?>): Any? {
            for (method in methods) {
                val function = runCatching { method.kotlinFunction }.getOrNull() ?: continue
                bind(function, receiver, positional, named)?.let { return callBy(function, it) }
            }
            // Without Kotlin metadata: Java methods, by position; an extension's receiver is the first argument.
            val all = if (static && receiver != null) listOf(receiver) + positional else positional
            for (method in methods.filter { it.parameterCount == all.size }) {
                val args = convert(method.parameterTypes, all) ?: continue
                return invoke(method, if (static) null else receiver, args)
            }
            throw IllegalArgumentException("No overload of '${methods.first().name}' takes these arguments")
        }
    }

    private class PropertySymbol(private val get: (Any?) -> Any?) : ExternalSymbol {
        override fun read(receiver: Any?): Any? = get(receiver)
        override fun call(receiver: Any?, positional: List<Any?>, named: Map<String, Any?>): Any? =
            throw UnsupportedOperationException("A property can't be called")
    }

    /**
     * Arguments of [callable] for `callBy`: the receiver, then [positional] in order and [named] by name, converted to
     * the parameter types; parameters with defaults may be left out. Null when the arguments don't fit.
     */
    private fun bind(callable: KCallable<*>, receiver: Any?, positional: List<Any?>, named: Map<String, Any?>): Map<KParameter, Any?>? {
        val args = HashMap<KParameter, Any?>()
        var next = 0
        for (parameter in callable.parameters) {
            when (parameter.kind) {
                KParameter.Kind.INSTANCE, KParameter.Kind.EXTENSION_RECEIVER -> args[parameter] = receiver ?: return null
                else -> {
                    val name = parameter.name
                    val value = when {
                        name != null && name in named -> named[name]
                        parameter.isVararg && next < positional.size -> positional.drop(next).also { next = positional.size }
                        next < positional.size -> positional[next++]
                        parameter.isOptional -> continue
                        parameter.type.isMarkedNullable -> null
                        else -> return null
                    }
                    args[parameter] = convert(value, parameter) ?: if (value == null) null else return null
                }
            }
        }
        if (next < positional.size) return null
        if (named.keys.any { key -> callable.parameters.none { it.name == key } }) return null
        return args
    }

    private fun callBy(callable: KCallable<*>, args: Map<KParameter, Any?>): Any? = try {
        callable.isAccessible = true
        callable.callBy(args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }

    private fun invoke(method: Method, receiver: Any?, args: Array<Any?>): Any? = try {
        method.invoke(receiver, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }

    private companion object {
        /** `Instant`'s methods from a value class have mangled names, e.g. `plus-LRDsOJo`. */
        fun sameName(method: Method, name: String): Boolean =
            method.name == name || method.name.startsWith("$name-") && !method.isSynthetic

        fun getterName(name: String): String = "get${name.capitalized()}"

        fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }

        fun staticField(type: Class<*>, name: String): Any? = runCatching {
            type.getField(name).takeIf { Modifier.isStatic(it.modifiers) }?.get(null)
        }.getOrNull()

        /** [value] for a Kotlin parameter: numbers widened or narrowed to its type, lists as arrays for varargs. */
        fun convert(value: Any?, parameter: KParameter): Any? {
            if (value == null) return null
            val classifier = parameter.type.classifier as? KClass<*> ?: return value
            val type = classifier.java
            if (value is List<*> && type.isArray) {
                val component = type.componentType
                val array = java.lang.reflect.Array.newInstance(component, value.size)
                value.forEachIndexed { i, item ->
                    java.lang.reflect.Array.set(array, i, item?.let { convertTo(it, boxed(component)) ?: return null })
                }
                return array
            }
            return convertTo(value, classifier.javaObjectType)
        }

        /** [values] for Java parameter [types], or null when one doesn't fit. */
        fun convert(types: Array<Class<*>>, values: List<Any?>): Array<Any?>? {
            val out = arrayOfNulls<Any?>(values.size)
            for (i in values.indices) {
                val value = values[i]
                if (value == null) {
                    if (types[i].isPrimitive) return null
                    continue
                }
                out[i] = convertTo(value, boxed(types[i])) ?: return null
            }
            return out
        }

        fun convertTo(value: Any, type: Class<*>): Any? = when {
            type.isInstance(value) -> value
            value is Number && type == java.lang.Long::class.java -> value.toLong()
            value is Number && type == java.lang.Integer::class.java -> value.toInt()
            value is Number && type == java.lang.Double::class.java -> value.toDouble()
            value is Number && type == java.lang.Float::class.java -> value.toFloat()
            value is Number && type == java.lang.Short::class.java -> value.toShort()
            value is Number && type == java.lang.Byte::class.java -> value.toByte()
            else -> null
        }

        fun boxed(type: Class<*>): Class<*> = if (type.isPrimitive) type.kotlin.javaObjectType else type
    }
}

/** A Java class used as a value, for its static members, e.g. `LocalDate` in `LocalDate.now()`. */
internal class StaticScope(val type: Class<*>) {
    override fun toString(): String = type.simpleName
}
