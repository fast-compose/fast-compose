package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ma7moud3ly.fast.compose.interpreter.Registry

/** `Dialog` and `Popup` windows. */
internal fun Registry.windows() {
    type<DialogProperties>("DialogProperties")
    function("DialogProperties(dismissOnBackPress, dismissOnClickOutside, usePlatformDefaultWidth)") { a ->
        DialogProperties(
            dismissOnBackPress = a.boolean("dismissOnBackPress", true),
            dismissOnClickOutside = a.boolean("dismissOnClickOutside", true),
            usePlatformDefaultWidth = a.boolean("usePlatformDefaultWidth", true),
        )
    }
    composable("Dialog(onDismissRequest, properties, content)") { a ->
        val content = a.content()
        Dialog(onDismissRequest = a.action("onDismissRequest"), properties = a.typed("properties", DialogProperties())) { content() }
    }
    type<PopupProperties>("PopupProperties")
    function("PopupProperties(focusable, dismissOnBackPress, dismissOnClickOutside, clippingEnabled, usePlatformDefaultWidth)") { a ->
        PopupProperties(
            focusable = a.boolean("focusable", false),
            dismissOnBackPress = a.boolean("dismissOnBackPress", true),
            dismissOnClickOutside = a.boolean("dismissOnClickOutside", true),
            clippingEnabled = a.boolean("clippingEnabled", true),
            usePlatformDefaultWidth = a.boolean("usePlatformDefaultWidth", false),
        )
    }
    composable("Popup(alignment, offset, onDismissRequest, properties, content)") { a ->
        val content = a.content()
        Popup(
            alignment = a.typed<Alignment>("alignment", Alignment.TopStart),
            offset = a.typed("offset", IntOffset(0, 0)),
            onDismissRequest = a.actionOrNull("onDismissRequest"),
            properties = a.typed("properties", PopupProperties()),
        ) { content() }
    }
}
