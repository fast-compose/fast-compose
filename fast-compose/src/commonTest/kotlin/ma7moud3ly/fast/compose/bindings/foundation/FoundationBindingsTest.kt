package ma7moud3ly.fast.compose.bindings.foundation

import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.parser.Diagnostic
import ma7moud3ly.fast.compose.parser.KotlinParser
import kotlin.test.Test
import kotlin.test.assertEquals

class FoundationBindingsTest {

    private val registry = Registry().apply { install(FoundationBindings) }

    private fun run(code: String): Any? {
        val result = KotlinParser.parse(code)
        assertEquals(emptyList<Diagnostic>(), result.diagnostics)
        return Interpreter(result.program, registry).call("test")
    }

    @Test
    fun annotatedStrings() {
        val code = """
            fun test(): String {
                val text = buildAnnotatedString {
                    append("Hi ")
                    withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                        append("there")
                    }
                }
                return text.text
            }
        """.trimIndent()
        assertEquals("Hi there", run(code))
    }

    @Test
    fun combinedTransitions() {
        assertEquals(true, run("fun test() = (fadeIn() + expandIn()) is EnterTransition"))
    }

    @Test
    fun paddingOverloads() {
        assertEquals(true, run("fun test() = Modifier.padding(4.dp).padding(horizontal = 8.dp).padding(1.dp, 2.dp, 3.dp, 4.dp) is Modifier"))
    }

    @Test
    fun graphicsValues() {
        assertEquals(true, run("fun test() = ColorFilter.tint(Color.Red) is ColorFilter && ContentScale.Crop is ContentScale && BlendMode.Multiply is BlendMode"))
    }

    @Test
    fun constraints() {
        assertEquals(50, run("fun test() = Constraints(maxWidth = 100).copy(maxWidth = 50).maxWidth"))
    }
}
