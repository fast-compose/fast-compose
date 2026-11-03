package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.unit.LayoutDirection
import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.interpreter.RuntimeError
import ma7moud3ly.fast.compose.interpreter.StandIn
import ma7moud3ly.fast.compose.parser.Diagnostic
import ma7moud3ly.fast.compose.parser.KotlinParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun primitiveStates() {
        val code = """
            fun test(): List<Any> {
                val count = mutableIntStateOf(1)
                count.intValue = count.intValue + 2
                val size = mutableFloatStateOf(1f)
                size.floatValue *= 2f
                return listOf(count.intValue, size.floatValue, mutableLongStateOf(5L).longValue)
            }
        """.trimIndent()
        assertEquals(listOf(3, 2f, 5L), run(code))
    }

    @Test
    fun densityConversions() {
        val code = """
            fun test(): List<Any> = with(Density(2f)) {
                listOf(10.toDp(), 4.dp.toPx(), 3.dp.roundToPx(), 1.5f.em)
            }
        """.trimIndent()
        assertEquals(listOf(5.dp, 8f, 6, 1.5f.em), run(code))
    }

    @Test
    fun platformApis() {
        val code = """
            fun test(): List<Any> {
                val words = buildString {
                    append("a")
                    append(1)
                }
                return listOf(
                    Integer.MAX_VALUE / 2,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    Regex("a+").matches("aaa"),
                    words,
                    Random(1).nextInt(10) in 0..9,
                    Color.Red.toArgb(),
                    Locale("ar").language,
                )
            }
        """.trimIndent()
        assertEquals(listOf(Int.MAX_VALUE / 2, false, true, "a1", true, Color.Red.toArgb(), "ar"), run(code))
    }

    @Test
    fun textAndFocusExtras() {
        val problems = mutableListOf<RuntimeError>()
        val code = """
            fun test(): List<Any> {
                val requester = FocusRequester()
                val m = Modifier.focusRequester(requester).pointerHoverIcon(PointerIcon.Hand).clipToBounds()
                val value = TextFieldValue("hello", selection = TextRange(1, 3))
                return listOf(
                    FontWeight.ExtraLight, TextDirection.Rtl, value.selection.length, DpOffset(2.dp, 4.dp).y,
                    lerp(0.dp, 10.dp, 0.5f), m != Modifier, EaseInOutCubic,
                )
            }
        """.trimIndent()
        val program = KotlinParser.parse(code).program
        val result = Interpreter(program, registry, onError = { problems += it }).call("test")
        assertEquals(listOf(FontWeight.ExtraLight, TextDirection.Rtl, 2, 4.dp, 5.dp, true, EaseInOutCubic), result)
        assertEquals(emptyList(), problems)
    }

    @Test
    fun intrinsicSizes() {
        val problems = mutableListOf<RuntimeError>()
        val code = """
            fun test(): List<Any> {
                val m = Modifier.height(IntrinsicSize.Min).width(IntrinsicSize.Max)
                    .requiredHeight(IntrinsicSize.Max).requiredWidth(IntrinsicSize.Min)
                return listOf(IntrinsicSize.Min, IntrinsicSize.Max, m != Modifier)
            }
        """.trimIndent()
        val program = KotlinParser.parse(code).program
        val result = Interpreter(program, registry, onError = { problems += it }).call("test")
        assertEquals(listOf(IntrinsicSize.Min, IntrinsicSize.Max, true), result)
        assertEquals(emptyList(), problems)
    }

    @Test
    fun borderOverloads() {
        val problems = mutableListOf<RuntimeError>()
        val code = """
            fun test(): Boolean {
                val stroke = BorderStroke(1.dp, Color.Red)
                return (
                    Modifier.border(stroke, RoundedCornerShape(4.dp)) is Modifier &&
                        Modifier.border(2.dp, Color.Blue) is Modifier && stroke.width == 1.dp
                )
            }
        """.trimIndent()
        val program = KotlinParser.parse(code).program
        assertEquals(true, Interpreter(program, registry, onError = { problems += it }).call("test"))
        assertEquals(emptyList(), problems)
    }

    /** Runs `test()` and checks that no problem was reported while it ran. */
    private fun runClean(code: String): Any? {
        val problems = mutableListOf<RuntimeError>()
        val program = KotlinParser.parse(code.trimIndent()).program
        val result = Interpreter(program, registry, onError = { problems += it }).call("test")
        assertEquals(emptyList(), problems)
        return result
    }

    @Test
    fun stateFlows() {
        val code = """
            fun test(): Int {
                val state = MutableStateFlow(1)
                val readOnly = state.asStateFlow()
                state.update { it + 1 }
                return readOnly.value
            }
        """
        assertEquals(2, runClean(code))
    }

    @Test
    fun javaSystemAndMath() {
        val code = """
            fun test(): String {
                val version = System.getProperty("java.version")
                return listOf(
                    version != null,
                    System.getProperty("fast.compose.missing", "default"),
                    Math.max(2, 5),
                    Math.abs(-3),
                    Math.round(2.6),
                    System.currentTimeMillis() > 0
                ).joinToString("|")
            }
        """
        assertEquals("true|default|5|3|3|true", runClean(code))
    }

    @Test
    fun layoutDirectionAndProvidedStandIns() {
        val provided = runClean("fun test() = LocalLayoutDirection provides LayoutDirection.Rtl")
        assertEquals(LayoutDirection.Rtl, (provided as ProvidedValue<*>).value)

        // A stand-in is not provided, so Compose never receives a value of the wrong type.
        val program = KotlinParser.parse("fun test() = LocalLayoutDirection provides RightToLeft").program
        assertTrue(Interpreter(program, registry).call("test") is StandIn)
    }

    @Test
    fun textUnitArithmetic() {
        val code = """
            fun test(): Float {
                val sizes = (14.sp * 1.5f).value + (2 * 3.sp).value + (12.sp / 2).value
                return if (14.sp > 12.sp) sizes else 0f
            }
        """
        assertEquals(33f, runClean(code))
    }

    @Test
    fun durations() {
        assertEquals(2530L, runClean("fun test() = (530.milliseconds + 2.seconds).inWholeMilliseconds"))
    }

    @Test
    fun flowsAndCoroutines() = runTest {
        val code = """
            suspend fun test(): List<Any?> {
                val numbers = flow {
                    emit(1)
                    emit(2)
                    emit(3)
                }
                val doubled = numbers.map { it * 2 }.filter { it > 2 }.toList()
                val first = flowOf("a", "b").first()
                val inContext = withContext(Dispatchers.IO) { "ctx" }
                return listOf(doubled, first, inContext)
            }
        """.trimIndent()
        assertEquals(listOf(listOf(4, 6), "a", "ctx"), Interpreter(KotlinParser.parse(code).program, registry).callSuspend("test"))
    }

    @Test
    fun jobsScopesAndContinuations() = runTest {
        val problems = mutableListOf<RuntimeError>()
        val code = """
            suspend fun test(): List<Any?> {
                val answer = coroutineScope { async { 40 + 2 }.await() }
                val resumed = suspendCancellableCoroutine { continuation -> continuation.resume("done") }
                val job = SupervisorJob()
                job.cancel()
                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val calm = withContext(NonCancellable) { "nc" }
                return listOf(answer, resumed, job.isActive, scope is CoroutineScope, calm)
            }
        """.trimIndent()
        val program = KotlinParser.parse(code).program
        val result = Interpreter(program, registry, onError = { problems += it }).callSuspend("test")
        assertEquals(emptyList(), problems)
        assertEquals(listOf(42, "done", false, true, "nc"), result)
    }

    @Test
    fun clickAndSizeModifiersWithOptions() {
        val code = """
            fun test() = listOf(
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = {},
                    indication = null,
                    interactionSource = MutableInteractionSource(),
                ) != Modifier,
                Modifier.animateContentSize(spring()) != Modifier,
                Modifier.animateContentSize(finishedListener = { initial, target -> }) != Modifier,
            )
        """
        assertEquals(listOf(true, true, true), runClean(code))
    }

    @Test
    fun scopeModifiersOutsideTheirScopeAreLeftOut() {
        val problems = mutableListOf<RuntimeError>()
        val code = "fun test() = Modifier.weight(1f).align(Alignment.Center) == Modifier"
        val program = KotlinParser.parse(code).program
        assertEquals(true, Interpreter(program, registry, onError = { problems += it }).call("test"))
        assertEquals(2, problems.size, problems.toString())
        assertTrue(problems.all { it.isWarning }, problems.toString())
    }

    @Test
    fun stringFormat() {
        val code = """
            class HomeUiState(val buildType: String? = null) {
                val appVersion = String.format("Version : %s %s", buildType, 3)
            }

            fun test() = listOf(HomeUiState("debug").appVersion, "%.1f%%".format(12.34), String.valueOf(7))
        """.trimIndent()
        assertEquals(listOf("Version : debug 3", "12.3%", "7"), run(code))
    }

    @Test
    fun systemBarCodeIsSilent() {
        val problems = mutableListOf<RuntimeError>()
        val code = """
            fun test(): String {
                val controller = WindowCompat.getInsetsController(null, null)
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = false
                WindowCompat.setDecorFitsSystemWindows(null, false)
                return "ok"
            }
        """.trimIndent()
        val program = KotlinParser.parse(code).program
        assertEquals("ok", Interpreter(program, registry, onError = { problems += it }).call("test"))
        assertEquals(emptyList(), problems)
    }

    @Test
    fun windowSizeClassesAndLifecycle() {
        val code = """
            fun test(): List<Any> = listOf(
                WindowWidthSizeClass.COMPACT == WindowWidthSizeClass.Compact,
                Lifecycle.State.RESUMED.isAtLeast(Lifecycle.State.STARTED),
                WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
            )
        """.trimIndent()
        assertEquals(listOf(true, true, 600), run(code))
        val size = PreviewWindowSizeClass(700f, 950f)
        assertEquals(listOf(SizeClass.Medium, SizeClass.Expanded), listOf(size.width, size.height))
    }

    @Test
    fun delayTakesDurationsAndStandIns() = runTest {
        val code = """
            suspend fun test(): Int {
                delay(5.milliseconds)
                delay(DefaultPause)
                return 1
            }
        """.trimIndent()
        assertEquals(1, Interpreter(KotlinParser.parse(code).program, registry).callSuspend("test"))
    }

    @Test
    fun stateCollections() {
        val code = """
            fun test(): Int {
                val list = listOf(1, 2).toMutableStateList()
                list.add(3)
                val empty = emptyList<Int>().toMutableStateList()
                val map = listOf("a" to 1).toMutableStateMap()
                return list.size * 100 + empty.size * 10 + map.size
            }
        """
        assertEquals(301, runClean(code))
    }

    @Test
    fun spacedByWithAlignment() {
        val code = "fun test() = Arrangement.spacedBy(4.dp, Alignment.CenterVertically) != Arrangement.spacedBy(4.dp)"
        assertEquals(true, runClean(code))
    }

    @Test
    fun strokeWithPathEffect() {
        val code = "fun test() = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 2f), 0f))"
        val stroke = runClean(code)
        assertTrue((stroke as Stroke).pathEffect != null)
    }

    @Test
    fun fontFamilyWithoutFontsIsTheDefault() {
        assertEquals(FontFamily.Default, runClean("fun test() = FontFamily()"))
    }

    @Test
    fun density() {
        val code = """
            fun test(): Float {
                val density = Density(2f, fontScale = 1.5f)
                val scaled = Density(density.density, fontScale = density.fontScale * 2)
                return scaled.density + scaled.fontScale
            }
        """.trimIndent()
        assertEquals(5f, run(code))
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
