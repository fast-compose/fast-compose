package ma7moud3ly.fast.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma7moud3ly.fast.compose.bindings.foundation.LocalBasicTextColor
import ma7moud3ly.fast.compose.bindings.foundation.LocalPreviewWindowSize
import ma7moud3ly.fast.compose.bindings.foundation.LocalPreviewDarkTheme
import ma7moud3ly.fast.compose.bindings.resources.LocalResourceEnvironment
import ma7moud3ly.fast.compose.bindings.resources.LocalResourceProvider
import ma7moud3ly.fast.compose.bindings.resources.RESOURCES_HOST_VALUE
import ma7moud3ly.fast.compose.interpreter.LocalExternalResolver
import ma7moud3ly.fast.compose.bindings.resources.ResourceEnvironment
import ma7moud3ly.fast.compose.bindings.resources.ResourceProvider
import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.parser.ast.Program

/**
 * Lightweight preview of every `@Preview` in [code], using only foundation APIs.
 * Supports layouts, `Text`, state, modifiers, drawing and animation; Material components, icons, resources and network
 * images show a hint instead. Use [FastComposePreview] for the full API. [frameSize] and [zoom] work as in
 * [FastComposePreview].
 */
@Composable
public fun BasicComposePreview(
    code: ComposeCode,
    modifier: Modifier = Modifier,
    registry: Registry = rememberBasicRegistry(),
    frameSize: DpSize? = null,
    zoom: Float = 1f,
) {
    PreviewList(
        code = code,
        registry = registry,
        resources = LocalResourceProvider.current,
        modifier = modifier,
        label = { BasicText(it, style = TextStyle(color = Color.Gray, fontSize = 12.sp)) },
        frame = { config, size, content -> BasicFrame(config, size, content) },
        frameSize = frameSize,
        zoom = zoom,
    )
}

@Composable
private fun BasicFrame(config: PreviewConfig, modifier: Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val background = when {
        config.showBackground -> config.backgroundColor
            ?: if (config.darkTheme) DARK_BACKGROUND else Color.White

        config.darkTheme -> DARK_BACKGROUND
        else -> Color.Transparent
    }
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, config.fontScale),
        LocalBasicTextColor provides if (config.darkTheme) Color.White else Color.Black,
    ) {
        Box(modifier.border(Dp.Hairline, FRAME_BORDER).background(background)) { content() }
    }
}

private val DARK_BACKGROUND = Color(0xFF121212)
internal val FRAME_BORDER = Color(0x33000000)

/**
 * Lists every preview of [code] ([previews]: `@Preview`s, multi-preview annotations and `@PreviewParameter` values) in
 * a scrollable column, sharing one interpreter. Each preview gets a [label] and a [frame]; the frame receives a size
 * modifier that applies `widthDp`/`heightDp` or wraps content up to the visible area, or up to [frameSize] when set.
 * With a [frameSize], the list scrolls both ways and is centered. [zoom] scales the frames through their density, so
 * previews lay out at their own size and stay sharp; labels keep the host's size.
 */
@Composable
internal fun PreviewList(
    code: ComposeCode,
    registry: Registry,
    resources: ResourceProvider,
    modifier: Modifier,
    label: @Composable (String) -> Unit,
    frame: @Composable (config: PreviewConfig, size: Modifier, content: @Composable () -> Unit) -> Unit,
    frameSize: DpSize? = null,
    zoom: Float = 1f,
) {
    val program = code.program
    if (program == null) {
        Box(modifier.padding(16.dp)) { label(if (code.isLoading) "Loading…" else "Fix the errors to see the preview") }
        return
    }
    val interpreter = rememberInterpreter(code, program, registry, resources)
    val previews = remember(interpreter) { interpreter.previews(program) }
    if (previews.isEmpty()) {
        Box(modifier.padding(16.dp)) { label("No preview yet. Add @Preview to a @Composable function.") }
        return
    }
    val scale = zoom.coerceAtLeast(MIN_ZOOM)
    val baseDensity = LocalDensity.current
    BoxWithConstraints(modifier) {
        val maxFrameWidth = frameSize?.width ?: ((maxWidth - 32.dp) / scale).coerceAtLeast(MIN_FRAME)
        val maxFrameHeight = frameSize?.height ?: ((maxHeight - 64.dp) / scale).coerceAtLeast(MIN_FRAME)
        val panelWidth = maxWidth
        val scrolling = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        Box(if (frameSize != null) scrolling.horizontalScroll(rememberScrollState()) else scrolling) {
            Column(
                modifier = Modifier.then(if (frameSize != null) Modifier.widthIn(min = panelWidth) else Modifier).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = if (frameSize != null) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                previews.forEachIndexed { index, preview ->
                    val config = preview.config
                    key(preview.function.name, index) {
                        Column {
                            Box(Modifier.padding(bottom = 6.dp)) { label(config.label()) }
                            val size = Modifier
                                .then(if (config.widthDp != null) Modifier.width(config.widthDp.dp) else Modifier.widthIn(max = maxFrameWidth))
                                .then(if (config.heightDp != null) Modifier.height(config.heightDp.dp) else Modifier.heightIn(max = maxFrameHeight))
                            // Frames read the zoomed density, so their dp sizes and content scale together.
                            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density * scale, baseDensity.fontScale)) {
                                frame(config, size) {
                                    CompositionLocalProvider(
                                        LocalResourceProvider provides resources,
                                        LocalResourceEnvironment provides ResourceEnvironment(config.locale, config.darkTheme),
                                        LocalPreviewDarkTheme provides config.darkTheme,
                                        LocalInspectionMode provides true,
                                        LocalPreviewWindowSize provides DpSize(
                                            config.widthDp?.dp ?: maxFrameWidth,
                                            config.heightDp?.dp ?: maxFrameHeight,
                                        ),
                                    ) {
                                        interpreter.Render(preview.function, preview.arguments)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MIN_ZOOM = 0.1f

private val MIN_FRAME = 120.dp

private fun PreviewConfig.label(): String {
    val details = listOfNotNull(
        if (widthDp != null || heightDp != null) "${widthDp ?: "wrap"}×${heightDp ?: "wrap"} dp" else null,
        if (darkTheme) "dark" else null,
        if (fontScale != 1f) "font ×$fontScale" else null,
        locale,
    )
    val title = listOfNotNull(group, name).joinToString(" / ")
    return if (details.isEmpty()) title else "$title  ·  ${details.joinToString("  ·  ")}"
}

/**
 * Remembers an interpreter for [program] and the [ComposeCode.dependencies] of [code] that sends runtime errors and
 * `println` output to the logs of [code]. Bindings outside composition read [resources] through it; when they found a
 * resource still loading, e.g. a font of a top-level `FontFamily`, a new interpreter runs the code once it is loaded.
 * Library names without a binding run through [LocalExternalResolver] when the host provides one.
 */
@Composable
internal fun rememberInterpreter(code: ComposeCode, program: Program, registry: Registry, resources: ResourceProvider): Interpreter {
    val dependencies = code.dependencies
    val externals = LocalExternalResolver.current
    var generation by remember(program, dependencies, registry, externals) { mutableIntStateOf(0) }
    val interpreter = remember(program, dependencies, registry, externals, generation) {
        Interpreter(
            program,
            registry,
            onError = { code.logError(it) },
            onLog = { code.log(it) },
            dependencies = dependencies,
            externals = externals,
        )
    }
    interpreter.hostValues[RESOURCES_HOST_VALUE] = resources
    val checks = interpreter.loadingChecks
    if (checks.isNotEmpty() && checks.all { it() }) {
        SideEffect { generation++ }
    }
    return interpreter
}
