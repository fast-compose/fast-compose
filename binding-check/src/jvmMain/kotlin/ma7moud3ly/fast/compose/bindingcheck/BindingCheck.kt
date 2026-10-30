package ma7moud3ly.fast.compose.bindingcheck

import ma7moud3ly.fast.compose.interpreter.Registry.BindingInfo
import ma7moud3ly.fast.compose.interpreter.Signature

/** A problem with one binding. Errors fail the check; warnings are listed in the report. */
data class Finding(val binding: String, val message: String, val isError: Boolean) {
    override fun toString() = "$binding: $message"
}

/** How many APIs of one [category] in [library] have a binding; [missing] lists the others. */
data class Coverage(val category: String, val library: String, val bound: Int, val total: Int, val missing: List<String>) {
    val percent: Int get() = percent(bound, total)
}

private fun percent(bound: Int, total: Int): Int = if (total == 0) 100 else bound * 100 / total

class BindingReport(val findings: List<Finding>, val unmatched: List<String>, val coverage: List<Coverage>) {
    val errors: List<Finding> get() = findings.filter { it.isError }
    val warnings: List<Finding> get() = findings.filterNot { it.isError }

    fun summary(): String = "Binding check: ${errors.size} errors, ${warnings.size} warnings, ${unmatched.size} not in the API files. " +
        coverage.groupBy { it.category }.entries.joinToString(", ", "Coverage: ") { (category, items) ->
            "$category ${percent(items.sumOf { it.bound }, items.sumOf { it.total })}%"
        }

    fun text(): String = buildString {
        appendLine(summary())
        section("Errors", errors.map { it.toString() })
        section("Warnings", warnings.map { it.toString() })
        section("Not in the API files", unmatched)
        for ((category, items) in coverage.groupBy { it.category }) {
            appendLine()
            appendLine("Coverage of $category")
            for (c in items) {
                appendLine("  ${c.library}: ${c.bound} of ${c.total} (${c.percent}%)")
                if (c.missing.isNotEmpty()) appendLine("    missing: ${c.missing.joinToString()}")
            }
        }
    }

    private fun StringBuilder.section(title: String, lines: List<String>) {
        appendLine()
        appendLine("$title (${lines.size})")
        lines.forEach { appendLine("  $it") }
    }
}

/**
 * Compares binding signatures with the Compose API in [api]. For each binding it picks the Compose overload with the
 * same name and receiver that fits its parameters best, then reports:
 * - errors: unknown parameters, missing required parameters, a different parameter order, or a trailing lambda
 *   that fills a different parameter than in Compose;
 * - warnings: missing optional parameters, positional parameters that skip a Compose parameter, a Compose trailing
 *   lambda the binding does not take, and bindings that match a deprecated Compose function.
 *
 * Findings whose text is in [allowlist] are left out. Coverage counts composables, `Modifier` functions, members of
 * `*Defaults` objects and `remember*` functions; names in [excluded] (`Name` or `Owner.name`) are not counted.
 */
class BindingCheck(
    private val api: List<ApiFunction>,
    private val allowlist: Set<String> = emptySet(),
    private val excluded: Set<String> = emptySet(),
) {

    private val byName = api.filterNot { it.isProperty }.groupBy { it.name }

    fun run(bindings: List<BindingInfo>): BindingReport {
        val findings = mutableListOf<Finding>()
        val unmatched = mutableListOf<String>()
        for (binding in bindings) {
            val signature = binding.signature
            if (signature.params.isEmpty() && signature.namedOnly.isEmpty()) continue
            val label = label(binding)
            val candidates = candidates(binding)
            if (candidates.isEmpty()) unmatched += label else findings += compare(label, signature, candidates)
        }
        return BindingReport(
            findings = findings.filterNot { it.toString() in allowlist }.distinct().sortedBy { it.toString() },
            unmatched = unmatched.distinct().sorted(),
            coverage = coverage(bindings),
        )
    }

    private fun label(binding: BindingInfo): String = (binding.receiver?.let { "$it." } ?: "") + binding.signature

    /** Compose functions with the binding's name and receiver. */
    private fun candidates(binding: BindingInfo): List<ApiFunction> {
        val sameName = byName[binding.signature.name].orEmpty()
        val receiver = binding.receiver ?: return sameName.filter { it.isTopLevel && it.receiver == null }.ifEmpty { sameName.filter { it.isTopLevel } }
        return sameName.filter { it.receiver?.substringAfterLast('.') == receiver || (!it.isTopLevel && it.owner.substringAfterLast('.') == receiver) }
    }

    private fun compare(label: String, signature: Signature, candidates: List<ApiFunction>): List<Finding> {
        val ours = signature.params + signature.namedOnly
        val best = candidates.minWith(
            compareBy<ApiFunction>(
                { fn -> signature.params.count { it !in fn.names } },
                { fn -> ours.count { it !in fn.names } },
                { fn -> fn.params.count { !it.isOptional && !it.isVararg && it.name !in ours } },
                { fn -> signature.params.filter { it in fn.names }.zipWithNext().count { (a, b) -> fn.names.indexOf(a) > fn.names.indexOf(b) } },
                { fn -> fn.params.count { it.name !in ours } },
                { fn -> fn.isDeprecated },
            ),
        )
        val findings = mutableListOf<Finding>()
        fun error(message: String) = findings.add(Finding(label, message, isError = true))
        fun warning(message: String) = findings.add(Finding(label, message, isError = false))

        val known = candidates.flatMap { it.names }.toSet()
        for (name in ours) if (name !in known) error("unknown parameter '$name'" + suggestion(name, known))

        for (param in best.params) {
            if (!param.isOptional && !param.isVararg && param.name !in ours) error("missing required parameter '${param.name}'")
        }

        val positional = signature.params.filter { it in best.names }
        val inComposeOrder = positional.sortedBy { best.names.indexOf(it) }
        if (positional != inComposeOrder) {
            error("parameter order (${positional.joinToString()}) differs from Compose (${inComposeOrder.joinToString()})")
        }

        val last = signature.params.lastOrNull()
        val composeLast = best.params.lastOrNull()
        if (last != null && last != signature.varargParam && composeLast != null && composeLast.isFunction && composeLast.name != last) {
            val lastIsLambda = best.params.firstOrNull { it.name == last }?.isFunction == true
            if (composeLast.name in ours || lastIsLambda) {
                error("a trailing lambda fills '$last', Compose fills '${composeLast.name}'")
            } else {
                warning("a trailing lambda fills '$last', Compose fills '${composeLast.name}', which this binding does not take")
            }
        }
        if (best.isDeprecated) warning("matches a deprecated Compose function")

        // The last parameter is usually filled by a trailing lambda, so only the ones before it are compared.
        val passedPositionally = if (last != null && last == composeLast?.name) signature.params.dropLast(1) else signature.params
        if (positional == inComposeOrder && passedPositionally.size > 1) {
            val skipped = best.names.take(passedPositionally.size).firstOrNull { it !in passedPositionally }
            if (skipped != null) warning("positional parameters skip '$skipped'")
        }

        val missingOptional = best.params.filter { it.isOptional && it.name !in ours }.map { it.name }
        if (missingOptional.isNotEmpty()) warning("missing optional parameters: ${missingOptional.joinToString()}")
        return findings
    }

    private fun suggestion(name: String, known: Set<String>): String =
        known.minByOrNull { distance(it, name) }?.takeIf { distance(it, name) <= 3 }?.let { ". Did you mean '$it'?" } ?: ""

    /** Coverage of each API category per library. */
    private fun coverage(bindings: List<BindingInfo>): List<Coverage> {
        val current = api.filterNot { it.isDeprecated || it.name.isEmpty() }
        fun boundNames(vararg kinds: BindingInfo.Kind, receiver: (String?) -> Boolean = { it == null }) =
            bindings.filter { it.kind in kinds && receiver(it.receiver) }.map { it.signature.name }.toSet()

        val composables = boundNames(BindingInfo.Kind.COMPOSABLE)
        val functions = boundNames(BindingInfo.Kind.COMPOSABLE, BindingInfo.Kind.FUNCTION)
        val modifiers = boundNames(BindingInfo.Kind.MEMBER, BindingInfo.Kind.COMPOSABLE_MEMBER) { it == "Modifier" }
        val defaults = bindings.filter { it.receiver?.endsWith("Defaults") == true }.map { "${it.receiver}.${it.signature.name}" }.toSet()

        return category("composables", composables, current.filter { it.isComposable && it.isTopLevel && it.receiver == null && it.name.first().isUpperCase() }) +
            category("modifiers", modifiers, current.filter { it.receiver == "Modifier" && it.name.first().isLowerCase() }) +
            category("defaults", defaults, current.filter { it.owner.endsWith("Defaults") && !it.isAccessor() }) { "${it.owner}.${it.name}" } +
            category("remember functions", functions, current.filter { it.isTopLevel && it.name.startsWith("remember") })
    }

    private fun category(
        name: String,
        bound: Set<String>,
        functions: List<ApiFunction>,
        key: (ApiFunction) -> String = { it.name },
    ): List<Coverage> = functions
        .filterNot { key(it) in excluded || it.name in excluded }
        .groupBy { it.library }
        .map { (library, items) ->
            val keys = items.map(key).toSortedSet()
            Coverage(name, library, keys.count { it in bound }, keys.size, keys.filterNot { it in bound })
        }
        .sortedBy { it.library }

    /** Getters and setters of properties, which are counted as the properties themselves. */
    private fun ApiFunction.isAccessor(): Boolean =
        !isProperty && params.size <= 1 && Regex("(get|set|is)[A-Z].*").matches(name)

    private fun distance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = temp
            }
        }
        return dp[b.length]
    }
}
