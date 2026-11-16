package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import ma7moud3ly.fast.compose.interpreter.Registry

/** Marker for `TextFieldState.Saver`, read as `TextFieldState.Saver`. */
private object TextFieldStates

/**
 * The state-based text field: `TextFieldState`, `rememberTextFieldState`, `BasicTextField(state = …)` with its line
 * limits, and the `InputTransformation { }`, `OutputTransformation { }` and keyboard action lambdas it takes.
 */
internal fun Registry.textFieldState() {
    type<TextFieldState>("TextFieldState")
    function("TextFieldState(initialText, initialSelection)") { a ->
        val text = a.string("initialText")
        TextFieldState(text, a.typed("initialSelection", TextRange(text.length)))
    }
    composable("rememberTextFieldState(initialText, initialSelection)") { a ->
        val text = a.string("initialText")
        rememberTextFieldState(text, a.typed("initialSelection", TextRange(text.length)))
    }
    properties<TextFieldState>("text" to { it.text.toString() }, "selection" to { it.selection })
    member<TextFieldState>("setTextAndPlaceCursorAtEnd(text)") { state, a -> state.setTextAndPlaceCursorAtEnd(a.string("text")) }
    member<TextFieldState>("clearText()") { state, _ -> state.clearText() }
    value("TextFieldState", TextFieldStates)
    constants<TextFieldStates>("Saver" to TextFieldState.Saver)

    type<TextFieldLineLimits>("TextFieldLineLimits")
    value("TextFieldLineLimits", TextFieldLineLimits.Companion)
    constants<TextFieldLineLimits.Companion>("SingleLine" to TextFieldLineLimits.SingleLine, "Default" to TextFieldLineLimits.Default)
    member<TextFieldLineLimits.Companion>("MultiLine(minHeightInLines, maxHeightInLines)") { _, a ->
        TextFieldLineLimits.MultiLine(a.int("minHeightInLines", 1), a.int("maxHeightInLines", Int.MAX_VALUE))
    }

    // `fun interface`s built from a lambda, whose receiver is the field's text buffer.
    type<InputTransformation>("InputTransformation")
    function("InputTransformation(transformInput)") { a ->
        val transform = a.scopedAction<TextFieldBuffer>("transformInput")
        InputTransformation { transform() }
    }
    type<OutputTransformation>("OutputTransformation")
    function("OutputTransformation(transformOutput)") { a ->
        val transform = a.scopedAction<TextFieldBuffer>("transformOutput")
        OutputTransformation { transform() }
    }

    composable(
        "BasicTextField(state: TextFieldState, modifier, enabled, readOnly, inputTransformation, textStyle, " +
            "keyboardOptions, onKeyboardAction, lineLimits, onTextLayout, interactionSource, cursorBrush, " +
            "outputTransformation, decorator, scrollState)",
    ) { a ->
        val state = a.typed("state", TextFieldState())
        val onKeyboardAction = a.action1OrNull("onKeyboardAction")
        val decorate = if (a.lambda("decorator") != null) a.decorator("decorator") else null
        BasicTextField(
            state = state,
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            readOnly = a.boolean("readOnly", false),
            inputTransformation = a.typedOrNull<InputTransformation>("inputTransformation"),
            textStyle = a.typed("textStyle", TextStyle.Default),
            keyboardOptions = a.typed("keyboardOptions", KeyboardOptions.Default),
            onKeyboardAction = onKeyboardAction?.let { action -> KeyboardActionHandler { performDefault -> action(performDefault) } },
            lineLimits = a.typed("lineLimits", TextFieldLineLimits.Default),
            interactionSource = a.typedOrNull<MutableInteractionSource>("interactionSource"),
            cursorBrush = a.typed<Brush>("cursorBrush", SolidColor(Color.Black)),
            outputTransformation = a.typedOrNull<OutputTransformation>("outputTransformation"),
            decorator = decorate?.let { decoration ->
                object : TextFieldDecorator {
                    @Composable
                    override fun Decoration(innerTextField: @Composable () -> Unit) = decoration(innerTextField)
                }
            },
            scrollState = a.typedOrNull<ScrollState>("scrollState") ?: rememberScrollState(),
        )
    }
}
