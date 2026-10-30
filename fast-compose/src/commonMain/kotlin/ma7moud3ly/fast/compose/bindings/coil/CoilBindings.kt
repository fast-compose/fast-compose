package ma7moud3ly.fast.compose.bindings.coil

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.BindingPack
import ma7moud3ly.fast.compose.interpreter.Registry

/**
 * Network images with Coil: `AsyncImage` and `rememberAsyncImagePainter`. The model is passed to Coil unchanged and
 * loaded by Coil's singleton `ImageLoader`, which hosts configure with `setSingletonImageLoaderFactory`. Images that
 * fail to load draw a placeholder unless `error` is passed, and the failure is logged as a warning.
 */
public object CoilBindings : BindingPack {
    override fun install(registry: Registry): Unit = registry.coil()
}

private fun Registry.coil() {
    composable(
        "AsyncImage(model, contentDescription, modifier, placeholder, error, fallback, onLoading, onSuccess, onError, " +
            "alignment, contentScale, alpha, colorFilter, filterQuality, clipToBounds)",
    ) { a ->
        val error = a.typedOrNull<Painter>("error") ?: remember { FailedImagePainter() }
        AsyncImage(
            model = a["model"],
            contentDescription = a.stringOrNull("contentDescription"),
            modifier = a.modifier(),
            placeholder = a.typedOrNull<Painter>("placeholder"),
            error = error,
            fallback = a.typedOrNull<Painter>("fallback") ?: error,
            onLoading = a.action1OrNull("onLoading"),
            onSuccess = a.action1OrNull("onSuccess"),
            onError = a.onError(),
            alignment = a.typed<Alignment>("alignment", Alignment.Center),
            contentScale = a.typed<ContentScale>("contentScale", ContentScale.Fit),
            alpha = a.float("alpha", DefaultAlpha),
            colorFilter = a.typedOrNull<ColorFilter>("colorFilter"),
            filterQuality = a.typed("filterQuality", DrawScope.DefaultFilterQuality),
            clipToBounds = a.boolean("clipToBounds", true),
        )
    }
    composable("rememberAsyncImagePainter(model, placeholder, error, fallback, onLoading, onSuccess, onError, contentScale, filterQuality)") { a ->
        val error = a.typedOrNull<Painter>("error") ?: remember { FailedImagePainter() }
        rememberAsyncImagePainter(
            model = a["model"],
            placeholder = a.typedOrNull<Painter>("placeholder"),
            error = error,
            fallback = a.typedOrNull<Painter>("fallback") ?: error,
            onLoading = a.action1OrNull("onLoading"),
            onSuccess = a.action1OrNull("onSuccess"),
            onError = a.onError(),
            contentScale = a.typed<ContentScale>("contentScale", ContentScale.Fit),
            filterQuality = a.typed<FilterQuality>("filterQuality", DrawScope.DefaultFilterQuality),
        )
    }
}

/** Logs a failed load, then calls the `onError` argument if there is one. */
private fun Args.onError(): (AsyncImagePainter.State.Error) -> Unit {
    val model = get("model")
    val callback = action1OrNull("onError")
    return { state ->
        warn("Cannot load image '$model': ${state.result.throwable.message ?: state.result.throwable::class.simpleName}")
        callback?.invoke(state)
    }
}

/** A light gray box with a crossed outline, drawn where an image failed to load. */
private class FailedImagePainter : Painter() {
    override val intrinsicSize: Size = Size(96f, 96f)

    override fun DrawScope.onDraw() {
        val line = Color(0x66888888)
        drawRect(Color(0x1F888888))
        drawRect(line, style = Stroke(width = 2f))
        drawLine(line, Offset.Zero, Offset(size.width, size.height), strokeWidth = 2f)
        drawLine(line, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 2f)
    }
}
