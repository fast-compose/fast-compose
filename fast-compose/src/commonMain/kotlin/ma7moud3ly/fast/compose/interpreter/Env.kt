package ma7moud3ly.fast.compose.interpreter

/**
 * Variable scope; lookups fall back to [parent]. [receiver] is the implicit `this` of this scope, if any, and [label]
 * the name `this@label` reaches it by: the lambda's label or call, or the function's name. [scope] is the file whose
 * code runs in this scope.
 *
 * Scopes are created for every block and call, so their maps are only allocated once a name is defined.
 */
public class Env internal constructor(
    private val parent: Env?,
    internal val receiver: Any? = NoReceiver,
    internal val scope: FileScope? = parent?.scope,
    internal val label: String? = null,
) {

    private var values: MutableMap<String, Any?>? = null
    private var readOnly: MutableSet<String>? = null
    private var cachedReceivers: List<Any?>? = null

    public fun define(name: String, value: Any?, mutable: Boolean = true) {
        (values ?: HashMap<String, Any?>().also { values = it })[name] = value
        if (mutable) readOnly?.remove(name) else (readOnly ?: HashSet<String>().also { readOnly = it }) += name
    }

    internal fun defineDelegated(name: String, delegate: Any?) {
        define(name, Delegated(delegate))
    }

    private fun owner(name: String): Env? {
        var env: Env? = this
        while (env != null) {
            if (env.values?.containsKey(name) == true) return env
            env = env.parent
        }
        return null
    }

    public fun contains(name: String): Boolean = owner(name) != null

    public fun get(name: String): Any? {
        val value = find(name)
        if (value === Unresolved) throw InterpreterException("Unresolved reference '$name'")
        return value
    }

    /** Value of [name], or [Unresolved] when no scope defines it; one walk instead of [contains] then [get]. */
    internal fun find(name: String): Any? {
        val env = owner(name) ?: return Unresolved
        val value = env.values!![name]
        return if (value is Delegated) value.get() else value
    }

    public fun set(name: String, value: Any?) {
        val env = owner(name) ?: throw InterpreterException("Unresolved reference '$name'")
        if (env.readOnly?.contains(name) == true) throw InterpreterException("Val cannot be reassigned: '$name'")
        val values = env.values!!
        val current = values[name]
        if (current is Delegated) current.set(value) else values[name] = value
    }

    internal fun child(receiver: Any? = NoReceiver, label: String? = null): Env = Env(this, receiver, label = label)

    /** The receiver of the innermost scope labeled [label], e.g. `this@SharedTransitionLayout`; [Unresolved] if none. */
    internal fun labeledReceiver(label: String): Any? {
        var env: Env? = this
        while (env != null) {
            if (env.receiver !== NoReceiver && env.label == label) return env.receiver
            env = env.parent
        }
        return Unresolved
    }

    /** Implicit receivers in scope, innermost first; a scope without a receiver shares its parent's list. */
    internal val receivers: List<Any?>
        get() = cachedReceivers ?: computeReceivers().also { cachedReceivers = it }

    private fun computeReceivers(): List<Any?> {
        val inherited = parent?.receivers ?: emptyList()
        return if (receiver === NoReceiver) inherited else listOf(receiver) + inherited
    }
}
