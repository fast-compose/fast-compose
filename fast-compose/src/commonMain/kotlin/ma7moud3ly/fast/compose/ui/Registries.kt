package ma7moud3ly.fast.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ma7moud3ly.fast.compose.bindings.coil.CoilBindings
import ma7moud3ly.fast.compose.bindings.foundation.FoundationBindings
import ma7moud3ly.fast.compose.bindings.icons.IconBindings
import ma7moud3ly.fast.compose.bindings.material3.Material3Bindings
import ma7moud3ly.fast.compose.bindings.resources.ResourceBindings
import ma7moud3ly.fast.compose.interpreter.BindingPack
import ma7moud3ly.fast.compose.interpreter.Registry

/** Packs added on top of the basic API by the full API. */
private val fullPacks = arrayOf(Material3Bindings, IconBindings, ResourceBindings, CoilBindings)

/**
 * Shared preset with the basic API; built once and never modified.
 * Names only the full API knows get a hint pointing to [FastComposePreview].
 */
private val basicPreset: Registry by lazy {
    Registry().apply {
        install(FoundationBindings, PreviewConstants)
        val fullOnly = Registry().apply { install(*fullPacks) }.ownGlobalNames() - globalNames()
        for (name in fullOnly) hint(name, "'$name' is only in the full API; use FastComposePreview")
    }
}

/** Shared preset with the full API, layered on [basicPreset]; built once and never modified. */
private val fullPreset: Registry by lazy { Registry(basicPreset).apply { install(*fullPacks) } }

/**
 * Remembers a registry with the basic, Material-free API: layouts, `Text`, state, modifiers, drawing and animation.
 * [packs] and [extensions] add your own APIs; they are layered on a shared preset, so nothing is copied.
 */
@Composable
public fun rememberBasicRegistry(vararg packs: BindingPack, extensions: Registry.() -> Unit = {}): Registry =
    remember { Registry(basicPreset).apply { install(*packs); extensions() } }

/**
 * Remembers a registry with the full API: everything in [rememberBasicRegistry] plus Material 3, Material icons,
 * resources (`R`/`Res`, `painterResource`, `stringResource`) and network images (`AsyncImage`).
 * [packs] and [extensions] add your own APIs; they are layered on a shared preset, so nothing is copied.
 */
@Composable
public fun rememberFullRegistry(vararg packs: BindingPack, extensions: Registry.() -> Unit = {}): Registry =
    remember { Registry(fullPreset).apply { install(*packs); extensions() } }
