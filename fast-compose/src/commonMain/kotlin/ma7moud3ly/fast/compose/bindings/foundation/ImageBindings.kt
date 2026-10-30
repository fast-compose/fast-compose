package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.ErrorBox
import ma7moud3ly.fast.compose.interpreter.Registry

/** `Image` of an `ImageVector`, a `Painter` (e.g. from `painterResource`) or an `ImageBitmap`; the first argument picks the overload. */
internal fun Registry.images() {
    type<ImageVector>("ImageVector")
    type<Painter>("Painter")
    type<ImageBitmap>("ImageBitmap")
    composable("Image(imageVector: ImageVector, contentDescription, modifier, alignment, contentScale, alpha, colorFilter)") { a ->
        val image = a.typedOrNull<ImageVector>("imageVector")
        if (image == null) {
            ErrorBox("Image needs an ImageVector, a Painter or an ImageBitmap, e.g. painterResource(R.drawable.logo)")
        } else {
            Image(
                image,
                a.stringOrNull("contentDescription"),
                a.modifier(),
                a.alignment(),
                a.contentScale(),
                a.alpha(),
                a.colorFilter()
            )
        }
    }
    composable("Image(painter: Painter, contentDescription, modifier, alignment, contentScale, alpha, colorFilter)") { a ->
        val painter = a.typedOrNull<Painter>("painter")
        if (painter == null) {
            ErrorBox("Image needs a Painter, e.g. painterResource(R.drawable.logo)")
        } else {
            Image(
                painter,
                a.stringOrNull("contentDescription"),
                a.modifier(),
                a.alignment(),
                a.contentScale(),
                a.alpha(),
                a.colorFilter()
            )
        }
    }
    composable("Image(bitmap: ImageBitmap, contentDescription, modifier, alignment, contentScale, alpha, colorFilter)") { a ->
        val bitmap = a.typedOrNull<ImageBitmap>("bitmap")
        if (bitmap == null) {
            ErrorBox("Image needs an ImageBitmap, e.g. imageResource(Res.drawable.photo)")
        } else {
            Image(
                bitmap,
                a.stringOrNull("contentDescription"),
                a.modifier(),
                a.alignment(),
                a.contentScale(),
                a.alpha(),
                a.colorFilter()
            )
        }
    }
}

private fun Args.alignment(): Alignment = typed<Alignment>("alignment", Alignment.Center)

private fun Args.contentScale(): ContentScale =
    typed<ContentScale>("contentScale", ContentScale.Fit)

private fun Args.alpha(): Float = float("alpha", 1f)

private fun Args.colorFilter(): ColorFilter? = typedOrNull<ColorFilter>("colorFilter")
