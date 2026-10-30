package ma7moud3ly.fast.compose.interpreter

import ma7moud3ly.fast.compose.parser.KotlinParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Timings of parsing and of plain interpreted code, the source of the numbers in `docs/PERFORMANCE.md`.
 * Each workload runs [WARMUP] times, then [RUNS] times; the median run and the cost per operation are printed.
 * Nothing is asserted on time. Run with `./gradlew :interpreter:jvmTest --tests "*InterpreterBenchmarkTest*" -i`.
 */
class InterpreterBenchmarkTest {

    private val registry = Registry().apply {
        function("square(x)") { a -> a.int("x", 0).let { it * it } }
        member<Point>("shift(dx)") { p, a -> Point(p.x + a.int("dx", 0)) }
        properties<Point>("x" to { it.x })
        function("Point(x)") { a -> Point(a.int("x", 0)) }
    }

    private class Point(val x: Int)

    /** Median time of `test()` in [code], after checking that it returns [expected]. */
    private fun median(code: String, expected: Any?): Duration {
        val program = KotlinParser.parse(code.trimIndent()).program
        val interpreter = Interpreter(program, registry, maxRunTime = Duration.INFINITE)
        repeat(WARMUP) { interpreter.call("test") }
        var result: Any? = null
        val times = List(RUNS) { measureTime { result = interpreter.call("test") } }
        assertEquals(expected, result)
        return times.sorted()[RUNS / 2]
    }

    private fun report(name: String, median: Duration, operations: Int, unit: String) {
        println("benchmark $name: median ${ms(median)} ms, ${perOperation(median, operations)} us per $unit ($operations ${unit}s)")
    }

    @Test
    fun environment() {
        println(
            "benchmark environment: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}, " +
                "${System.getProperty("os.name")} ${System.getProperty("os.arch")}, " +
                "${Runtime.getRuntime().availableProcessors()} processors, max heap ${Runtime.getRuntime().maxMemory() / MB} MB; " +
                "$WARMUP warm-up runs, median of $RUNS runs, $PAIRS pairs for binding calls",
        )
    }

    @Test
    fun parsing() {
        val source = SAMPLE.trimIndent()
        val lines = source.lines().size
        repeat(WARMUP * PARSES) { KotlinParser.parse(source) }
        val times = List(RUNS) { measureTime { repeat(PARSES) { assertEquals(false, KotlinParser.parse(source).hasErrors) } } }
        val median = times.sorted()[RUNS / 2] / PARSES
        println("benchmark parsing: median ${ms(median)} ms per parse of $lines lines, ${perOperation(median, lines)} us per line")
    }

    @Test
    fun recursion() {
        val code = """
            fun fib(n: Int): Int = if (n < 2) n else fib(n - 1) + fib(n - 2)
            fun test() = fib(20)
        """
        report("recursion (fib 20)", median(code, 6765), FIB_20_CALLS, "call")
    }

    @Test
    fun loopsAndLocals() {
        val code = """
            fun test(): Int {
                var sum = 0
                for (i in 0 until 50000) {
                    val doubled = i * 2
                    if (doubled % 3 == 0) sum += 1
                }
                return sum
            }
        """
        report("loops and locals", median(code, 16667), 50_000, "iteration")
    }

    /**
     * Cost of one binding call. A loop that calls the binding and the same loop without the call are measured in
     * alternation, [PAIRS] times, after warming up both; the median of the paired differences, divided by
     * [CALLS], is the cost of one call. Measuring in pairs cancels drift in JIT and machine speed between runs.
     */
    private fun bindingCost(name: String, baseline: String, baselineResult: Any?, calls: String, callsResult: Any?) {
        val withoutCall = prepare(baseline)
        val withCall = prepare(calls)
        repeat(WARMUP) {
            withoutCall.call("test")
            withCall.call("test")
        }
        val withoutTimes = mutableListOf<Duration>()
        val withTimes = mutableListOf<Duration>()
        val differences = mutableListOf<Duration>()
        repeat(PAIRS) {
            val a = timed(withoutCall, baselineResult)
            val b = timed(withCall, callsResult)
            withoutTimes += a
            withTimes += b
            differences += b - a
        }
        report("$name baseline", withoutTimes.median(), CALLS, "iteration")
        report("$name loop", withTimes.median(), CALLS, "iteration")
        println("benchmark $name call: ${perOperation(differences.median(), CALLS)} us per call (median of $PAIRS paired differences)")
    }

    /** Benchmarks run their workloads back to back without a pause, so the interpreter's time limit is turned off. */
    private fun prepare(code: String) =
        Interpreter(KotlinParser.parse(code.trimIndent()).program, registry, maxRunTime = Duration.INFINITE)

    private fun timed(interpreter: Interpreter, expected: Any?): Duration {
        var result: Any? = null
        val time = measureTime { result = interpreter.call("test") }
        assertEquals(expected, result)
        return time
    }

    private fun List<Duration>.median() = sorted()[size / 2]

    @Test
    fun functionBindingCalls() = bindingCost(
        name = "function binding",
        baseline = """
            fun test(): Int {
                var total = 0
                repeat($CALLS) { i -> total += 1 }
                return total
            }
        """,
        baselineResult = CALLS,
        calls = """
            fun test(): Int {
                var total = 0
                repeat($CALLS) { i -> total += square(1) }
                return total
            }
        """,
        callsResult = CALLS,
    )

    @Test
    fun memberBindingCalls() = bindingCost(
        name = "member binding",
        baseline = """
            fun test(): Int {
                var p = Point(0)
                repeat($CALLS) { i -> p = p }
                return p.x
            }
        """,
        baselineResult = 0,
        calls = """
            fun test(): Int {
                var p = Point(0)
                repeat($CALLS) { i -> p = p.shift(1) }
                return p.x
            }
        """,
        callsResult = CALLS,
    )

    @Test
    fun classesAndCollections() {
        val code = """
            data class Item(val id: Int, val name: String)

            fun test(): Int {
                val items = (1..5000).map { Item(it, "item " + it) }
                return items.filter { it.id % 2 == 0 }.map { it.name.length }.sum()
            }
        """
        report("classes and collections", median(code, 21948), 5_000, "item")
    }

    private companion object {
        const val WARMUP = 10
        const val RUNS = 7
        const val PARSES = 20
        const val PAIRS = 15
        const val CALLS = 100_000
        const val MB = 1024 * 1024

        /** Calls made by `fib(20)`: 2 × fib(21) − 1. */
        const val FIB_20_CALLS = 21_891

        fun ms(duration: Duration) = "%.2f".format(duration.inWholeNanoseconds / 1_000_000.0)

        fun perOperation(duration: Duration, operations: Int) = "%.3f".format(duration.inWholeNanoseconds / 1_000.0 / operations)

        /** A typical preview file: a data class, state, layout, Material components, a list and two previews. */
        val SAMPLE = """
            data class Message(val author: String, val body: String, val unread: Boolean)

            val messages = listOf(
                Message("Lexi", "Hey, take a look at Jetpack Compose, it's great!", true),
                Message("Sam", "Fast Compose renders it without a compiler.", false),
                Message("Ana", "Let's try it in the playground.", true),
            )

            @Composable
            fun MessageCard(msg: Message, modifier: Modifier = Modifier) {
                var expanded by remember { mutableStateOf(false) }
                Card(modifier = modifier.fillMaxWidth().padding(8.dp).clickable { expanded = !expanded }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(msg.author.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(msg.author, style = MaterialTheme.typography.titleMedium)
                            Text(
                                msg.body,
                                maxLines = if (expanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (msg.unread) {
                            Badge { Text("new") }
                        }
                    }
                }
            }

            @Composable
            fun Inbox(items: List<Message>) {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(items) { message ->
                        MessageCard(message)
                    }
                }
            }

            @Preview(name = "Inbox")
            @Composable
            fun PreviewInbox() {
                MaterialTheme {
                    Inbox(messages)
                }
            }

            @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
            @Composable
            fun PreviewDark() {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    MessageCard(messages.first())
                }
            }
        """
    }
}
