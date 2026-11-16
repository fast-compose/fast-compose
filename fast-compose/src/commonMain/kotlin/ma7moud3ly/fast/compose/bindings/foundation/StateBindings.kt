package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.UriHandler
import ma7moud3ly.fast.compose.interpreter.Registry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Dark mode of the preview, e.g. from `@Preview(uiMode = UI_MODE_NIGHT_YES)`; `isSystemInDarkTheme()` returns it when set. */
public val LocalPreviewDarkTheme: ProvidableCompositionLocal<Boolean?> = staticCompositionLocalOf<Boolean?> { null }

/** Saveable and specialized state, dark theme, platform locals and flows. */
internal fun Registry.stateExtras() {
    savers()
    // Previews are not saved and restored, so rememberSaveable behaves like remember.
    // Previews don't save state, so a custom saver isn't used.
    composable("rememberSaveable(vararg inputs, saver, key, init)") { a ->
        val init = a.calculation("init")
        remember(a.varargs("inputs")) { init() }
    }
    function("mutableStateMapOf(vararg pairs)") { a ->
        mutableStateMapOf<Any?, Any?>().apply { a.varargs("pairs").filterIsInstance<Pair<*, *>>().forEach { (key, value) -> put(key, value) } }
    }
    member<Collection<*>>("toMutableStateList()") { items, _ -> items.toMutableStateList() }
    member<Iterable<*>>("toMutableStateMap()") { pairs, _ ->
        pairs.filterIsInstance<Pair<Any?, Any?>>().toMutableStateMap()
    }
    function("mutableLongStateOf(value)") { a -> mutableLongStateOf(a.long("value", 0L)) }
    function("mutableDoubleStateOf(value)") { a -> mutableDoubleStateOf(a.typed<Number>("value", 0.0).toDouble()) }
    composable("isSystemInDarkTheme()") { LocalPreviewDarkTheme.current ?: isSystemInDarkTheme() }
    locals()
    flows()
}

/** `Saver(save, restore)`, as `rememberSaveable(saver = …)` and companion `Saver` properties build them. */
private fun Registry.savers() {
    type<Saver<*, *>>("Saver")
    function("Saver(save, restore)") { a ->
        val save = a.scopedFunction1<SaverScope>("save")
        val restore = a.function1("restore")
        Saver<Any?, Any>(save = { value -> save(this, value) }, restore = { saved -> restore(saved) })
    }
}

private fun Registry.locals() {
    value("LocalUriHandler", LocalUriHandler)
    member<UriHandler>("openUri(uri)") { handler, a -> handler.openUri(a.string("uri")) }
    value("LocalFocusManager", LocalFocusManager)
    member<FocusManager>("clearFocus(force)") { manager, a -> manager.clearFocus(a.boolean("force", false)) }
    value("LocalSoftwareKeyboardController", LocalSoftwareKeyboardController)
    member<SoftwareKeyboardController>("hide") { controller, _ -> controller.hide() }
    member<SoftwareKeyboardController>("show") { controller, _ -> controller.show() }
}

@Suppress("UNCHECKED_CAST")
private fun Registry.flows() {
    type<Flow<*>>("Flow")
    type<StateFlow<*>>("StateFlow")
    type<MutableStateFlow<*>>("MutableStateFlow")
    function("MutableStateFlow(value)") { a -> MutableStateFlow(a["value"]) }
    properties<StateFlow<*>>("value" to { it.value })
    setter<MutableStateFlow<*>>("value") { flow, value -> (flow as MutableStateFlow<Any?>).value = value }
    member<MutableStateFlow<*>>("asStateFlow()") { flow, _ -> flow.asStateFlow() }
    member<MutableStateFlow<*>>("update(function)") { flow, a ->
        val function = a.function1("function")
        (flow as MutableStateFlow<Any?>).update { function(it) }
    }
    function("snapshotFlow(block)") { a ->
        val block = a.calculation("block")
        snapshotFlow { block() }
    }
    suspendMember<Flow<*>>("collect(collector)") { flow, a ->
        val collector = a.suspendAction1("collector")
        flow.collect { collector(it) }
    }
    composableMember<StateFlow<*>>("collectAsState()") { flow, _ -> flow.collectAsState() }
    composableMember<Flow<*>>("collectAsState(initial)") { flow, a -> flow.collectAsState(a["initial"]) }
    // Previews are always resumed, so collecting with the lifecycle is plain collecting.
    composableMember<StateFlow<*>>("collectAsStateWithLifecycle()") { flow, _ -> flow.collectAsState() }
    composableMember<Flow<*>>("collectAsStateWithLifecycle(initialValue)") { flow, a -> flow.collectAsState(a["initialValue"]) }
}
