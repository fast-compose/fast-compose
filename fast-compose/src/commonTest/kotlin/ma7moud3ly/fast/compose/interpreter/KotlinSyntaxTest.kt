package ma7moud3ly.fast.compose.interpreter

import ma7moud3ly.fast.compose.parser.Diagnostic
import ma7moud3ly.fast.compose.parser.KotlinParser
import kotlin.test.Test
import kotlin.test.assertEquals

/** Kotlin syntax found in real projects (REAL_PROJECTS.md P70–P81), parsed without errors and run as Kotlin does. */
class KotlinSyntaxTest {

    private fun run(code: String): Any? {
        val result = KotlinParser.parse(code.trimIndent())
        assertEquals(emptyList<Diagnostic>(), result.diagnostics)
        return Interpreter(result.program, Registry()).call("test")
    }

    @Test
    fun fractionsWithoutALeadingDigit() {
        val code = """
            fun test() = listOf(.8f, .5, (.25f), 2 * .5, .5e1, listOf(1.5).size)
        """
        assertEquals(listOf(0.8f, 0.5, 0.25f, 1.0, 5.0, 1), run(code))
    }

    @Test
    fun trailingCommaAfterWhenConditions() {
        val code = """
            fun kind(n: Int) = when (n) {
                1,
                2,
                -> "small"
                else -> "big"
            }

            fun test() = listOf(kind(1), kind(2), kind(3))
        """
        assertEquals(listOf("small", "small", "big"), run(code))
    }

    @Test
    fun typeAliases() {
        val code = """
            private typealias Measure = (String) -> Int
            typealias Names<T> = List<T>

            fun test(): Int {
                val measure: Measure = { it.length }
                return measure("abc")
            }
        """
        assertEquals(3, run(code))
    }

    @Test
    fun labeledLambdas() {
        val code = """
            fun test(): List<Int> {
                val kept = mutableListOf<Int>()
                listOf(1, 2, 3).forEach each@{
                    if (it == 2) return@each
                    kept.add(it)
                }
                val half = half@{ n: Int ->
                    if (n % 2 != 0) return@half -1
                    n / 2
                }
                return kept + listOf(half(4), half(5))
            }
        """
        assertEquals(listOf(1, 3, 2, -1), run(code))
    }

    @Test
    fun suspendFunctionTypesWithAReceiver() {
        val code = """
            fun track(report: suspend CoroutineScope.(state: Int) -> Unit, vararg keys: Any?) = keys.size

            fun test(): Int {
                val keys = arrayOf("a", "b")
                return track({ _ -> }, *keys)
            }
        """
        assertEquals(2, run(code))
    }

    @Test
    fun secondaryAndModifiedConstructors() {
        val code = """
            class Point internal constructor(val x: Int, val y: Int) {
                var label = ""

                constructor(both: Int) : this(both, both) {
                    label = "square"
                }

                constructor(text: String, scale: Int = 2) : this(text.length * scale) {
                    label = label + "!"
                }
            }

            class Counter {
                var count = 0

                constructor(start: Int) {
                    count = start
                }
            }

            fun test() = listOf(Point(1, 2).y, Point(3).x, Point(3).label, Point("ab").x, Point("ab").label, Counter(5).count)
        """
        assertEquals(listOf(2, 3, "square", 4, "square!", 5), run(code))
    }
}
