package ma7moud3ly.fast.compose.interpreter

import androidx.compose.runtime.mutableStateOf
import ma7moud3ly.fast.compose.parser.Diagnostic
import ma7moud3ly.fast.compose.parser.KotlinParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class InterpreterTest {

    private fun interpreter(code: String, registry: Registry = Registry()): Interpreter {
        val result = KotlinParser.parse(code)
        assertEquals(emptyList<Diagnostic>(), result.diagnostics)
        return Interpreter(result.program, registry)
    }

    private fun run(code: String, vararg args: Any?): Any? = interpreter(code).call("test", *args)

    @Test
    fun arithmetic() {
        assertEquals(7, run("fun test() = 1 + 2 * 3"))
        assertEquals(3, run("fun test() = 7 / 2"))
        assertEquals(3.5, run("fun test() = 7 / 2.0"))
        assertEquals(2, run("fun test() = 10 % 4"))
        assertEquals(3f, run("fun test() = 1.5f * 2"))
        assertEquals(-4, run("fun test() = -(2 + 2)"))
    }

    @Test
    fun stringTemplates() {
        assertEquals("Hi Lexi, 4", run("fun test(name: String) = \"Hi \$name, \${name.length}\"", "Lexi"))
    }

    @Test
    fun defaultAndNamedArguments() {
        val code = """
            fun greet(name: String, greeting: String = "Hello") = "${'$'}greeting ${'$'}name"
            fun test() = greet(greeting = "Hi", name = "A") + "|" + greet("B")
        """.trimIndent()
        assertEquals("Hi A|Hello B", run(code))
    }

    @Test
    fun recursion() {
        assertEquals(55, run("fun test(n: Int): Int = if (n < 2) n else test(n - 1) + test(n - 2)", 10))
    }

    @Test
    fun longRunningCodeIsStopped() {
        val code = """
            fun test(): Int {
                var n = 0
                for (i in 0 until 100000) {
                    for (j in 0 until 100000) n++
                }
                return n
            }
        """.trimIndent()
        val error = assertFailsWith<InterpreterException> { run(code) }
        assertTrue("without a pause" in error.message.orEmpty(), error.message)
    }

    @Test
    fun loopsWithBreakAndContinue() {
        val code = """
            fun test(): Int {
                var sum = 0
                for (i in 1..10) {
                    if (i % 2 == 0) continue
                    if (i > 7) break
                    sum += i
                }
                var n = 0
                while (n < 3) n++
                return sum + n
            }
        """.trimIndent()
        assertEquals(19, run(code))
    }

    @Test
    fun whenExpression() {
        val code = """
            fun test(x: Int) = when (x) {
                0 -> "zero"
                in 1..9 -> "small"
                else -> "big"
            }
        """.trimIndent()
        val interpreter = interpreter(code)
        assertEquals("zero", interpreter.call("test", 0))
        assertEquals("small", interpreter.call("test", 5))
        assertEquals("big", interpreter.call("test", 50))
    }

    @Test
    fun dataClass() {
        val code = """
            data class Message(val author: String, val body: String)

            fun test(): List<Any?> {
                val a = Message("Lexi", "Hi")
                val b = a.copy(body = "Hey")
                return listOf(a == Message("Lexi", "Hi"), b.body, a.toString())
            }
        """.trimIndent()
        assertEquals(listOf(true, "Hey", "Message(author=Lexi, body=Hi)"), run(code))
    }

    @Test
    fun classWithStateAndGetter() {
        val code = """
            class Counter(start: Int) {
                var count = start
                fun inc() {
                    count++
                }
                val doubled get() = count * 2
            }

            fun test(): Int {
                val c = Counter(5)
                c.inc()
                c.inc()
                return c.doubled
            }
        """.trimIndent()
        assertEquals(14, run(code))
    }

    @Test
    fun enumClass() {
        val code = """
            enum class Screen(val title: String) { Home("Home"), Settings("Settings") }

            fun test() = Screen.Settings.title + Screen.entries.size + Screen.Home.ordinal
        """.trimIndent()
        assertEquals("Settings20", run(code))
    }

    @Test
    fun lambdasAndCollections() {
        val code = """
            fun test(): String {
                val items = listOf(1, 2, 3, 4)
                val evens = items.filter { it % 2 == 0 }.map { it * 10 }
                var total = 0
                items.forEach { total += it }
                return evens.joinToString(",") + ":" + total
            }
        """.trimIndent()
        assertEquals("20,40:10", run(code))
    }

    @Test
    fun labeledReturnFromLambda() {
        val code = """
            fun test(): Int {
                var count = 0
                listOf(1, 2, 3, 4).forEach {
                    if (it > 2) return@forEach
                    count++
                }
                return count
            }
        """.trimIndent()
        assertEquals(2, run(code))
    }

    @Test
    fun extensionFunction() {
        assertEquals(49, run("fun Int.squared() = this * this\nfun test() = 7.squared()"))
    }

    @Test
    fun nullSafety() {
        val interpreter = interpreter("fun test(s: String?) = s?.length ?: -1")
        assertEquals(-1, interpreter.call("test", null))
        assertEquals(3, interpreter.call("test", "abc"))
    }

    @Test
    fun objectsAndTopLevelProperties() {
        val code = """
            object Config {
                val name = "App"
            }
            val version = 3

            fun test() = "${'$'}{Config.name} v${'$'}version"
        """.trimIndent()
        assertEquals("App v3", run(code))
    }

    @Test
    fun stateDelegate() {
        val registry = Registry().apply { function("mutableStateOf(value)") { a -> mutableStateOf(a["value"]) } }
        val code = """
            fun test(): Int {
                var count by mutableStateOf(0)
                count += 5
                return count
            }
        """.trimIndent()
        assertEquals(5, interpreter(code, registry).call("test"))
    }

    @Test
    fun unresolvedReferenceSuggestsName() {
        val registry = Registry().apply { value("Modifier", 1) }
        val error = assertFailsWith<InterpreterException> { interpreter("fun test() = Modefier", registry).call("test") }
        assertTrue("Did you mean 'Modifier'?" in error.message!!, error.message)
    }

    @Test
    fun composableCallFromPlainCodeFails() {
        val error = assertFailsWith<InterpreterException> { run("@Composable fun A() {}\nfun test() = A()") }
        assertTrue("@Composable" in error.message!!)
    }

    @Test
    fun qualifiedNames() {
        val registry = Registry().apply { value("Answer", 42) }
        assertEquals(42, interpreter("fun test() = com.example.Answer", registry).call("test"))
        assertEquals(3, run("fun test() = kotlin.math.maxOf(1, 3)"))
    }

    @Test
    fun registryLayers() {
        val base = Registry().apply {
            value("A", 1)
            value("B", 2)
            hint("Card", "'Card' needs the full API")
        }
        val child = Registry(base).apply { value("B", 3) }
        assertEquals(1, child.findValue("A"))
        assertEquals(3, child.findValue("B"))
        assertEquals(2, base.findValue("B"))
        val error = assertFailsWith<InterpreterException> { interpreter("fun test() = Card", child).call("test") }
        assertEquals("'Card' needs the full API", error.message)
    }

    private val delayRegistry = Registry().apply {
        suspendFunction("delay(timeMillis)") { a ->
            delay(a.long("timeMillis", 0L).milliseconds)
            Unit
        }
    }

    @Test
    fun suspendFunctions() = runTest {
        val code = """
            suspend fun load(): Int {
                delay(10)
                return 42
            }

            suspend fun test(): Int {
                var total = 0
                repeat(3) {
                    delay(5)
                    total += load()
                }
                return total
            }
        """.trimIndent()
        assertEquals(126, interpreter(code, delayRegistry).callSuspend("test"))
    }

    @Test
    fun suspendCallFromPlainCodeFails() {
        val error = assertFailsWith<InterpreterException> {
            interpreter("suspend fun load() = 1\nfun test() = load()", delayRegistry).call("test")
        }
        assertTrue("Suspend function" in error.message!!, error.message)
    }

    private val paddingRegistry = Registry().apply {
        function("pad(all)") { a -> "all=${a["all"]}" }
        function("pad(horizontal, vertical)") { a -> "h=${a["horizontal"]} v=${a["vertical"]}" }
    }

    @Test
    fun overloadsChosenByCallShape() {
        assertEquals("all=1", interpreter("fun test() = pad(1)", paddingRegistry).call("test"))
        assertEquals("h=1 v=2", interpreter("fun test() = pad(1, 2)", paddingRegistry).call("test"))
        assertEquals("h=null v=3", interpreter("fun test() = pad(vertical = 3)", paddingRegistry).call("test"))
    }

    @Test
    fun unknownParameterIsReported() {
        val errors = mutableListOf<String>()
        val result = KotlinParser.parse("fun test() = pad(al = 1)")
        Interpreter(result.program, paddingRegistry, onError = { errors += it.toString() }).call("test")
        assertTrue(errors.any { "Line 1: Unknown parameter 'al' for pad. Did you mean 'all'?" in it }, errors.toString())
    }

    @Test
    fun errorsReportTheirLine() {
        val errors = mutableListOf<RuntimeError>()
        val code = "fun test() {\n    val a = 1\n    pad(\n        al = a,\n    )\n}"
        Interpreter(KotlinParser.parse(code).program, paddingRegistry, onError = { errors += it }).call("test")
        assertEquals(3, errors.single().line)
        assertEquals("Line 3: Unknown parameter 'al' for pad. Did you mean 'all'?", errors.single().toString())
    }

    @Test
    fun overloadsChosenByArgumentType() {
        val registry = Registry().apply {
            function("gradient(colors: List)") { a -> "colors=${a["colors"]}" }
            function("gradient(vararg colorStops: Pair)") { a -> "stops=${a.varargs("colorStops").size}" }
        }
        assertEquals("colors=[1, 2]", interpreter("fun test() = gradient(listOf(1, 2))", registry).call("test"))
        assertEquals("stops=2", interpreter("fun test() = gradient(0f to 1, 1f to 2)", registry).call("test"))
    }

    @Test
    fun tryCatchFinally() {
        val code = """
            fun parse(text: String): Int = try {
                text.toInt()
            } catch (e: NumberFormatException) {
                -1
            }

            fun test(): String {
                var log = ""
                try {
                    error("boom")
                } catch (e: IllegalArgumentException) {
                    log += "wrong"
                } catch (e: IllegalStateException) {
                    log += e.message
                } finally {
                    log += "!"
                }
                return listOf(parse("12"), parse("x"), log).joinToString(" ")
            }
        """.trimIndent()
        assertEquals("12 -1 boom!", interpreter(code).call("test"))
    }

    @Test
    fun throwUserException() {
        val code = """
            class NotFound(val message: String) : Exception(message)

            fun find(id: Int): String = if (id == 1) "one" else throw NotFound("No item " + id)

            fun test(): String = try {
                find(2)
            } catch (e: RuntimeException) {
                "runtime"
            } catch (e: Exception) {
                e.message
            }
        """.trimIndent()
        assertEquals("No item 2", interpreter(code).call("test"))
    }

    @Test
    fun uncaughtExceptionIsReported() {
        val error = assertFailsWith<IllegalArgumentException> { run("fun test() { throw IllegalArgumentException(\"bad\") }") }
        assertEquals("bad", error.message)
        assertEquals("NotFound: gone", describe(assertFailsWith<ThrownValue> {
            run("class NotFound(val message: String)\nfun test() { throw NotFound(\"gone\") }")
        }))
    }

    @Test
    fun interpreterErrorsAreNotCaught() {
        assertFailsWith<InterpreterException> { run("fun test() = try { missing() } catch (e: Exception) { 0 }") }
    }

    @Test
    fun destructuring() {
        val code = """
            data class Point(val x: Int, val y: Int)

            fun test(): String {
                val (a, b) = 1 to 2
                val (x, _) = Point(3, 4)
                var total = a + b + x
                for ((key, value) in mapOf("k" to 10)) total += value
                for ((index, item) in listOf(100).withIndex()) total += index + item
                listOf(Point(1000, 0)).forEach { (px, py) -> total += px + py }
                val (first, second) = listOf("p", "q")
                return first + second + total
            }
        """.trimIndent()
        assertEquals("pq1116", interpreter(code).call("test"))
    }

    @Test
    fun valCannotBeReassigned() {
        assertFailsWith<InterpreterException> { run("fun test() {\n    val x = 1\n    x = 2\n}") }
    }
}
