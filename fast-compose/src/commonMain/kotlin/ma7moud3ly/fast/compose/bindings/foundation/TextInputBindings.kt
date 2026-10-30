package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import ma7moud3ly.fast.compose.interpreter.Registry

/** `BasicText`, `BasicTextField`, text selection, keyboard options, visual transformations and `TextStyle`. */
internal fun Registry.textInput() {
    keyboard()
    textStyles()
    composable("BasicText(text, modifier, style, onTextLayout, overflow, softWrap, maxLines, minLines)") { a ->
        val style = a.typed("style", TextStyle.Default)
        val onTextLayout = a.action1OrNull("onTextLayout")
        val overflow = a.typed("overflow", TextOverflow.Clip)
        val softWrap = a.boolean("softWrap", true)
        val maxLines = a.int("maxLines", Int.MAX_VALUE).coerceAtLeast(1)
        val minLines = a.int("minLines", 1).coerceAtLeast(1)
        val annotated = a["text"] as? AnnotatedString
        if (annotated != null) {
            BasicText(
                text = annotated,
                modifier = a.modifier(),
                style = style,
                onTextLayout = onTextLayout,
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
                minLines = minLines,
            )
        } else {
            BasicText(
                text = a.string("text"),
                modifier = a.modifier(),
                style = style,
                onTextLayout = onTextLayout,
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
                minLines = minLines,
            )
        }
    }
    composable("BasicTextField(value, onValueChange, modifier, enabled, readOnly, textStyle, decorationBox, *, singleLine, maxLines, minLines, cursorBrush)") { a ->
        val onValueChange = a.action1("onValueChange")
        val singleLine = a.boolean("singleLine", false)
        BasicTextField(
            value = a.string("value"),
            onValueChange = { text: String -> onValueChange(text) },
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            readOnly = a.boolean("readOnly", false),
            textStyle = a.typed("textStyle", TextStyle.Default),
            singleLine = singleLine,
            maxLines = a.int("maxLines", if (singleLine) 1 else Int.MAX_VALUE).coerceAtLeast(1),
            minLines = a.int("minLines", 1).coerceAtLeast(1),
            cursorBrush = a.typed<Brush>("cursorBrush", SolidColor(Color.Black)),
            decorationBox = a.decorator("decorationBox"),
        )
    }
    composable("SelectionContainer(modifier, content)") { a ->
        val content = a.content()
        SelectionContainer(modifier = a.modifier()) { content() }
    }
    composable("DisableSelection(content)") { a ->
        val content = a.content()
        DisableSelection { content() }
    }
}

private fun Registry.keyboard() {
    type<KeyboardOptions>("KeyboardOptions")
    function("KeyboardOptions(capitalization, autoCorrectEnabled, keyboardType, imeAction)") { a ->
        KeyboardOptions(
            capitalization = a.typed("capitalization", KeyboardCapitalization.Unspecified),
            autoCorrectEnabled = a.typedOrNull<Boolean>("autoCorrectEnabled"),
            keyboardType = a.typed("keyboardType", KeyboardType.Unspecified),
            imeAction = a.typed("imeAction", ImeAction.Unspecified),
        )
    }
    value("KeyboardOptions", KeyboardOptions.Companion)
    constants<KeyboardOptions.Companion>("Default" to KeyboardOptions.Default)
    type<KeyboardActions>("KeyboardActions")
    function("KeyboardActions(onDone, onGo, onNext, onPrevious, onSearch, onSend)") { a ->
        fun handler(name: String): (KeyboardActionScope.() -> Unit)? = a.actionOrNull(name)?.let { action -> { action() } }
        KeyboardActions(
            onDone = handler("onDone"),
            onGo = handler("onGo"),
            onNext = handler("onNext"),
            onPrevious = handler("onPrevious"),
            onSearch = handler("onSearch"),
            onSend = handler("onSend"),
        )
    }
    value("KeyboardType", KeyboardType.Companion)
    constants<KeyboardType.Companion>(
        "Text" to KeyboardType.Text, "Ascii" to KeyboardType.Ascii, "Number" to KeyboardType.Number,
        "Phone" to KeyboardType.Phone, "Uri" to KeyboardType.Uri, "Email" to KeyboardType.Email,
        "Password" to KeyboardType.Password, "NumberPassword" to KeyboardType.NumberPassword, "Decimal" to KeyboardType.Decimal,
    )
    value("ImeAction", ImeAction.Companion)
    constants<ImeAction.Companion>(
        "Default" to ImeAction.Default, "None" to ImeAction.None, "Go" to ImeAction.Go, "Search" to ImeAction.Search,
        "Send" to ImeAction.Send, "Previous" to ImeAction.Previous, "Next" to ImeAction.Next, "Done" to ImeAction.Done,
    )
    value("KeyboardCapitalization", KeyboardCapitalization.Companion)
    constants<KeyboardCapitalization.Companion>(
        "None" to KeyboardCapitalization.None, "Characters" to KeyboardCapitalization.Characters,
        "Words" to KeyboardCapitalization.Words, "Sentences" to KeyboardCapitalization.Sentences,
    )
    type<VisualTransformation>("VisualTransformation")
    value("VisualTransformation", VisualTransformation.Companion)
    constants<VisualTransformation.Companion>("None" to VisualTransformation.None)
    function("PasswordVisualTransformation(mask)") { a ->
        PasswordVisualTransformation(a.string("mask", "•").firstOrNull() ?: '•')
    }
}

private fun Registry.textStyles() {
    function(
        "TextStyle(color, fontSize, fontWeight, fontStyle, *, fontFamily, letterSpacing, background, textDecoration, shadow, textAlign, lineHeight)",
    ) { a ->
        TextStyle(
            color = a.color("color"),
            fontSize = a.sp("fontSize"),
            fontWeight = a.typedOrNull<FontWeight>("fontWeight"),
            fontStyle = a.typedOrNull<FontStyle>("fontStyle"),
            fontFamily = a.typedOrNull<FontFamily>("fontFamily"),
            letterSpacing = a.sp("letterSpacing"),
            background = a.color("background"),
            textDecoration = a.typedOrNull<TextDecoration>("textDecoration"),
            shadow = a.typedOrNull<Shadow>("shadow"),
            textAlign = a.typed("textAlign", TextAlign.Unspecified),
            lineHeight = a.sp("lineHeight"),
        )
    }
    value("TextStyle", TextStyle.Companion)
    constants<TextStyle.Companion>("Default" to TextStyle.Default)
    type<Shadow>("Shadow")
    function("Shadow(color, offset, blurRadius)") { a ->
        Shadow(a.color("color", Color(0xFF000000)), a.typed("offset", Offset.Zero), a.float("blurRadius", 0f))
    }
    member<TextStyle>("merge(other)") { style, a -> style.merge(a.typedOrNull<TextStyle>("other")) }
}
