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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.BindingPack
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.interpreter.StandIn
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
 * `imageResource`, `stringResource`, `colorResource` and `Font`. Files, strings and colors come from [LocalResourceProvider], in the variant that fits
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
    function("Font(resId, weight, style, *, resource)") { a -> font(a) }
    composable("pluralStringResource(id, count, vararg formatArgs, *, resource, quantity)") { a ->
        val ref = a.resourceRef()
        val environment = resourceEnvironment()
        val provider = LocalResourceProvider.current
        val count = a.int("count", a.int("quantity", 0))
        val text = ref?.let {
            provider.string(StringsXml.pluralKey(it.name, pluralQuantity(count, environment.locale)), environment)
                ?: provider.string(StringsXml.pluralKey(it.name, "other"), environment)
        }
        when {
            ref == null -> "".also { a.report("pluralStringResource needs a resource such as R.plurals.items or Res.plurals.items") }
            text == null && provider.isLoading("string", ref.name, environment) -> ""
            text == null -> "[${ref.name}]".also { a.warn("Plurals resource '${ref.name}' not found") }
            else -> formatResource(text, a.varargs("formatArgs"))
        }
    }
    composable("stringArrayResource(id, *, resource)") { a ->
        val ref = a.resourceRef()
        val environment = resourceEnvironment()
        val provider = LocalResourceProvider.current
        if (ref == null) {
            a.report("stringArrayResource needs a resource such as R.array.items or Res.array.items")
            emptyList()
        } else {
            generateSequence(0) { it + 1 }.map { provider.string(StringsXml.arrayKey(ref.name, it), environment) }
                .takeWhile { it != null }.toList()
                .also { if (it.isEmpty() && !provider.isLoading("string", ref.name, environment)) a.warn("String array '${ref.name}' not found") }
        }
    }
    composable("colorResource(id)") { a ->
        val ref = a.resourceRef()
        val environment = resourceEnvironment()
        val provider = LocalResourceProvider.current
        val colors = colorsOf(provider, environment)
        val value = ref?.let { colors[it.name]?.let { raw -> resolveColor(raw, colors) } }
        when {
            ref == null -> Color.Unspecified.also { a.report("colorResource needs a resource such as R.color.primary") }
            value == null && provider.isLoading("values", "colors", environment) -> Color.Unspecified
            value == null -> Color.Unspecified.also { a.warn("Color resource '${ref.name}' not found") }
            else -> parseColor(value) ?: Color.Unspecified.also { a.warn("Invalid color '$value' for '${ref.name}'") }
        }
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

/**
 * Plural form of [count] in [locale], as in CLDR: Arabic has `zero`, `one`, `two`, `few`, `many`; French and
 * Portuguese use `one` for 0 and 1; other languages `one` for 1. Everything else is `other`.
 */
internal fun pluralQuantity(count: Int, locale: String?): String {
    val n = count.let { if (it < 0) -it else it }
    return when (locale?.substringBefore('-')?.substringBefore('_')) {
        "ar" -> when {
            n == 0 -> "zero"
            n == 1 -> "one"
            n == 2 -> "two"
            n % 100 in 3..10 -> "few"
            n % 100 in 11..99 -> "many"
            else -> "other"
        }
        "fr", "pt" -> if (n <= 1) "one" else "other"
        else -> if (n == 1) "one" else "other"
    }
}

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
    // Vector drawables may use `@color/…` from colors.xml; decoded again once it is read.
    val colors = colorsOf(provider, environment)
    val result = remember(ref, bytes, density, loading, colors) {
        when {
            ref == null -> Result.failure(IllegalArgumentException("${a.signature.name} needs a resource such as R.drawable.logo or Res.drawable.logo"))
            loading -> Result.failure(StillLoading)
            bytes == null -> Result.failure(NoSuchElementException("Resource '$ref' not found; showing a placeholder"))
            else -> decode(bytes, density, colors)
        }
    }
    when (val error = result.exceptionOrNull()) {
        null, StillLoading -> Unit
        is NoSuchElementException -> a.warn(error.message ?: "Resource '$ref' not found")
        else -> a.report(error.message ?: "Cannot load resource '$ref'")
    }
    return result.getOrNull()
}

/** Key of the [ResourceProvider] in `Interpreter.hostValues`, for resources read outside composition such as fonts. */
internal const val RESOURCES_HOST_VALUE: String = "resources"

/**
 * `Font(R.font.x, weight, style)` or `Font(Res.font.x, …)` from the font file's bytes. Fonts are often built outside
 * composition (a top-level `FontFamily`), so the provider comes from the interpreter's host values. While the file
 * loads, or when it is missing, a stand-in is returned, which `FontFamily` leaves out; the code runs again once it loads.
 */
private fun font(a: Args): Any {
    val ref = (a["resId"] ?: a["resource"]) as? ResourceRef
    if (ref == null) {
        a.report("Font needs a resource such as R.font.inter or Res.font.inter")
        return StandIn("Font")
    }
    val provider = a.hostValue(RESOURCES_HOST_VALUE) as? ResourceProvider ?: ResourceProvider.Empty
    val environment = ResourceEnvironment()
    val bytes = provider.bytes(ref.type, ref.name, environment)
    return when {
        bytes != null -> try {
            platformFont("$ref#${bytes.contentHashCode()}", bytes, a.typed("weight", FontWeight.Normal), a.typed("style", FontStyle.Normal))
        } catch (e: Exception) {
            a.warn("Font '$ref' could not be loaded (${e.message}); the default font is used")
            StandIn("Font")
        }

        provider.isLoading(ref.type, ref.name, environment) -> {
            a.runAgainWhen { provider.bytes(ref.type, ref.name, environment) != null || !provider.isLoading(ref.type, ref.name, environment) }
            StandIn("Font")
        }

        else -> {
            a.warn("Font '$ref' not found; the default font is used")
            StandIn("Font")
        }
    }
}

/** A font from the bytes of a TTF or OTF file; [identity] names it in the platform's font cache. */
internal expect fun platformFont(identity: String, bytes: ByteArray, weight: FontWeight, style: FontStyle): Font

/** Colors of the `colors.xml` matching [environment], by name; empty while it is read or when there is none. */
@Composable
private fun colorsOf(provider: ResourceProvider, environment: ResourceEnvironment): Map<String, String> {
    val bytes = provider.bytes("values", "colors", environment)
    return remember(bytes) { bytes?.let { ColorsXml.parse(it.decodeToString()) } ?: emptyMap() }
}

/** A color written as `#RGB`, `#ARGB`, `#RRGGBB` or `#AARRGGBB`; null if it is not one. */
internal fun parseColor(value: String): Color? {
    val hex = value.removePrefix("#")
    val full = when (hex.length) {
        3 -> "FF" + hex.map { "$it$it" }.joinToString("")
        4 -> hex.map { "$it$it" }.joinToString("")
        6 -> "FF$hex"
        8 -> hex
        else -> return null
    }
    return full.toLongOrNull(16)?.let { Color(it) }
}

/** Marks a resource that is still being read; it shows a placeholder without a warning. */
private object StillLoading : Exception()

/**
 * Decodes vector drawable XML to an [ImageVector] and PNG, JPEG, WebP or BMP files to an [ImageBitmap]. Color
 * references in vectors (`@android:color/…`, `@color/…` from [colors], `?attr/…`) are replaced by literal colors first.
 */
private fun decode(bytes: ByteArray, density: Density, colors: Map<String, String>): Result<Any> = runCatching {
    when (xmlRoot(bytes)) {
        "vector" -> resolveColorReferences(bytes.decodeToString(), colors).encodeToByteArray()
            .decodeToImageVector(density)
        "svg" -> throw UnsupportedOperationException("SVG images are not supported yet")
        else -> bytes.decodeToImageBitmap()
    }
}

/**
 * The root element of an XML file, e.g. `vector`, after its declaration and comments, such as a license header; null
 * when [bytes] don't start like XML, e.g. a PNG.
 */
internal fun xmlRoot(bytes: ByteArray): String? {
    val text = bytes.decodeToString(0, minOf(bytes.size, XML_HEAD_BYTES)).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
    if (!text.startsWith("<")) return null
    var i = 0
    while (true) {
        i = text.indexOf('<', i)
        if (i < 0) return null
        i = when {
            text.startsWith("<?", i) -> text.indexOf("?>", i).takeIf { it >= 0 }?.plus(2) ?: return null
            text.startsWith("<!--", i) -> text.indexOf("-->", i).takeIf { it >= 0 }?.plus(3) ?: return null
            text.startsWith("<!", i) -> text.indexOf('>', i).takeIf { it >= 0 }?.plus(1) ?: return null
            else -> return XML_NAME.find(text, i + 1)?.takeIf { it.range.first == i + 1 }?.value
        }
    }
}

/** How much of a file [xmlRoot] reads; license comments are a few hundred bytes. */
private const val XML_HEAD_BYTES = 16 * 1024
private val XML_NAME = Regex("""[A-Za-z_][\w:.-]*""")

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
