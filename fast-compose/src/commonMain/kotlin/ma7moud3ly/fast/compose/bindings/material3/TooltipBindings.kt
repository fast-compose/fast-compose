@file:OptIn(ExperimentalMaterial3Api::class)

package ma7moud3ly.fast.compose.bindings.material3

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import ma7moud3ly.fast.compose.interpreter.ErrorBox
import ma7moud3ly.fast.compose.interpreter.Registry

/** `TooltipBox`, `PlainTooltip` and tooltip state. */
internal fun Registry.tooltips() {
    type<TooltipState>("TooltipState")
    composable("rememberTooltipState(initialIsVisible, isPersistent)") { a ->
        rememberTooltipState(initialIsVisible = a.boolean("initialIsVisible", false), isPersistent = a.boolean("isPersistent", false))
    }
    properties<TooltipState>("isVisible" to { it.isVisible })
    suspendMember<TooltipState>("show") { state, _ -> state.show() }
    member<TooltipState>("dismiss") { state, _ -> state.dismiss() }

    value("TooltipAnchorPosition", TooltipAnchorPosition.Companion)
    constants<TooltipAnchorPosition.Companion>(
        "Above" to TooltipAnchorPosition.Above,
        "Below" to TooltipAnchorPosition.Below,
        "Left" to TooltipAnchorPosition.Left,
        "Right" to TooltipAnchorPosition.Right,
        "Start" to TooltipAnchorPosition.Start,
        "End" to TooltipAnchorPosition.End,
    )
    value("TooltipDefaults", TooltipDefaults)
    composableMember<TooltipDefaults>("rememberTooltipPositionProvider(positioning, spacingBetweenTooltipAndAnchor)") { _, a ->
        val positioning = a.typed("positioning", TooltipAnchorPosition.Above)
        if (a.has("spacingBetweenTooltipAndAnchor")) {
            TooltipDefaults.rememberTooltipPositionProvider(positioning, a.dp("spacingBetweenTooltipAndAnchor", 0.dp))
        } else {
            TooltipDefaults.rememberTooltipPositionProvider(positioning)
        }
    }

    // Older name of rememberTooltipPositionProvider, for tooltips shown above their anchor.
    composableMember<TooltipDefaults>("rememberPlainTooltipPositionProvider(spacingBetweenTooltipAndAnchor)") { _, a ->
        if (a.has("spacingBetweenTooltipAndAnchor")) {
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above, a.dp("spacingBetweenTooltipAndAnchor", 0.dp))
        } else {
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above)
        }
    }

    composable("TooltipBox(positionProvider, tooltip, state, modifier, onDismissRequest, focusable, enableUserInput, hasAction, content)") { a ->
        val tooltip = a.scopedContent<TooltipScope>("tooltip")
        val content = a.content()
        val state = a.typedOrNull<TooltipState>("state")
        if (state == null) {
            ErrorBox("TooltipBox needs a TooltipState, e.g. rememberTooltipState()")
        } else {
            TooltipBox(
                positionProvider = a.typedOrNull<PopupPositionProvider>("positionProvider")
                    ?: TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { tooltip() },
                state = state,
                modifier = a.modifier(),
                onDismissRequest = a.actionOrNull("onDismissRequest"),
                focusable = a.boolean("focusable", false),
                enableUserInput = a.boolean("enableUserInput", true),
                hasAction = a.boolean("hasAction", false),
            ) { content() }
        }
    }
    composable("PlainTooltip(modifier, caretShape, maxWidth, shape, contentColor, containerColor, tonalElevation, shadowElevation, content)") { a ->
        val scope = a.receiver<TooltipScope>()
        val content = a.content()
        if (scope == null) {
            ErrorBox("PlainTooltip must be inside the tooltip of TooltipBox")
        } else {
            with(scope) {
                PlainTooltip(
                    modifier = a.modifier(),
                    caretShape = a.typedOrNull<Shape>("caretShape"),
                    maxWidth = a.dp("maxWidth", TooltipDefaults.plainTooltipMaxWidth),
                    shape = a.typed<Shape>("shape", TooltipDefaults.plainTooltipContainerShape),
                    contentColor = a.color("contentColor", TooltipDefaults.plainTooltipContentColor),
                    containerColor = a.color("containerColor", TooltipDefaults.plainTooltipContainerColor),
                    tonalElevation = a.dp("tonalElevation", 0.dp),
                    shadowElevation = a.dp("shadowElevation", 0.dp),
                ) { content() }
            }
        }
    }
}
