package ma7moud3ly.fast.compose.bindings.foundation
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.BindingPack
import ma7moud3ly.fast.compose.interpreter.InterpreterException
import ma7moud3ly.fast.compose.interpreter.Registry
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * Foundation and UI bindings: layouts, `Text`, state, modifiers, values, drawing, effects, gestures, animation, grids,
 * pagers, text fields, flow and custom layouts, dialogs, popups and images.
 * Needs no Material library.
 */
public object FoundationBindings : BindingPack {
    override fun install(registry: Registry): Unit = with(registry) {
        types()
        layouts()
        basicText()
        state()
        stateExtras()
        modifiers()
        modifierExtras()
        values()
        drawing()
        effects()
        extras()
        graphics()
        animation()
        motion()
        grids()
        textInput()
        pagers()
        layoutBindings()
        windows()
        images()
    }
}

private fun Registry.types() {
    type<Modifier>("Modifier")
    type<Dp>("Dp")
    type<TextUnit>("TextUnit")
    type<Color>("Color")
    type<Shape>("Shape")
    type<FontWeight>("FontWeight")
    type<TextStyle>("TextStyle")
    type<TextAlign>("TextAlign")
    type<PaddingValues>("PaddingValues")
    type<RowScope>("RowScope")
    type<ColumnScope>("ColumnScope")
    type<BoxScope>("BoxScope")
    type<LazyListScope>("LazyListScope")
    type<LazyItemScope>("LazyItemScope")
}

private fun Registry.layouts() {
    composable("Column(modifier, verticalArrangement, horizontalAlignment, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        Column(
            modifier = a.modifier(),
            verticalArrangement = a.typed<Arrangement.Vertical>("verticalArrangement", Arrangement.Top),
            horizontalAlignment = a.typed<Alignment.Horizontal>("horizontalAlignment", Alignment.Start),
        ) { content() }
    }
    composable("Row(modifier, horizontalArrangement, verticalAlignment, content)") { a ->
        val content = a.scopedContent<RowScope>()
        Row(
            modifier = a.modifier(),
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", Arrangement.Start),
            verticalAlignment = a.typed<Alignment.Vertical>("verticalAlignment", Alignment.Top),
        ) { content() }
    }
    composable("Box(modifier, contentAlignment, propagateMinConstraints, content)") { a ->
        val content = a.scopedContent<BoxScope>()
        Box(
            modifier = a.modifier(),
            contentAlignment = a.typed<Alignment>("contentAlignment", Alignment.TopStart),
            propagateMinConstraints = a.boolean("propagateMinConstraints", false),
        ) { content() }
    }
    composable("Spacer(modifier)") { a -> Spacer(a.modifier()) }
    composable("LazyColumn(modifier, state, contentPadding, reverseLayout, verticalArrangement, horizontalAlignment, userScrollEnabled, content)") { a ->
        val content = a.scopedAction<LazyListScope>()
        val reverseLayout = a.boolean("reverseLayout", false)
        LazyColumn(
            modifier = a.modifier(),
            state = a.typed("state", rememberLazyListState()),
            contentPadding = a.typed<PaddingValues>("contentPadding", PaddingValues(0.dp)),
            reverseLayout = reverseLayout,
            verticalArrangement = a.typed<Arrangement.Vertical>("verticalArrangement", if (reverseLayout) Arrangement.Bottom else Arrangement.Top),
            horizontalAlignment = a.typed<Alignment.Horizontal>("horizontalAlignment", Alignment.Start),
            userScrollEnabled = a.boolean("userScrollEnabled", true),
        ) { content() }
    }
    composable("LazyRow(modifier, state, contentPadding, reverseLayout, horizontalArrangement, verticalAlignment, userScrollEnabled, content)") { a ->
        val content = a.scopedAction<LazyListScope>()
        val reverseLayout = a.boolean("reverseLayout", false)
        LazyRow(
            modifier = a.modifier(),
            state = a.typed("state", rememberLazyListState()),
            contentPadding = a.typed<PaddingValues>("contentPadding", PaddingValues(0.dp)),
            reverseLayout = reverseLayout,
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", if (reverseLayout) Arrangement.End else Arrangement.Start),
            verticalAlignment = a.typed<Alignment.Vertical>("verticalAlignment", Alignment.Top),
            userScrollEnabled = a.boolean("userScrollEnabled", true),
        ) { content() }
    }
    member<LazyListScope>("item(key, contentType, content)") { scope, a ->
        val content = a.scopedContent<LazyItemScope>()
        scope.item(key = a["key"]?.let(::lazyKey), contentType = a["contentType"]) { content() }
    }
    member<LazyListScope>("items(items, key, contentType, itemContent, *, count)") { scope, a ->
        val content = a.scopedContent1<LazyItemScope>("itemContent")
        when (val items = a["items"] ?: a["count"]) {
            is Int -> scope.items(items, a.itemFunction("key", null)?.keys(), a.itemFunction("contentType", null) ?: { null }) { content(it) }
            is List<*> -> scope.items(items.size, a.itemFunction("key", items)?.keys(), a.itemFunction("contentType", items) ?: { null }) { content(items[it]) }
            else -> a.report("items needs a count or a list")
        }
    }
    composable("key(vararg keys, block)") { a -> Keyed(a.varargs("keys"), a.content("block")) }
}

/** Default text color of [basicText]; previews provide white for dark frames. */
public val LocalBasicTextColor: ProvidableCompositionLocal<Color> = staticCompositionLocalOf { Color.Black }

/** `Text` drawn with foundation's `BasicText`, for registries without Material. */
private fun Registry.basicText() {
    composable(TEXT_SIGNATURE) { a ->
        val merged = a.typed("style", TextStyle.Default).merge(a.textStyle())
        val style = if (merged.color == Color.Unspecified) merged.copy(color = LocalBasicTextColor.current) else merged
        val overflow = a.typed("overflow", TextOverflow.Clip)
        val softWrap = a.boolean("softWrap", true)
        val maxLines = a.int("maxLines", Int.MAX_VALUE).coerceAtLeast(1)
        val minLines = a.int("minLines", 1).coerceAtLeast(1)
        val annotated = a["text"] as? AnnotatedString
        if (annotated != null) {
            BasicText(annotated, a.modifier(), style, a.action1OrNull("onTextLayout"), overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines)
        } else {
            BasicText(a.string("text"), a.modifier(), style, a.action1OrNull("onTextLayout"), overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines)
        }
    }
}

/** Signature shared by the basic and Material `Text`, in the order of Compose's `Text` parameters. */
public const val TEXT_SIGNATURE: String = "Text(text, modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, " +
    "textDecoration, textAlign, lineHeight, overflow, softWrap, maxLines, minLines, onTextLayout, style)"

/** Text style from the [TEXT_SIGNATURE] arguments: color, fontSize, fontWeight, fontStyle, fontFamily, letterSpacing, textDecoration, textAlign, lineHeight. */
public fun Args.textStyle(): TextStyle = TextStyle(
    color = color("color"),
    fontSize = sp("fontSize"),
    fontWeight = typedOrNull<FontWeight>("fontWeight"),
    fontStyle = typedOrNull<FontStyle>("fontStyle"),
    fontFamily = typedOrNull<FontFamily>("fontFamily"),
    letterSpacing = sp("letterSpacing"),
    textDecoration = typedOrNull<TextDecoration>("textDecoration"),
    textAlign = typed("textAlign", TextAlign.Unspecified),
    lineHeight = sp("lineHeight"),
)

/** Calls `key` from a named function; the Compose compiler fails on `key` inside lambdas stored in a registry. */
@Composable
private fun Keyed(keys: List<Any?>, content: @Composable () -> Unit) {
    key(keys) { content() }
}

private fun Registry.state() {
    composable("remember(vararg keys, calculation)") { a ->
        val calculation = a.calculation()
        remember(a.varargs("keys")) { calculation() }
    }
    composable("rememberScrollState") { rememberScrollState() }
    function("mutableStateOf(value)") { a -> mutableStateOf(a["value"]) }
    function("mutableIntStateOf(value)") { a -> mutableIntStateOf(a.int("value", 0)) }
    function("mutableFloatStateOf(value)") { a -> mutableFloatStateOf(a.float("value", 0f)) }
    function("mutableStateListOf(vararg elements)") { a -> mutableStateListOf<Any?>().apply { addAll(a.varargs("elements")) } }
}

private fun Registry.modifiers() {
    value("Modifier", Modifier)
    member<Modifier>("padding(all: Dp)") { m, a -> m.padding(a.dp("all", 0.dp)) }
    member<Modifier>("padding(paddingValues: PaddingValues)") { m, a ->
        a.typedOrNull<PaddingValues>("paddingValues")?.let { m.padding(it) } ?: m
    }
    member<Modifier>("padding(horizontal, vertical)") { m, a ->
        m.padding(horizontal = a.dp("horizontal", 0.dp), vertical = a.dp("vertical", 0.dp))
    }
    member<Modifier>("padding(start, top, end, bottom)") { m, a ->
        m.padding(start = a.dp("start", 0.dp), top = a.dp("top", 0.dp), end = a.dp("end", 0.dp), bottom = a.dp("bottom", 0.dp))
    }
    member<Modifier>("fillMaxSize(fraction)") { m, a -> m.fillMaxSize(a.float("fraction", 1f)) }
    member<Modifier>("fillMaxWidth(fraction)") { m, a -> m.fillMaxWidth(a.float("fraction", 1f)) }
    member<Modifier>("fillMaxHeight(fraction)") { m, a -> m.fillMaxHeight(a.float("fraction", 1f)) }
    member<Modifier>("size(size)") { m, a -> m.size(a.dp("size")) }
    member<Modifier>("size(width, height)") { m, a -> m.size(a.dp("width"), a.dp("height")) }
    member<Modifier>("width(width)") { m, a -> m.width(a.dp("width")) }
    member<Modifier>("height(height)") { m, a -> m.height(a.dp("height")) }
    member<Modifier>("offset(x: Dp, y: Dp)") { m, a -> m.offset(a.dp("x", 0.dp), a.dp("y", 0.dp)) }
    member<Modifier>("offset(offset: Density.() -> IntOffset)") { m, a ->
        val position = a.scopedCalculation<Density>("offset")
        m.offset { position() as? IntOffset ?: IntOffset.Zero }
    }
    // `background(color, shape)` and `background(brush, shape, alpha)` share one binding; the first argument decides.
    member<Modifier>("background(color, shape, *, brush, alpha)") { m, a ->
        val brush = a["brush"] as? Brush ?: a["color"] as? Brush
        val shape = a.typed("shape", RectangleShape)
        if (brush != null) m.background(brush, shape, a.float("alpha", 1f)) else m.background(a.color("color"), shape)
    }
    member<Modifier>("border(width, color, shape)") { m, a -> m.border(a.dp("width", 1.dp), a.color("color", Color.Black), a.typed("shape", RectangleShape)) }
    member<Modifier>("clip(shape)") { m, a -> m.clip(a.typed("shape", RectangleShape)) }
    member<Modifier>("alpha(alpha)") { m, a -> m.alpha(a.float("alpha", 1f)) }
    member<Modifier>("clickable(enabled, onClickLabel, role, onClick)") { m, a ->
        m.clickable(
            enabled = a.boolean("enabled", true),
            onClickLabel = a.stringOrNull("onClickLabel"),
            role = a.typedOrNull<Role>("role"),
            onClick = a.action("onClick"),
        )
    }
    member<Modifier>("verticalScroll(state, enabled, *, reverseScrolling)") { m, a ->
        a.typedOrNull<ScrollState>("state")?.let {
            m.verticalScroll(it, enabled = a.boolean("enabled", true), reverseScrolling = a.boolean("reverseScrolling", false))
        } ?: m
    }
    member<Modifier>("horizontalScroll(state, enabled, *, reverseScrolling)") { m, a ->
        a.typedOrNull<ScrollState>("state")?.let {
            m.horizontalScroll(it, enabled = a.boolean("enabled", true), reverseScrolling = a.boolean("reverseScrolling", false))
        } ?: m
    }
    member<Modifier>("weight(weight, fill)") { m, a ->
        val weight = a.float("weight", 1f)
        val fill = a.boolean("fill", true)
        when (val scope = a.receivers.firstOrNull { it is RowScope || it is ColumnScope }) {
            is RowScope -> with(scope) { m.weight(weight, fill) }
            is ColumnScope -> with(scope) { m.weight(weight, fill) }
            else -> throw InterpreterException("Modifier.weight is only available in Row or Column")
        }
    }
    member<Modifier>("align(alignment)") { m, a ->
        val alignment = a["alignment"]
        when (val scope = a.receivers.firstOrNull { it is BoxScope || it is RowScope || it is ColumnScope }) {
            is BoxScope -> if (alignment is Alignment) with(scope) { m.align(alignment) } else null
            is RowScope -> if (alignment is Alignment.Vertical) with(scope) { m.align(alignment) } else null
            is ColumnScope -> if (alignment is Alignment.Horizontal) with(scope) { m.align(alignment) } else null
            else -> null
        } ?: throw InterpreterException("Modifier.align is not available here")
    }
    member<Modifier>("shadow(elevation, shape, clip, ambientColor, spotColor)") { m, a ->
        val elevation = a.dp("elevation", 0.dp)
        m.shadow(
            elevation = elevation,
            shape = a.typed("shape", RectangleShape),
            clip = a.boolean("clip", elevation > 0.dp),
            ambientColor = a.color("ambientColor", DefaultShadowColor),
            spotColor = a.color("spotColor", DefaultShadowColor),
        )
    }
    member<Modifier>("aspectRatio(ratio, matchHeightConstraintsFirst)") { m, a ->
        m.aspectRatio(a.float("ratio", 1f), a.boolean("matchHeightConstraintsFirst", false))
    }
    member<Modifier>("wrapContentSize") { m, _ -> m.wrapContentSize() }
    member<Modifier>("wrapContentWidth") { m, _ -> m.wrapContentWidth() }
    member<Modifier>("wrapContentHeight") { m, _ -> m.wrapContentHeight() }
    member<Modifier>("rotate(degrees)") { m, a -> m.rotate(a.float("degrees", 0f)) }
    member<Modifier>("scale(scale)") { m, a -> m.scale(a.float("scale", 1f)) }
    member<Modifier>("zIndex(zIndex)") { m, a -> m.zIndex(a.float("zIndex", 0f)) }
    member<Modifier>("requiredSize(size)") { m, a -> m.requiredSize(a.dp("size")) }
    member<Modifier>("defaultMinSize(minWidth, minHeight)") { m, a -> m.defaultMinSize(a.dp("minWidth"), a.dp("minHeight")) }
    member<Modifier>("then(other)") { m, a -> m.then(a.modifier("other")) }
}

private fun Registry.values() {
    member<Number>("dp") { n, _ -> n.toFloat().dp }
    member<Number>("sp") { n, _ -> n.toFloat().sp }
    member<Number>("times(other)") { n, a -> (a["other"] as? Dp)?.times(n.toFloat()) ?: throw InterpreterException("Unsupported operand for '*'") }
    member<Dp>("plus(other)") { d, a -> d + a.dp("other", 0.dp) }
    member<Dp>("minus(other)") { d, a -> d - a.dp("other", 0.dp) }
    member<Dp>("times(other)") { d, a -> d * a.float("other", 1f) }
    member<Dp>("div(other)") { d, a -> (a["other"] as? Dp)?.let { d / it } ?: (d / a.float("other", 1f)) }
    member<Dp>("unaryMinus") { d, _ -> -d }
    member<Dp>("value") { d, _ -> d.value }

    value("Color", Color.Companion)
    function("Color(color)") { a ->
        when (val color = a["color"]) {
            is Long -> Color(color)
            is Int -> Color(color)
            else -> throw InterpreterException("Color needs an ARGB value like 0xFF6200EE")
        }
    }
    function("Color(red, green, blue, alpha)") { a ->
        if (listOf("red", "green", "blue").all { a[it] is Int }) {
            Color(a.int("red", 0), a.int("green", 0), a.int("blue", 0), a.int("alpha", 255))
        } else {
            Color(a.float("red", 0f), a.float("green", 0f), a.float("blue", 0f), a.float("alpha", 1f))
        }
    }
    constants<Color.Companion>(
        "Black" to Color.Black, "White" to Color.White, "Gray" to Color.Gray, "LightGray" to Color.LightGray,
        "DarkGray" to Color.DarkGray, "Red" to Color.Red, "Green" to Color.Green, "Blue" to Color.Blue,
        "Yellow" to Color.Yellow, "Cyan" to Color.Cyan, "Magenta" to Color.Magenta,
        "Transparent" to Color.Transparent, "Unspecified" to Color.Unspecified,
    )
    member<Color>("copy(alpha)") { c, a -> c.copy(alpha = a.float("alpha", c.alpha)) }

    value("FontWeight", FontWeight.Companion)
    constants<FontWeight.Companion>(
        "Thin" to FontWeight.Thin, "Light" to FontWeight.Light, "Normal" to FontWeight.Normal,
        "Medium" to FontWeight.Medium, "SemiBold" to FontWeight.SemiBold, "Bold" to FontWeight.Bold,
        "ExtraBold" to FontWeight.ExtraBold, "Black" to FontWeight.Black,
    )

    value("TextAlign", TextAlign.Companion)
    constants<TextAlign.Companion>(
        "Start" to TextAlign.Start, "End" to TextAlign.End, "Center" to TextAlign.Center,
        "Left" to TextAlign.Left, "Right" to TextAlign.Right, "Justify" to TextAlign.Justify,
    )

    value("Alignment", Alignment.Companion)
    constants<Alignment.Companion>(
        "TopStart" to Alignment.TopStart, "TopCenter" to Alignment.TopCenter, "TopEnd" to Alignment.TopEnd,
        "CenterStart" to Alignment.CenterStart, "Center" to Alignment.Center, "CenterEnd" to Alignment.CenterEnd,
        "BottomStart" to Alignment.BottomStart, "BottomCenter" to Alignment.BottomCenter, "BottomEnd" to Alignment.BottomEnd,
        "Top" to Alignment.Top, "CenterVertically" to Alignment.CenterVertically, "Bottom" to Alignment.Bottom,
        "Start" to Alignment.Start, "CenterHorizontally" to Alignment.CenterHorizontally, "End" to Alignment.End,
    )

    value("Arrangement", Arrangement)
    constants<Arrangement>(
        "Top" to Arrangement.Top, "Bottom" to Arrangement.Bottom, "Center" to Arrangement.Center,
        "Start" to Arrangement.Start, "End" to Arrangement.End, "SpaceBetween" to Arrangement.SpaceBetween,
        "SpaceAround" to Arrangement.SpaceAround, "SpaceEvenly" to Arrangement.SpaceEvenly,
    )
    member<Arrangement>("spacedBy(space)") { _, a -> Arrangement.spacedBy(a.dp("space", 0.dp)) }

    value("CircleShape", CircleShape)
    value("RectangleShape", RectangleShape)
    function("RoundedCornerShape(size)") { a ->
        when (val size = a["size"]) {
            is Int -> RoundedCornerShape(size)
            else -> RoundedCornerShape(a.dp("size", 0.dp))
        }
    }
    function("RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)") { a ->
        RoundedCornerShape(
            topStart = a.dp("topStart", 0.dp), topEnd = a.dp("topEnd", 0.dp),
            bottomEnd = a.dp("bottomEnd", 0.dp), bottomStart = a.dp("bottomStart", 0.dp),
        )
    }
    function("PaddingValues(all)") { a -> PaddingValues(a.dp("all", 0.dp)) }
    function("PaddingValues(horizontal, vertical)") { a -> PaddingValues(a.dp("horizontal", 0.dp), a.dp("vertical", 0.dp)) }
    function("PaddingValues(start, top, end, bottom)") { a ->
        PaddingValues(a.dp("start", 0.dp), a.dp("top", 0.dp), a.dp("end", 0.dp), a.dp("bottom", 0.dp))
    }

    value("TextOverflow", TextOverflow.Companion)
    constants<TextOverflow.Companion>("Clip" to TextOverflow.Clip, "Ellipsis" to TextOverflow.Ellipsis, "Visible" to TextOverflow.Visible)
    value("FontStyle", FontStyle.Companion)
    constants<FontStyle.Companion>("Normal" to FontStyle.Normal, "Italic" to FontStyle.Italic)
    value("TextDecoration", TextDecoration.Companion)
    constants<TextDecoration.Companion>("None" to TextDecoration.None, "Underline" to TextDecoration.Underline, "LineThrough" to TextDecoration.LineThrough)
    value("FontFamily", FontFamily.Companion)
    constants<FontFamily.Companion>(
        "Default" to FontFamily.Default, "Monospace" to FontFamily.Monospace, "Serif" to FontFamily.Serif,
        "SansSerif" to FontFamily.SansSerif, "Cursive" to FontFamily.Cursive,
    )

    value("Brush", Brush.Companion)
    // Each gradient takes a `colors` list or `0f to Color.Red` color stops.
    member<Brush.Companion>("verticalGradient(colors: List, startY, endY, tileMode)") { _, a ->
        Brush.verticalGradient(gradientColors(a), a.float("startY", 0f), a.float("endY", Float.POSITIVE_INFINITY), a.tileMode())
    }
    member<Brush.Companion>("verticalGradient(vararg colorStops: Pair, startY, endY, tileMode)") { _, a ->
        Brush.verticalGradient(*colorStops(a), startY = a.float("startY", 0f), endY = a.float("endY", Float.POSITIVE_INFINITY), tileMode = a.tileMode())
    }
    member<Brush.Companion>("horizontalGradient(colors: List, startX, endX, tileMode)") { _, a ->
        Brush.horizontalGradient(gradientColors(a), a.float("startX", 0f), a.float("endX", Float.POSITIVE_INFINITY), a.tileMode())
    }
    member<Brush.Companion>("horizontalGradient(vararg colorStops: Pair, startX, endX, tileMode)") { _, a ->
        Brush.horizontalGradient(*colorStops(a), startX = a.float("startX", 0f), endX = a.float("endX", Float.POSITIVE_INFINITY), tileMode = a.tileMode())
    }
    member<Brush.Companion>("linearGradient(colors: List, start, end, tileMode)") { _, a ->
        Brush.linearGradient(gradientColors(a), a.typed("start", Offset.Zero), a.typed("end", Offset.Infinite), a.tileMode())
    }
    member<Brush.Companion>("linearGradient(vararg colorStops: Pair, start, end, tileMode)") { _, a ->
        Brush.linearGradient(*colorStops(a), start = a.typed("start", Offset.Zero), end = a.typed("end", Offset.Infinite), tileMode = a.tileMode())
    }
    member<Brush.Companion>("radialGradient(colors: List, center, radius, tileMode)") { _, a ->
        Brush.radialGradient(gradientColors(a), a.typed("center", Offset.Unspecified), a.float("radius", Float.POSITIVE_INFINITY), a.tileMode())
    }
    member<Brush.Companion>("radialGradient(vararg colorStops: Pair, center, radius, tileMode)") { _, a ->
        Brush.radialGradient(
            *colorStops(a),
            center = a.typed("center", Offset.Unspecified),
            radius = a.float("radius", Float.POSITIVE_INFINITY),
            tileMode = a.tileMode(),
        )
    }
    member<Brush.Companion>("sweepGradient(colors: List, center)") { _, a ->
        Brush.sweepGradient(gradientColors(a), a.typed("center", Offset.Unspecified))
    }
    member<Brush.Companion>("sweepGradient(vararg colorStops: Pair, center)") { _, a ->
        Brush.sweepGradient(*colorStops(a), center = a.typed("center", Offset.Unspecified))
    }
    type<TileMode>("TileMode")
    value("TileMode", TileMode.Companion)
    constants<TileMode.Companion>(
        "Clamp" to TileMode.Clamp, "Repeated" to TileMode.Repeated, "Mirror" to TileMode.Mirror, "Decal" to TileMode.Decal,
    )

    value("Int", Int.Companion)
    constants<Int.Companion>("MAX_VALUE" to Int.MAX_VALUE, "MIN_VALUE" to Int.MIN_VALUE)
}

private fun Args.tileMode(): TileMode = typed("tileMode", TileMode.Clamp)

/** Gradient color stops from `0f to Color.Red` pairs; invalid stops are skipped. */
private fun colorStops(a: Args): Array<Pair<Float, Color>> = a.varargs("colorStops").mapNotNull { stop ->
    val pair = stop as? Pair<*, *> ?: return@mapNotNull null
    val offset = (pair.first as? Number)?.toFloat() ?: return@mapNotNull null
    val color = pair.second as? Color ?: return@mapNotNull null
    offset to color
}.ifEmpty { listOf(0f to Color.Transparent, 1f to Color.Transparent) }.toTypedArray()

/** Gradient colors from the `colors` list argument. */
private fun gradientColors(a: Args): List<Color> =
    a.varargs("colors").filterIsInstance<Color>().ifEmpty { listOf(Color.Transparent, Color.Transparent) }

internal fun Registry.graphics() {
    type<StrokeCap>("StrokeCap")
    value("StrokeCap", StrokeCap.Companion)
    constants<StrokeCap.Companion>("Butt" to StrokeCap.Butt, "Round" to StrokeCap.Round, "Square" to StrokeCap.Square)

    type<ContentScale>("ContentScale")
    value("ContentScale", ContentScale.Companion)
    constants<ContentScale.Companion>(
        "Crop" to ContentScale.Crop,
        "Fit" to ContentScale.Fit,
        "FillBounds" to ContentScale.FillBounds,
        "FillWidth" to ContentScale.FillWidth,
        "FillHeight" to ContentScale.FillHeight,
        "Inside" to ContentScale.Inside,
        "None" to ContentScale.None,
    )

    type<BlendMode>("BlendMode")
    value("BlendMode", BlendMode.Companion)
    constants<BlendMode.Companion>(
        "Clear" to BlendMode.Clear,
        "Src" to BlendMode.Src,
        "Dst" to BlendMode.Dst,
        "SrcOver" to BlendMode.SrcOver,
        "DstOver" to BlendMode.DstOver,
        "SrcIn" to BlendMode.SrcIn,
        "DstIn" to BlendMode.DstIn,
        "SrcOut" to BlendMode.SrcOut,
        "DstOut" to BlendMode.DstOut,
        "SrcAtop" to BlendMode.SrcAtop,
        "DstAtop" to BlendMode.DstAtop,
        "Xor" to BlendMode.Xor,
        "Plus" to BlendMode.Plus,
        "Modulate" to BlendMode.Modulate,
        "Screen" to BlendMode.Screen,
        "Overlay" to BlendMode.Overlay,
        "Darken" to BlendMode.Darken,
        "Lighten" to BlendMode.Lighten,
        "ColorDodge" to BlendMode.ColorDodge,
        "ColorBurn" to BlendMode.ColorBurn,
        "Hardlight" to BlendMode.Hardlight,
        "Softlight" to BlendMode.Softlight,
        "Difference" to BlendMode.Difference,
        "Exclusion" to BlendMode.Exclusion,
        "Multiply" to BlendMode.Multiply,
        "Hue" to BlendMode.Hue,
        "Saturation" to BlendMode.Saturation,
        "Color" to BlendMode.Color,
        "Luminosity" to BlendMode.Luminosity,
    )

    type<ColorFilter>("ColorFilter")
    value("ColorFilter", ColorFilter.Companion)
    member<ColorFilter.Companion>("tint(color, blendMode)") { _, a ->
        ColorFilter.tint(a.color("color", Color.Black), a.typed("blendMode", BlendMode.SrcIn))
    }
}

internal fun Registry.animation() {
    composable("AnimatedVisibility(visible, modifier, enter, exit, label, content)") { a ->
        val content = a.scopedContent<AnimatedVisibilityScope>()
        AnimatedVisibility(
            visible = a.boolean("visible", true),
            modifier = a.modifier(),
            enter = a.typed("enter", fadeIn() + expandIn()),
            exit = a.typed("exit", shrinkOut() + fadeOut()),
            label = a.string("label", "AnimatedVisibility"),
        ) { content() }
    }
    composable("animateFloatAsState(targetValue, animationSpec, visibilityThreshold, label, finishedListener)") { a ->
        animateFloatAsState(
            a.float("targetValue", 0f),
            a.animationSpec(),
            a.float("visibilityThreshold", 0.01f),
            a.string("label", "FloatAnimation"),
            a.action1OrNull("finishedListener"),
        )
    }
    composable("animateDpAsState(targetValue, animationSpec, label, finishedListener)") { a ->
        animateDpAsState(a.dp("targetValue", 0.dp), a.animationSpec(), a.string("label", "DpAnimation"), a.action1OrNull("finishedListener"))
    }
    composable("animateColorAsState(targetValue, animationSpec, label, finishedListener)") { a ->
        animateColorAsState(
            a.color("targetValue", Color.Transparent),
            a.animationSpec(),
            a.string("label", "ColorAnimation"),
            a.action1OrNull("finishedListener"),
        )
    }
}

/**
 * Lazy item key or content type lambda, called with the item of [items] at an index, or with the index when there is
 * no list. Null when it was not passed.
 */
internal fun Args.itemFunction(name: String, items: List<*>?): ((Int) -> Any?)? {
    if (lambda(name) == null) return null
    val fn = function1(name)
    return { index -> fn(if (items != null) items[index] else index) }
}

/** Keys from an [itemFunction]; a missing key falls back to the index. */
internal fun ((Int) -> Any?).keys(): (Int) -> Any = { index -> lazyKey(this(index) ?: index) }

/** Lazy item key: strings, numbers, booleans and chars as they are, other values as their text, since keys must be saveable. */
internal fun lazyKey(key: Any): Any = when (key) {
    is String, is Number, is Boolean, is Char -> key
    else -> key.toString()
}

/** `animationSpec` argument (e.g. `tween(500)`), or a spring by default. */
@Suppress("UNCHECKED_CAST")
internal fun <T> Args.animationSpec(): AnimationSpec<T> =
    typed<AnimationSpec<*>>("animationSpec", spring<T>()) as AnimationSpec<T>

/** Marker for `GridCells.Fixed(n)` and `GridCells.Adaptive(size)`. */
private object GridCellsFactory

internal fun Registry.grids() {
    type<LazyGridScope>("LazyGridScope")
    type<LazyGridItemScope>("LazyGridItemScope")
    value("GridCells", GridCellsFactory)
    member<GridCellsFactory>("Fixed(count)") { _, a -> GridCells.Fixed(a.int("count", 1).coerceAtLeast(1)) }
    member<GridCellsFactory>("Adaptive(minSize)") { _, a -> GridCells.Adaptive(a.dp("minSize", 100.dp)) }
    composable("LazyVerticalGrid(columns, modifier, state, contentPadding, reverseLayout, verticalArrangement, horizontalArrangement, userScrollEnabled, content)") { a ->
        val content = a.scopedAction<LazyGridScope>()
        val reverseLayout = a.boolean("reverseLayout", false)
        LazyVerticalGrid(
            columns = a.typed<GridCells>("columns", GridCells.Fixed(2)),
            modifier = a.modifier(),
            state = a.typed("state", rememberLazyGridState()),
            contentPadding = a.typed<PaddingValues>("contentPadding", PaddingValues(0.dp)),
            reverseLayout = reverseLayout,
            verticalArrangement = a.typed<Arrangement.Vertical>("verticalArrangement", if (reverseLayout) Arrangement.Bottom else Arrangement.Top),
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", Arrangement.Start),
            userScrollEnabled = a.boolean("userScrollEnabled", true),
        ) { content() }
    }
    composable("LazyHorizontalGrid(rows, modifier, state, contentPadding, reverseLayout, horizontalArrangement, verticalArrangement, userScrollEnabled, content)") { a ->
        val content = a.scopedAction<LazyGridScope>()
        val reverseLayout = a.boolean("reverseLayout", false)
        LazyHorizontalGrid(
            rows = a.typed<GridCells>("rows", GridCells.Fixed(2)),
            modifier = a.modifier(),
            state = a.typed("state", rememberLazyGridState()),
            contentPadding = a.typed<PaddingValues>("contentPadding", PaddingValues(0.dp)),
            reverseLayout = reverseLayout,
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", if (reverseLayout) Arrangement.End else Arrangement.Start),
            verticalArrangement = a.typed<Arrangement.Vertical>("verticalArrangement", Arrangement.Top),
            userScrollEnabled = a.boolean("userScrollEnabled", true),
        ) { content() }
    }
    member<LazyGridScope>("item(key, span, contentType, content)") { scope, a ->
        val content = a.scopedContent<LazyGridItemScope>()
        val span = a.scopedCalculation<LazyGridItemSpanScope>("span")
        scope.item(
            key = a["key"]?.let(::lazyKey),
            span = if (a.lambda("span") == null) null else ({ span() as? GridItemSpan ?: GridItemSpan(1) }),
            contentType = a["contentType"],
        ) { content() }
    }
    member<LazyGridScope>("items(items, key, span, contentType, itemContent, *, count)") { scope, a ->
        val content = a.scopedContent1<LazyGridItemScope>("itemContent")
        val list = a["items"] as? List<*>
        val count = list?.size ?: a["items"] as? Int ?: a["count"] as? Int
        if (count == null) {
            a.report("items needs a count or a list")
        } else {
            val span = a.lambda("span")?.let { a.scopedFunction1<LazyGridItemSpanScope>("span") }
            scope.items(
                count = count,
                key = a.itemFunction("key", list)?.keys(),
                span = span?.let { fn -> { index -> fn(if (list != null) list[index] else index) as? GridItemSpan ?: GridItemSpan(1) } },
                contentType = a.itemFunction("contentType", list) ?: { null },
            ) { content(if (list != null) list[it] else it) }
        }
    }
    type<LazyGridItemSpanScope>("LazyGridItemSpanScope")
    properties<LazyGridItemSpanScope>("maxLineSpan" to { it.maxLineSpan }, "maxCurrentLineSpan" to { it.maxCurrentLineSpan })
    type<GridItemSpan>("GridItemSpan")
    function("GridItemSpan(currentLineSpan)") { a -> GridItemSpan(a.int("currentLineSpan", 1).coerceAtLeast(1)) }
    member<LazyListScope>("itemsIndexed(items, key, contentType, itemContent)") { scope, a ->
        val content = a.scopedContent2<LazyItemScope>("itemContent")
        val items = a["items"] as? List<*> ?: emptyList<Any?>()
        val key = a.lambda("key")?.let { a.function2("key") }
        val contentType = a.lambda("contentType")?.let { a.function2("contentType") }
        scope.items(
            count = items.size,
            key = key?.let { fn -> { index -> lazyKey(fn(index, items[index]) ?: index) } },
            contentType = { index -> contentType?.invoke(index, items[index]) },
        ) { content(it, items[it]) }
    }
}
