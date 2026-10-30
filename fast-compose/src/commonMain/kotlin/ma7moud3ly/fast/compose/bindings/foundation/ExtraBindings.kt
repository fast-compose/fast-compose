@file:OptIn(ExperimentalFoundationApi::class)

package ma7moud3ly.fast.compose.bindings.foundation

import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.InterpreterException
import ma7moud3ly.fast.compose.interpreter.Registry
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset

/** CompositionLocal, styled text, gestures, BoxWithConstraints and animated content. */
internal fun Registry.extras() {
    compositionLocals()
    annotatedStrings()
    gestures()
    constraints()
    transitions()
}

@Suppress("UNCHECKED_CAST")
private fun Registry.compositionLocals() {
    type<CompositionLocal<*>>("CompositionLocal")
    type<ProvidedValue<*>>("ProvidedValue")
    function("compositionLocalOf(defaultFactory)") { a ->
        val default = a.calculation("defaultFactory")
        compositionLocalOf { default() }
    }
    function("staticCompositionLocalOf(defaultFactory)") { a ->
        val default = a.calculation("defaultFactory")
        staticCompositionLocalOf { default() }
    }
    member<ProvidableCompositionLocal<*>>("provides(value)") { local, a -> (local as ProvidableCompositionLocal<Any?>) provides a["value"] }
    composableMember<CompositionLocal<*>>("current") { local, _ -> local.current }
    composable("CompositionLocalProvider(vararg values, content)") { a ->
        ProvideLocals(a.varargs("values").filterIsInstance<ProvidedValue<*>>(), a.content())
    }
    value("LocalDensity", LocalDensity)
    value("LocalLayoutDirection", LocalLayoutDirection)
}

/** Calls `CompositionLocalProvider` from a named function, like `Keyed`, to keep the vararg spread out of registry lambdas. */
@Composable
private fun ProvideLocals(values: List<ProvidedValue<*>>, content: @Composable () -> Unit) {
    CompositionLocalProvider(*values.toTypedArray(), content = content)
}

private fun Registry.annotatedStrings() {
    type<AnnotatedString>("AnnotatedString")
    type<SpanStyle>("SpanStyle")
    type<ParagraphStyle>("ParagraphStyle")
    function("AnnotatedString(text)") { a -> AnnotatedString(a.string("text")) }
    function("buildAnnotatedString(builder)") { a ->
        val builder = a.scopedAction<AnnotatedString.Builder>("builder")
        buildAnnotatedString { builder() }
    }
    member<AnnotatedString.Builder>("append(text)") { builder, a ->
        when (val text = a["text"]) {
            is AnnotatedString -> builder.append(text)
            is Char -> builder.append(text)
            else -> builder.append(a.string("text"))
        }
        Unit
    }
    member<AnnotatedString.Builder>("withStyle(style, block)") { builder, a ->
        val block = a.scopedCalculation<AnnotatedString.Builder>("block")
        when (val style = a["style"]) {
            is SpanStyle -> builder.withStyle(style) { block() ?: Unit }
            is ParagraphStyle -> builder.withStyle(style) { block() ?: Unit }
            else -> throw InterpreterException("withStyle needs a SpanStyle or ParagraphStyle")
        }
    }
    member<AnnotatedString.Builder>("pushStyle(style)") { builder, a ->
        val style = a.typedOrNull<SpanStyle>("style")
            ?: throw InterpreterException("pushStyle needs a SpanStyle")
        builder.pushStyle(style)
    }
    member<AnnotatedString.Builder>("pop") { builder, _ -> builder.pop() }
    properties<AnnotatedString.Builder>("length" to { it.length })
    properties<AnnotatedString>("text" to { it.text }, "length" to { it.length })
    member<AnnotatedString>("plus(other)") { text, a ->
        text + (a.typedOrNull<AnnotatedString>("other") ?: AnnotatedString(a.string("other")))
    }
    function("SpanStyle(color, fontSize, fontWeight, fontStyle, fontSynthesis, fontFamily, fontFeatureSettings, letterSpacing, baselineShift, textGeometricTransform, localeList, background, textDecoration)") { a ->
        SpanStyle(
            color = a.color("color"),
            fontSize = a.sp("fontSize"),
            fontWeight = a.typedOrNull<FontWeight>("fontWeight"),
            fontStyle = a.typedOrNull<FontStyle>("fontStyle"),
            fontSynthesis = a.typedOrNull<FontSynthesis>("fontSynthesis"),
            fontFamily = a.typedOrNull<FontFamily>("fontFamily"),
            fontFeatureSettings = a.stringOrNull("fontFeatureSettings"),
            letterSpacing = a.sp("letterSpacing"),
            baselineShift = a.typedOrNull<BaselineShift>("baselineShift"),
            textGeometricTransform = a.typedOrNull<TextGeometricTransform>("textGeometricTransform"),
            localeList = a.typedOrNull<LocaleList>("localeList"),
            background = a.color("background"),
            textDecoration = a.typedOrNull<TextDecoration>("textDecoration"),
        )
    }
    function("ParagraphStyle(textAlign, textDirection, lineHeight)") { a ->
        ParagraphStyle(
            textAlign = a.typed("textAlign", TextAlign.Unspecified),
            textDirection = a.typed("textDirection", TextDirection.Unspecified),
            lineHeight = a.sp("lineHeight"),
        )
    }
}

/** Marker for `Orientation.Horizontal` and `Orientation.Vertical` (an enum without a companion). */
private object Orientations

private fun Registry.gestures() {
    type<PointerInputScope>("PointerInputScope")
    type<PointerInputChange>("PointerInputChange")
    member<Modifier>("pointerInput(vararg keys, block)") { m, a ->
        val block = a.suspendBlock<PointerInputScope>()
        m.pointerInput(a.varargs("keys")) { block() }
    }
    suspendMember<PointerInputScope>("detectTapGestures(onDoubleTap, onLongPress, onPress, onTap)") { scope, a ->
        scope.detectTapGestures(
            onDoubleTap = a.action1OrNull("onDoubleTap"),
            onLongPress = a.action1OrNull("onLongPress"),
            onTap = a.action1OrNull("onTap"),
        )
        Unit
    }
    suspendMember<PointerInputScope>("detectDragGestures(onDragStart, onDragEnd, onDragCancel, onDrag)") { scope, a ->
        scope.detectDragGestures(
            onDragStart = a.action1("onDragStart"),
            onDragEnd = a.action("onDragEnd"),
            onDragCancel = a.action("onDragCancel"),
            onDrag = a.action2("onDrag"),
        )
        Unit
    }
    suspendMember<PointerInputScope>("detectHorizontalDragGestures(onDragStart, onDragEnd, onDragCancel, onHorizontalDrag)") { scope, a ->
        scope.detectHorizontalDragGestures(
            onDragStart = a.action1("onDragStart"),
            onDragEnd = a.action("onDragEnd"),
            onDragCancel = a.action("onDragCancel"),
            onHorizontalDrag = a.action2("onHorizontalDrag"),
        )
        Unit
    }
    suspendMember<PointerInputScope>("detectVerticalDragGestures(onDragStart, onDragEnd, onDragCancel, onVerticalDrag)") { scope, a ->
        scope.detectVerticalDragGestures(
            onDragStart = a.action1("onDragStart"),
            onDragEnd = a.action("onDragEnd"),
            onDragCancel = a.action("onDragCancel"),
            onVerticalDrag = a.action2("onVerticalDrag"),
        )
        Unit
    }
    properties<PointerInputChange>("position" to { it.position }, "pressed" to { it.pressed })
    member<PointerInputChange>("consume") { change, _ -> change.consume() }

    value("Orientation", Orientations)
    constants<Orientations>(
        "Horizontal" to Orientation.Horizontal,
        "Vertical" to Orientation.Vertical
    )
    composable("rememberDraggableState(onDelta)") { a -> rememberDraggableState(a.action1("onDelta")) }
    member<Modifier>("draggable(state, orientation, enabled)") { m, a ->
        m.draggable(
            state = a.typedOrNull<DraggableState>("state")
                ?: throw InterpreterException("draggable needs state = rememberDraggableState { … }"),
            orientation = a.typed("orientation", Orientation.Horizontal),
            enabled = a.boolean("enabled", true),
        )
    }
    member<Modifier>("combinedClickable(enabled, onClickLabel, role, onLongClickLabel, onLongClick, onDoubleClick, onClick)") { m, a ->
        m.combinedClickable(
            enabled = a.boolean("enabled", true),
            onClickLabel = a.stringOrNull("onClickLabel"),
            role = a.typedOrNull<Role>("role"),
            onLongClickLabel = a.stringOrNull("onLongClickLabel"),
            onLongClick = a.actionOrNull("onLongClick"),
            onDoubleClick = a.actionOrNull("onDoubleClick"),
            onClick = a.action("onClick"),
        )
    }

    type<IntOffset>("IntOffset")
    function("IntOffset(x, y)") { a -> IntOffset(a.int("x", 0), a.int("y", 0)) }
    value("IntOffset", IntOffset.Companion)
    constants<IntOffset.Companion>("Zero" to IntOffset.Zero)
    properties<IntOffset>("x" to { it.x }, "y" to { it.y })
}

private fun Registry.constraints() {
    type<BoxWithConstraintsScope>("BoxWithConstraintsScope")
    composable("BoxWithConstraints(modifier, contentAlignment, content)") { a ->
        val content = a.scopedContent<BoxWithConstraintsScope>()
        BoxWithConstraints(
            modifier = a.modifier(),
            contentAlignment = a.typed<Alignment>("contentAlignment", Alignment.TopStart),
        ) { content() }
    }
    properties<BoxWithConstraintsScope>(
        "maxWidth" to { it.maxWidth },
        "maxHeight" to { it.maxHeight },
        "minWidth" to { it.minWidth },
        "minHeight" to { it.minHeight },
        "constraints" to { it.constraints },
    )
}

/** `animationSpec` argument at [index], or null for the transition's default. */
@Suppress("UNCHECKED_CAST")
private fun <T> Args.finiteSpec(): FiniteAnimationSpec<T>? =
    typedOrNull<FiniteAnimationSpec<*>>("animationSpec") as FiniteAnimationSpec<T>?

/** Offset lambda such as `initialOffsetY = { it / 2 }`, called with the full size in pixels. */
private fun Args.offsetLambda(name: String): ((Int) -> Int)? {
    if (lambda(name) == null) return null
    val fn = function1(name)
    return { size -> (fn(size) as? Number)?.toInt() ?: 0 }
}

private fun Registry.transitions() {
    type<EnterTransition>("EnterTransition")
    type<ExitTransition>("ExitTransition")
    type<ContentTransform>("ContentTransform")
    function("fadeIn(animationSpec, initialAlpha)") { a ->
        a.finiteSpec<Float>()?.let { fadeIn(it, a.float("initialAlpha", 0f)) } ?: fadeIn(
            initialAlpha = a.float("initialAlpha", 0f)
        )
    }
    function("fadeOut(animationSpec, targetAlpha)") { a ->
        a.finiteSpec<Float>()?.let { fadeOut(it, a.float("targetAlpha", 0f)) } ?: fadeOut(
            targetAlpha = a.float("targetAlpha", 0f)
        )
    }
    function("slideInVertically(animationSpec, initialOffsetY)") { a ->
        val offset = a.offsetLambda("initialOffsetY") ?: { size: Int -> -size / 2 }
        a.finiteSpec<IntOffset>()?.let { slideInVertically(it, offset) } ?: slideInVertically(
            initialOffsetY = offset
        )
    }
    function("slideOutVertically(animationSpec, targetOffsetY)") { a ->
        val offset = a.offsetLambda("targetOffsetY") ?: { size: Int -> -size / 2 }
        a.finiteSpec<IntOffset>()?.let { slideOutVertically(it, offset) } ?: slideOutVertically(
            targetOffsetY = offset
        )
    }
    function("slideInHorizontally(animationSpec, initialOffsetX)") { a ->
        val offset = a.offsetLambda("initialOffsetX") ?: { size: Int -> -size / 2 }
        a.finiteSpec<IntOffset>()?.let { slideInHorizontally(it, offset) } ?: slideInHorizontally(
            initialOffsetX = offset
        )
    }
    function("slideOutHorizontally(animationSpec, targetOffsetX)") { a ->
        val offset = a.offsetLambda("targetOffsetX") ?: { size: Int -> -size / 2 }
        a.finiteSpec<IntOffset>()?.let { slideOutHorizontally(it, offset) } ?: slideOutHorizontally(
            targetOffsetX = offset
        )
    }
    function("expandVertically") { _ -> expandVertically() }
    function("shrinkVertically") { _ -> shrinkVertically() }
    function("expandHorizontally") { _ -> expandHorizontally() }
    function("shrinkHorizontally") { _ -> shrinkHorizontally() }
    function("expandIn") { _ -> expandIn() }
    function("shrinkOut") { _ -> shrinkOut() }
    function("scaleIn(animationSpec, initialScale)") { a ->
        val initialScale = a.float("initialScale", 0f)
        a.finiteSpec<Float>()?.let { scaleIn(it, initialScale) }
            ?: scaleIn(initialScale = initialScale)
    }
    function("scaleOut(animationSpec, targetScale)") { a ->
        val targetScale = a.float("targetScale", 0f)
        a.finiteSpec<Float>()?.let { scaleOut(it, targetScale) }
            ?: scaleOut(targetScale = targetScale)
    }
    member<EnterTransition>("plus(enter)") { enter, a ->
        enter + (a.typedOrNull<EnterTransition>("enter")
            ?: throw InterpreterException("Expected an EnterTransition"))
    }
    member<ExitTransition>("plus(exit)") { exit, a ->
        exit + (a.typedOrNull<ExitTransition>("exit")
            ?: throw InterpreterException("Expected an ExitTransition"))
    }
    member<EnterTransition>("togetherWith(exit)") { enter, a ->
        enter togetherWith (a.typedOrNull<ExitTransition>("exit")
            ?: throw InterpreterException("Expected an ExitTransition"))
    }

    composable("AnimatedContent(targetState, modifier, transitionSpec, contentAlignment, label, contentKey, content)") { a ->
        val content = a.scopedContent1<AnimatedContentScope>()
        val transition = if (a.lambda("transitionSpec") == null) null
        else a.scopedCalculation<AnimatedContentTransitionScope<Any?>>("transitionSpec")
        AnimatedContent(
            targetState = a["targetState"],
            modifier = a.modifier(),
            transitionSpec = {
                transition?.invoke(this) as? ContentTransform
                    ?: ((fadeIn(tween(220, delayMillis = 90)) + scaleIn(
                        tween(
                            220,
                            delayMillis = 90
                        ), initialScale = 0.92f
                    )) togetherWith fadeOut(tween(90)))
            },
            contentAlignment = a.typed("contentAlignment", Alignment.TopStart),
            label = a.string("label", "AnimatedContent"),
            contentKey = if (a.lambda("contentKey") == null) ({ it }) else a.function1("contentKey"),
        ) { state -> content(state) }
    }
    composable("Crossfade(targetState, modifier, animationSpec, label, content)") { a ->
        val content = a.content1()
        Crossfade(
            targetState = a["targetState"],
            modifier = a.modifier(),
            animationSpec = a.finiteSpec<Float>() ?: tween(),
            label = a.string("label", "Crossfade"),
        ) { state -> content(state) }
    }
    member<Modifier>("animateContentSize") { m, _ -> m.animateContentSize() }
}
