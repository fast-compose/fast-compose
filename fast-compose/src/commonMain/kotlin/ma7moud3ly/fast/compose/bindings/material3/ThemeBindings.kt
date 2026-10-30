package ma7moud3ly.fast.compose.bindings.material3

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.Registry

/**
 * Theme builders: `lightColorScheme`, `darkColorScheme`, `Typography` and `Shapes`. Values that are not passed keep
 * their Material defaults.
 */
internal fun Registry.themes() {
    type<ColorScheme>("ColorScheme")
    type<Typography>("Typography")
    type<Shapes>("Shapes")
    function(
        "lightColorScheme(primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary, secondary, onSecondary, " +
            "secondaryContainer, onSecondaryContainer, tertiary, onTertiary, tertiaryContainer, " +
            "onTertiaryContainer, background, onBackground, surface, onSurface, surfaceVariant, " +
            "onSurfaceVariant, surfaceTint, inverseSurface, inverseOnSurface, error, onError, errorContainer, " +
            "onErrorContainer, outline, outlineVariant, scrim, surfaceBright, surfaceContainer, " +
            "surfaceContainerHigh, surfaceContainerHighest, surfaceContainerLow, surfaceContainerLowest, " +
            "surfaceDim)",
    ) { a -> a.colorScheme(lightColorScheme()) }
    function(
        "darkColorScheme(primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary, secondary, onSecondary, " +
            "secondaryContainer, onSecondaryContainer, tertiary, onTertiary, tertiaryContainer, " +
            "onTertiaryContainer, background, onBackground, surface, onSurface, surfaceVariant, " +
            "onSurfaceVariant, surfaceTint, inverseSurface, inverseOnSurface, error, onError, errorContainer, " +
            "onErrorContainer, outline, outlineVariant, scrim, surfaceBright, surfaceContainer, " +
            "surfaceContainerHigh, surfaceContainerHighest, surfaceContainerLow, surfaceContainerLowest, " +
            "surfaceDim)",
    ) { a -> a.colorScheme(darkColorScheme()) }
    function(
        "Typography(displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall, " +
            "titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, " +
            "labelSmall)",
    ) { a -> a.typography(Typography()) }
    function("Shapes(extraSmall, small, medium, large, extraLarge)") { a -> a.shapes(Shapes()) }
}

private fun Args.colorScheme(base: ColorScheme): ColorScheme = base.copy(
        primary = color("primary", base.primary),
        onPrimary = color("onPrimary", base.onPrimary),
        primaryContainer = color("primaryContainer", base.primaryContainer),
        onPrimaryContainer = color("onPrimaryContainer", base.onPrimaryContainer),
        inversePrimary = color("inversePrimary", base.inversePrimary),
        secondary = color("secondary", base.secondary),
        onSecondary = color("onSecondary", base.onSecondary),
        secondaryContainer = color("secondaryContainer", base.secondaryContainer),
        onSecondaryContainer = color("onSecondaryContainer", base.onSecondaryContainer),
        tertiary = color("tertiary", base.tertiary),
        onTertiary = color("onTertiary", base.onTertiary),
        tertiaryContainer = color("tertiaryContainer", base.tertiaryContainer),
        onTertiaryContainer = color("onTertiaryContainer", base.onTertiaryContainer),
        background = color("background", base.background),
        onBackground = color("onBackground", base.onBackground),
        surface = color("surface", base.surface),
        onSurface = color("onSurface", base.onSurface),
        surfaceVariant = color("surfaceVariant", base.surfaceVariant),
        onSurfaceVariant = color("onSurfaceVariant", base.onSurfaceVariant),
        surfaceTint = color("surfaceTint", base.surfaceTint),
        inverseSurface = color("inverseSurface", base.inverseSurface),
        inverseOnSurface = color("inverseOnSurface", base.inverseOnSurface),
        error = color("error", base.error),
        onError = color("onError", base.onError),
        errorContainer = color("errorContainer", base.errorContainer),
        onErrorContainer = color("onErrorContainer", base.onErrorContainer),
        outline = color("outline", base.outline),
        outlineVariant = color("outlineVariant", base.outlineVariant),
        scrim = color("scrim", base.scrim),
        surfaceBright = color("surfaceBright", base.surfaceBright),
        surfaceContainer = color("surfaceContainer", base.surfaceContainer),
        surfaceContainerHigh = color("surfaceContainerHigh", base.surfaceContainerHigh),
        surfaceContainerHighest = color("surfaceContainerHighest", base.surfaceContainerHighest),
        surfaceContainerLow = color("surfaceContainerLow", base.surfaceContainerLow),
        surfaceContainerLowest = color("surfaceContainerLowest", base.surfaceContainerLowest),
        surfaceDim = color("surfaceDim", base.surfaceDim),
)

private fun Args.typography(base: Typography): Typography = base.copy(
        displayLarge = typed("displayLarge", base.displayLarge),
        displayMedium = typed("displayMedium", base.displayMedium),
        displaySmall = typed("displaySmall", base.displaySmall),
        headlineLarge = typed("headlineLarge", base.headlineLarge),
        headlineMedium = typed("headlineMedium", base.headlineMedium),
        headlineSmall = typed("headlineSmall", base.headlineSmall),
        titleLarge = typed("titleLarge", base.titleLarge),
        titleMedium = typed("titleMedium", base.titleMedium),
        titleSmall = typed("titleSmall", base.titleSmall),
        bodyLarge = typed("bodyLarge", base.bodyLarge),
        bodyMedium = typed("bodyMedium", base.bodyMedium),
        bodySmall = typed("bodySmall", base.bodySmall),
        labelLarge = typed("labelLarge", base.labelLarge),
        labelMedium = typed("labelMedium", base.labelMedium),
        labelSmall = typed("labelSmall", base.labelSmall),
)

private fun Args.shapes(base: Shapes): Shapes = base.copy(
        extraSmall = typed<CornerBasedShape>("extraSmall", base.extraSmall),
        small = typed<CornerBasedShape>("small", base.small),
        medium = typed<CornerBasedShape>("medium", base.medium),
        large = typed<CornerBasedShape>("large", base.large),
        extraLarge = typed<CornerBasedShape>("extraLarge", base.extraLarge),
)
