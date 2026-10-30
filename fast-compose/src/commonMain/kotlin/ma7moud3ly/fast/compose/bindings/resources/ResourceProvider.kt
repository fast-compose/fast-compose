package ma7moud3ly.fast.compose.bindings.resources

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.ProvidableCompositionLocal

/**
 * What selects a resource variant: [locale] picks `values-ar` or `values-pt-rBR` (null for the default strings),
 * [isDark] picks `-night` folders, and [density] picks the closest of `-mdpi` … `-xxxhdpi`.
 */
public data class ResourceEnvironment(val locale: String? = null, val isDark: Boolean = false, val density: Float = 1f)

/** Supplies resource files and strings to previews, e.g. from a project's `res/` or `composeResources/` folders. */
public interface ResourceProvider {

    /** Bytes of resource [name] of [type] (`drawable`, `font`, …) best matching [environment], or null when it is missing. */
    public fun bytes(type: String, name: String, environment: ResourceEnvironment): ByteArray?

    /** String resource [name] best matching [environment], or null when it is missing. */
    public fun string(name: String, environment: ResourceEnvironment): String?

    /**
     * True while resource [name] of [type] (`string` for strings) is still being read; previews then show a placeholder
     * without reporting it as missing.
     */
    public fun isLoading(type: String, name: String, environment: ResourceEnvironment): Boolean = false

    public companion object {
        /** Provides nothing: images show a placeholder and strings show their name. */
        public val Empty: ResourceProvider = object : ResourceProvider {
            override fun bytes(type: String, name: String, environment: ResourceEnvironment): ByteArray? = null
            override fun string(name: String, environment: ResourceEnvironment): String? = null
        }
    }
}

/** Resources used by `painterResource`, `stringResource` and the other resource bindings. */
public val LocalResourceProvider: ProvidableCompositionLocal<ResourceProvider> = staticCompositionLocalOf { ResourceProvider.Empty }

/** Locale and theme of resources, e.g. from `@Preview(locale = "ar", uiMode = UI_MODE_NIGHT_YES)`; the density comes from the layout. */
public val LocalResourceEnvironment: ProvidableCompositionLocal<ResourceEnvironment> = staticCompositionLocalOf { ResourceEnvironment() }
