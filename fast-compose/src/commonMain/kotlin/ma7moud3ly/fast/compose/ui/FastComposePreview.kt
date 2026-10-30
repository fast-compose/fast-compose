package ma7moud3ly.fast.compose.ui

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import ma7moud3ly.fast.compose.bindings.resources.LocalResourceProvider
import ma7moud3ly.fast.compose.bindings.resources.ResourceProvider
import ma7moud3ly.fast.compose.interpreter.Registry

/**
 * Preview of every `@Preview` in [code] with the full API (Material 3, icons and network images). Each preview applies its
 * `@Preview` size, device, background, font scale and night mode, with a light or dark Material theme.
 * Images and strings from `R`/`Res` come from [resources]; `@Preview` locale and night mode pick their variants.
 * Output and runtime errors are collected in [ComposeCode.logs]. Use [BasicComposePreview] for a lighter version.
 */
@Composable
public fun FastComposePreview(
    code: ComposeCode,
    modifier: Modifier = Modifier,
    registry: Registry = rememberFullRegistry(),
    resources: ResourceProvider = LocalResourceProvider.current,
) {
    PreviewList(
        code = code,
        registry = registry,
        resources = resources,
        modifier = modifier,
        label = { Text(it, color = Color.Gray, fontSize = 12.sp) },
        frame = { config, size, content -> MaterialFrame(config, size, content) },
    )
}

@Composable
private fun MaterialFrame(
    config: PreviewConfig,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, config.fontScale)) {
        MaterialTheme(colorScheme = if (config.darkTheme) darkColorScheme() else lightColorScheme()) {
            val background = when {
                config.showBackground -> config.backgroundColor
                    ?: MaterialTheme.colorScheme.background

                config.darkTheme -> MaterialTheme.colorScheme.background
                else -> Color.Transparent
            }
            Surface(
                modifier = modifier.border(Dp.Hairline, FRAME_BORDER),
                color = background,
                content = content
            )
        }
    }
}
