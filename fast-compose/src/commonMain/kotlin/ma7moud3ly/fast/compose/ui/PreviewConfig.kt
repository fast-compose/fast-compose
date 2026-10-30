package ma7moud3ly.fast.compose.ui

import androidx.compose.ui.graphics.Color
import ma7moud3ly.fast.compose.interpreter.BindingPack
import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.interpreter.InterpreterException
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.parser.ast.AnnotationRef
import ma7moud3ly.fast.compose.parser.ast.FunDecl

/** Settings from one `@Preview` annotation. Sizes are in dp; null means "wrap content". */
public data class PreviewConfig(
    val name: String,
    val group: String? = null,
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val locale: String? = null,
    val fontScale: Float = 1f,
    val showBackground: Boolean = false,
    val backgroundColor: Color? = null,
    val darkTheme: Boolean = false,
)

/** `@Preview` parameter names in declaration order, for positional arguments. */
private val previewParams = listOf(
    "name", "group", "apiLevel", "widthDp", "heightDp", "locale", "fontScale",
    "showSystemUi", "showBackground", "backgroundColor", "uiMode", "device", "wallpaper",
)

private const val UI_MODE_NIGHT_MASK = 0x30
private const val UI_MODE_NIGHT_YES = 0x20

/** Reads the arguments of [annotation] on [function]. Invalid arguments are reported and ignored. */
public fun Interpreter.previewConfig(function: FunDecl, annotation: AnnotationRef): PreviewConfig {
    val values = mutableMapOf<String, Any?>()
    annotation.args.forEachIndexed { i, arg ->
        val name = arg.name ?: previewParams.getOrNull(i) ?: return@forEachIndexed
        try {
            values[name] = evaluate(arg.value)
        } catch (e: InterpreterException) {
            report("@Preview $name: ${e.message}")
        }
    }
    val device = (values["device"] as? String)?.let { deviceSize(it) }
    val uiMode = (values["uiMode"] as? Number)?.toInt() ?: 0
    return PreviewConfig(
        name = values["name"] as? String ?: function.name,
        group = values["group"] as? String,
        widthDp = (values["widthDp"] as? Number)?.toInt()?.takeIf { it > 0 } ?: device?.first,
        heightDp = (values["heightDp"] as? Number)?.toInt()?.takeIf { it > 0 } ?: device?.second,
        locale = (values["locale"] as? String)?.takeIf { it.isNotEmpty() },
        fontScale = (values["fontScale"] as? Number)?.toFloat()?.takeIf { it > 0f } ?: 1f,
        showBackground = values["showBackground"] == true,
        backgroundColor = when (val color = values["backgroundColor"]) {
            is Long -> if (color != 0L) Color(color) else null
            is Int -> if (color != 0) Color(color) else null
            is Color -> color
            else -> null
        },
        darkTheme = (uiMode and UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES,
    )
}

/** Width and height in dp from a device spec like `spec:width=411dp,height=891dp`, or null. */
private fun deviceSize(device: String): Pair<Int, Int>? {
    fun value(key: String) = Regex("$key=(\\d+)").find(device)?.groupValues?.get(1)?.toIntOrNull()
    val width = value("width") ?: return null
    val height = value("height") ?: return null
    return width to height
}

/** Marker objects for `Configuration.UI_MODE_*` and `Devices.*` used in `@Preview` arguments. */
private object ConfigurationConstants
private object DevicesConstants

/** Constants commonly passed to `@Preview`: night mode flags and device specs. */
internal object PreviewConstants : BindingPack {
    override fun install(registry: Registry): Unit = registry.previewConstants()
}

private fun Registry.previewConstants() {
    val uiModes = arrayOf<Pair<String, Any?>>(
        "UI_MODE_NIGHT_YES" to UI_MODE_NIGHT_YES,
        "UI_MODE_NIGHT_NO" to 0x10,
        "UI_MODE_NIGHT_UNDEFINED" to 0,
        "UI_MODE_TYPE_NORMAL" to 0x01,
    )
    value("Configuration", ConfigurationConstants)
    constants<ConfigurationConstants>(*uiModes)
    for ((name, mode) in uiModes) value(name, mode)

    value("Devices", DevicesConstants)
    constants<DevicesConstants>(
        "DEFAULT" to "",
        "PHONE" to "spec:width=411dp,height=891dp",
        "FOLDABLE" to "spec:width=673dp,height=841dp",
        "TABLET" to "spec:width=1280dp,height=800dp",
        "DESKTOP" to "spec:width=1920dp,height=1080dp",
        "NEXUS_5" to "spec:width=360dp,height=640dp",
        "PIXEL_4" to "spec:width=393dp,height=851dp",
        "PIXEL_7" to "spec:width=412dp,height=915dp",
        "PIXEL_TABLET" to "spec:width=1280dp,height=800dp",
    )
}
