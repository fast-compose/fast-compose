package ma7moud3ly.fast.compose.interpreter

import ma7moud3ly.fast.compose.parser.KotlinParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalResolverTest {

    /** A value library code returns, with a property and a method. */
    private class FakeInstant(val epochMillis: Long) {
        fun plus(millis: Long) = FakeInstant(epochMillis + millis)
    }

    private fun symbol(read: (Any?) -> Any? = { null }, call: (Any?, List<Any?>) -> Any? = { _, _ -> null }) =
        object : ExternalSymbol {
            override fun read(receiver: Any?): Any? = read(receiver)
            override fun call(receiver: Any?, positional: List<Any?>, named: Map<String, Any?>): Any? = call(receiver, positional)
        }

    /** Library `io.lib`: `now()`, `Version`, `FakeInstant.toLabel()`, `twice(block)` and `fails()`. */
    @Suppress("UNCHECKED_CAST")
    private val resolver = object : ExternalResolver {
        override fun find(packageName: String, name: String): ExternalSymbol? = if (packageName != "io.lib") null else when (name) {
            "now" -> symbol(call = { _, _ -> FakeInstant(1000) })
            "Version" -> symbol(read = { "1.2" })
            "toLabel" -> symbol(call = { receiver, _ -> "t=" + (receiver as FakeInstant).epochMillis })
            "twice" -> symbol(call = { _, args -> (args.single() as (Any?) -> Any?).let { listOf(it(1), it(2)) } })
            "fails" -> symbol(call = { _, _ -> error("boom") })
            else -> null
        }

        override fun member(receiver: Any, name: String): ExternalSymbol? = if (receiver !is FakeInstant) null else when (name) {
            "epochMillis" -> symbol(read = { (it as FakeInstant).epochMillis })
            "plus" -> symbol(call = { r, args -> (r as FakeInstant).plus((args.single() as Number).toLong()) })
            else -> null
        }
    }

    @Test
    fun libraryNamesRunThroughTheResolver() {
        val code = """
            import io.lib.now
            import io.lib.Version
            import io.lib.toLabel
            import io.lib.twice
            import io.lib.fails
            import io.lib.missing

            fun test(): List<Any?> {
                val t = now()
                return listOf(Version, t.epochMillis, t.plus(5).epochMillis, t.toLabel(), twice { it * 10 }, "" + fails(), "" + missing())
            }
        """.trimIndent()
        val problems = mutableListOf<RuntimeError>()
        val program = KotlinParser.parse(code).program
        val result = Interpreter(program, Registry(), onError = { problems += it }, externals = resolver).call("test")
        assertEquals(listOf("1.2", 1000L, 1005L, "t=1000", listOf(10, 20), "", ""), result)
        // `fails()` threw in library code (an error); `missing` isn't in the library, so it is a stand-in (a warning).
        assertEquals(2, problems.size, problems.toString())
        assertTrue("boom" in problems.first { !it.isWarning }.message, problems.toString())
    }

    @Test
    fun withoutAResolverLibraryNamesAreStandIns() {
        val code = """
            import io.lib.now

            fun test() = "" + now().epochMillis
        """.trimIndent()
        val program = KotlinParser.parse(code).program
        assertEquals("", Interpreter(program, Registry()).call("test"))
    }
}
