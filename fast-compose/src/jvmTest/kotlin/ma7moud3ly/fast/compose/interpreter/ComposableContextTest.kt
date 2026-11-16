package ma7moud3ly.fast.compose.interpreter

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.test.runTest
import ma7moud3ly.fast.compose.bindings.foundation.FoundationBindings
import ma7moud3ly.fast.compose.parser.KotlinParser
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lambdas of Kotlin's inline functions keep the caller's composable context (REAL_PROJECTS.md P88). */
class ComposableContextTest {

    @Test
    fun inlineBuildersRunComposableCode() = runTest {
        val code = """
            @Composable
            fun Screen() {
                val text = buildAnnotatedString {
                    append(remember { "a" })
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(remember { "b" }) }
                    withLink(LinkAnnotation.Url("https://example.com")) {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold),) { append(remember { "c" }) }
                    }
                }
                val items = List(2) { i -> remember { i * 10 } }
                println(text.text + " " + items)
            }
        """.trimIndent()
        val problems = mutableListOf<RuntimeError>()
        val logs = mutableListOf<String>()
        val program = KotlinParser.parse(code).program
        val registry = Registry().apply { install(FoundationBindings) }
        val interpreter = Interpreter(program, registry, onError = { problems += it }, onLog = { logs += it })
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
        assertEquals(emptyList(), problems)
        assertEquals(listOf("abc [0, 10]"), logs)
    }

    @Test
    fun inlineBuildersStillRunInPlainCode() {
        val code = "fun test() = buildAnnotatedString { append(\"x\") }.text + List(3) { it * 2 }"
        val program = KotlinParser.parse(code).program
        val registry = Registry().apply { install(FoundationBindings) }
        assertEquals("x[0, 2, 4]", Interpreter(program, registry).call("test"))
    }
}
