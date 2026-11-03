package ma7moud3ly.fast.compose.interpreter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import ma7moud3ly.fast.compose.parser.ast.ClassDecl
import ma7moud3ly.fast.compose.parser.ast.ClassKind
import ma7moud3ly.fast.compose.parser.ast.FunDecl
import ma7moud3ly.fast.compose.parser.ast.Lambda

public class InterpreterException(message: String) : Exception(message)

/**
 * A problem found while running code, at source [line] (1-based) when known. [isWarning] marks problems that are not
 * mistakes in the code, such as a missing resource file. [file] is the path of another project file the problem is
 * in; null for the previewed file.
 */
public data class RuntimeError(val message: String, val line: Int?, val isWarning: Boolean = false, val file: String? = null) {
    override fun toString(): String = when {
        file != null && line != null -> "${file.substringAfterLast('/').substringAfterLast('\\')}:$line: $message"
        file != null -> "${file.substringAfterLast('/').substringAfterLast('\\')}: $message"
        line != null -> "Line $line: $message"
        else -> message
    }
}

/** Result of a lookup that found nothing. */
internal object Unresolved

/** Marks an [Env] scope without an implicit receiver. */
internal object NoReceiver

/** Value of a property declared without an initializer, such as a `lateinit var`, until it is assigned. */
internal object Uninitialized

internal fun notInitialized(name: String) = InterpreterException("lateinit property '$name' has not been initialized")

/** Value produced by a runtime error inside composition; rendered once as an error box where it occurred. */
public class ErrorValue(public val message: String) {
    internal var shown = false
    override fun toString(): String = "<error: $message>"
}

/** `return`, `break` or `continue` travelling up to the enclosing function, lambda or loop. */
internal class Jump(val kind: Kind, val value: Any? = null, val label: String? = null) {
    enum class Kind { RETURN, BREAK, CONTINUE }
}

/**
 * Property backed by a delegate: Compose [State] or [Lazy]. A stand-in delegate, e.g. `by unboundApi()`, makes the
 * property a stand-in: reads give it and writes are skipped.
 */
internal class Delegated(private val delegate: Any?) {
    fun get(): Any? = when (delegate) {
        is State<*> -> delegate.value
        is Lazy<*> -> delegate.value
        is StandIn -> delegate
        else -> throw InterpreterException("Property delegate must be a State or Lazy, found ${typeName(delegate)}")
    }

    @Suppress("UNCHECKED_CAST")
    fun set(value: Any?) {
        if (delegate is StandIn) return
        if (delegate !is MutableState<*>) throw InterpreterException("Property delegate is read-only")
        (delegate as MutableState<Any?>).value = value
    }
}

/**
 * Property of an object, initialized on its first read. A failure is kept and thrown again on every read, so the
 * error names its real cause.
 */
internal class LazyField(private val initializer: () -> Any?) {
    private var isComputing = false
    private var failure: InterpreterException? = null

    fun compute(name: String): Any? {
        failure?.let { throw it }
        if (isComputing) throw InterpreterException("Property '$name' depends on itself")
        isComputing = true
        try {
            return initializer()
        } catch (e: InterpreterException) {
            failure = e
            throw e
        } finally {
            isComputing = false
        }
    }
}

/**
 * Stand-in for a library API that has no binding, e.g. `EditorTheme(…)` imported from a library. Calls, members and
 * operations on it give stand-ins, conditions on it are false, loops over it are empty, it shows as empty text, and
 * bindings use their defaults instead.
 */
internal class StandIn(val name: String) {
    override fun toString(): String = ""
}

/** Instance of a user-declared class, object or enum entry. */
public abstract class UserValue internal constructor(public val decl: ClassDecl) {
    internal val fields = mutableMapOf<String, Any?>()

    internal fun hasField(name: String) = name in fields

    internal fun read(name: String): Any? = when (val value = initialized(name)) {
        is Delegated -> value.get()
        Uninitialized -> throw notInitialized(name)
        else -> value
    }

    internal fun write(name: String, value: Any?) {
        val current = initialized(name)
        if (current is Delegated) current.set(value) else fields[name] = value
    }

    /** Raw value of a field, computing a [LazyField] first. */
    private fun initialized(name: String): Any? {
        val value = fields[name]
        if (value !is LazyField) return value
        return value.compute(name).also { fields[name] = it }
    }
}

public class ObjectValue internal constructor(decl: ClassDecl) : UserValue(decl) {

    /** Scope an anonymous `object : … { … }` was created in; its members see it. Null for declared classes. */
    internal var closure: Env? = null

    private val isData get() = decl.kind == ClassKind.DATA_CLASS

    internal fun dataValues(): List<Any?> =
        decl.constructorParams.filter { it.isProperty }.map { read(it.param.name) }

    override fun equals(other: Any?): Boolean =
        if (isData) other is ObjectValue && other.decl === decl && other.dataValues() == dataValues() else this === other

    override fun hashCode(): Int = if (isData) 31 * decl.name.hashCode() + dataValues().hashCode() else super.hashCode()

    override fun toString(): String = when (decl.kind) {
        ClassKind.DATA_CLASS -> decl.constructorParams.filter { it.isProperty }
            .joinToString(", ", "${decl.name}(", ")") { "${it.param.name}=${read(it.param.name)}" }
        ClassKind.OBJECT -> decl.name
        else -> "${decl.name}@${super.hashCode().toUInt().toString(16)}"
    }
}

public class EnumValue internal constructor(decl: ClassDecl, public val name: String, public val ordinal: Int) : UserValue(decl), Comparable<EnumValue> {
    override fun compareTo(other: EnumValue): Int = ordinal.compareTo(other.ordinal)
    override fun toString(): String = name
}

/** A lambda with its captured scope. [label] is the name of the call it was passed to, for `return@label`. */
public class LambdaValue internal constructor(internal val lambda: Lambda, internal val closure: Env, internal val label: String?) {
    override fun toString(): String = "(lambda)"
}

/** A composable lambda from Compose, such as BasicTextField's `innerTextField`, that interpreted code calls inside composition. */
public class ComposableSlot(public val content: @Composable () -> Unit) {
    override fun toString(): String = "(composable)"
}

/** Reference to a user function, optionally bound to a receiver; [closure] is set for local functions. */
public class FunctionRef internal constructor(internal val decl: FunDecl, internal val receiver: Any?, internal val closure: Env?) {
    override fun toString(): String = "fun ${decl.name}"
}

/**
 * `Type::name`, a reference to a member of a type such as `Char::lowercase` or `Item::title`: a function whose first
 * argument is the receiver. [env] is where the reference was written, for resolving extensions.
 */
internal class MemberRef(val type: String, val name: String, val env: Env) {
    override fun toString(): String = "$type::$name"
}

/**
 * A lambda already run for each of [items], giving [results] in the same order. Called with an item, or with
 * `(index, item)` when [indexed], it returns that item's result; equal items share the first one's.
 */
internal class PrecomputedLambda(private val items: List<Any?>, private val results: List<Any?>, private val indexed: Boolean) {
    fun result(args: List<Any?>): Any? {
        if (indexed) return results.getOrNull(args.firstOrNull() as? Int ?: -1)
        val item = args.firstOrNull()
        val index = items.indexOfFirst { it === item }.takeIf { it >= 0 } ?: items.indexOf(item)
        return results.getOrNull(index)
    }
}

/**
 * An object of library class [name], which has no binding, built from named arguments: its [properties] read back as
 * given and other members are stand-ins, e.g. `EditorTheme(background = Color.Black).background`.
 */
internal data class StandInObject(val name: String, val properties: Map<String, Any?>) {
    override fun toString(): String = "$name(${properties.entries.joinToString { "${it.key}=${it.value}" }})"
}

/**
 * An interpreted lambda or function reference [fn] as a Kotlin function of [arity] parameters (0 to 3), for library
 * code; errors inside are reported, not thrown.
 */
internal fun externalFunction(interpreter: Interpreter, fn: Any?, arity: Int): Any {
    val none: () -> Any? = { interpreter.invokeSafely(fn, NoReceiver, emptyList()) }
    val one: (Any?) -> Any? = { a -> interpreter.invokeSafely(fn, NoReceiver, listOf(a)) }
    val two: (Any?, Any?) -> Any? = { a, b -> interpreter.invokeSafely(fn, NoReceiver, listOf(a, b)) }
    val three: (Any?, Any?, Any?) -> Any? = { a, b, c -> interpreter.invokeSafely(fn, NoReceiver, listOf(a, b, c)) }
    return when (arity) {
        0 -> none
        1 -> one
        2 -> two
        else -> three
    }
}

/** A user class used as a value: `Screen.Home`, `Message::class`, companion access. */
public class ClassRef internal constructor(public val decl: ClassDecl) {
    override fun toString(): String = "class ${decl.name}"
}

internal val FunDecl.isComposable: Boolean get() = hasAnnotation("Composable")

internal fun typeName(value: Any?): String = when (value) {
    null -> "null"
    is UserValue -> value.decl.name
    is LambdaValue, is FunctionRef, is MemberRef, is ComposableSlot -> "Function"
    is StandIn -> value.name
    else -> value::class.simpleName ?: "value"
}
