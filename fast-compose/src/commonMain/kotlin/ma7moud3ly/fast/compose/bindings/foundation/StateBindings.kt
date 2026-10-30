package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
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

/** Dark mode of the preview, e.g. from `@Preview(uiMode = UI_MODE_NIGHT_YES)`; `isSystemInDarkTheme()` returns it when set. */
public val LocalPreviewDarkTheme: ProvidableCompositionLocal<Boolean?> = staticCompositionLocalOf<Boolean?> { null }

/** Saveable and specialized state, dark theme, platform locals and flows. */
internal fun Registry.stateExtras() {
    // Previews are not saved and restored, so rememberSaveable behaves like remember.
    composable("rememberSaveable(vararg inputs, init)") { a ->
        val init = a.calculation("init")
        remember(a.varargs("inputs")) { init() }
    }
    function("mutableStateMapOf(vararg pairs)") { a ->
        mutableStateMapOf<Any?, Any?>().apply { a.varargs("pairs").filterIsInstance<Pair<*, *>>().forEach { (key, value) -> put(key, value) } }
    }
    function("mutableLongStateOf(value)") { a -> mutableLongStateOf(a.long("value", 0L)) }
    function("mutableDoubleStateOf(value)") { a -> mutableDoubleStateOf(a.typed<Number>("value", 0.0).toDouble()) }
    composable("isSystemInDarkTheme()") { LocalPreviewDarkTheme.current ?: isSystemInDarkTheme() }
    locals()
    flows()
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
}
