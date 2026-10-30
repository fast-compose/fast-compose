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

/** Value produced by a runtime error inside composition; rendered once as an error box where it occurred. */
public class ErrorValue(public val message: String) {
    internal var shown = false
    override fun toString(): String = "<error: $message>"
}

/** `return`, `break` or `continue` travelling up to the enclosing function, lambda or loop. */
internal class Jump(val kind: Kind, val value: Any? = null, val label: String? = null) {
    enum class Kind { RETURN, BREAK, CONTINUE }
}

/** Property backed by a delegate: Compose [State] or [Lazy]. */
internal class Delegated(private val delegate: Any?) {
    fun get(): Any? = when (delegate) {
        is State<*> -> delegate.value
        is Lazy<*> -> delegate.value
        else -> throw InterpreterException("Property delegate must be a State or Lazy, found ${typeName(delegate)}")
    }

    @Suppress("UNCHECKED_CAST")
    fun set(value: Any?) {
        if (delegate !is MutableState<*>) throw InterpreterException("Property delegate is read-only")
        (delegate as MutableState<Any?>).value = value
    }
}

/** Instance of a user-declared class, object or enum entry. */
public abstract class UserValue internal constructor(public val decl: ClassDecl) {
    internal val fields = mutableMapOf<String, Any?>()

    internal fun hasField(name: String) = name in fields

    internal fun read(name: String): Any? = fields[name].let { if (it is Delegated) it.get() else it }

    internal fun write(name: String, value: Any?) {
        val current = fields[name]
        if (current is Delegated) current.set(value) else fields[name] = value
    }
}

public class ObjectValue internal constructor(decl: ClassDecl) : UserValue(decl) {

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

/** A user class used as a value: `Screen.Home`, `Message::class`, companion access. */
public class ClassRef internal constructor(public val decl: ClassDecl) {
    override fun toString(): String = "class ${decl.name}"
}

internal val FunDecl.isComposable: Boolean get() = hasAnnotation("Composable")

internal fun typeName(value: Any?): String = when (value) {
    null -> "null"
    is UserValue -> value.decl.name
    is LambdaValue, is FunctionRef, is ComposableSlot -> "Function"
    else -> value::class.simpleName ?: "value"
}
