package ma7moud3ly.fast.compose.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma7moud3ly.fast.compose.bindings.foundation.LocalBasicTextColor
import ma7moud3ly.fast.compose.bindings.foundation.LocalPreviewDarkTheme
import ma7moud3ly.fast.compose.bindings.resources.LocalResourceEnvironment
import ma7moud3ly.fast.compose.bindings.resources.LocalResourceProvider
import ma7moud3ly.fast.compose.bindings.resources.ResourceEnvironment
import ma7moud3ly.fast.compose.bindings.resources.ResourceProvider
import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.parser.ast.Program

/**
 * Lightweight preview of every `@Preview` in [code], using only foundation APIs.
 * Supports layouts, `Text`, state, modifiers, drawing and animation; Material components, icons, resources and network
 * images show a hint instead. Use [FastComposePreview] for the full API.
 */
@Composable
public fun BasicComposePreview(
    code: ComposeCode,
    modifier: Modifier = Modifier,
    registry: Registry = rememberBasicRegistry(),
) {
    PreviewList(
        code = code,
        registry = registry,
        resources = LocalResourceProvider.current,
        modifier = modifier,
        label = { BasicText(it, style = TextStyle(color = Color.Gray, fontSize = 12.sp)) },
        frame = { config, size, content -> BasicFrame(config, size, content) },
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
 * Lists every `@Preview` of [code] in a scrollable column. Each preview gets a [label] and a [frame];
 * the frame receives a size modifier that applies `widthDp`/`heightDp` or wraps content up to the visible area.
 */
@Composable
internal fun PreviewList(
    code: ComposeCode,
    registry: Registry,
    resources: ResourceProvider,
    modifier: Modifier,
    label: @Composable (String) -> Unit,
    frame: @Composable (config: PreviewConfig, size: Modifier, content: @Composable () -> Unit) -> Unit,
) {
    val program = code.program
    if (program == null) {
        Box(modifier.padding(16.dp)) { label(if (code.isLoading) "Loading…" else "Fix the errors to see the preview") }
        return
    }
    val interpreter = rememberInterpreter(code, program, registry)
    val previews = remember(interpreter) {
        program.previews.map { (function, annotation) ->
            function to interpreter.previewConfig(
                function,
                annotation
            )
        }
    }
    if (previews.isEmpty()) {
        Box(modifier.padding(16.dp)) { label("No preview yet. Add @Preview to a @Composable function.") }
        return
    }
    BoxWithConstraints(modifier) {
        val maxFrameWidth = (maxWidth - 32.dp).coerceAtLeast(MIN_FRAME)
        val maxFrameHeight = (maxHeight - 64.dp).coerceAtLeast(MIN_FRAME)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            previews.forEachIndexed { index, (function, config) ->
                key(function.name, index) {
                    Column {
                        Box(Modifier.padding(bottom = 6.dp)) { label(config.label()) }
                        val size = Modifier
                            .then(
                                if (config.widthDp != null) Modifier.width(config.widthDp.dp) else Modifier.widthIn(
                                    max = maxFrameWidth
                                )
                            )
                            .then(
                                if (config.heightDp != null) Modifier.height(config.heightDp.dp) else Modifier.heightIn(
                                    max = maxFrameHeight
                                )
                            )
                        frame(config, size) {
                            CompositionLocalProvider(
                                LocalResourceProvider provides resources,
                                LocalResourceEnvironment provides ResourceEnvironment(config.locale, config.darkTheme),
                                LocalPreviewDarkTheme provides config.darkTheme,
                            ) {
                                interpreter.Render(function)
                            }
                        }
                    }
                }
            }
        }
    }
}

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
 * `println` output to the logs of [code].
 */
@Composable
internal fun rememberInterpreter(code: ComposeCode, program: Program, registry: Registry): Interpreter {
    val dependencies = code.dependencies
    return remember(program, dependencies, registry) {
        Interpreter(program, registry, onError = { code.logError(it) }, onLog = { code.log(it) }, dependencies = dependencies)
    }
}
