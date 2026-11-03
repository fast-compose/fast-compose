package ma7moud3ly.fast.compose.interpreter.jvm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import ma7moud3ly.fast.compose.interpreter.ExternalSymbol
import ma7moud3ly.fast.compose.interpreter.InterpretedLambda
import ma7moud3ly.fast.compose.interpreter.InterpretedObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.metadata.ClassName
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmFunction
import kotlin.metadata.KmValueParameter
import kotlin.metadata.declaresDefaultValue
import kotlin.metadata.isNullable
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.signature

/** True when [method] is a compiled composable: the Compose compiler adds a `Composer` parameter. */
internal fun isComposable(method: Method): Boolean = method.parameterTypes.any { it == Composer::class.java }

/**
 * Overloads of a library composable. The Compose compiler changes its JVM signature: after the value parameters come
 * the `Composer`, then `$changed` ints (3 bits per parameter, 10 per int) and, with default arguments, `$default`
 * ints (a bit per parameter, 31 per int). The original parameters, their names and which have defaults come from the
 * class's Kotlin metadata.
 */
internal class ComposableSymbol(private val methods: List<Method>, private val static: Boolean) : ExternalSymbol {

    override val isComposable: Boolean get() = true

    override fun read(receiver: Any?): Any? = throw UnsupportedOperationException("'${methods.first().name}' is a composable")

    override fun call(receiver: Any?, positional: List<Any?>, named: Map<String, Any?>): Any? =
        throw UnsupportedOperationException("'${methods.first().name}' is a composable; call it from a composable")

    override fun composable(receiver: Any?, positional: List<Any?>, named: Map<String, Any?>): @Composable () -> Unit {
        for (method in methods) {
            prepare(method, dispatch = if (static) null else receiver, extension = if (static) receiver else null, positional, named)
                ?.let { return it }
        }
        throw IllegalArgumentException("No overload of '${methods.first().name}' takes these arguments")
    }

    /** The call of [method] with these arguments, or null when they don't fit its parameters. */
    private fun prepare(
        method: Method,
        dispatch: Any?,
        extension: Any?,
        positional: List<Any?>,
        named: Map<String, Any?>,
    ): (@Composable () -> Unit)? {
        val types = method.parameterTypes
        val composerIndex = types.indexOf(Composer::class.java)
        val function = KotlinMetadata.functionOf(method, composerIndex)
        val receiverSlots = if (function?.receiverParameterType != null || extension != null) 1 else 0
        val valueCount = composerIndex - receiverSlots
        if (valueCount < 0) return null
        val parameters: List<KmValueParameter>? = function?.valueParameters?.takeIf { it.size == valueCount }
        if (named.isNotEmpty() && (parameters == null || named.keys.any { key -> parameters.none { it.name == key } })) return null

        val args = arrayOfNulls<Any?>(types.size)
        if (receiverSlots == 1) args[0] = extension ?: return null
        val defaults = IntArray((valueCount + 30) / 31)
        var next = 0
        for (i in 0 until valueCount) {
            val slot = receiverSlots + i
            val parameter = parameters?.get(i)
            val name = parameter?.name
            when {
                name != null && name in named -> args[slot] = adapt(named[name], types[slot], parameter).let { if (it === NoFit) return null else it }
                next < positional.size -> args[slot] = adapt(positional[next++], types[slot], parameter).let { if (it === NoFit) return null else it }
                parameter != null && parameter.declaresDefaultValue -> {
                    args[slot] = zeroOf(types[slot])
                    defaults[i / 31] = defaults[i / 31] or (1 shl (i % 31))
                }
                parameter != null && parameter.type.isNullable && !types[slot].isPrimitive -> args[slot] = null
                else -> return null
            }
        }
        if (next < positional.size) return null

        val ints = types.size - composerIndex - 1
        val changedCount = changedParamCount(valueCount, receiverSlots + if (dispatch != null) 1 else 0)
        val defaultCount = ints - changedCount
        if (defaultCount < 0 || defaults.any { it != 0 } && defaultCount < defaults.size) return null
        for (k in 0 until changedCount) args[composerIndex + 1 + k] = 0
        for (k in 0 until defaultCount) args[composerIndex + 1 + changedCount + k] = defaults.getOrElse(k) { 0 }

        return {
            val callArgs = args.copyOf()
            callArgs[composerIndex] = currentComposer
            try {
                method.invoke(dispatch, *callArgs)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    private companion object {
        /** Number of `$changed` ints for these parameters, as the Compose compiler computes it. */
        fun changedParamCount(valueParams: Int, thisParams: Int): Int {
            val total = valueParams + thisParams
            return if (total == 0) 1 else (total + 9) / 10
        }

        /**
         * [value] for a JVM parameter of [type]: interpreted lambdas as composable or plain functions, value classes
         * (`Color`, `Dp`) unboxed, numbers converted; [NoFit] when it doesn't fit.
         */
        fun adapt(value: Any?, type: Class<*>, parameter: KmValueParameter?): Any? {
            if (value == null) return if (type.isPrimitive) NoFit else null
            if (value is InterpretedLambda) return lambda(value, type, parameter) ?: NoFit
            if (value is InterpretedObject) return fromInterpreted(value, type) ?: NoFit
            val boxed = boxed(type)
            if (boxed.isInstance(value)) return value
            unboxValueClass(value)?.let { raw -> if (boxed.isInstance(raw)) return raw else convertNumber(raw, boxed)?.let { return it } }
            return convertNumber(value, boxed) ?: NoFit
        }

        /**
         * [lambda] as the function [type] takes: composable when the JVM function has two more parameters (the
         * `Composer` and `$changed`) than the Kotlin one in the metadata.
         */
        fun lambda(lambda: InterpretedLambda, type: Class<*>, parameter: KmValueParameter?): Any? {
            val jvmArity = type.name.removePrefix("kotlin.jvm.functions.Function").toIntOrNull() ?: return null
            val kotlinArity = (parameter?.type?.classifier as? KmClassifier.Class)?.name?.let(::functionArity)
            return if (kotlinArity != null && jvmArity == kotlinArity + 2) composable(lambda, kotlinArity) else plain(lambda, jvmArity)
        }

        fun functionArity(name: ClassName): Int? = name.removePrefix("kotlin/Function").toIntOrNull()

        fun composable(lambda: InterpretedLambda, arity: Int): Any? = when (arity) {
            0 -> composable0(lambda)
            1 -> composable1(lambda)
            2 -> composable2(lambda)
            else -> null
        }

        fun composable0(lambda: InterpretedLambda): @Composable () -> Unit = { lambda.compose(null, emptyList()) }

        /** One argument: the lambda's receiver (a scope such as `RowScope`) unless it declares a parameter or uses `it`. */
        fun composable1(lambda: InterpretedLambda): @Composable (Any?) -> Unit {
            val asReceiver = lambda.parameterCount == 0 && !lambda.usesIt
            return { value -> if (asReceiver) lambda.compose(value, emptyList()) else lambda.compose(null, listOf(value)) }
        }

        fun composable2(lambda: InterpretedLambda): @Composable (Any?, Any?) -> Unit = { a, b -> lambda.compose(null, listOf(a, b)) }

        fun plain(lambda: InterpretedLambda, arity: Int): Any? {
            val none: () -> Any? = { lambda.invoke(emptyList()) }
            val one: (Any?) -> Any? = { a -> lambda.invoke(listOf(a)) }
            val two: (Any?, Any?) -> Any? = { a, b -> lambda.invoke(listOf(a, b)) }
            val three: (Any?, Any?, Any?) -> Any? = { a, b, c -> lambda.invoke(listOf(a, b, c)) }
            return when (arity) {
                0 -> none
                1 -> one
                2 -> two
                3 -> three
                else -> null
            }
        }

        /** The underlying value of a value class instance, e.g. the `Long` of a `Color`; null for other values. */
        fun unboxValueClass(value: Any): Any? = runCatching {
            value.javaClass.getMethod("unbox-impl").invoke(value)
        }.getOrNull()

        fun convertNumber(value: Any, type: Class<*>): Any? = when {
            value !is Number -> null
            type == Long::class.javaObjectType -> value.toLong()
            type == Int::class.javaObjectType -> value.toInt()
            type == Double::class.javaObjectType -> value.toDouble()
            type == Float::class.javaObjectType -> value.toFloat()
            type == Short::class.javaObjectType -> value.toShort()
            type == Byte::class.javaObjectType -> value.toByte()
            else -> null
        }

        fun boxed(type: Class<*>): Class<*> = if (type.isPrimitive) type.kotlin.javaObjectType else type

        /** The value of an omitted parameter whose default the composable computes itself. */
        fun zeroOf(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Character.TYPE -> '\u0000'
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }
}

/** An argument that doesn't fit its parameter, so the next overload is tried. */
private object NoFit

/** Kotlin metadata of library classes: the functions they declare, by class. */
private object KotlinMetadata {
    private val functions = ConcurrentHashMap<Class<*>, List<KmFunction>>()

    /** The declaration of [method], whose value parameters come before [composerIndex] (and an extension receiver). */
    fun functionOf(method: Method, composerIndex: Int): KmFunction? {
        val declared = functionsOf(method.declaringClass)
        declared.firstOrNull { it.signature?.name == method.name && it.signature?.descriptor == descriptor(method) }?.let { return it }
        return declared.firstOrNull { function ->
            (function.name == method.name || method.name.startsWith(function.name + "-")) &&
                function.valueParameters.size + (if (function.receiverParameterType != null) 1 else 0) == composerIndex
        }
    }

    private fun functionsOf(type: Class<*>): List<KmFunction> = functions.getOrPut(type) {
        val annotation = type.getAnnotation(Metadata::class.java) ?: return@getOrPut emptyList()
        when (val metadata = runCatching { KotlinClassMetadata.readLenient(annotation) }.getOrNull()) {
            is KotlinClassMetadata.FileFacade -> metadata.kmPackage.functions
            is KotlinClassMetadata.MultiFileClassPart -> metadata.kmPackage.functions
            is KotlinClassMetadata.Class -> metadata.kmClass.functions
            is KotlinClassMetadata.MultiFileClassFacade -> metadata.partClassNames.flatMap { part ->
                runCatching { functionsOf(Class.forName(part.replace('/', '.'), false, type.classLoader)) }.getOrDefault(emptyList())
            }
            else -> emptyList()
        }
    }

    private fun descriptor(method: Method): String =
        method.parameterTypes.joinToString("", "(", ")") { descriptor(it) } + descriptor(method.returnType)

    private fun descriptor(type: Class<*>): String = when {
        type == Void.TYPE -> "V"
        type == java.lang.Boolean.TYPE -> "Z"
        type == java.lang.Character.TYPE -> "C"
        type == java.lang.Byte.TYPE -> "B"
        type == java.lang.Short.TYPE -> "S"
        type == java.lang.Integer.TYPE -> "I"
        type == java.lang.Long.TYPE -> "J"
        type == java.lang.Float.TYPE -> "F"
        type == java.lang.Double.TYPE -> "D"
        type.isArray -> "[" + descriptor(type.componentType)
        else -> "L" + type.name.replace('.', '/') + ";"
    }
}
