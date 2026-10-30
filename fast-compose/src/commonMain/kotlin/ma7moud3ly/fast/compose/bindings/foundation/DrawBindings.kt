package ma7moud3ly.fast.compose.bindings.foundation

import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.InterpreterException
import ma7moud3ly.fast.compose.interpreter.Registry
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/** `Canvas`, `Modifier.drawBehind`, DrawScope drawing, geometry and infinite animations. */
internal fun Registry.drawing() {
    canvas()
    drawScope()
    geometry()
    infiniteAnimations()
}

private fun Registry.canvas() {
    type<DrawScope>("DrawScope")
    composable("Canvas(modifier, onDraw)") { a ->
        val draw = a.scopedAction<DrawScope>("onDraw")
        Canvas(a.modifier()) { draw() }
    }
    member<Modifier>("drawBehind(onDraw)") { m, a ->
        val draw = a.scopedAction<DrawScope>("onDraw")
        m.drawBehind { draw() }
    }
}

/** Brush passed by name or as the first argument (`drawCircle(brush, …)`), else null for the color overload. */
private fun Args.brush(first: String): Brush? = this["brush"] as? Brush ?: this[first] as? Brush

private fun Args.style(): DrawStyle = typed("style", Fill)

private fun Args.colorFilter(): ColorFilter? = typedOrNull<ColorFilter>("colorFilter")

private fun Args.blendMode(): BlendMode = typed("blendMode", DrawScope.DefaultBlendMode)

private fun Registry.drawScope() {
    member<DrawScope>("size") { s, _ -> s.size }
    member<DrawScope>("center") { s, _ -> s.center }
    member<DrawScope>("drawCircle(color, radius, center, alpha, style, colorFilter, blendMode, *, brush)") { s, a ->
        val radius = a.float("radius", s.size.minDimension / 2f)
        val center = a.typed("center", s.center)
        val alpha = a.float("alpha", 1f)
        val brush = a.brush("color")
        if (brush != null) s.drawCircle(brush, radius, center, alpha, a.style(), a.colorFilter(), a.blendMode())
        else s.drawCircle(a.color("color", Color.Black), radius, center, alpha, a.style(), a.colorFilter(), a.blendMode())
    }
    member<DrawScope>("drawRect(color, topLeft, size, alpha, style, colorFilter, blendMode, *, brush)") { s, a ->
        val topLeft = a.typed("topLeft", Offset.Zero)
        val size = a.typed("size", Size(s.size.width - topLeft.x, s.size.height - topLeft.y))
        val alpha = a.float("alpha", 1f)
        val brush = a.brush("color")
        if (brush != null) s.drawRect(brush, topLeft, size, alpha, a.style(), a.colorFilter(), a.blendMode())
        else s.drawRect(a.color("color", Color.Black), topLeft, size, alpha, a.style(), a.colorFilter(), a.blendMode())
    }
    member<DrawScope>("drawRoundRect(color, topLeft, size, cornerRadius, style, alpha, colorFilter, blendMode, *, brush)") { s, a ->
        val topLeft = a.typed("topLeft", Offset.Zero)
        val size = a.typed("size", Size(s.size.width - topLeft.x, s.size.height - topLeft.y))
        val cornerRadius = a.typed("cornerRadius", CornerRadius.Zero)
        val alpha = a.float("alpha", 1f)
        val brush = a.brush("color")
        if (brush != null) s.drawRoundRect(brush, topLeft, size, cornerRadius, alpha, a.style(), a.colorFilter(), a.blendMode())
        else s.drawRoundRect(a.color("color", Color.Black), topLeft, size, cornerRadius, a.style(), alpha, a.colorFilter(), a.blendMode())
    }
    member<DrawScope>("drawOval(color, topLeft, size, alpha, style, colorFilter, blendMode, *, brush)") { s, a ->
        val topLeft = a.typed("topLeft", Offset.Zero)
        val size = a.typed("size", Size(s.size.width - topLeft.x, s.size.height - topLeft.y))
        val alpha = a.float("alpha", 1f)
        val brush = a.brush("color")
        if (brush != null) s.drawOval(brush, topLeft, size, alpha, a.style(), a.colorFilter(), a.blendMode())
        else s.drawOval(a.color("color", Color.Black), topLeft, size, alpha, a.style(), a.colorFilter(), a.blendMode())
    }
    member<DrawScope>("drawArc(color, startAngle, sweepAngle, useCenter, topLeft, size, alpha, style, colorFilter, blendMode, *, brush)") { s, a ->
        val startAngle = a.float("startAngle", 0f)
        val sweepAngle = a.float("sweepAngle", 360f)
        val useCenter = a.boolean("useCenter", false)
        val topLeft = a.typed("topLeft", Offset.Zero)
        val size = a.typed("size", Size(s.size.width - topLeft.x, s.size.height - topLeft.y))
        val alpha = a.float("alpha", 1f)
        val brush = a.brush("color")
        if (brush != null) s.drawArc(brush, startAngle, sweepAngle, useCenter, topLeft, size, alpha, a.style(), a.colorFilter(), a.blendMode())
        else s.drawArc(a.color("color", Color.Black), startAngle, sweepAngle, useCenter, topLeft, size, alpha, a.style(), a.colorFilter(), a.blendMode())
    }
    member<DrawScope>("drawLine(color, start, end, strokeWidth, cap, pathEffect, alpha, colorFilter, blendMode, *, brush)") { s, a ->
        val start = a.typed("start", Offset.Zero)
        val end = a.typed("end", Offset.Zero)
        val strokeWidth = a.float("strokeWidth", Stroke.HairlineWidth)
        val cap = a.typed("cap", Stroke.DefaultCap)
        val alpha = a.float("alpha", 1f)
        val brush = a.brush("color")
        val pathEffect = a.typedOrNull<PathEffect>("pathEffect")
        if (brush != null) s.drawLine(brush, start, end, strokeWidth, cap, pathEffect, alpha, a.colorFilter(), a.blendMode())
        else s.drawLine(a.color("color", Color.Black), start, end, strokeWidth, cap, pathEffect, alpha, a.colorFilter(), a.blendMode())
    }
    member<DrawScope>("drawPath(path, color, alpha, style, colorFilter, blendMode, *, brush)") { s, a ->
        val path = a.typedOrNull<Path>("path") ?: throw InterpreterException("drawPath needs a Path")
        val alpha = a.float("alpha", 1f)
        val brush = a.brush("color")
        if (brush != null) s.drawPath(path, brush, alpha, a.style(), a.colorFilter(), a.blendMode())
        else s.drawPath(path, a.color("color", Color.Black), alpha, a.style(), a.colorFilter(), a.blendMode())
    }
    member<DrawScope>("rotate(degrees, pivot, block)") { s, a ->
        val block = a.scopedAction<DrawScope>("block")
        s.rotate(a.float("degrees", 0f), a.typed("pivot", s.center)) { block() }
    }
    member<DrawScope>("scale(scale, pivot, block)") { s, a ->
        val block = a.scopedAction<DrawScope>("block")
        s.scale(a.float("scale", 1f), a.typed("pivot", s.center)) { block() }
    }
    member<DrawScope>("translate(left, top, block)") { s, a ->
        val block = a.scopedAction<DrawScope>("block")
        s.translate(a.float("left", 0f), a.float("top", 0f)) { block() }
    }
    member<DrawScope>("inset(horizontal, vertical, block)") { s, a ->
        val block = a.scopedAction<DrawScope>("block")
        s.inset(a.float("horizontal", 0f), a.float("vertical", 0f)) { block() }
    }
    member<Dp>("toPx") { d, a ->
        val density = a.receivers.firstOrNull { it is Density } as? Density
            ?: throw InterpreterException("toPx() needs a density scope such as Canvas")
        with(density) { d.toPx() }
    }
}

private fun Registry.geometry() {
    type<PathEffect>("PathEffect")
    value("PathEffect", PathEffect.Companion)
    member<PathEffect.Companion>("dashPathEffect(intervals, phase)") { _, a ->
        val intervals = (a["intervals"] as? FloatArray) ?: (a["intervals"] as? List<*>)?.map { (it as? Number)?.toFloat() ?: 0f }?.toFloatArray()
        PathEffect.dashPathEffect(intervals ?: floatArrayOf(10f, 10f), a.float("phase", 0f))
    }
    member<PathEffect.Companion>("cornerPathEffect(radius)") { _, a -> PathEffect.cornerPathEffect(a.float("radius", 0f)) }
    type<Offset>("Offset")
    type<Size>("Size")
    type<Path>("Path")
    type<DrawStyle>("DrawStyle")
    type<Stroke>("Stroke")

    function("Offset(x, y)") { a -> Offset(a.float("x", 0f), a.float("y", 0f)) }
    value("Offset", Offset.Companion)
    constants<Offset.Companion>("Zero" to Offset.Zero, "Infinite" to Offset.Infinite, "Unspecified" to Offset.Unspecified)
    properties<Offset>("x" to { it.x }, "y" to { it.y })
    member<Offset>("plus(other)") { o, a -> o + a.typed("other", Offset.Zero) }
    member<Offset>("minus(other)") { o, a -> o - a.typed("other", Offset.Zero) }
    member<Offset>("times(operand)") { o, a -> o * a.float("operand", 1f) }
    member<Offset>("div(operand)") { o, a -> o / a.float("operand", 1f) }
    member<Offset>("unaryMinus") { o, _ -> -o }
    member<Offset>("copy(x, y)") { o, a -> o.copy(x = a.float("x", o.x), y = a.float("y", o.y)) }

    function("Size(width, height)") { a -> Size(a.float("width", 0f), a.float("height", 0f)) }
    value("Size", Size.Companion)
    constants<Size.Companion>("Zero" to Size.Zero)
    properties<Size>(
        "width" to { it.width },
        "height" to { it.height },
        "minDimension" to { it.minDimension },
        "maxDimension" to { it.maxDimension },
    )
    member<Size>("times(operand)") { s, a -> s * a.float("operand", 1f) }
    member<Size>("div(operand)") { s, a -> s / a.float("operand", 1f) }

    function("CornerRadius(x, y)") { a ->
        val x = a.float("x", 0f)
        CornerRadius(x, a.float("y", x))
    }

    function("Path") { _ -> Path() }
    member<Path>("moveTo(x, y)") { p, a -> p.moveTo(a.float("x", 0f), a.float("y", 0f)) }
    member<Path>("lineTo(x, y)") { p, a -> p.lineTo(a.float("x", 0f), a.float("y", 0f)) }
    member<Path>("relativeMoveTo(dx, dy)") { p, a -> p.relativeMoveTo(a.float("dx", 0f), a.float("dy", 0f)) }
    member<Path>("relativeLineTo(dx, dy)") { p, a -> p.relativeLineTo(a.float("dx", 0f), a.float("dy", 0f)) }
    member<Path>("quadraticTo(x1, y1, x2, y2)") { p, a -> p.quadraticTo(a.float("x1", 0f), a.float("y1", 0f), a.float("x2", 0f), a.float("y2", 0f)) }
    member<Path>("cubicTo(x1, y1, x2, y2, x3, y3)") { p, a ->
        p.cubicTo(
            a.float("x1", 0f), a.float("y1", 0f),
            a.float("x2", 0f), a.float("y2", 0f),
            a.float("x3", 0f), a.float("y3", 0f),
        )
    }
    member<Path>("close") { p, _ -> p.close() }
    member<Path>("reset") { p, _ -> p.reset() }

    value("Fill", Fill)
    function("Stroke(width, miter, cap, join)") { a ->
        Stroke(
            width = a.float("width", 0f),
            miter = a.float("miter", Stroke.DefaultMiter),
            cap = a.typed("cap", Stroke.DefaultCap),
            join = a.typed<StrokeJoin>("join", Stroke.DefaultJoin),
        )
    }
    value("StrokeJoin", StrokeJoin.Companion)
    constants<StrokeJoin.Companion>("Miter" to StrokeJoin.Miter, "Round" to StrokeJoin.Round, "Bevel" to StrokeJoin.Bevel)
    type<StrokeCap>("StrokeCap")
}

/**
 * Runs [animation] again from the start when [spec] changes. Infinite animations otherwise keep their first spec,
 * so an edited duration would not show in a running preview. Calls `key` from a named function; the Compose compiler
 * fails on `key` inside lambdas stored in a registry.
 */
@Composable
private fun <T> restarting(spec: Any?, animation: @Composable () -> T): T = key(spec) { animation() }

/** Marker for `RepeatMode.Restart` and `RepeatMode.Reverse` (an enum without a companion). */
private object RepeatModes

@Suppress("UNCHECKED_CAST")
private fun Registry.infiniteAnimations() {
    type<InfiniteTransition>("InfiniteTransition")
    composable("rememberInfiniteTransition(label)") { a -> rememberInfiniteTransition(a.string("label", "InfiniteTransition")) }
    composableMember<InfiniteTransition>("animateFloat(initialValue, targetValue, animationSpec, label)") { t, a ->
        val spec = a.typed<InfiniteRepeatableSpec<*>>("animationSpec", infiniteRepeatable(tween<Float>())) as InfiniteRepeatableSpec<Float>
        restarting(spec) {
            t.animateFloat(
                initialValue = a.float("initialValue", 0f),
                targetValue = a.float("targetValue", 1f),
                animationSpec = spec,
                label = a.string("label", "FloatAnimation"),
            )
        }
    }
    composableMember<InfiniteTransition>("animateColor(initialValue, targetValue, animationSpec, label)") { t, a ->
        val spec = a.typed<InfiniteRepeatableSpec<*>>("animationSpec", infiniteRepeatable(tween<Color>())) as InfiniteRepeatableSpec<Color>
        restarting(spec) {
            t.animateColor(
                initialValue = a.color("initialValue", Color.Black),
                targetValue = a.color("targetValue", Color.White),
                animationSpec = spec,
                label = a.string("label", "ColorAnimation"),
            )
        }
    }
    function("tween(durationMillis, delayMillis, easing)") { a ->
        tween<Any?>(
            durationMillis = a.int("durationMillis", 300),
            delayMillis = a.int("delayMillis", 0),
            easing = a.typed<Easing>("easing", FastOutSlowInEasing),
        )
    }
    function("infiniteRepeatable(animation, repeatMode)") { a ->
        infiniteRepeatable(
            animation = a.typed<DurationBasedAnimationSpec<*>>("animation", tween<Any?>()) as DurationBasedAnimationSpec<Any?>,
            repeatMode = a.typed("repeatMode", RepeatMode.Restart),
        )
    }
    value("RepeatMode", RepeatModes)
    constants<RepeatModes>("Restart" to RepeatMode.Restart, "Reverse" to RepeatMode.Reverse)
    value("LinearEasing", LinearEasing)
    value("FastOutSlowInEasing", FastOutSlowInEasing)
    value("LinearOutSlowInEasing", LinearOutSlowInEasing)
    value("FastOutLinearInEasing", FastOutLinearInEasing)
}
