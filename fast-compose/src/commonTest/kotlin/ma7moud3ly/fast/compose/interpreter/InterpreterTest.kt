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
import kotlin.time.TimeSource

class InterpreterTest {

    private fun interpreter(code: String, registry: Registry = Registry()): Interpreter {
        val result = KotlinParser.parse(code)
        assertEquals(emptyList<Diagnostic>(), result.diagnostics)
        return Interpreter(result.program, registry)
    }

    private fun run(code: String, vararg args: Any?): Any? = interpreter(code).call("test", *args)

    @Test
    fun eachEntryGetsItsOwnTimeLimit() {
        val code = """
            fun work(): Int {
                var n = 0
                for (i in 0 until 3000) n++
                return n
            }
        """.trimIndent()
        val interpreter = Interpreter(KotlinParser.parse(code).program, Registry(), maxRunTime = 100.milliseconds)
        // Many short calls, like a draw callback on every frame, run longer than the limit in total but never stop.
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < 400.milliseconds) assertEquals(3000, interpreter.call("work"))
    }

    @Test
    fun missingOperatorsOnLibraryValuesRunAsStandIns() {
        val (result, problems) = runWithProblems("fun test() = \"x\" + (Pair(1, 2) * 3)")
        assertEquals("x", result)
        assertEquals(1, problems.size, problems.toString())
    }

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
    fun accessorsWithAnnotationsAndModifiers() {
        val code = """
            class Store {
                var count = 0
                    private set
                fun inc() {
                    count++
                }
                val doubled: Int
                    @Suppress("unused") get() = count * 2
            }

            fun test(): Int {
                val s = Store()
                s.inc()
                return s.doubled
            }
        """.trimIndent()
        assertEquals(2, run(code))
    }

    @Test
    fun whenGuards() {
        val code = """
            fun label(n: Int) = when (n) {
                0 -> "zero"
                in 1..9 if n % 2 == 0 -> "small even"
                in 1..9 -> "small odd"
                else if n < 0 -> "negative"
                else -> "big"
            }

            fun test() = label(0) + "," + label(4) + "," + label(3) + "," + label(-5) + "," + label(50)
        """.trimIndent()
        assertEquals("zero,small even,small odd,negative,big", run(code))
    }

    @Test
    fun whenSubjectName() {
        val code = """
            fun describe(x: Int) = when (val half = x / 2) {
                0 -> "none"
                in 1..3 if half != 2 -> "few ${'$'}half"
                in 1..3 -> "two"
                else -> "many ${'$'}half"
            }

            fun test() = describe(1) + "|" + describe(2) + "|" + describe(5) + "|" + describe(40)
        """.trimIndent()
        assertEquals("none|few 1|two|many 20", run(code))
    }

    @Test
    fun standInObjectsKeepNamedArguments() {
        val (result, problems) = runWithProblems(
            """
            import io.lib.EditorTheme

            fun test(): List<Any?> {
                val theme = EditorTheme(background = 1, dark = true)
                val light = theme.copy(dark = false)
                return listOf(theme.background, theme.dark, light.dark, light.background, theme is EditorTheme)
            }
            """,
        )
        assertEquals(listOf(1, true, false, 1, true), result)
        assertEquals(1, problems.size, problems.toString())
    }

    @Test
    fun suspendCallsInSingleLambdas() = runTest {
        val code = """
            suspend fun label(): String {
                delay(1)
                return "none"
            }

            suspend fun test(): List<Any?> = listOf(
                "".ifEmpty { label() },
                "x".ifEmpty { label() },
                " ".ifBlank { label() },
                5.takeIf { label() == "none" },
                listOf(1).getOrElse(3) { label().length },
            )
        """.trimIndent()
        assertEquals(listOf("none", "x", "none", 5, 4), interpreter(code, delayRegistry).callSuspend("test"))
    }

    @Test
    fun suspendCallsInsideCollectionLambdas() = runTest {
        val code = """
            suspend fun twice(n: Int): Int {
                delay(1)
                return n * 2
            }

            suspend fun test(): List<Any> = listOf(
                listOf(1, 2, 3).map { twice(it) },
                listOf(1, 2, 3, 4).filter { twice(it) > 4 },
                listOf(3, 1, 2).sortedBy { twice(it) },
                listOf(1, 2).mapIndexed { i, v -> twice(i) + v },
            )
        """.trimIndent()
        assertEquals(
            listOf(listOf(2, 4, 6), listOf(3, 4), listOf(1, 2, 3), listOf(1, 4)),
            interpreter(code, delayRegistry).callSuspend("test"),
        )
    }

    @Test
    fun whenGuardsAndSubjectNameInSuspendCode() = runTest {
        val code = """
            suspend fun test(): String {
                delay(1)
                return when (val n = 7) {
                    in 0..5 -> "low"
                    in 6..9 if n % 2 == 1 -> "odd ${'$'}n"
                    else -> "other"
                }
            }
        """.trimIndent()
        assertEquals("odd 7", interpreter(code, delayRegistry).callSuspend("test"))
    }

    private object Palette

    @Test
    fun importedMembersOfBoundObjects() {
        val registry = Registry().apply {
            value("Palette", Palette)
            constants<Palette>("Red" to "red")
        }
        val code = """
            import com.example.Palette.Red

            fun test() = Red
        """.trimIndent()
        assertEquals("red", interpreter(code, registry).call("test"))
    }

    /** Runs `test()` in [code] and returns its result with the problems reported while it ran. */
    private fun runWithProblems(code: String): Pair<Any?, List<RuntimeError>> {
        val problems = mutableListOf<RuntimeError>()
        val program = KotlinParser.parse(code.trimIndent()).program
        val result = Interpreter(program, Registry(), onError = { problems += it }).call("test")
        return result to problems
    }

    @Test
    fun importedLibraryApisRunAsStandIns() {
        val (result, problems) = runWithProblems(
            """
            import io.lib.Theme
            import io.lib.rememberThing

            fun test(): String {
                val t = Theme(background = 1)
                val label = "bg " + t.background + "!"
                val mode = if (t.dark) "dark" else "light"
                var count = 0
                for (x in t.items) count++
                return label + "|" + mode + "|" + count + "|" + (t.size > 2) + "|" + rememberThing().value
            }
            """,
        )
        // Named arguments of an unbound class are kept: `background` reads back as given.
        assertEquals("bg 1!|light|0|false|", result)
        assertEquals(2, problems.size, problems.toString())
        assertTrue(problems.all { it.isWarning && "stand-in" in it.message }, problems.toString())
    }

    @Test
    fun implicitJavaLangClassesRunAsStandIns() {
        val (result, problems) = runWithProblems("fun test() = \"\" + Thread.currentThread().name + \"|done\"")
        assertEquals("|done", result)
        assertEquals(1, problems.size, problems.toString())
    }

    @Test
    fun unknownNamesRunAsStandInsButTyposFail() {
        val (result, problems) = runWithProblems("fun test() = \"t=\" + getTimeMillis()")
        assertEquals("t=", result)
        assertTrue(problems.single().isWarning, problems.toString())

        val code = """
            fun label(text: String) = text
            fun test() = lable("x")
        """.trimIndent()
        val error = assertFailsWith<InterpreterException> { run(code) }
        assertEquals("Unresolved reference 'lable'. Did you mean 'label'?", error.message)
    }

    @Test
    fun memberReferencesOnTypes() {
        val code = """
            data class Item(val title: String, val rank: Int) {
                fun label() = title + rank
            }

            fun test(): List<Any> {
                val items = listOf(Item("b", 2), Item("a", 1))
                return listOf(
                    "per kWh".replaceFirstChar(Char::uppercase),
                    items.map(Item::title),
                    items.sortedBy(Item::rank).map(Item::label),
                    listOf(" x ", "").map(String::trim).filter(String::isNotEmpty),
                )
            }
        """.trimIndent()
        assertEquals(listOf("Per kWh", listOf("b", "a"), listOf("a1", "b2"), listOf("x")), run(code))
    }

    @Test
    fun sortedMapsAndTypedDestructuring() {
        val code = """
            fun test(): List<Any> {
                val grouped = listOf("kia", "bmw", "audi", "byd").groupBy { it.first() }.toSortedMap()
                val lines = mutableListOf<String>()
                grouped.forEach { (letter: Char, names: List<String>) -> lines += "" + letter + names.size }
                return listOf(lines, sortedSetOf(3, 1, 2).toList(), listOf("b", "a").toSortedSet().toList())
            }
        """.trimIndent()
        assertEquals(listOf(listOf("a1", "b2", "k1"), listOf(1, 2, 3), listOf("a", "b")), run(code))
    }

    @Test
    fun extensionsOnNullableReceivers() {
        val code = """
            fun String?.orDash(): String = this ?: "-"

            fun test(): List<String> {
                val missing: String? = null
                return listOf(missing.orDash(), "a".orDash())
            }
        """.trimIndent()
        val program = KotlinParser.parse(code).program
        assertEquals("String?", program.functions.first().receiver?.text)
        assertEquals(listOf("-", "a"), run(code))
    }

    @Test
    fun variablesThatCantBeInvokedDontHideFunctions() {
        val code = """
            class Scope {
                fun items(count: Int) = "scope " + count
            }

            fun test(): List<String> {
                val items = listOf(1, 2)
                val twice = { n: Int -> "twice " + n * 2 }
                return listOf(with(Scope()) { items(items.size) }, twice(3))
            }
        """.trimIndent()
        assertEquals(listOf("scope 2", "twice 6"), run(code))
    }

    @Test
    fun primitiveArraysAndMath() {
        val code = """
            fun test(): List<Any> {
                val counts = IntArray(3)
                counts[1] = 5
                val squares = IntArray(3) { it * it }
                return listOf(counts, squares, tan(0.0), round(2.6), longArrayOf(1L, 2L))
            }
        """.trimIndent()
        assertEquals(listOf(listOf(0, 5, 0), listOf(0, 1, 4), 0.0, 3.0, listOf(1L, 2L)), run(code))
    }

    @Test
    fun negatedStandInsAreTrue() {
        val (result, _) = runWithProblems(
            """
            import io.lib.Theme

            fun test(): String {
                val t = Theme()
                val a = if (t.dark) "dark" else if (!t.dark) "light" else "none"
                val b = if (t.dark.not()) "on" else "off"
                return a + "|" + b
            }
            """,
        )
        assertEquals("light|on", result)
    }

    @Test
    fun standInDelegatesAreStandIns() {
        val (result, problems) = runWithProblems(
            """
            import io.lib.rememberThing

            fun test(): String {
                val dragged by rememberThing().collectIsDraggedAsState()
                var count by rememberThing()
                count = 3
                return "" + dragged + "|" + (if (dragged) "yes" else "no")
            }
            """,
        )
        assertEquals("|no", result)
        assertTrue(problems.all { it.isWarning }, problems.toString())
    }

    @Test
    fun missingMembersRunAsStandIns() {
        val (result, problems) = runWithProblems(
            """
            data class Box(val size: Int)

            fun test(): String {
                val box = Box(3)
                val pair = Pair(1, 2)
                pair.extra = 3
                return "" + box.size + "|" + box.color + "|" + box.resize(2)
            }
            """,
        )
        assertEquals("3||", result)
        assertEquals(3, problems.size, problems.toString())
    }

    @Test
    fun objectPropertiesInitializeOnFirstRead() {
        val code = """
            object Themes {
                val broken = tset()
                val ok = "fine"
                val alias = broken
            }

            fun test() = Themes.ok
            fun alias() = Themes.alias
        """.trimIndent()
        val interpreter = interpreter(code)
        assertEquals("fine", interpreter.call("test"))
        val error = assertFailsWith<InterpreterException> { interpreter.call("alias") }
        assertEquals("Unresolved reference 'tset'. Did you mean 'test'?", error.message)
    }

    @Test
    fun functionsWithoutABodyDoNothing() {
        val (result, problems) = runWithProblems(
            """
            expect fun log(message: String)

            expect object Log {
                fun i(tag: String)
            }

            fun test(): String {
                log("a")
                log("b")
                Log.i("t")
                return "done"
            }
            """,
        )
        assertEquals("done", result)
        assertEquals(2, problems.size, problems.toString())

        val code = "expect fun platform(): String\nfun test() = platform()"
        val error = assertFailsWith<InterpreterException> { run(code) }
        assertTrue("no body" in error.message!!, error.message)
    }

    @Test
    fun nullableReceiverExtensions() {
        val code = """
            fun name(): String? = null
            fun items(): List<Int>? = null

            fun test() = (
                name().orEmpty() + "|" + name().isNullOrEmpty() + "|" + "a".orEmpty() + "|" +
                    listOf(1).orEmpty().size + "|" + " ".isNullOrBlank() + "|" + items().isNullOrEmpty() + "|" +
                    "x".isNullOrEmpty()
            )
        """.trimIndent()
        assertEquals("|true|a|1|true|true|false", run(code))
    }

    @Test
    fun propertiesWithoutAnInitializer() {
        val code = """
            class Manager {
                private lateinit var session: String
                val count: Int

                init {
                    count = 2
                }

                fun open(session: String) {
                    this.session = session
                }

                fun current() = session
            }

            lateinit var global: String

            fun test(): String {
                val manager = Manager()
                manager.open("s1")
                global = "g"
                return manager.current() + "|" + manager.count + "|" + global
            }

            fun early() = Manager().current()
        """.trimIndent()
        val interpreter = interpreter(code)
        assertEquals("s1|2|g", interpreter.call("test"))
        val error = assertFailsWith<InterpreterException> { interpreter.call("early") }
        assertEquals("lateinit property 'session' has not been initialized", error.message)
    }

    @Test
    fun stringHelpers() {
        val code = """
            fun test(): String {
                val path = "/lib/main.py"
                return listOf(
                    path.substringAfterLast('/'),
                    path.substringBeforeLast('/', ""),
                    "noslash".substringBeforeLast('/', ""),
                    path.substringAfter("/"),
                    path.substringBefore(".py"),
                    "main.py".removeSuffix(".py"),
                    "#tag".removePrefix("#"),
                    "(x)".removeSurrounding("(", ")"),
                    "  a  ".trimStart() + "|",
                    "|" + "  a  ".trimEnd(),
                    "" + path.lastIndexOf('/')
                ).joinToString(",")
            }
        """.trimIndent()
        assertEquals("main.py,/lib,,lib/main.py,/lib/main,main,tag,x,a  |,|  a,4", run(code))
    }

    @Test
    fun builders() {
        val code = """
            fun test(): Int {
                val list = buildList {
                    add(1)
                    add(2)
                }
                val map = buildMap { put("a", 1) }
                val set = buildSet {
                    add(1)
                    add(1)
                }
                return list.size * 100 + map.size * 10 + set.size
            }
        """.trimIndent()
        assertEquals(211, run(code))
    }

    @Test
    fun companionMembersInsideTheirClass() {
        val code = """
            data class MicroFile(val name: String = "", private val type: Int = FILE) {
                val isFile: Boolean get() = type == FILE
                fun kind() = if (isFile) label(FILE) else "dir"

                companion object {
                    const val DIRECTORY = 0x4000
                    const val FILE = 0x8000
                    fun label(type: Int) = "file " + type
                }
            }

            enum class Vendor(val id: Int, val title: String) {
                A(1, "a"), B(2, "b");

                companion object {
                    fun of(id: Int?): Vendor? = entries.firstOrNull { it.id == id }
                }
            }

            fun test() = (
                "" + MicroFile("x").isFile + "|" + MicroFile("x").kind() + "|" +
                    MicroFile("d", MicroFile.DIRECTORY).isFile + "|" + Vendor.of(2)?.title
            )
        """.trimIndent()
        assertEquals("true|file 32768|false|b", run(code))
    }

    @Test
    fun extensionPropertiesThroughTheImplicitReceiver() {
        val code = """
            data class Device(val board: String, val vendorId: Int)

            val Device.vendor: String? get() = if (vendorId == 1) "acme" else null

            val Device.label: String get() = listOfNotNull(vendor?.uppercase(), board).joinToString(" ")

            fun test() = Device("pico", 1).label + "|" + Device("esp", 2).label
        """.trimIndent()
        assertEquals("ACME pico|esp", run(code))
    }

    @Test
    fun objectExpressions() {
        val code = """
            interface Greeter {
                fun greet(name: String): String
            }

            fun make(prefix: String): Greeter {
                var count = 0
                return object : Greeter {
                    override fun greet(name: String): String {
                        count++
                        return prefix + " " + name + " " + count
                    }
                }
            }

            fun test(): String {
                val greeter = make("hi")
                greeter.greet("a")
                return greeter.greet("b") + "|" + (greeter is Greeter)
            }
        """.trimIndent()
        assertEquals("hi b 2|true", run(code))
    }

    @Test
    fun failingDefaultsUseEmptyValues() {
        // Typos of `test` and `next` fail; unknown names would run as stand-ins instead.
        val (result, problems) = runWithProblems(
            """
            data class Bookmark(val id: Int, val time: Long = tset(), val note: String = nxet())

            fun next(x: Int = tset()) = x + 1

            fun test() = Bookmark(1).toString() + "|" + next()
            """,
        )
        assertEquals("Bookmark(id=1, time=0, note=)|1", result)
        assertEquals(3, problems.size, problems.toString())
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
    fun positionalArgumentsAfterNamedOnes() {
        val registry = Registry().apply {
            function("trio(first, second, third)") { a -> "${a["first"]}${a["second"]}${a["third"]}" }
        }
        val code = """
            fun mine(a: Int, b: Int, c: Int) = "" + a + b + c
            fun test() = trio(first = 1, second = 2, 3) + "|" + mine(a = 4, b = 5, 6)
        """.trimIndent()
        val problems = mutableListOf<RuntimeError>()
        val result = Interpreter(KotlinParser.parse(code).program, registry, onError = { problems += it }).call("test")
        assertEquals("123|456", result)
        assertEquals(emptyList(), problems)
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
        assertFailsWith<InterpreterException> { run("fun test() = try { tset() } catch (e: Exception) { 0 }") }
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
