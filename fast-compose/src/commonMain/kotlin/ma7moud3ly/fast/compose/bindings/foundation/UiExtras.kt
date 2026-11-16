package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.animation.core.Ease
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInBack
import androidx.compose.animation.core.EaseInBounce
import androidx.compose.animation.core.EaseInCirc
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseInElastic
import androidx.compose.animation.core.EaseInExpo
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseInOutBack
import androidx.compose.animation.core.EaseInOutBounce
import androidx.compose.animation.core.EaseInOutCirc
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseInOutElastic
import androidx.compose.animation.core.EaseInOutExpo
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.EaseInOutQuint
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseInQuad
import androidx.compose.animation.core.EaseInQuart
import androidx.compose.animation.core.EaseInQuint
import androidx.compose.animation.core.EaseInSine
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutBounce
import androidx.compose.animation.core.EaseOutCirc
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseOutElastic
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.EaseOutSine
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.Registry

/** `LinkAnnotation.Url` and `LinkAnnotation.Clickable`. */
private object LinkAnnotations

/** `Key.A`, `Key.Enter`, … */
private object Keys

/** `PointerIcon.Default`, `Hand`, `Text` and `Crosshair`. */
private object PointerIcons

/**
 * Compose UI APIs used by real screens: density conversions, text extras (links, auto size, selection colors, line
 * height style, `TextFieldValue`), focus and keys, pointer icons, easing curves, layout coordinates and window insets.
 */
@Suppress("DEPRECATION") // LocalClipboardManager, still used by apps
internal fun Registry.uiExtras() {
    densityConversions()
    textExtras()
    focusAndPointer()
    easings()
    layoutExtras()
    value("LocalInspectionMode", LocalInspectionMode)
    value("LocalClipboardManager", LocalClipboardManager)
    value("LocalClipboard", LocalClipboard)
    function("mutableStateSetOf(vararg elements)") { a -> mutableStateSetOf<Any?>().apply { addAll(a.varargs("elements")) } }
    member<Color>("toArgb()") { color, _ -> color.toArgb() }
    member<Color>("compositeOver(background)") { color, a -> color.compositeOver(a.color("background", Color.White)) }
    member<Modifier>("composed(inspectorInfo, factory)") { m, a ->
        // The factory runs in composition, with the modifier it extends as `this`.
        val factory = a.composableScopedCalculation<Modifier>("factory")
        m.composed { factory(this) as? Modifier ?: this }
    }
    function("lerp(start, stop, fraction)") { a ->
        val fraction = a.float("fraction", 0f)
        when (val start = a["start"]) {
            is Color -> lerp(start, a.color("stop", start), fraction)
            is Dp -> lerp(start, a.dp("stop", start), fraction)
            is TextUnit -> lerp(start, a.sp("stop", start), fraction)
            is Offset -> androidx.compose.ui.geometry.lerp(start, a.typed("stop", start), fraction)
            else -> a.float("start", 0f) + (a.float("stop", 0f) - a.float("start", 0f)) * fraction
        }
    }
}

/**
 * `toDp()`, `toPx()`, `roundToPx()` and `toSp()` inside `with(density) { … }` or a draw scope. Outside one, the
 * density of the preview's default (1) is used.
 */
private fun Registry.densityConversions() {
    member<Number>("toDp()") { n, a -> with(a.density()) { if (n is Int) n.toDp() else n.toFloat().toDp() } }
    member<Number>("toSp()") { n, a -> with(a.density()) { if (n is Int) n.toSp() else n.toFloat().toSp() } }
    member<Dp>("toPx()") { dp, a -> with(a.density()) { dp.toPx() } }
    member<Dp>("roundToPx()") { dp, a -> with(a.density()) { dp.roundToPx() } }
    member<Dp>("toSp()") { dp, a -> with(a.density()) { dp.toSp() } }
    member<TextUnit>("toPx()") { sp, a -> with(a.density()) { sp.toPx() } }
    member<TextUnit>("toDp()") { sp, a -> with(a.density()) { sp.toDp() } }
    // compose-ui-util's packing of two values into a Long, as value classes such as a scrollbar's state store them.
    function("packFloats(val1, val2)") { a ->
        (a.float("val1", 0f).toRawBits().toLong() shl 32) or (a.float("val2", 0f).toRawBits().toLong() and 0xFFFFFFFFL)
    }
    function("unpackFloat1(value)") { a -> Float.fromBits((a.long("value", 0L) ushr 32).toInt()) }
    function("unpackFloat2(value)") { a -> Float.fromBits((a.long("value", 0L) and 0xFFFFFFFFL).toInt()) }
    function("packInts(val1, val2)") { a -> (a.int("val1", 0).toLong() shl 32) or (a.int("val2", 0).toLong() and 0xFFFFFFFFL) }
    function("unpackInt1(value)") { a -> (a.long("value", 0L) shr 32).toInt() }
    function("unpackInt2(value)") { a -> (a.long("value", 0L) and 0xFFFFFFFFL).toInt() }
    member<TextUnit>("roundToPx()") { sp, a -> with(a.density()) { sp.roundToPx() } }
    member<Number>("em") { n, _ -> n.toFloat().em }
}

private fun Args.density(): Density = receiver<Density>() ?: Density(1f)

private fun Registry.textExtras() {
    value("TextAutoSize", TextAutoSize.Companion)
    type<TextAutoSize>("TextAutoSize")
    member<TextAutoSize.Companion>("StepBased(minFontSize, maxFontSize, stepSize)") { _, a ->
        TextAutoSize.StepBased(
            minFontSize = a.sp("minFontSize", 12.sp),
            maxFontSize = a.sp("maxFontSize", 112.sp),
            stepSize = a.sp("stepSize", 0.25.sp),
        )
    }

    value("LocalTextSelectionColors", LocalTextSelectionColors)
    type<TextSelectionColors>("TextSelectionColors")
    function("TextSelectionColors(handleColor, backgroundColor)") { a ->
        TextSelectionColors(a.color("handleColor"), a.color("backgroundColor"))
    }

    type<TextRange>("TextRange")
    function("TextRange(start, end)") { a -> TextRange(a.int("start", 0), a.int("end", a.int("start", 0))) }
    properties<TextRange>("start" to { it.start }, "end" to { it.end }, "length" to { it.length }, "collapsed" to { it.collapsed })
    type<TextFieldValue>("TextFieldValue")
    function("TextFieldValue(text, selection)") { a ->
        val text = a.string("text")
        TextFieldValue(text, a.typed("selection", TextRange(text.length)))
    }
    properties<TextFieldValue>("text" to { it.text }, "selection" to { it.selection }, "annotatedString" to { it.annotatedString })
    member<TextFieldValue>("copy(text, selection)") { value, a ->
        value.copy(text = a.string("text", value.text), selection = a.typed("selection", value.selection))
    }

    type<TextLayoutResult>("TextLayoutResult")
    properties<TextLayoutResult>(
        "lineCount" to { it.lineCount },
        "hasVisualOverflow" to { it.hasVisualOverflow },
        "didOverflowWidth" to { it.didOverflowWidth },
        "didOverflowHeight" to { it.didOverflowHeight },
        "size" to { it.size },
        "firstBaseline" to { it.firstBaseline },
        "lastBaseline" to { it.lastBaseline },
    )
    member<TextLayoutResult>("getLineEnd(lineIndex, visibleEnd)") { result, a ->
        result.getLineEnd(a.int("lineIndex", 0), a.boolean("visibleEnd", false))
    }
    member<TextLayoutResult>("isLineEllipsized(lineIndex)") { result, a -> result.isLineEllipsized(a.int("lineIndex", 0)) }

    value("LinkAnnotation", LinkAnnotations)
    type<LinkAnnotation>("LinkAnnotation")
    // Link listeners would open URLs or run app code, so previews keep the default behavior.
    member<LinkAnnotations>("Url(url, styles)") { _, a -> LinkAnnotation.Url(a.string("url"), a.typedOrNull<TextLinkStyles>("styles")) }
    member<LinkAnnotations>("Clickable(tag, styles)") { _, a ->
        LinkAnnotation.Clickable(a.string("tag"), a.typedOrNull<TextLinkStyles>("styles"), null)
    }
    type<TextLinkStyles>("TextLinkStyles")
    function("TextLinkStyles(style, focusedStyle, hoveredStyle, pressedStyle)") { a ->
        TextLinkStyles(
            style = a.typedOrNull<SpanStyle>("style"),
            focusedStyle = a.typedOrNull<SpanStyle>("focusedStyle"),
            hoveredStyle = a.typedOrNull<SpanStyle>("hoveredStyle"),
            pressedStyle = a.typedOrNull<SpanStyle>("pressedStyle"),
        )
    }
    member<AnnotatedString.Builder>("withLink(link, block)") { builder, a ->
        val block = a.scopedCalculation<AnnotatedString.Builder>("block")
        val link = a.typedOrNull<LinkAnnotation>("link")
        if (link == null) builder.block() else builder.withLink(link) { block() ?: Unit }
    }
    // Inside composition, the block is composable code, e.g. `withStyle(…) { append(stringResource(…)) }`.
    composableMember<AnnotatedString.Builder>("withLink(link, block)") { builder, a ->
        val block = a.composableScopedCalculation<AnnotatedString.Builder>("block")
        val link = a.typedOrNull<LinkAnnotation>("link")
        val index = link?.let { builder.pushLink(it) }
        val result = builder.block() ?: Unit
        index?.let { builder.pop(it) }
        result
    }
    member<AnnotatedString.Builder>("pushLink(link)") { builder, a ->
        a.typedOrNull<LinkAnnotation>("link")?.let { builder.pushLink(it) } ?: Unit
    }

    properties<TextStyle>(
        "color" to { it.color },
        "fontSize" to { it.fontSize },
        "fontWeight" to { it.fontWeight },
        "fontStyle" to { it.fontStyle },
        "fontFamily" to { it.fontFamily },
        "letterSpacing" to { it.letterSpacing },
        "lineHeight" to { it.lineHeight },
        "textAlign" to { it.textAlign },
        "textDecoration" to { it.textDecoration },
        "background" to { it.background },
        "lineHeightStyle" to { it.lineHeightStyle },
        "lineBreak" to { it.lineBreak },
    )
    member<TextStyle>("merge(other)") { style, a -> style.merge(a.typedOrNull<TextStyle>("other")) }

    constants<FontWeight.Companion>(
        "ExtraLight" to FontWeight.ExtraLight,
        "W100" to FontWeight.W100, "W200" to FontWeight.W200, "W300" to FontWeight.W300, "W400" to FontWeight.W400,
        "W500" to FontWeight.W500, "W600" to FontWeight.W600, "W700" to FontWeight.W700, "W800" to FontWeight.W800,
        "W900" to FontWeight.W900,
    )
    function("FontWeight(weight)") { a -> FontWeight(a.int("weight", 400).coerceIn(1, 1000)) }
    properties<FontWeight>("weight" to { it.weight })

    value("TextDirection", TextDirection.Companion)
    constants<TextDirection.Companion>(
        "Ltr" to TextDirection.Ltr, "Rtl" to TextDirection.Rtl, "Content" to TextDirection.Content,
        "ContentOrLtr" to TextDirection.ContentOrLtr, "ContentOrRtl" to TextDirection.ContentOrRtl,
        "Unspecified" to TextDirection.Unspecified,
    )

    type<LineBreak>("LineBreak")
    value("LineBreak", LineBreak.Companion)
    constants<LineBreak.Companion>(
        "Simple" to LineBreak.Simple, "Heading" to LineBreak.Heading, "Paragraph" to LineBreak.Paragraph,
        "Unspecified" to LineBreak.Unspecified,
    )

    type<LineHeightStyle>("LineHeightStyle")
    value("LineHeightStyle", LineHeightStyle.Companion)
    function("LineHeightStyle(alignment, trim)") { a ->
        LineHeightStyle(a.typed("alignment", LineHeightStyle.Alignment.Proportional), a.typed("trim", LineHeightStyle.Trim.Both))
    }
    constants<LineHeightStyle.Companion>(
        "Default" to LineHeightStyle.Default,
        "Alignment" to LineHeightStyle.Alignment.Companion,
        "Trim" to LineHeightStyle.Trim.Companion,
    )
    constants<LineHeightStyle.Alignment.Companion>(
        "Top" to LineHeightStyle.Alignment.Top, "Center" to LineHeightStyle.Alignment.Center,
        "Proportional" to LineHeightStyle.Alignment.Proportional, "Bottom" to LineHeightStyle.Alignment.Bottom,
    )
    constants<LineHeightStyle.Trim.Companion>(
        "FirstLineTop" to LineHeightStyle.Trim.FirstLineTop, "LastLineBottom" to LineHeightStyle.Trim.LastLineBottom,
        "Both" to LineHeightStyle.Trim.Both, "None" to LineHeightStyle.Trim.None,
    )
}

private fun Registry.focusAndPointer() {
    type<FocusRequester>("FocusRequester")
    function("FocusRequester()") { FocusRequester() }
    // Focus changes from a preview's own code would fail before the requester is attached, so they are ignored.
    member<FocusRequester>("requestFocus()") { requester, _ -> runCatching { requester.requestFocus() }.getOrDefault(false) }
    member<FocusRequester>("freeFocus()") { requester, _ -> runCatching { requester.freeFocus() }.getOrDefault(false) }
    member<FocusRequester>("captureFocus()") { requester, _ -> runCatching { requester.captureFocus() }.getOrDefault(false) }
    member<Modifier>("focusRequester(focusRequester)") { m, a ->
        a.typedOrNull<FocusRequester>("focusRequester")?.let { m.focusRequester(it) } ?: m
    }
    member<Modifier>("focusable(enabled, interactionSource)") { m, a ->
        m.focusable(a.boolean("enabled", true), a.typedOrNull<MutableInteractionSource>("interactionSource"))
    }
    keyEvents()

    value("PointerIcon", PointerIcons)
    type<PointerIcon>("PointerIcon")
    constants<PointerIcons>(
        "Default" to PointerIcon.Default, "Hand" to PointerIcon.Hand, "Text" to PointerIcon.Text,
        "Crosshair" to PointerIcon.Crosshair,
    )
    member<Modifier>("pointerHoverIcon(icon, overrideDescendants)") { m, a ->
        a.typedOrNull<PointerIcon>("icon")?.let { m.pointerHoverIcon(it, a.boolean("overrideDescendants", false)) } ?: m
    }
    member<Modifier>("clickable(interactionSource, indication, enabled, onClickLabel, role, onClick)") { m, a ->
        m.clickable(
            interactionSource = a.typedOrNull<MutableInteractionSource>("interactionSource"),
            indication = a.typedOrNull<Indication>("indication"),
            enabled = a.boolean("enabled", true),
            onClickLabel = a.stringOrNull("onClickLabel"),
            role = a.typedOrNull<Role>("role"),
            onClick = a.action("onClick"),
        )
    }
    composableMember<InteractionSource>("collectIsDraggedAsState()") { source, _ -> source.collectIsDraggedAsState() }
}

/** Key events: `onKeyEvent`, `onPreviewKeyEvent`, `KeyEvent.key`, `type`, modifier keys, and common `Key`s. */
private fun Registry.keyEvents() {
    member<Modifier>("onPreviewKeyEvent(onPreviewKeyEvent)") { m, a ->
        val handler = a.function1("onPreviewKeyEvent")
        m.onPreviewKeyEvent { handler(it) == true }
    }
    member<Modifier>("onKeyEvent(onKeyEvent)") { m, a ->
        val handler = a.function1("onKeyEvent")
        m.onKeyEvent { handler(it) == true }
    }
    type<KeyEvent>("KeyEvent")
    properties<KeyEvent>(
        "key" to { it.key },
        "type" to { it.type },
        "isCtrlPressed" to { it.isCtrlPressed },
        "isMetaPressed" to { it.isMetaPressed },
        "isShiftPressed" to { it.isShiftPressed },
        "isAltPressed" to { it.isAltPressed },
        "utf16CodePoint" to { it.utf16CodePoint },
    )
    value("KeyEventType", KeyEventType.Companion)
    constants<KeyEventType.Companion>("KeyDown" to KeyEventType.KeyDown, "KeyUp" to KeyEventType.KeyUp, "Unknown" to KeyEventType.Unknown)
    value("Key", Keys)
    constants<Keys>(
        "A" to Key.A, "B" to Key.B, "C" to Key.C, "D" to Key.D, "E" to Key.E, "F" to Key.F, "G" to Key.G, "H" to
        Key.H, "I" to Key.I, "J" to Key.J, "K" to Key.K, "L" to Key.L, "M" to Key.M, "N" to Key.N, "O" to Key.O, "P"
        to Key.P, "Q" to Key.Q, "R" to Key.R, "S" to Key.S, "T" to Key.T, "U" to Key.U, "V" to Key.V, "W" to Key.W,
        "X" to Key.X, "Y" to Key.Y, "Z" to Key.Z, "Zero" to Key.Zero, "One" to Key.One, "Two" to Key.Two, "Three" to
        Key.Three, "Four" to Key.Four, "Five" to Key.Five, "Six" to Key.Six, "Seven" to Key.Seven, "Eight" to
        Key.Eight, "Nine" to Key.Nine, "Enter" to Key.Enter, "Escape" to Key.Escape, "Tab" to Key.Tab, "Backspace" to
        Key.Backspace, "Delete" to Key.Delete, "Spacebar" to Key.Spacebar, "Insert" to Key.Insert, "DirectionUp" to
        Key.DirectionUp, "DirectionDown" to Key.DirectionDown, "DirectionLeft" to Key.DirectionLeft, "DirectionRight"
        to Key.DirectionRight, "PageUp" to Key.PageUp, "PageDown" to Key.PageDown, "MoveHome" to Key.MoveHome,
        "MoveEnd" to Key.MoveEnd, "ShiftLeft" to Key.ShiftLeft, "ShiftRight" to Key.ShiftRight, "CtrlLeft" to
        Key.CtrlLeft, "CtrlRight" to Key.CtrlRight, "AltLeft" to Key.AltLeft, "AltRight" to Key.AltRight, "MetaLeft"
        to Key.MetaLeft, "MetaRight" to Key.MetaRight, "Comma" to Key.Comma, "Period" to Key.Period, "Slash" to
        Key.Slash, "Minus" to Key.Minus, "Equals" to Key.Equals, "Plus" to Key.Plus, "Semicolon" to Key.Semicolon,
        "Apostrophe" to Key.Apostrophe, "Grave" to Key.Grave, "LeftBracket" to Key.LeftBracket, "RightBracket" to
        Key.RightBracket, "Backslash" to Key.Backslash, "F1" to Key.F1, "F2" to Key.F2, "F3" to Key.F3, "F4" to
        Key.F4, "F5" to Key.F5, "F6" to Key.F6, "F7" to Key.F7, "F8" to Key.F8, "F9" to Key.F9, "F10" to Key.F10,
        "F11" to Key.F11, "F12" to Key.F12
    )
}

private fun Registry.easings() {
    for ((name, easing) in listOf(
        "Ease" to Ease, "EaseIn" to EaseIn, "EaseOut" to EaseOut, "EaseInOut" to EaseInOut,
        "EaseInSine" to EaseInSine, "EaseOutSine" to EaseOutSine, "EaseInOutSine" to EaseInOutSine,
        "EaseInQuad" to EaseInQuad, "EaseOutQuad" to EaseOutQuad, "EaseInOutQuad" to EaseInOutQuad,
        "EaseInCubic" to EaseInCubic, "EaseOutCubic" to EaseOutCubic, "EaseInOutCubic" to EaseInOutCubic,
        "EaseInQuart" to EaseInQuart, "EaseOutQuart" to EaseOutQuart, "EaseInOutQuart" to EaseInOutQuart,
        "EaseInQuint" to EaseInQuint, "EaseOutQuint" to EaseOutQuint, "EaseInOutQuint" to EaseInOutQuint,
        "EaseInExpo" to EaseInExpo, "EaseOutExpo" to EaseOutExpo, "EaseInOutExpo" to EaseInOutExpo,
        "EaseInCirc" to EaseInCirc, "EaseOutCirc" to EaseOutCirc, "EaseInOutCirc" to EaseInOutCirc,
        "EaseInBack" to EaseInBack, "EaseOutBack" to EaseOutBack, "EaseInOutBack" to EaseInOutBack,
        "EaseInElastic" to EaseInElastic, "EaseOutElastic" to EaseOutElastic, "EaseInOutElastic" to EaseInOutElastic,
        "EaseInBounce" to EaseInBounce, "EaseOutBounce" to EaseOutBounce, "EaseInOutBounce" to EaseInOutBounce,
    )) value(name, easing)
}

private fun Registry.layoutExtras() {
    type<DpOffset>("DpOffset")
    function("DpOffset(x, y)") { a -> DpOffset(a.dp("x", 0.dp), a.dp("y", 0.dp)) }
    properties<DpOffset>("x" to { it.x }, "y" to { it.y })
    member<Modifier>("clipToBounds()") { m, _ -> m.clipToBounds() }
    composableMember<WindowInsets>("asPaddingValues()") { insets, _ -> insets.asPaddingValues() }
    member<Modifier>("windowInsetsBottomHeight(insets)") { m, a ->
        a.typedOrNull<WindowInsets>("insets")?.let { m.windowInsetsBottomHeight(it) } ?: m
    }
    member<Modifier>("windowInsetsTopHeight(insets)") { m, a ->
        a.typedOrNull<WindowInsets>("insets")?.let { m.windowInsetsTopHeight(it) } ?: m
    }
    member<LayoutCoordinates>("positionInRoot()") { coordinates, _ -> coordinates.positionInRoot() }
    member<LayoutCoordinates>("positionInWindow()") { coordinates, _ -> coordinates.positionInWindow() }
    member<LayoutCoordinates>("boundsInRoot()") { coordinates, _ -> coordinates.boundsInRoot() }
}

/**
 * `height(IntrinsicSize.Min)` and the other intrinsic sizes, except that content which can't report an intrinsic size
 * (lazy lists, `BoxWithConstraints`, other subcompositions) is measured without one instead of failing the layout.
 * Compose throws in that case, which on a device shows only when the app's own sizes don't settle it first.
 */
internal fun Modifier.intrinsicSize(size: IntrinsicSize, height: Boolean, required: Boolean): Modifier =
    layout { measurable, constraints ->
        val intrinsic = try {
            when {
                height && size == IntrinsicSize.Min -> measurable.minIntrinsicHeight(constraints.maxWidth)
                height -> measurable.maxIntrinsicHeight(constraints.maxWidth)
                size == IntrinsicSize.Min -> measurable.minIntrinsicWidth(constraints.maxHeight)
                else -> measurable.maxIntrinsicWidth(constraints.maxHeight)
            }
        } catch (e: IllegalStateException) {
            null
        }
        val fitted = when {
            intrinsic == null -> constraints
            height -> {
                val value = if (required) intrinsic else intrinsic.coerceIn(constraints.minHeight, constraints.maxHeight)
                constraints.copy(minHeight = value, maxHeight = value)
            }
            else -> {
                val value = if (required) intrinsic else intrinsic.coerceIn(constraints.minWidth, constraints.maxWidth)
                constraints.copy(minWidth = value, maxWidth = value)
            }
        }
        val placeable = measurable.measure(fitted)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
