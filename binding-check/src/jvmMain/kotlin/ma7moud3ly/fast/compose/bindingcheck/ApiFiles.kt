package ma7moud3ly.fast.compose.bindingcheck

import java.io.File

/**
 * A function, constructor or property in an AndroidX API file. [owner] is the declaring class, e.g. `Brush.Companion`
 * or `TextKt`; [receiver] is the simple name of an extension receiver, e.g. `Modifier`.
 */
data class ApiFunction(
    val library: String,
    val owner: String,
    val name: String,
    val receiver: String?,
    val params: List<ApiParam>,
    val isComposable: Boolean,
    val isDeprecated: Boolean,
    val isConstructor: Boolean,
    val isProperty: Boolean = false,
) {
    /** True for top-level functions (declared in a `…Kt` file class) and constructors. */
    val isTopLevel: Boolean get() = isConstructor || owner.endsWith("Kt")

    val names: List<String> get() = params.map { it.name }

    override fun toString() = "$name(${names.joinToString()})"
}

data class ApiParam(val name: String, val type: String, val isOptional: Boolean) {
    val isVararg: Boolean get() = type.endsWith("...")

    /** True for lambda parameters, which a trailing lambda can fill. */
    val isFunction: Boolean
        get() = type.startsWith("kotlin.jvm.functions.Function") || type.startsWith("kotlin.coroutines.SuspendFunction")
}

/** Reads AndroidX API signature files, the `api/current.txt` files published with each library. */
object ApiFiles {

    /** Reads every `.txt` file in [dir]; the file name is the library name. */
    fun load(dir: File): List<ApiFunction> =
        dir.listFiles { file -> file.extension == "txt" }.orEmpty().sortedBy { it.name }
            .flatMap { parse(it.nameWithoutExtension, it.readText()) }

    fun parse(library: String, text: String): List<ApiFunction> {
        val functions = mutableListOf<ApiFunction>()
        var owner = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                raw.startsWith("  ") && !raw.startsWith("    ") && line.endsWith("{") -> owner = className(line)
                line.startsWith("method ") || line.startsWith("ctor ") -> parseFunction(library, owner, line)?.let { functions += it }
                line.startsWith("property ") -> functions += parseProperty(library, owner, line)
            }
        }
        return functions
    }

    private val classKeywords = setOf("class", "interface", "enum", "object", "@interface")

    private fun className(line: String): String {
        val tokens = split(line.removeSuffix("{").trim(), ' ')
        val keyword = tokens.indexOfFirst { it in classKeywords }
        return tokens.getOrNull(keyword + 1)?.substringBefore('<') ?: ""
    }

    private fun parseFunction(library: String, owner: String, line: String): ApiFunction? {
        val body = line.removeSuffix(";")
        val (nameStart, open) = parameterListStart(body) ?: return null
        val close = matchingParen(body, open)
        val prefix = body.substring(0, nameStart)
        if ("@BytecodeOnly" in prefix) return null
        var params = split(body.substring(open + 1, close), ',').filter { it.isNotBlank() }.map(::parseParam)
            .filterNot { it.name.isEmpty() && it.type.startsWith("kotlin.coroutines.Continuation") }
        val receiver = params.firstOrNull()?.takeIf { it.name.isEmpty() }?.let { simpleType(it.type) }
        if (receiver != null) params = params.drop(1)
        if (params.any { it.name.isEmpty() }) return null
        return ApiFunction(
            library = library,
            owner = owner,
            name = body.substring(nameStart, open).substringAfterLast('.'),
            receiver = receiver,
            params = params,
            isComposable = "@androidx.compose.runtime.Composable" in prefix,
            isDeprecated = "@Deprecated" in prefix,
            isConstructor = line.startsWith("ctor "),
        )
    }

    private fun parseProperty(library: String, owner: String, line: String): ApiFunction {
        val prefix = line.removeSuffix(";")
        return ApiFunction(
            library = library,
            owner = owner,
            name = prefix.substringAfterLast(' '),
            receiver = null,
            params = emptyList(),
            isComposable = "@androidx.compose.runtime.Composable" in prefix,
            isDeprecated = "@Deprecated" in prefix,
            isConstructor = false,
            isProperty = true,
        )
    }

    /** Start of the function name and its `(`, skipping annotation arguments such as `@IntRange(from=0)`. */
    private fun parameterListStart(text: String): Pair<Int, Int>? {
        var angle = 0
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '<' -> angle++
                '>' -> angle--
                '(' -> if (angle == 0) {
                    val start = text.lastIndexOf(' ', i - 1) + 1
                    if (!text.startsWith("@", start)) return start to i
                    i = matchingParen(text, i)
                }
            }
            i++
        }
        return null
    }

    private fun matchingParen(text: String, open: Int): Int {
        var depth = 0
        for (i in open until text.length) {
            if (text[i] == '(') depth++
            if (text[i] == ')' && --depth == 0) return i
        }
        return text.length
    }

    private fun parseParam(text: String): ApiParam {
        val tokens = split(text.trim(), ' ').filter { it.isNotEmpty() && !it.startsWith("@") }
        val isOptional = "optional" in tokens
        val parts = tokens - "optional"
        return if (parts.size >= 2) {
            ApiParam(parts.last(), parts.dropLast(1).joinToString(" "), isOptional)
        } else {
            ApiParam("", parts.firstOrNull().orEmpty(), isOptional)
        }
    }

    /** `androidx.compose.ui.graphics.Brush.Companion?` → `Brush.Companion`. */
    internal fun simpleType(type: String): String {
        val base = type.substringBefore('<').removeSuffix("...").removeSuffix("?")
        val segments = base.split('.')
        val first = segments.indexOfFirst { it.firstOrNull()?.isUpperCase() == true }
        return if (first < 0) base else segments.drop(first).joinToString(".")
    }

    /** Splits [text] at [separator] outside of `<>` and `()`. */
    private fun split(text: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        for ((i, c) in text.withIndex()) {
            when (c) {
                '<', '(' -> depth++
                '>', ')' -> depth--
                separator -> if (depth == 0) {
                    parts += text.substring(start, i)
                    start = i + 1
                }
            }
        }
        parts += text.substring(start)
        return parts.map { it.trim() }
    }
}
