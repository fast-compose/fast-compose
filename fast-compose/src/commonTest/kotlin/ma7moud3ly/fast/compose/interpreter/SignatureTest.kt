package ma7moud3ly.fast.compose.interpreter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignatureTest {

    private val text = Signature.parse("Text(text, modifier, color, *, fontWeight)")

    @Test
    fun positionalAndNamed() {
        val match = text.match(listOf("Hi", "m"), mapOf("fontWeight" to 700), trailing = null)
        assertEquals(mapOf("text" to "Hi", "modifier" to "m", "fontWeight" to 700), match.values)
        assertEquals(emptyList(), match.problems)
    }

    @Test
    fun trailingLambdaFillsLastParameter() {
        val match = Signature.parse("Box(modifier, content)").match(emptyList(), emptyMap(), trailing = "lambda")
        assertEquals(mapOf("content" to "lambda"), match.values)
    }

    @Test
    fun varargCollectsPositionalArguments() {
        val match = Signature.parse("key(vararg keys, content)").match(listOf(1, 2), emptyMap(), trailing = "lambda")
        assertEquals(mapOf("keys" to listOf(1, 2), "content" to "lambda"), match.values)
    }

    @Test
    fun problems() {
        assertEquals(
            listOf("Unknown parameter 'colr' for Text. Did you mean 'color'?"),
            text.match(listOf("Hi"), mapOf("colr" to 1), trailing = null).problems,
        )
        assertEquals(listOf("Too many arguments for Text"), text.match(listOf(1, 2, 3, 4), emptyMap(), null).problems)
        assertEquals(listOf("Parameter 'text' of Text is passed twice"), text.match(listOf("a"), mapOf("text" to "b"), null).problems)
        assertEquals(listOf("value does not take a trailing lambda"), Signature.parse("value").match(emptyList(), emptyMap(), "lambda").problems)
    }

    @Test
    fun acceptsCallShape() {
        assertTrue(text.accepts(CallShape(listOf(1, 2, 3), emptyMap(), null)))
        assertFalse(text.accepts(CallShape(listOf(1, 2, 3, 4), emptyMap(), null)))
        assertTrue(text.accepts(CallShape(listOf(1), mapOf("fontWeight" to 700), null)))
        assertFalse(text.accepts(CallShape(listOf(1), mapOf("size" to 1), null)))
    }

    @Test
    fun acceptsTypes() {
        val isA = { value: Any?, type: String -> if (type == "Pair") value is Pair<*, *> else value is List<*> }
        val colors = Signature.parse("gradient(colors: List, end)")
        val stops = Signature.parse("gradient(vararg colorStops: Pair, end)")
        assertTrue(colors.accepts(CallShape(listOf(listOf(1, 2)), emptyMap(), null), isA))
        assertFalse(colors.accepts(CallShape(listOf(0f to 1, 1f to 2), emptyMap(), null), isA))
        assertTrue(stops.accepts(CallShape(listOf(0f to 1, 1f to 2), emptyMap(), null), isA))
        assertFalse(stops.accepts(CallShape(listOf(0f to 1, "x"), emptyMap(), null), isA))
    }

    @Test
    fun printsAsWritten() {
        assertEquals("Text(text, modifier, color, *, fontWeight)", text.toString())
        assertEquals("key(vararg keys, content)", Signature.parse("key(vararg keys, content)").toString())
        assertEquals("gradient(vararg colorStops: Pair, end)", Signature.parse("gradient(vararg colorStops: Pair, end)").toString())
    }
}
