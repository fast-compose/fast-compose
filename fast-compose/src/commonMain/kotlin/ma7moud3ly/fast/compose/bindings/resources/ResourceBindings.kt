package ma7moud3ly.fast.compose.bindings.resources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.BindingPack
import ma7moud3ly.fast.compose.interpreter.Registry
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToImageVector
import kotlin.math.abs
import kotlin.math.roundToLong

/** A resource such as `R.drawable.logo` or `Res.string.title`. */
public data class ResourceRef(val type: String, val name: String) {
    override fun toString(): String = "$type/$name"
}

/** `R` and `Res`: their members are resource types such as `drawable` and `string`. */
private object ResourceRoot

/** `R.drawable`, `Res.string`, …: their members are the resources of [type]. */
private class ResourceType(val type: String)

/**
 * Resources from `R` (Android) and `Res` (Compose Multiplatform): `painterResource`, `vectorResource`,
 * `imageResource` and `stringResource`. Files and strings come from [LocalResourceProvider], in the variant that fits
 * [LocalResourceEnvironment] and the current density; missing images draw a placeholder and missing strings show their name.
 */
public object ResourceBindings : BindingPack {
    override fun install(registry: Registry): Unit = registry.resources()
}

private fun Registry.resources() {
    type<ResourceRef>("ResourceRef")
    value("R", ResourceRoot)
    value("Res", ResourceRoot)
    fallbackMember<ResourceRoot> { _, name, _ -> ResourceType(name) }
    fallbackMember<ResourceType> { type, name, _ -> ResourceRef(type.type, name) }

    // `id` is the Android parameter name and `resource` the Compose Multiplatform one.
    composable("painterResource(id, *, resource)") { a -> painterOf(image(a)) }
    composable("vectorResource(id, *, resource)") { a ->
        val image = image(a)
        if (image != null && image !is ImageVector) a.report("vectorResource needs a vector drawable, found an image file")
        image as? ImageVector
    }
    composable("imageResource(id, *, resource)") { a ->
        val image = image(a)
        if (image != null && image !is ImageBitmap) a.report("imageResource needs a PNG, JPEG, WebP or BMP file, found a vector drawable")
        image as? ImageBitmap
    }
    composable("stringResource(id, vararg formatArgs, *, resource)") { a ->
        val ref = a.resourceRef()
        val environment = resourceEnvironment()
        val provider = LocalResourceProvider.current
        val text = ref?.let { provider.string(it.name, environment) }
        when {
            ref == null -> "".also { a.report("stringResource needs a resource such as R.string.title or Res.string.title") }
            text == null && provider.isLoading("string", ref.name, environment) -> ""
            text == null -> "[${ref.name}]".also { a.warn("String resource '${ref.name}' not found") }
            else -> formatResource(text, a.varargs("formatArgs"))
        }
    }
}

private fun Args.resourceRef(): ResourceRef? = (get("id") ?: get("resource")) as? ResourceRef

/** [LocalResourceEnvironment] with the current density. */
@Composable
private fun resourceEnvironment(): ResourceEnvironment =
    LocalResourceEnvironment.current.copy(density = LocalDensity.current.density)

/**
 * The decoded image of the resource argument: an [ImageVector], an [ImageBitmap], or null after reporting why.
 * The bytes are read on every composition, so a replaced file is decoded again.
 */
@Composable
private fun image(a: Args): Any? {
    val ref = a.resourceRef()
    val density = LocalDensity.current
    val provider = LocalResourceProvider.current
    val environment = resourceEnvironment()
    val bytes = ref?.let { provider.bytes(it.type, it.name, environment) }
    val loading = ref != null && bytes == null && provider.isLoading(ref.type, ref.name, environment)
    val result = remember(ref, bytes, density, loading) {
        when {
            ref == null -> Result.failure(IllegalArgumentException("${a.signature.name} needs a resource such as R.drawable.logo or Res.drawable.logo"))
            loading -> Result.failure(StillLoading)
            bytes == null -> Result.failure(NoSuchElementException("Resource '$ref' not found; showing a placeholder"))
            else -> decode(bytes, density)
        }
    }
    when (val error = result.exceptionOrNull()) {
        null, StillLoading -> Unit
        is NoSuchElementException -> a.warn(error.message ?: "Resource '$ref' not found")
        else -> a.report(error.message ?: "Cannot load resource '$ref'")
    }
    return result.getOrNull()
}

/** Marks a resource that is still being read; it shows a placeholder without a warning. */
private object StillLoading : Exception()

/** Decodes vector drawable XML to an [ImageVector] and PNG, JPEG, WebP or BMP files to an [ImageBitmap]. */
private fun decode(bytes: ByteArray, density: Density): Result<Any> = runCatching {
    val head = bytes.decodeToString(0, minOf(bytes.size, 512))
    when {
        "<vector" in head -> bytes.decodeToImageVector(density)
        "<svg" in head -> throw UnsupportedOperationException("SVG images are not supported yet")
        else -> bytes.decodeToImageBitmap()
    }
}

/** A painter for [image], or a placeholder when it is missing. */
@Composable
private fun painterOf(image: Any?): Painter = when (image) {
    is ImageVector -> rememberVectorPainter(image)
    is ImageBitmap -> remember(image) { BitmapPainter(image) }
    else -> remember { PlaceholderPainter() }
}

/** A light gray box with a crossed outline, drawn where an image is missing. */
private class PlaceholderPainter : Painter() {
    override val intrinsicSize: Size = Size(96f, 96f)

    override fun DrawScope.onDraw() {
        val line = Color(0x66888888)
        drawRect(Color(0x1F888888))
        drawRect(line, style = Stroke(width = 2f))
        drawLine(line, Offset.Zero, Offset(size.width, size.height), strokeWidth = 2f)
        drawLine(line, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 2f)
    }
}

private val placeholder = Regex("""%(?:(\d+)\$)?([-#+ 0,(]*)(\d*)(?:\.(\d+))?([sdf%])""")

/** Fills `%s`, `%d`, `%.2f` and `%1$s`-style placeholders of a string resource with [args]; `%%` is a percent sign. */
internal fun formatResource(text: String, args: List<Any?>): String {
    var next = 0
    return placeholder.replace(text) { match ->
        val (position, _, _, precision, conversion) = match.destructured
        if (conversion == "%") return@replace "%"
        val value = args.getOrNull(position.toIntOrNull()?.minus(1) ?: next++)
        when {
            conversion == "f" && value is Number -> fixed(value.toDouble(), precision.toIntOrNull() ?: 6)
            conversion == "d" && value is Number -> value.toLong().toString()
            else -> value.toString()
        }
    }
}

/** [value] with [digits] decimal places, e.g. `fixed(3.14159, 2)` is `3.14`. */
private fun fixed(value: Double, digits: Int): String {
    var factor = 1L
    repeat(digits) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    if (digits == 0) return "$sign$scaled"
    return "$sign${scaled / factor}.${(scaled % factor).toString().padStart(digits, '0')}"
}
