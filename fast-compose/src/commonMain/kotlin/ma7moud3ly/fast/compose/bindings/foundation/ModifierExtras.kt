@file:OptIn(ExperimentalFoundationApi::class)

package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Indication
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.Registry

/**
 * More modifiers: size limits, scope modifiers (`matchParentSize`, `fillParentMaxWidth`, `animateItem`), graphics
 * layers, selection and toggling, semantics, window insets, drawing with content or a cache, size and position
 * callbacks, scrolling and interaction. Also, sticky list headers and grid state.
 */
internal fun Registry.modifierExtras() {
    sizeModifiers()
    graphicsModifiers()
    selectionModifiers()
    windowInsets()
    drawModifiers()
    layoutCallbacks()
    interaction()
    lazyExtras()
}

private fun Registry.sizeModifiers() {
    member<Modifier>("heightIn(min, max)") { m, a -> m.heightIn(a.dp("min"), a.dp("max")) }
    member<Modifier>("widthIn(min, max)") { m, a -> m.widthIn(a.dp("min"), a.dp("max")) }
    member<Modifier>("sizeIn(minWidth, minHeight, maxWidth, maxHeight)") { m, a ->
        m.sizeIn(a.dp("minWidth"), a.dp("minHeight"), a.dp("maxWidth"), a.dp("maxHeight"))
    }
    member<Modifier>("requiredWidth(width)") { m, a -> m.requiredWidth(a.dp("width")) }
    member<Modifier>("requiredHeight(height)") { m, a -> m.requiredHeight(a.dp("height")) }
    member<Modifier>("absoluteOffset(x: Dp, y: Dp)") { m, a -> m.absoluteOffset(a.dp("x", 0.dp), a.dp("y", 0.dp)) }
    member<Modifier>("absoluteOffset(offset: Density.() -> IntOffset)") { m, a ->
        val position = a.scopedCalculation<Density>("offset")
        m.absoluteOffset { position() as? IntOffset ?: IntOffset.Zero }
    }
    member<Modifier>("matchParentSize") { m, a ->
        val scope = a.receiver<BoxScope>()
        if (scope == null) m.also { a.report("matchParentSize() must be used inside Box") } else with(scope) { m.matchParentSize() }
    }
    member<Modifier>("fillParentMaxWidth(fraction)") { m, a ->
        val scope = a.receiver<LazyItemScope>()
        if (scope == null) m.also { a.report("fillParentMaxWidth() must be used inside a lazy list item") }
        else with(scope) { m.fillParentMaxWidth(a.float("fraction", 1f)) }
    }
    member<Modifier>("fillParentMaxHeight(fraction)") { m, a ->
        val scope = a.receiver<LazyItemScope>()
        if (scope == null) m.also { a.report("fillParentMaxHeight() must be used inside a lazy list item") }
        else with(scope) { m.fillParentMaxHeight(a.float("fraction", 1f)) }
    }
}

private fun Registry.graphicsModifiers() {
    // The lambda overload sets layer properties, e.g. `graphicsLayer { scaleX = 1.2f; alpha = 0.5f }`.
    member<Modifier>("graphicsLayer(block: GraphicsLayerScope.() -> Unit)") { m, a ->
        val block = a.scopedAction<GraphicsLayerScope>("block")
        m.graphicsLayer { block() }
    }
    member<Modifier>(
        "graphicsLayer(scaleX, scaleY, alpha, translationX, translationY, shadowElevation, rotationX, rotationY, rotationZ, " +
            "cameraDistance, transformOrigin, shape, clip)",
    ) { m, a ->
        m.graphicsLayer(
            scaleX = a.float("scaleX", 1f),
            scaleY = a.float("scaleY", 1f),
            alpha = a.float("alpha", 1f),
            translationX = a.float("translationX", 0f),
            translationY = a.float("translationY", 0f),
            shadowElevation = a.float("shadowElevation", 0f),
            rotationX = a.float("rotationX", 0f),
            rotationY = a.float("rotationY", 0f),
            rotationZ = a.float("rotationZ", 0f),
            cameraDistance = a.float("cameraDistance", 8f),
            transformOrigin = a.typed("transformOrigin", TransformOrigin.Center),
            shape = a.typed<Shape>("shape", RectangleShape),
            clip = a.boolean("clip", false),
        )
    }
    type<GraphicsLayerScope>("GraphicsLayerScope")
    val floats = listOf<Pair<String, (GraphicsLayerScope) -> Float>>(
        "scaleX" to { it.scaleX }, "scaleY" to { it.scaleY }, "alpha" to { it.alpha },
        "translationX" to { it.translationX }, "translationY" to { it.translationY }, "shadowElevation" to { it.shadowElevation },
        "rotationX" to { it.rotationX }, "rotationY" to { it.rotationY }, "rotationZ" to { it.rotationZ },
        "cameraDistance" to { it.cameraDistance },
    )
    properties<GraphicsLayerScope>(*floats.toTypedArray(), "clip" to { it.clip }, "shape" to { it.shape }, "size" to { it.size })
    setter<GraphicsLayerScope>("scaleX") { s, v -> s.scaleX = v.toFloat(s.scaleX) }
    setter<GraphicsLayerScope>("scaleY") { s, v -> s.scaleY = v.toFloat(s.scaleY) }
    setter<GraphicsLayerScope>("alpha") { s, v -> s.alpha = v.toFloat(s.alpha) }
    setter<GraphicsLayerScope>("translationX") { s, v -> s.translationX = v.toFloat(s.translationX) }
    setter<GraphicsLayerScope>("translationY") { s, v -> s.translationY = v.toFloat(s.translationY) }
    setter<GraphicsLayerScope>("shadowElevation") { s, v -> s.shadowElevation = v.toFloat(s.shadowElevation) }
    setter<GraphicsLayerScope>("rotationX") { s, v -> s.rotationX = v.toFloat(s.rotationX) }
    setter<GraphicsLayerScope>("rotationY") { s, v -> s.rotationY = v.toFloat(s.rotationY) }
    setter<GraphicsLayerScope>("rotationZ") { s, v -> s.rotationZ = v.toFloat(s.rotationZ) }
    setter<GraphicsLayerScope>("cameraDistance") { s, v -> s.cameraDistance = v.toFloat(s.cameraDistance) }
    setter<GraphicsLayerScope>("clip") { s, v -> s.clip = v == true }
    setter<GraphicsLayerScope>("shape") { s, v -> (v as? Shape)?.let { s.shape = it } }
    setter<GraphicsLayerScope>("transformOrigin") { s, v -> (v as? TransformOrigin)?.let { s.transformOrigin = it } }
    type<TransformOrigin>("TransformOrigin")
    function("TransformOrigin(pivotFractionX, pivotFractionY)") { a ->
        TransformOrigin(a.float("pivotFractionX", 0.5f), a.float("pivotFractionY", 0.5f))
    }
    value("TransformOrigin", TransformOrigin.Companion)
    constants<TransformOrigin.Companion>("Center" to TransformOrigin.Center)
    member<Modifier>("blur(radius: Dp)") { m, a -> m.blur(a.dp("radius", 0.dp)) }
    member<Modifier>("blur(radiusX, radiusY)") { m, a -> m.blur(a.dp("radiusX", 0.dp), a.dp("radiusY", 0.dp)) }
    member<Modifier>("paint(painter, sizeToIntrinsics, alignment, contentScale, alpha, colorFilter)") { m, a ->
        val painter = a.typedOrNull<Painter>("painter")
        if (painter == null) {
            m.also { a.report("paint() needs a Painter, e.g. painterResource(R.drawable.logo)") }
        } else {
            m.paint(
                painter = painter,
                sizeToIntrinsics = a.boolean("sizeToIntrinsics", true),
                alignment = a.typed<Alignment>("alignment", Alignment.Center),
                contentScale = a.typed<ContentScale>("contentScale", ContentScale.Inside),
                alpha = a.float("alpha", DefaultAlpha),
                colorFilter = a.typedOrNull<ColorFilter>("colorFilter"),
            )
        }
    }
}

private fun Registry.selectionModifiers() {
    member<Modifier>("selectable(selected, enabled, role, onClick)") { m, a ->
        m.selectable(
            selected = a.boolean("selected", false),
            enabled = a.boolean("enabled", true),
            role = a.typedOrNull<Role>("role"),
            onClick = a.action("onClick"),
        )
    }
    member<Modifier>("toggleable(value, enabled, role, onValueChange)") { m, a ->
        val onValueChange = a.action1("onValueChange")
        m.toggleable(
            value = a.boolean("value", false),
            enabled = a.boolean("enabled", true),
            role = a.typedOrNull<Role>("role"),
            onValueChange = { value: Boolean -> onValueChange(value) },
        )
    }
    member<Modifier>("selectableGroup") { m, _ -> m.selectableGroup() }
    type<Role>("Role")
    value("Role", Role.Companion)
    constants<Role.Companion>(
        "Button" to Role.Button,
        "Checkbox" to Role.Checkbox,
        "Switch" to Role.Switch,
        "RadioButton" to Role.RadioButton,
        "Tab" to Role.Tab,
        "Image" to Role.Image,
        "DropdownList" to Role.DropdownList,
    )
    member<Modifier>("testTag(tag)") { m, a -> m.testTag(a.string("tag")) }
    // Semantics only affect accessibility and tests, so their properties block is not run in previews.
    member<Modifier>("semantics(mergeDescendants, properties)") { m, a -> m.semantics(mergeDescendants = a.boolean("mergeDescendants", false)) {} }
    member<Modifier>("clearAndSetSemantics(properties)") { m, _ -> m.clearAndSetSemantics {} }
}

private fun Registry.windowInsets() {
    type<WindowInsets>("WindowInsets")
    function("WindowInsets(left, top, right, bottom)") { a ->
        WindowInsets(left = a.dp("left", 0.dp), top = a.dp("top", 0.dp), right = a.dp("right", 0.dp), bottom = a.dp("bottom", 0.dp))
    }
    value("WindowInsets", WindowInsets.Companion)
    composableMember<WindowInsets.Companion>("systemBars") { _, _ -> WindowInsets.systemBars }
    composableMember<WindowInsets.Companion>("statusBars") { _, _ -> WindowInsets.statusBars }
    composableMember<WindowInsets.Companion>("navigationBars") { _, _ -> WindowInsets.navigationBars }
    composableMember<WindowInsets.Companion>("safeDrawing") { _, _ -> WindowInsets.safeDrawing }
    composableMember<WindowInsets.Companion>("safeContent") { _, _ -> WindowInsets.safeContent }
    composableMember<WindowInsets.Companion>("ime") { _, _ -> WindowInsets.ime }
    member<Modifier>("windowInsetsPadding(insets)") { m, a -> a.typedOrNull<WindowInsets>("insets")?.let { m.windowInsetsPadding(it) } ?: m }
    member<Modifier>("statusBarsPadding") { m, _ -> m.statusBarsPadding() }
    member<Modifier>("navigationBarsPadding") { m, _ -> m.navigationBarsPadding() }
    member<Modifier>("systemBarsPadding") { m, _ -> m.systemBarsPadding() }
    member<Modifier>("safeDrawingPadding") { m, _ -> m.safeDrawingPadding() }
    member<Modifier>("safeContentPadding") { m, _ -> m.safeContentPadding() }
    member<Modifier>("imePadding") { m, _ -> m.imePadding() }
    member<Modifier>("nestedScroll(connection)") { m, a -> a.typedOrNull<NestedScrollConnection>("connection")?.let { m.nestedScroll(it) } ?: m }
}

private fun Registry.drawModifiers() {
    type<ContentDrawScope>("ContentDrawScope")
    member<Modifier>("drawWithContent(onDraw)") { m, a ->
        val draw = a.scopedAction<ContentDrawScope>("onDraw")
        m.drawWithContent { draw() }
    }
    member<ContentDrawScope>("drawContent") { scope, _ -> scope.drawContent() }
    type<CacheDrawScope>("CacheDrawScope")
    member<Modifier>("drawWithCache(onBuildDrawCache)") { m, a ->
        val build = a.scopedCalculation<CacheDrawScope>("onBuildDrawCache")
        m.drawWithCache { build() as? DrawResult ?: onDrawBehind {} }
    }
    member<CacheDrawScope>("onDrawBehind(block)") { scope, a ->
        val block = a.scopedAction<DrawScope>("block")
        scope.onDrawBehind { block() }
    }
    member<CacheDrawScope>("onDrawWithContent(block)") { scope, a ->
        val block = a.scopedAction<ContentDrawScope>("block")
        scope.onDrawWithContent { block() }
    }
    properties<CacheDrawScope>("size" to { it.size })
}

private fun Registry.layoutCallbacks() {
    type<IntSize>("IntSize")
    properties<IntSize>("width" to { it.width }, "height" to { it.height })
    type<LayoutCoordinates>("LayoutCoordinates")
    properties<LayoutCoordinates>("size" to { it.size }, "isAttached" to { it.isAttached })
    member<Modifier>("onSizeChanged(onSizeChanged)") { m, a ->
        val callback = a.action1("onSizeChanged")
        m.onSizeChanged { callback(it) }
    }
    member<Modifier>("onGloballyPositioned(onGloballyPositioned)") { m, a ->
        val callback = a.action1("onGloballyPositioned")
        m.onGloballyPositioned { callback(it) }
    }
}

private fun Registry.interaction() {
    type<ScrollableState>("ScrollableState")
    composable("rememberScrollableState(consumeScrollDelta)") { a ->
        val consume = a.function1("consumeScrollDelta")
        rememberScrollableState { delta -> (consume(delta) as? Number)?.toFloat() ?: 0f }
    }
    member<Modifier>("scrollable(state, orientation, enabled, reverseDirection)") { m, a ->
        val state = a.typedOrNull<ScrollableState>("state")
        if (state == null) {
            m.also { a.report("scrollable() needs a ScrollableState, e.g. rememberScrollableState { it }") }
        } else {
            m.scrollable(
                state = state,
                orientation = a.typed("orientation", Orientation.Vertical),
                enabled = a.boolean("enabled", true),
                reverseDirection = a.boolean("reverseDirection", false),
            )
        }
    }
    type<InteractionSource>("InteractionSource")
    function("MutableInteractionSource()") { MutableInteractionSource() }
    composableMember<InteractionSource>("collectIsPressedAsState()") { source, _ -> source.collectIsPressedAsState() }
    composableMember<InteractionSource>("collectIsHoveredAsState()") { source, _ -> source.collectIsHoveredAsState() }
    composableMember<InteractionSource>("collectIsFocusedAsState()") { source, _ -> source.collectIsFocusedAsState() }
    member<Modifier>("hoverable(interactionSource, enabled)") { m, a ->
        a.typedOrNull<MutableInteractionSource>("interactionSource")?.let { m.hoverable(it, a.boolean("enabled", true)) } ?: m
    }
    member<Modifier>("indication(interactionSource, indication)") { m, a ->
        a.typedOrNull<InteractionSource>("interactionSource")?.let { m.indication(it, a.typedOrNull<Indication>("indication")) } ?: m
    }
}

private fun Registry.lazyExtras() {
    composable("rememberLazyGridState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset)") { a ->
        rememberLazyGridState(a.int("initialFirstVisibleItemIndex", 0), a.int("initialFirstVisibleItemScrollOffset", 0))
    }
    member<LazyListScope>("stickyHeader(key, contentType, content)") { scope, a ->
        val content = a.scopedContent1<LazyItemScope>()
        scope.stickyHeader(key = a["key"]?.let(::lazyKey), contentType = a["contentType"]) { index -> content(index) }
    }
    member<Modifier>("animateItem(fadeInSpec, placementSpec, fadeOutSpec)") { m, a ->
        val scope = a.receiver<LazyItemScope>()
        if (scope == null) {
            m.also { a.report("animateItem() must be used inside a lazy list item") }
        } else {
            with(scope) {
                m.animateItem(
                    fadeInSpec = a.finiteSpecOr("fadeInSpec", spring(stiffness = Spring.StiffnessMediumLow)),
                    placementSpec = a.finiteSpecOr("placementSpec", spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)),
                    fadeOutSpec = a.finiteSpecOr("fadeOutSpec", spring(stiffness = Spring.StiffnessMediumLow)),
                )
            }
        }
    }
}

/** The animation spec passed as [name] (null included), or [default] when it was not passed. */
@Suppress("UNCHECKED_CAST")
private fun <T> Args.finiteSpecOr(name: String, default: FiniteAnimationSpec<T>): FiniteAnimationSpec<T>? =
    if (has(name)) typedOrNull<FiniteAnimationSpec<*>>(name) as FiniteAnimationSpec<T>? else default

private fun Any?.toFloat(fallback: Float): Float = (this as? Number)?.toFloat() ?: fallback
