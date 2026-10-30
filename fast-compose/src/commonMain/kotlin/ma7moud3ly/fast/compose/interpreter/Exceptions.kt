package ma7moud3ly.fast.compose.interpreter

/** An instance of a user class passed to `throw`, carried as a Kotlin exception. */
internal class ThrownValue(val value: UserValue) :
    RuntimeException(if (value.hasField("message")) value.read("message")?.toString() else null)

/** Standard exception types, each mapped to its parent type. */
private val exceptionParents = mapOf(
    "Exception" to "Throwable",
    "Error" to "Throwable",
    "RuntimeException" to "Exception",
    "IllegalStateException" to "RuntimeException",
    "IllegalArgumentException" to "RuntimeException",
    "UnsupportedOperationException" to "RuntimeException",
    "IndexOutOfBoundsException" to "RuntimeException",
    "NoSuchElementException" to "RuntimeException",
    "ArithmeticException" to "RuntimeException",
    "NullPointerException" to "RuntimeException",
    "ClassCastException" to "RuntimeException",
    "NumberFormatException" to "IllegalArgumentException",
    "NotImplementedError" to "Error",
)

internal fun isExceptionType(name: String) = name == "Throwable" || name in exceptionParents

/** True when the standard exception type [name] is [ancestor] or extends it. */
internal fun exceptionExtends(name: String, ancestor: String): Boolean {
    var current: String? = name
    while (current != null) {
        if (current == ancestor) return true
        current = exceptionParents[current]
    }
    return false
}

/** True when this exception is an instance of the standard exception type [name]. */
internal fun Throwable.isA(name: String): Boolean = when (name) {
    "Throwable" -> true
    "Exception" -> this is Exception
    "Error" -> this is Error
    "RuntimeException" -> this is RuntimeException
    "IllegalStateException" -> this is IllegalStateException
    "IllegalArgumentException" -> this is IllegalArgumentException
    "UnsupportedOperationException" -> this is UnsupportedOperationException
    "IndexOutOfBoundsException" -> this is IndexOutOfBoundsException
    "NoSuchElementException" -> this is NoSuchElementException
    "ArithmeticException" -> this is ArithmeticException
    "NullPointerException" -> this is NullPointerException
    "ClassCastException" -> this is ClassCastException
    "NumberFormatException" -> this is NumberFormatException
    "NotImplementedError" -> this is NotImplementedError
    else -> false
}

/** Creates the standard exception [name], or returns null when [name] is not one. */
internal fun newException(name: String, message: String?, cause: Throwable?): Throwable? = when (name) {
    "Throwable" -> Throwable(message, cause)
    "Exception" -> Exception(message, cause)
    "Error" -> Error(message, cause)
    "RuntimeException" -> RuntimeException(message, cause)
    "IllegalStateException" -> IllegalStateException(message, cause)
    "IllegalArgumentException" -> IllegalArgumentException(message, cause)
    "UnsupportedOperationException" -> UnsupportedOperationException(message, cause)
    "IndexOutOfBoundsException" -> IndexOutOfBoundsException(message)
    "NoSuchElementException" -> NoSuchElementException(message)
    "ArithmeticException" -> ArithmeticException(message)
    "NullPointerException" -> NullPointerException(message)
    "ClassCastException" -> ClassCastException(message)
    "NumberFormatException" -> NumberFormatException(message)
    "NotImplementedError" -> NotImplementedError(message ?: "An operation is not implemented.")
    else -> null
}

/** Error text for an uncaught exception, e.g. `IllegalStateException: Not found`. */
internal fun describe(e: Throwable): String {
    if (e is InterpreterException) return e.message ?: "Error"
    val name = if (e is ThrownValue) e.value.decl.name else e::class.simpleName ?: "Error"
    return e.message?.let { "$name: $it" } ?: name
}
