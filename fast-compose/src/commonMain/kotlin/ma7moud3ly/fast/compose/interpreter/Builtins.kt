package ma7moud3ly.fast.compose.interpreter

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Subset of the Kotlin standard library. Every function returns [Unresolved] for names or receivers it does not support. */
@Suppress("UNCHECKED_CAST")
internal class Builtins(private val interpreter: Interpreter) {

    private fun call(fn: Any?, vararg args: Any?): Any? =
        interpreter.invokeValue(fn, CallArgs(args.toList()))

    private fun predicate(fn: Any?, value: Any?): Boolean =
        call(fn, value) as? Boolean ?: throw InterpreterException("Predicate must return Boolean")

    private fun str(value: Any?) = interpreter.stringify(value)

    private fun int(value: Any?): Int = (value as? Number)?.toInt()
        ?: throw InterpreterException("Expected a number, found ${typeName(value)}")

    private fun double(value: Any?): Double = (value as? Number)?.toDouble()
        ?: throw InterpreterException("Expected a number, found ${typeName(value)}")

    /** Lambda argument: the trailing lambda or the last positional argument. */
    private fun CallArgs.fn(): Any? = trailing ?: positional.lastOrNull()

    private fun pairOf(value: Any?): Pair<Any?, Any?> =
        value as? Pair<Any?, Any?> ?: throw InterpreterException("Expected a Pair, found ${typeName(value)}")

    // ---------- Globals ----------

    fun globalValue(name: String): Any = when (name) {
        "PI" -> PI
        "E" -> E
        "Unit" -> Unit
        else -> Unresolved
    }

    fun global(name: String, args: CallArgs): Any? {
        val a = args.positional
        return when (name) {
            "listOf", "arrayOf", "floatArrayOf", "intArrayOf" -> a
            "mutableListOf", "arrayListOf" -> a.toMutableList()
            "emptyList", "emptyArray" -> emptyList<Any?>()
            "listOfNotNull" -> a.filterNotNull()
            "setOf" -> a.toSet()
            "mutableSetOf" -> a.toMutableSet()
            "emptySet" -> emptySet<Any?>()
            "mapOf" -> a.associate { pairOf(it) }
            "mutableMapOf" -> a.associate { pairOf(it) }.toMutableMap()
            "emptyMap" -> emptyMap<Any?, Any?>()
            "Pair" -> Pair(a[0], a[1])
            "List" -> List(int(a[0])) { call(args.fn(), it) }
            "MutableList" -> MutableList(int(a[0])) { call(args.fn(), it) }
            "println", "print" -> interpreter.log(a.firstOrNull()?.let { str(it) } ?: "")
            "maxOf" -> a.reduce { x, y -> if ((Operators.compare(x, y) as Int) >= 0) x else y }
            "minOf" -> a.reduce { x, y -> if ((Operators.compare(x, y) as Int) <= 0) x else y }
            "abs" -> when (val v = a[0]) {
                is Int -> abs(v); is Long -> abs(v); is Float -> abs(v); else -> abs(double(v))
            }

            "sqrt" -> (a[0] as? Float)?.let { sqrt(it) } ?: sqrt(double(a[0]))
            "sin" -> (a[0] as? Float)?.let { sin(it) } ?: sin(double(a[0]))
            "cos" -> (a[0] as? Float)?.let { cos(it) } ?: cos(double(a[0]))
            "floor" -> floor(double(a[0]))
            "ceil" -> ceil(double(a[0]))
            "require" -> if (a[0] == true) Unit else throw IllegalArgumentException(
                lazyMessage(
                    args,
                    "Failed requirement."
                )
            )

            "check" -> if (a[0] == true) Unit else throw IllegalStateException(
                lazyMessage(
                    args,
                    "Check failed."
                )
            )

            "requireNotNull" -> a[0] ?: throw IllegalArgumentException(
                lazyMessage(
                    args,
                    "Required value was null."
                )
            )

            "checkNotNull" -> a[0] ?: throw IllegalStateException(
                lazyMessage(
                    args,
                    "Required value was null."
                )
            )

            "error" -> throw IllegalStateException(str(a.firstOrNull()))
            "TODO" -> throw NotImplementedError(
                "An operation is not implemented" + (a.firstOrNull()?.let { ": ${str(it)}" }
                    ?: "."))

            "lazy" -> {
                val fn = args.fn()
                lazy { call(fn) }
            }

            else -> if (isExceptionType(name)) exception(name, args) else Unresolved
        }
    }

    /** Message from the lambda of `require(value) { "message" }`, or [default]. */
    private fun lazyMessage(args: CallArgs, default: String): String =
        (args.trailing ?: args.positional.getOrNull(1))?.let { str(call(it)) } ?: default

    /** `IllegalStateException("message")`, `RuntimeException(cause)` or `Exception("message", cause)`. */
    private fun exception(name: String, args: CallArgs): Any {
        val first = args.named["message"] ?: args.positional.getOrNull(0)
        val cause = (args.named["cause"] ?: args.positional.getOrNull(1)
        ?: first.takeIf { it is Throwable }) as? Throwable
        val message = if (first is Throwable) first.toString() else first?.let { str(it) }
        return newException(name, message, cause) ?: Unresolved
    }

    // ---------- Properties ----------

    fun property(receiver: Any?, name: String): Any? = when (receiver) {
        is CharSequence -> when (name) {
            "length" -> receiver.length
            "indices" -> receiver.indices
            "lastIndex" -> receiver.lastIndex
            else -> Unresolved
        }

        is Map<*, *> -> when (name) {
            "size" -> receiver.size
            "keys" -> receiver.keys
            "values" -> receiver.values
            "entries" -> receiver.entries
            else -> Unresolved
        }

        is Collection<*> -> when (name) {
            "size" -> receiver.size
            "indices" -> receiver.indices
            "lastIndex" -> receiver.size - 1
            else -> Unresolved
        }

        is Map.Entry<*, *> -> when (name) {
            "key" -> receiver.key
            "value" -> receiver.value
            else -> Unresolved
        }

        is Pair<*, *> -> when (name) {
            "first" -> receiver.first
            "second" -> receiver.second
            else -> Unresolved
        }

        is IndexedValue<*> -> when (name) {
            "index" -> receiver.index
            "value" -> receiver.value
            else -> Unresolved
        }

        is IntProgression -> when (name) {
            "first" -> receiver.first
            "last" -> receiver.last
            "step" -> receiver.step
            else -> Unresolved
        }

        is Throwable -> when (name) {
            "message" -> receiver.message
            "cause" -> receiver.cause
            else -> Unresolved
        }

        is State<*> -> if (name == "value") receiver.value else Unresolved
        is Lazy<*> -> if (name == "value") receiver.value else Unresolved
        is Char -> if (name == "code") receiver.code else Unresolved
        is Number -> if (name == "absoluteValue") when (receiver) {
            is Int -> abs(receiver)
            is Long -> abs(receiver)
            is Float -> abs(receiver)
            else -> abs(receiver.toDouble())
        } else {
            Unresolved
        }

        else -> Unresolved
    }

    fun setProperty(receiver: Any?, name: String, value: Any?): Boolean {
        if (receiver is MutableState<*> && name == "value") {
            (receiver as MutableState<Any?>).value = value
            return true
        }
        return false
    }

    fun index(receiver: Any?, indices: List<Any?>): Any? {
        val i = indices.singleOrNull()
        return when (receiver) {
            is List<*> -> receiver.getOrElse(int(i)) { throw IndexOutOfBoundsException("Index $i out of bounds for length ${receiver.size}") }
            is Map<*, *> -> receiver[i]
            is CharSequence -> receiver.getOrElse(int(i)) { throw IndexOutOfBoundsException("Index $i out of bounds for length ${receiver.length}") }
            else -> Unresolved
        }
    }

    fun setIndex(receiver: Any?, indices: List<Any?>, value: Any?): Boolean {
        val i = indices.singleOrNull()
        when (receiver) {
            is MutableList<*> -> (receiver as MutableList<Any?>)[int(i)] = value
            is MutableMap<*, *> -> (receiver as MutableMap<Any?, Any?>)[i] = value
            else -> return false
        }
        return true
    }

    // ---------- Member functions ----------

    fun member(receiver: Any?, name: String, args: CallArgs): Any? {
        val a = args.positional
        when (name) {
            "toString" -> if (a.isEmpty()) return str(receiver)
            "hashCode" -> return receiver.hashCode()
            "equals" -> return receiver == a.firstOrNull()
            "to" -> return Pair(receiver, a.firstOrNull())
            "takeIf" -> return if (predicate(args.fn(), receiver)) receiver else null
            "takeUnless" -> return if (predicate(args.fn(), receiver)) null else receiver
            "compareTo" -> return Operators.compare(receiver, a.firstOrNull())
        }
        return when (receiver) {
            is String -> string(receiver, name, args)
            is Number -> number(receiver, name, a)
            is Boolean -> when (name) {
                "not" -> !receiver
                "and" -> receiver and (a[0] as Boolean)
                "or" -> receiver or (a[0] as Boolean)
                "xor" -> receiver xor (a[0] as Boolean)
                else -> Unresolved
            }

            is Char -> when (name) {
                "isDigit" -> receiver.isDigit()
                "isLetter" -> receiver.isLetter()
                "isWhitespace" -> receiver.isWhitespace()
                "isUpperCase" -> receiver.isUpperCase()
                "isLowerCase" -> receiver.isLowerCase()
                "uppercaseChar" -> receiver.uppercaseChar()
                "lowercaseChar" -> receiver.lowercaseChar()
                "uppercase" -> receiver.uppercase()
                "lowercase" -> receiver.lowercase()
                "digitToInt" -> receiver.digitToInt()
                else -> Unresolved
            }

            is IntProgression -> when (name) {
                "step" -> receiver step int(a[0])
                "reversed" -> receiver.reversed()
                else -> iterable(receiver, name, args)
            }

            is Map<*, *> -> map(receiver as Map<Any?, Any?>, name, args)
            is Iterable<*> -> iterable(receiver, name, args)
            is Pair<*, *> -> if (name == "toList") receiver.toList() else Unresolved
            is Throwable -> when (name) {
                "stackTraceToString" -> receiver.stackTraceToString()
                "printStackTrace" -> interpreter.log(receiver.stackTraceToString())
                else -> Unresolved
            }

            else -> Unresolved
        }
    }

    private fun string(s: String, name: String, args: CallArgs): Any? {
        val a = args.positional
        return when (name) {
            "uppercase" -> s.uppercase()
            "lowercase" -> s.lowercase()
            "trim" -> s.trim()
            "isEmpty" -> s.isEmpty()
            "isNotEmpty" -> s.isNotEmpty()
            "isBlank" -> s.isBlank()
            "isNotBlank" -> s.isNotBlank()
            "startsWith" -> s.startsWith(str(a[0]))
            "endsWith" -> s.endsWith(str(a[0]))
            "contains" -> (a[0] as? Char)?.let { it in s } ?: s.contains(str(a[0]))
            "indexOf" -> (a[0] as? Char)?.let { s.indexOf(it) } ?: s.indexOf(str(a[0]))
            "substring" -> if (a.size > 1) s.substring(
                int(a[0]),
                int(a[1])
            ) else s.substring(int(a[0]))

            "split" -> s.split(str(a[0]))
            "replace" -> s.replace(str(a[0]), str(a[1]))
            "repeat" -> s.repeat(int(a[0]))
            "take" -> s.take(int(a[0]))
            "drop" -> s.drop(int(a[0]))
            "takeLast" -> s.takeLast(int(a[0]))
            "dropLast" -> s.dropLast(int(a[0]))
            "reversed" -> s.reversed()
            "first" -> s.first()
            "last" -> s.last()
            "get" -> s[int(a[0])]
            "lines" -> s.lines()
            "padStart" -> s.padStart(int(a[0]), a.getOrNull(1) as? Char ?: ' ')
            "padEnd" -> s.padEnd(int(a[0]), a.getOrNull(1) as? Char ?: ' ')
            "toInt" -> s.toIntOrNull() ?: throw NumberFormatException("For input string: \"$s\"")
            "toIntOrNull" -> s.toIntOrNull()
            "toFloat" -> s.toFloatOrNull()
                ?: throw NumberFormatException("For input string: \"$s\"")

            "toDouble" -> s.toDoubleOrNull()
                ?: throw NumberFormatException("For input string: \"$s\"")

            "toLong" -> s.toLongOrNull() ?: throw NumberFormatException("For input string: \"$s\"")
            "toList" -> s.toList()
            "ifEmpty" -> if (s.isEmpty()) call(args.fn()) else s
            "replaceFirstChar" -> if (s.isEmpty()) s else str(
                call(
                    args.fn(),
                    s[0]
                )
            ) + s.substring(1)

            else -> Unresolved
        }
    }

    private fun number(n: Number, name: String, a: List<Any?>): Any? = when (name) {
        "toInt" -> n.toInt()
        "toLong" -> n.toLong()
        "toFloat" -> n.toFloat()
        "toDouble" -> n.toDouble()
        "toChar" -> n.toInt().toChar()
        "roundToInt" -> if (n is Float) n.roundToInt() else n.toDouble().roundToInt()
        "pow" -> if (n is Float) n.pow(double(a[0]).toFloat()) else n.toDouble().pow(double(a[0]))
        "coerceIn" -> coerce(coerce(n, a[0], atLeast = true), a[1], atLeast = false)
        "coerceAtLeast" -> coerce(n, a[0], atLeast = true)
        "coerceAtMost" -> coerce(n, a[0], atLeast = false)
        "until" -> when {
            n is Int && a[0] is Int -> n until (a[0] as Int)
            else -> n.toLong() until (a[0] as Number).toLong()
        }

        "or", "and", "xor", "shl", "shr" -> bitwise(n, name, a[0])
        "inv" -> if (n is Long) n.inv() else n.toInt().inv()
        "downTo" -> when {
            n is Int && a[0] is Int -> n downTo (a[0] as Int)
            else -> n.toLong() downTo (a[0] as Number).toLong()
        }

        else -> Unresolved
    }

    private fun bitwise(n: Number, name: String, other: Any?): Any {
        if (n is Long || other is Long) {
            val a = n.toLong()
            val b = (other as Number).toLong()
            return when (name) {
                "or" -> a or b
                "and" -> a and b
                "xor" -> a xor b
                "shl" -> a shl b.toInt()
                else -> a shr b.toInt()
            }
        }
        val a = n.toInt()
        val b = int(other)
        return when (name) {
            "or" -> a or b
            "and" -> a and b
            "xor" -> a xor b
            "shl" -> a shl b
            else -> a shr b
        }
    }

    private fun coerce(n: Any?, bound: Any?, atLeast: Boolean): Any? {
        val c = Operators.compare(n, bound) as? Int
            ?: throw InterpreterException("Cannot compare ${typeName(n)} with ${typeName(bound)}")
        return if ((atLeast && c < 0) || (!atLeast && c > 0)) bound else n
    }

    private fun iterable(items: Iterable<Any?>, name: String, args: CallArgs): Any? {
        val a = args.positional
        val fn = args.fn()
        val list = items as? List<Any?> ?: items.toList()
        return when (name) {
            "isEmpty" -> list.isEmpty()
            "isNotEmpty" -> list.isNotEmpty()
            "first" -> if (args.trailing != null) list.first { predicate(fn, it) } else list.first()
            "last" -> if (args.trailing != null) list.last { predicate(fn, it) } else list.last()
            "firstOrNull" -> if (args.trailing != null) list.firstOrNull {
                predicate(
                    fn,
                    it
                )
            } else list.firstOrNull()

            "lastOrNull" -> if (args.trailing != null) list.lastOrNull {
                predicate(
                    fn,
                    it
                )
            } else list.lastOrNull()

            "find" -> list.firstOrNull { predicate(fn, it) }
            "get", "elementAt" -> list[int(a[0])]
            "getOrNull" -> list.getOrNull(int(a[0]))
            "contains" -> a[0] in list
            "indexOf" -> list.indexOf(a[0])
            "map" -> list.map { call(fn, it) }
            "mapIndexed" -> list.mapIndexed { i, v -> call(fn, i, v) }
            "mapNotNull" -> list.mapNotNull { call(fn, it) }
            "flatMap" -> list.flatMap { call(fn, it) as Iterable<Any?> }
            "flatten" -> list.flatMap { it as Iterable<Any?> }
            "filter" -> list.filter { predicate(fn, it) }
            "filterNot" -> list.filterNot { predicate(fn, it) }
            "filterIndexed" -> list.filterIndexed { i, v -> call(fn, i, v) == true }
            "filterNotNull" -> list.filterNotNull()
            "forEach" -> list.forEach { call(fn, it) }
            "forEachIndexed" -> list.forEachIndexed { i, v -> call(fn, i, v) }
            "any" -> if (fn == null) list.isNotEmpty() else list.any { predicate(fn, it) }
            "all" -> list.all { predicate(fn, it) }
            "none" -> if (fn == null) list.isEmpty() else list.none { predicate(fn, it) }
            "count" -> if (fn == null) list.size else list.count { predicate(fn, it) }
            "sumOf" -> list.fold(0 as Any?) { acc, v -> Operators.binary("+", acc, call(fn, v)) }
            "sum" -> list.fold(0 as Any?) { acc, v -> Operators.binary("+", acc, v) }
            "maxOrNull" -> list.reduceOrNull { x, y ->
                if ((Operators.compare(
                        x,
                        y
                    ) as Int) >= 0
                ) x else y
            }

            "minOrNull" -> list.reduceOrNull { x, y ->
                if ((Operators.compare(
                        x,
                        y
                    ) as Int) <= 0
                ) x else y
            }

            "maxByOrNull" -> list.maxByOrNull { call(fn, it) as Comparable<Any> }
            "minByOrNull" -> list.minByOrNull { call(fn, it) as Comparable<Any> }
            "sorted" -> list.sortedWith { x, y -> Operators.compare(x, y) as Int }
            "sortedDescending" -> list.sortedWith { x, y -> Operators.compare(y, x) as Int }
            "sortedBy" -> list.sortedWith { x, y ->
                Operators.compare(
                    call(fn, x),
                    call(fn, y)
                ) as Int
            }

            "sortedByDescending" -> list.sortedWith { x, y ->
                Operators.compare(
                    call(fn, y),
                    call(fn, x)
                ) as Int
            }

            "reversed", "asReversed" -> list.reversed()
            "take" -> list.take(int(a[0]))
            "drop" -> list.drop(int(a[0]))
            "takeLast" -> list.takeLast(int(a[0]))
            "dropLast" -> list.dropLast(int(a[0]))
            "distinct" -> list.distinct()
            "shuffled" -> list.shuffled()
            "random" -> list.random()
            "chunked" -> list.chunked(int(a[0]))
            "zip" -> list.zip(a[0] as Iterable<Any?>)
            "withIndex" -> list.withIndex().toList()
            "subList" -> list.subList(int(a[0]), int(a[1]))
            "groupBy" -> list.groupBy { call(fn, it) }
            "associateWith" -> list.associateWith { call(fn, it) }
            "associateBy" -> list.associateBy { call(fn, it) }
            "joinToString" -> list.joinToString(
                separator = args.named["separator"]?.let { str(it) } ?: a.getOrNull(0)
                    ?.let { str(it) } ?: ", ",
                prefix = args.named["prefix"]?.let { str(it) } ?: "",
                postfix = args.named["postfix"]?.let { str(it) } ?: "",
            ) {
                str(
                    if (args.trailing != null || "transform" in args.named) call(
                        args.trailing ?: args.named["transform"], it
                    ) else it
                )
            }

            "plus" -> list + (a[0] as? Iterable<Any?> ?: listOf(a[0]))
            "minus" -> list - (a[0] as? Iterable<Any?>)?.toSet().orEmpty().ifEmpty { setOf(a[0]) }
            "toList" -> list.toList()
            "toMutableList" -> list.toMutableList()
            "toSet" -> list.toSet()
            "toMutableSet" -> list.toMutableSet()
            "ifEmpty" -> if (list.isEmpty()) call(fn) else items
            "add" -> (items as? MutableCollection<Any?>)?.let {
                if (a.size == 2) (it as MutableList<Any?>).add(
                    int(a[0]),
                    a[1]
                ) else it.add(a[0])
            } ?: Unresolved

            "addAll" -> (items as? MutableCollection<Any?>)?.addAll(a[0] as Iterable<Any?>)
                ?: Unresolved

            "remove" -> (items as? MutableCollection<Any?>)?.remove(a[0]) ?: Unresolved
            "removeAt" -> (items as? MutableList<Any?>)?.removeAt(int(a[0])) ?: Unresolved
            "removeAll" -> (items as? MutableCollection<Any?>)?.removeAll { predicate(fn, it) }
                ?: Unresolved

            "clear" -> (items as? MutableCollection<Any?>)?.clear() ?: Unresolved
            "set" -> (items as? MutableList<Any?>)?.set(int(a[0]), a[1]) ?: Unresolved
            else -> Unresolved
        }
    }

    private fun map(m: Map<Any?, Any?>, name: String, args: CallArgs): Any? {
        val a = args.positional
        val fn = args.fn()
        return when (name) {
            "get" -> m[a[0]]
            "getValue" -> m.getValue(a[0])
            "getOrDefault" -> m[a[0]] ?: a[1]
            "getOrElse" -> m[a[0]] ?: call(fn)
            "containsKey" -> m.containsKey(a[0])
            "containsValue" -> m.containsValue(a[0])
            "isEmpty" -> m.isEmpty()
            "isNotEmpty" -> m.isNotEmpty()
            "toList" -> m.toList()
            "forEach" -> m.forEach { call(fn, it) }
            "map" -> m.map { call(fn, it) }
            "filter" -> m.filter { predicate(fn, it) }
            "mapValues" -> m.mapValues { call(fn, it) }
            "plus" -> m + pairOf(a[0])
            "minus" -> m - a[0]
            "toMutableMap" -> m.toMutableMap()
            "put" -> (m as? MutableMap<Any?, Any?>)?.put(a[0], a[1]) ?: Unresolved
            "remove" -> (m as? MutableMap<Any?, Any?>)?.remove(a[0]) ?: Unresolved
            "clear" -> (m as? MutableMap<Any?, Any?>)?.clear() ?: Unresolved
            "getOrPut" -> (m as? MutableMap<Any?, Any?>)?.getOrPut(a[0]) { call(fn) } ?: Unresolved
            else -> Unresolved
        }
    }
}
