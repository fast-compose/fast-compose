package ma7moud3ly.fast.compose.interpreter.jvm

import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.interpreter.RuntimeError
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.test.runTest
import ma7moud3ly.fast.compose.bindings.foundation.FoundationBindings
import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.Money
import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.Recorded
import ma7moud3ly.fast.compose.parser.KotlinParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class JarResolverTest {

    /** The test classes, standing in for a library jar: they hold the fixtures and their `LibraryKt` file class. */
    private val classes = File(Money::class.java.protectionDomain.codeSource.location.toURI())

    @Test
    fun runsJdkAndKotlinLibraryCode() {
        val code = """
            import java.time.LocalDate
            import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.Money
            import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.Formats
            import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.DEFAULT_CURRENCY
            import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.doubled
            import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.total
            import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.mapEach

            fun test(): List<Any?> {
                val date = LocalDate.of(2025, 1, 15).plusDays(7)
                val money = Money(5, currency = "EGP")
                val more = money.plus(Money.of(3))
                return listOf(
                    date.toString(), date.dayOfWeek.toString(),
                    more.amount, more.currency,
                    Formats.label(more), Formats.label(more, prefix = "£"),
                    DEFAULT_CURRENCY, money.doubled().amount,
                    total(1, 2, 3), mapEach(listOf(1, 2)) { it * 10 },
                )
            }
        """.trimIndent()
        val problems = mutableListOf<RuntimeError>()
        JarResolver(listOf(classes)).use { resolver ->
            val program = KotlinParser.parse(code).program
            val result = Interpreter(program, Registry(), onError = { problems += it }, externals = resolver).call("test")
            assertEquals(
                listOf("2025-01-22", "WEDNESDAY", 8L, "EGP", "$8", "£8", "EUR", 10L, 6, listOf(10, 20)),
                result,
            )
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun runsLibraryComposables() = runTest {
        val code = """
            import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.Greeting
            import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.Card
            import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.Lane
            import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.Swatch
            import ma7moud3ly.fast.compose.interpreter.jvm.fixtures.Counter

            @Composable
            fun Screen() {
                Card("box") {
                    Greeting("Ana")
                    Greeting(name = "Bo", punctuation = "?")
                }
                Lane { mark("x") }
                Swatch(Color.Red)
                Counter { n -> println("count " + n) }
            }
        """.trimIndent()
        Recorded.lines.clear()
        val problems = mutableListOf<RuntimeError>()
        val logs = mutableListOf<String>()
        val registry = Registry().apply { install(FoundationBindings) }
        JarResolver(listOf(classes)).use { resolver ->
            val program = KotlinParser.parse(code).program
            val interpreter = Interpreter(program, registry, onError = { problems += it }, onLog = { logs += it }, externals = resolver)
            val recomposer = Recomposer(coroutineContext)
            val composition = Composition(object : AbstractApplier<Unit>(Unit) {
                override fun insertTopDown(index: Int, instance: Unit) = Unit
                override fun insertBottomUp(index: Int, instance: Unit) = Unit
                override fun remove(index: Int, count: Int) = Unit
                override fun move(from: Int, to: Int, count: Int) = Unit
                override fun onClear() = Unit
            }, recomposer)
            try {
                composition.setContent { interpreter.Render(program.functions.single()) }
            } finally {
                composition.dispose()
                recomposer.cancel()
            }
        }
        assertEquals(emptyList(), problems)
        assertEquals(listOf("[box]", "Hello Ana!", "Hello Bo?", "lane x", "swatch true 4.0.dp"), Recorded.lines)
        assertEquals(listOf("count 3"), logs)
    }

    @Test
    fun failuresInLibraryCodeAreReported() {
        val code = """
            import java.time.LocalDate

            fun test() = "" + LocalDate.of(2025, 13, 1)
        """.trimIndent()
        val problems = mutableListOf<RuntimeError>()
        JarResolver(emptyList()).use { resolver ->
            val program = KotlinParser.parse(code).program
            assertEquals("", Interpreter(program, Registry(), onError = { problems += it }, externals = resolver).call("test"))
        }
        assertEquals(1, problems.size, problems.toString())
    }
}
