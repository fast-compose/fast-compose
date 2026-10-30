package ma7moud3ly.fast.compose.interpreter

/** Built-in operators on Kotlin values. Each returns [Unresolved] when the operands are not supported, so callers can fall back to operator functions. */
internal object Operators {

    private val arithmetic = setOf("+", "-", "*", "/", "%")

    fun binary(op: String, l: Any?, r: Any?): Any {
        when (op) {
            "+" -> {
                if (l is String) return l + r.toString()
                if (l is Char && r is Int) return l + r
            }
            "-" -> {
                if (l is Char && r is Char) return l - r
                if (l is Char && r is Int) return l - r
            }
            ".." -> return rangeTo(l, r)
            "..<" -> return rangeUntil(l, r)
            "<", ">", "<=", ">=" -> {
                val c = compare(l, r)
                if (c !is Int) return Unresolved
                return when (op) {
                    "<" -> c < 0
                    ">" -> c > 0
                    "<=" -> c <= 0
                    else -> c >= 0
                }
            }
        }
        if (op in arithmetic && l is Number && r is Number) return numeric(op, l, r)
        return Unresolved
    }

    private fun rank(n: Number) = when (n) {
        is Double -> 3
        is Float -> 2
        is Long -> 1
        else -> 0
    }

    private fun numeric(op: String, l: Number, r: Number): Any = when (maxOf(rank(l), rank(r))) {
        3 -> l.toDouble().let { a ->
            val b = r.toDouble()
            when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> a / b; else -> a % b }
        }
        2 -> l.toFloat().let { a ->
            val b = r.toFloat()
            when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> a / b; else -> a % b }
        }
        1 -> l.toLong().let { a ->
            val b = r.toLong()
            if ((op == "/" || op == "%") && b == 0L) throw InterpreterException("Division by zero")
            when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> a / b; else -> a % b }
        }
        else -> l.toInt().let { a ->
            val b = r.toInt()
            if ((op == "/" || op == "%") && b == 0) throw InterpreterException("Division by zero")
            when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> a / b; else -> a % b }
        }
    }

    /** Compares two values, returning a negative, zero or positive Int, or [Unresolved]. */
    @Suppress("UNCHECKED_CAST")
    fun compare(l: Any?, r: Any?): Any {
        if (l is Number && r is Number) {
            return if (maxOf(rank(l), rank(r)) >= 2) l.toDouble().compareTo(r.toDouble()) else l.toLong().compareTo(r.toLong())
        }
        if (l is Comparable<*> && r != null && l::class == r::class) return (l as Comparable<Any>).compareTo(r)
        return Unresolved
    }

    @Suppress("UNCHECKED_CAST")
    private fun rangeTo(l: Any?, r: Any?): Any = when {
        l is Int && r is Int -> l..r
        (l is Int || l is Long) && (r is Int || r is Long) -> (l as Number).toLong()..(r as Number).toLong()
        l is Char && r is Char -> l..r
        l is Float && r is Float -> l..r
        l is Number && r is Number -> l.toDouble()..r.toDouble()
        l is Comparable<*> && r != null && l::class == r::class -> (l as Comparable<Any>)..(r as Comparable<Any>)
        else -> Unresolved
    }

    private fun rangeUntil(l: Any?, r: Any?): Any = when {
        l is Int && r is Int -> l until r
        (l is Int || l is Long) && (r is Int || r is Long) -> (l as Number).toLong() until (r as Number).toLong()
        l is Char && r is Char -> l until r
        else -> Unresolved
    }

    /** `element in container`, or [Unresolved]. */
    @Suppress("UNCHECKED_CAST")
    fun contains(container: Any?, element: Any?): Any = when (container) {
        is IntRange -> element is Int && element in container
        is LongRange -> element is Number && element !is Double && element !is Float && element.toLong() in container
        is CharRange -> element is Char && element in container
        is ClosedRange<*> -> element is Comparable<*> && (container as ClosedRange<Comparable<Any>>).contains(element as Comparable<Any>)
        is Collection<*> -> element in container
        is Iterable<*> -> container.contains(element)
        is Map<*, *> -> element in container.keys
        is CharSequence -> when (element) {
            is Char -> element in container
            is CharSequence -> element in container
            else -> false
        }
        else -> Unresolved
    }

    fun negate(v: Any?): Any = when (v) {
        is Int -> -v
        is Long -> -v
        is Float -> -v
        is Double -> -v
        else -> Unresolved
    }

    /** Operator function name for an operator, used for overloads like `Dp.plus`. */
    fun functionName(op: String): String? = when (op) {
        "+" -> "plus"
        "-" -> "minus"
        "*" -> "times"
        "/" -> "div"
        "%" -> "rem"
        ".." -> "rangeTo"
        "..<" -> "rangeUntil"
        "<", ">", "<=", ">=" -> "compareTo"
        else -> null
    }
}
