package ma7moud3ly.fast.compose.bindingcheck

import java.io.File

/**
 * Finds binding parameters that a signature declares but the binding never reads.
 *
 * Reads binding sources as text. For each `composable`, `function`, `member`, … registration it takes the signature
 * string and the lambda body. A parameter is read when its name appears as a string literal in the body or in a
 * helper function the body calls, or through an `Args` getter with a default name, such as `content()` or `modifier()`.
 */
object UnusedParameters {

    data class Finding(val binding: String, val parameter: String, val location: String) {
        val key: String get() = "$binding.$parameter"
        override fun toString() = "$key ($location)"
    }

    fun find(sources: List<File>): List<Finding> {
        val texts = sources.associateWith { it.readText().replace("\r\n", "\n") }
        val constants = texts.values.flatMap { constants(it).toList() }.toMap()
        val helpers = texts.values.flatMap { helpers(it) }.groupBy({ it.first }, { it.second })
        val findings = mutableListOf<Finding>()
        for ((file, text) in texts) {
            for (match in REGISTRATION.findAll(text)) {
                val open = text.lastIndexOf('(', match.range.last)
                val close = matchBrace(text, open)
                val signature = literals(text.substring(open + 1, close), constants)
                if ('(' !in signature) continue
                var start = close + 1
                while (start < text.length && text[start].isWhitespace()) start++
                if (start >= text.length || text[start] != '{') continue
                val body = expand(text.substring(start, matchBrace(text, start) + 1), helpers, mutableSetOf(), 0)
                val used = STRING_NAME.findAll(body).map { it.groupValues[1] }.toMutableSet()
                DEFAULT_NAMES.forEach { (getter, name) -> if (Regex("\\b$getter(<[^>]*>)?\\(\\)").containsMatchIn(body)) used += name }
                val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                val name = signature.substringBefore('(')
                parameters(signature).filter { it !in used }.forEach { findings += Finding(name, it, "${file.name}:$line") }
            }
        }
        return findings
    }

    private val REGISTRATION = Regex("""\b(composable|function|suspendFunction|member|composableMember|suspendMember)(<[^(]*?>)?\(\s*""")
    private val STRING_NAME = Regex(""""(\w+)"""")
    private val LITERAL = Regex(""""((?:[^"\\]|\\.)*)"|\b([A-Z_][A-Z0-9_]+)\b""")
    private val CONSTANT = Regex("""const val (\w+)\s*=\s*""")
    private val HELPER = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>*]+\.)?(\w+)\s*\(([^)]*)\)""")
    private val CALL = Regex("""\b(\w+)\s*[(<]""")

    /** `Args` getters whose parameter name has a default. */
    private val DEFAULT_NAMES = mapOf(
        "content" to "content", "content1" to "content", "scopedContent" to "content", "scopedContent1" to "content",
        "scopedContent2" to "content", "scopedAction" to "content", "modifier" to "modifier",
        "calculation" to "calculation", "suspendBlock" to "block",
    )

    /** Parameter names of a signature, without `vararg`, `*` and types. */
    private fun parameters(signature: String): List<String> =
        signature.substring(signature.indexOf('(') + 1, signature.lastIndexOf(')')).split(',')
            .map { it.trim().removePrefix("vararg ").substringBefore(':').trim() }
            .filter { it.isNotEmpty() && it != "*" }

    /** Text of the string literals in [expr], with constant names replaced by their values. */
    private fun literals(expr: String, constants: Map<String, String>): String = buildString {
        for (m in LITERAL.findAll(expr)) {
            val literal = m.groups[1]
            if (literal != null) append(literal.value) else constants[m.groupValues[2]]?.let { append(it) }
        }
    }

    /** `const val` string constants, such as the shared `Text` signature. */
    private fun constants(text: String): Map<String, String> = CONSTANT.findAll(text).associate { m ->
        val end = text.indexOf("\n\n", m.range.last).let { if (it < 0) text.length else it }
        m.groupValues[1] to literals(text.substring(m.range.last + 1, end), emptyMap())
    }

    /** Functions that take or extend `Args`, with their bodies. */
    private fun helpers(text: String): List<Pair<String, String>> = HELPER.findAll(text).mapNotNull { m ->
        if ("Args" !in m.value) return@mapNotNull null
        var start = m.range.last + 1
        while (start < text.length && text[start] != '{' && text[start] != '=') start++
        if (start >= text.length) return@mapNotNull null
        val body = if (text[start] == '{') {
            text.substring(start, matchBrace(text, start) + 1)
        } else {
            text.substring(start, text.indexOf("\n\n", start).let { if (it < 0) text.length else it })
        }
        m.groupValues[1] to body
    }.toList()

    /** [body] followed by the bodies of the helpers it calls, recursively. */
    private fun expand(body: String, helpers: Map<String, List<String>>, seen: MutableSet<String>, depth: Int): String {
        if (depth > 3) return body
        val text = StringBuilder(body)
        for (name in CALL.findAll(body).map { it.groupValues[1] }.toSet()) {
            val bodies = helpers[name] ?: continue
            if (!seen.add(name)) continue
            bodies.forEach { text.append(expand(it, helpers, seen, depth + 1)) }
        }
        return text.toString()
    }

    /** Index of the bracket closing the one at [open], skipping strings, chars and comments. */
    private fun matchBrace(text: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < text.length) {
            when {
                text[i] == '"' || text[i] == '\'' -> { i = skipString(text, i); continue }
                text.startsWith("//", i) -> { i = text.indexOf('\n', i).let { if (it < 0) text.length else it }; continue }
                text.startsWith("/*", i) -> { i = text.indexOf("*/", i) + 2; continue }
                text[i] == '(' || text[i] == '{' -> depth++
                text[i] == ')' || text[i] == '}' -> if (--depth == 0) return i
            }
            i++
        }
        error("Unclosed bracket at $open")
    }

    /** Index after the string or char literal starting at [start]. */
    private fun skipString(text: String, start: Int): Int {
        if (text.startsWith("\"\"\"", start)) return text.indexOf("\"\"\"", start + 3) + 3
        val quote = text[start]
        var i = start + 1
        while (text[i] != quote) {
            if (text[i] == '\\') i++
            i++
        }
        return i + 1
    }
}
