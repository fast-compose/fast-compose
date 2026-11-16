package ma7moud3ly.fast.compose.ui

import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.parser.ast.AnnotationRef
import ma7moud3ly.fast.compose.parser.ast.FunDecl
import ma7moud3ly.fast.compose.parser.ast.Program

/**
 * One preview to render: [function] with the settings of one `@Preview` ([config]), called with [arguments], e.g. a
 * value of a `@PreviewParameter` provider. Render it with [Interpreter.Render].
 */
public data class PreviewEntry(
    val function: FunDecl,
    val config: PreviewConfig,
    val arguments: List<Any?> = emptyList(),
)

/**
 * Every preview of [program], in source order:
 * - one per `@Preview` of a `@Composable` function;
 * - one per `@Preview` of a multi-preview annotation: an annotation class annotated with `@Preview`s (directly or
 *   through other multi-preview annotations), from the same file or another loaded one, such as `@DevicePreviews`, and
 *   AndroidX's `@PreviewLightDark`, `@PreviewScreenSizes` and `@PreviewFontScale`; named after the function and the
 *   `@Preview`'s name, or the annotation's;
 * - for a function with a `@PreviewParameter(Provider::class, limit = n)` parameter, each of those once per value of
 *   the provider's `values`, up to `limit`.
 *
 * Invalid arguments and failing providers are reported through the interpreter and skipped.
 */
public fun Interpreter.previews(program: Program): List<PreviewEntry> =
    program.functions.filter { it.hasAnnotation("Composable") }.flatMap { function ->
        val configs = function.annotations.flatMap { annotation ->
            previewConfigs(function, annotation, owner = null, seen = emptySet())
                .map { if (annotation.name == "Preview") it else it.copy(name = "${function.name} - ${it.name}") }
        }
        if (configs.isEmpty()) return@flatMap emptyList()
        val arguments = previewArguments(function)
        configs.flatMap { config ->
            arguments.mapIndexed { i, args ->
                PreviewEntry(function, if (arguments.size > 1) config.copy(name = "${config.name} (${i + 1})") else config, args)
            }
        }
    }

/**
 * Settings of the previews [annotation] gives [function]: one for a `@Preview`, named after it or its [owner]
 * multi-preview annotation, or those of a multi-preview annotation, expanded; none for other annotations.
 */
private fun Interpreter.previewConfigs(
    function: FunDecl,
    annotation: AnnotationRef,
    owner: String?,
    seen: Set<String>,
): List<PreviewConfig> {
    val name = annotation.name
    if (name == "Preview") {
        val config = previewConfig(function, annotation)
        return listOf(if (owner != null && !annotation.hasName()) config.copy(name = owner) else config)
    }
    builtInMultiPreviews[name]?.let { return it }
    if (name in seen) return emptyList()
    val decl = classVisibleFrom(function, name) ?: return emptyList()
    return decl.annotations.flatMap { previewConfigs(function, it, owner = name, seen = seen + name) }
}

/** True when [this] `@Preview` sets its `name`, by name or as its first argument. */
private fun AnnotationRef.hasName(): Boolean = args.any { it.name == "name" } || args.firstOrNull()?.name == null && args.isNotEmpty()

/**
 * Argument lists to call [function] with: one per value of its `@PreviewParameter` provider, up to the annotation's
 * `limit`; a single empty list without one.
 */
private fun Interpreter.previewArguments(function: FunDecl): List<List<Any?>> {
    val annotation = function.params.firstNotNullOfOrNull { param -> param.annotations.firstOrNull { it.name == "PreviewParameter" } }
        ?: return listOf(emptyList())
    val provider = (annotation.args.firstOrNull { it.name == "provider" } ?: annotation.args.firstOrNull { it.name == null })?.value
        ?: return listOf(emptyList())
    val limitArg = annotation.args.firstOrNull { it.name == "limit" } ?: annotation.args.getOrNull(1)?.takeIf { it.name == null }
    val limit = limitArg?.let { (runCatching { evaluate(it.value) }.getOrNull() as? Number)?.toInt() } ?: Int.MAX_VALUE
    return previewParameterValues(function, provider).take(limit).map { listOf(it) }.ifEmpty { listOf(emptyList()) }
}

/** AndroidX's multi-preview annotations, as the previews they stand for. */
private val builtInMultiPreviews: Map<String, List<PreviewConfig>> = mapOf(
    "PreviewLightDark" to listOf(PreviewConfig(name = "Light"), PreviewConfig(name = "Dark", darkTheme = true)),
    "PreviewScreenSizes" to listOf(
        PreviewConfig(name = "Phone", widthDp = 411, heightDp = 891),
        PreviewConfig(name = "Phone - Landscape", widthDp = 891, heightDp = 411),
        PreviewConfig(name = "Unfolded Foldable", widthDp = 673, heightDp = 841),
        PreviewConfig(name = "Tablet", widthDp = 1280, heightDp = 800),
        PreviewConfig(name = "Desktop", widthDp = 1920, heightDp = 1080),
    ),
    "PreviewFontScale" to listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f).map {
        PreviewConfig(name = "${(it * 100).toInt()}%", fontScale = it)
    },
    // Dynamic colors need Android's wallpaper colors, so one preview stands for the four.
    "PreviewDynamicColors" to listOf(PreviewConfig(name = "Dynamic colors")),
)
