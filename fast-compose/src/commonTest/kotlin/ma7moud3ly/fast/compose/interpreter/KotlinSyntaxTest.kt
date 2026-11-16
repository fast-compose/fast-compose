package ma7moud3ly.fast.compose.interpreter

import ma7moud3ly.fast.compose.parser.Diagnostic
import ma7moud3ly.fast.compose.parser.KotlinParser
import kotlin.test.Test
import kotlin.test.assertEquals

/** Kotlin found in real projects (REAL_PROJECTS.md P65–P83), parsed without errors and run as Kotlin does. */
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

    @Test
    fun anOverloadThatFitsWinsOverTheEnclosingExtension() {
        val code = """
            class Scroller {
                fun go(index: Int) = index * 2
            }

            fun Scroller.make(n: Int): Int = make(n, ::go)

            fun make(n: Int, scroll: (Int) -> Int): Int = scroll(n) + 1

            fun test() = Scroller().make(3)
        """
        assertEquals(7, run(code))
    }

    @Test
    fun numberConstantsNamedComparisonsAndDistinct() {
        val code = """
            fun test() = listOf(
                max(a = 3, b = 7),
                min(a = 2.5f, b = 1.5f),
                Float.NaN.isNaN(),
                Double.POSITIVE_INFINITY.isInfinite(),
                Int.MAX_VALUE,
                listOf(1, 2, 3, 4).distinctBy { it % 2 },
                listOf(1, 2, 3).partition { it > 1 },
            )
        """
        assertEquals(listOf(7, 1.5f, true, true, Int.MAX_VALUE, listOf(1, 2), Pair(listOf(2, 3), listOf(1))), run(code))
    }

    @Test
    fun extensionsOnATypeParameter() {
        val code = """
            class Grid {
                fun total() = describe()
            }

            fun <S : Any> S.describe(): String = "item"

            fun test() = Grid().total() + " " + Grid().describe()
        """
        assertEquals("item item", run(code))
    }

    @Test
    fun labeledThis() {
        val code = """
            class Screen {
                val name = "screen"

                fun title() = with(3) { this@Screen.name }
            }

            fun test() = listOf(with("outer") outer@{ with(5) { this@outer } }, Screen().title())
        """
        assertEquals(listOf("outer", "screen"), run(code))
    }

    @Test
    fun arrayLiteralsInAnnotations() {
        val code = """
            annotation class Tags(val names: Array<String>)

            @Tags(names = ["a", "b"])
            fun test() = 1
        """
        assertEquals(1, run(code))
    }

    @Test
    fun foldAndReduce() {
        val code = """
            fun List<Int>.total() = fold(initial = 0) { sum, n -> sum + n }

            fun test() = listOf(
                listOf(1, 2, 3).fold(10) { sum, n -> sum + n },
                listOf(1, 2, 3).total(),
                listOf(2, 3, 4).reduce { product, n -> product * n },
                listOf(1, 2, 3).runningFold(0) { sum, n -> sum + n },
            )
        """
        assertEquals(listOf(16, 6, 24, listOf(0, 1, 3, 6)), run(code))
    }

    @Test
    fun lambdaParametersOnSeveralLines() {
        val code = """
            fun test(): Int {
                val sum = {
                        (a, b): Pair<Int, Int>,
                        c: Int,
                    ->
                    a + b + c
                }
                return sum(Pair(1, 2), 3)
            }
        """
        assertEquals(6, run(code))
    }

    @Test
    fun enumEntriesInsideTheirOwnMembers() {
        val code = """
            enum class Layout {
                Compact, Expanded;

                val wide: Boolean get() = this == Expanded

                fun count() = values().size + entries.size
            }

            fun test() = listOf(Layout.Expanded.wide, Layout.Compact.wide, Layout.Compact.count())
        """
        assertEquals(listOf(true, false, 4), run(code))
    }

    @Test
    fun receiverLambdasGetTheCallersImplicitReceiver() {
        val code = """
            class Lane {
                val name = "lane"
            }

            fun Lane.tag() = "tag of " + name

            fun row(content: Lane.() -> String): String = with(Lane()) { content() }

            fun toolbar(title: Lane.() -> String) = row { title() }

            fun test() = toolbar { tag() }
        """
        assertEquals("tag of lane", run(code))
    }

    @Test
    fun aClassAndAFunctionWithTheSameName() {
        val code = """
            data class Message(val author: String, val text: String, val time: String = "")

            fun Message(message: Message, isFirst: Boolean, isLast: Boolean) = message.author + isFirst

            fun test() = listOf(Message("ana", "hi", "8:02").text, Message(Message("bo", "yo"), true, false))
        """
        assertEquals(listOf("hi", "botrue"), run(code))
    }

    @Test
    fun maxOfAndMinOfOnCollections() {
        val code = """
            fun test() = listOf(
                listOf(3, 9, 4).maxOf { it * 2 },
                listOf(3, 9, 4).minOf { it },
                setOf(1.5, 0.5).maxOf { it },
                emptyList<Int>().maxOfOrNull { it },
            )
        """
        assertEquals(listOf(18, 3, 1.5, null), run(code))
    }
}
