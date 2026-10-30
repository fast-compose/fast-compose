package ma7moud3ly.fast.compose.interpreter

import androidx.compose.runtime.Composable

public typealias ComposableBinding = @Composable (Args) -> Any?
public typealias FunctionBinding = (Args) -> Any?
public typealias SuspendFunctionBinding = suspend (Args) -> Any?
public typealias MemberBinding = (receiver: Any?, Args) -> Any?
public typealias ComposableMemberBinding = @Composable (receiver: Any?, Args) -> Any?
public typealias SuspendMemberBinding = suspend (receiver: Any?, Args) -> Any?
public typealias SetterBinding = (receiver: Any?, value: Any?) -> Unit

/**
 * Maps names in user code to real APIs. Each binding is registered with a [Signature] such as
 * `"Text(text, modifier, color)"`, and reads its arguments by name from [Args].
 *
 * - [composable]: UI calls like `Text(...)`
 * - [function], [suspendFunction]: plain calls like `Color(0xFF...)` and suspend calls like `delay(500)`
 * - [value]: global names like `Modifier`
 * - [member], [composableMember], [suspendMember]: members and extensions like `Modifier.padding(...)`,
 *   `MaterialTheme.colorScheme` or `animatable.animateTo(1f)`; properties are members with no parameters
 * - [properties], [constants]: read-only members of a type
 * - [fallbackMember]: any other member of a type
 * - [setter]: assignable members like `scaleX = 2f` in `graphicsLayer { }` or `flow.value = 1`
 * - [type]: type names usable in `is`, `as` and extension receivers
 * - [hint]: message shown when an unknown name is used
 *
 * Registering the same name again with another signature adds an overload; calls pick the first overload
 * whose signature fits the arguments. Lookups check this registry first and then [parent], so additions
 * can be layered on a shared preset without copying it. Groups of bindings are added with [install].
 */
public class Registry(private val parent: Registry? = null) {

    /** A binding found for a call, with the signature its arguments are matched against. */
    public class Bound<B>(public val signature: Signature, public val binding: B)

    /**
     * A registered binding. [accepts] checks the receiver of members; top-level bindings accept anything.
     * [receiver] is the simple name of the receiver type of members, e.g. `Modifier`.
     */
    public class Entry<B>(public val signature: Signature, public val accepts: (Any?) -> Boolean, public val binding: B, public val receiver: String? = null) {
        internal val bound = Bound(signature, binding)
    }

    /** A registered binding as listed by [bindings], for tools such as API checks. */
    public class BindingInfo(public val kind: Kind, public val signature: Signature, public val receiver: String?) {
        public enum class Kind { COMPOSABLE, FUNCTION, SUSPEND_FUNCTION, MEMBER, COMPOSABLE_MEMBER, SUSPEND_MEMBER }
    }

    public class FallbackEntry(public val accepts: (Any?) -> Boolean, public val binding: (receiver: Any?, name: String, Args) -> Any?)

    private val composables = mutableMapOf<String, MutableList<Entry<ComposableBinding>>>()
    private val functions = mutableMapOf<String, MutableList<Entry<FunctionBinding>>>()
    private val suspendFunctions = mutableMapOf<String, MutableList<Entry<SuspendFunctionBinding>>>()
    private val members = mutableMapOf<String, MutableList<Entry<MemberBinding>>>()
    private val composableMembers = mutableMapOf<String, MutableList<Entry<ComposableMemberBinding>>>()
    private val suspendMembers = mutableMapOf<String, MutableList<Entry<SuspendMemberBinding>>>()
    private val values = mutableMapOf<String, Any?>()
    private val types = mutableMapOf<String, (Any?) -> Boolean>()
    private val fallbacks = mutableListOf<FallbackEntry>()
    private val hints = mutableMapOf<String, String>()
    private val setters = mutableMapOf<String, MutableList<Entry<SetterBinding>>>()

    // ---------- Registration ----------

    public fun install(vararg packs: BindingPack) {
        for (pack in packs) pack.install(this)
    }

    public fun composable(signature: String, binding: ComposableBinding): Unit = add(composables, signature, anyReceiver, binding)

    public fun function(signature: String, binding: FunctionBinding): Unit = add(functions, signature, anyReceiver, binding)

    public fun suspendFunction(signature: String, binding: SuspendFunctionBinding): Unit = add(suspendFunctions, signature, anyReceiver, binding)

    public fun value(name: String, value: Any?) {
        values[name] = value
    }

    public inline fun <reified T> member(signature: String, noinline binding: (T, Args) -> Any?) {
        addMember(signature, { it is T }, T::class.simpleName) { receiver, args -> binding(receiver as T, args) }
    }

    public inline fun <reified T> composableMember(signature: String, noinline binding: @Composable (T, Args) -> Any?) {
        addComposableMember(signature, { it is T }, T::class.simpleName) { receiver, args -> binding(receiver as T, args) }
    }

    public inline fun <reified T> suspendMember(signature: String, noinline binding: suspend (T, Args) -> Any?) {
        addSuspendMember(signature, { it is T }, T::class.simpleName) { receiver, args -> binding(receiver as T, args) }
    }

    /** Registers read-only properties of [T], e.g. `properties<ColorScheme>("primary" to { it.primary })`. */
    public inline fun <reified T> properties(vararg entries: Pair<String, (T) -> Any?>) {
        for ((name, getter) in entries) member<T>(name) { receiver, _ -> getter(receiver) }
    }

    /** Registers constant members of [T], e.g. `constants<Color.Companion>("Red" to Color.Red)`. */
    public inline fun <reified T> constants(vararg entries: Pair<String, Any?>) {
        for ((name, value) in entries) member<T>(name) { _, _ -> value }
    }

    /** Handles any member of [T] not registered by name, e.g. icons that are not bundled. */
    public inline fun <reified T> fallbackMember(noinline binding: (T, String, Args) -> Any?) {
        addFallback(FallbackEntry({ it is T }) { receiver, name, args -> binding(receiver as T, name, args) })
    }

    public inline fun <reified T> type(name: String) {
        addType(name) { it is T }
    }

    /** Makes member [name] of [T] assignable, e.g. `setter<MutableStateFlow<*>>("value") { flow, value -> … }`. */
    public inline fun <reified T> setter(name: String, noinline binding: (T, Any?) -> Unit) {
        addSetter(name, { it is T }, T::class.simpleName) { receiver, value -> binding(receiver as T, value) }
    }

    public fun addSetter(name: String, accepts: (Any?) -> Boolean, receiver: String? = null, binding: SetterBinding): Unit =
        add(setters, name, accepts, binding, receiver)

    public fun hint(name: String, message: String) {
        hints[name] = message
    }

    public fun addMember(signature: String, accepts: (Any?) -> Boolean, receiver: String? = null, binding: MemberBinding): Unit =
        add(members, signature, accepts, binding, receiver)

    public fun addComposableMember(signature: String, accepts: (Any?) -> Boolean, receiver: String? = null, binding: ComposableMemberBinding): Unit =
        add(composableMembers, signature, accepts, binding, receiver)

    public fun addSuspendMember(signature: String, accepts: (Any?) -> Boolean, receiver: String? = null, binding: SuspendMemberBinding): Unit =
        add(suspendMembers, signature, accepts, binding, receiver)

    public fun addFallback(entry: FallbackEntry) {
        fallbacks += entry
    }

    public fun addType(name: String, check: (Any?) -> Boolean) {
        types[name] = check
    }

    private fun <B> add(
        table: MutableMap<String, MutableList<Entry<B>>>,
        signature: String,
        accepts: (Any?) -> Boolean,
        binding: B,
        receiver: String? = null,
    ) {
        val parsed = Signature.parse(signature)
        table.getOrPut(parsed.name) { mutableListOf() } += Entry(parsed, accepts, binding, receiver)
    }

    /** Bindings registered directly in this registry, without its parents. */
    public fun bindings(): List<BindingInfo> = listOf(
        BindingInfo.Kind.COMPOSABLE to composables,
        BindingInfo.Kind.FUNCTION to functions,
        BindingInfo.Kind.SUSPEND_FUNCTION to suspendFunctions,
        BindingInfo.Kind.MEMBER to members,
        BindingInfo.Kind.COMPOSABLE_MEMBER to composableMembers,
        BindingInfo.Kind.SUSPEND_MEMBER to suspendMembers,
    ).flatMap { (kind, table) -> table.values.flatten().map { BindingInfo(kind, it.signature, it.receiver) } }

    // ---------- Lookup ----------

    public fun findComposable(name: String, call: CallShape): Bound<ComposableBinding>? = choose(collect(name) { it.composables }, NoReceiver, call)

    public fun findFunction(name: String, call: CallShape): Bound<FunctionBinding>? = choose(collect(name) { it.functions }, NoReceiver, call)

    public fun findSuspendFunction(name: String, call: CallShape): Bound<SuspendFunctionBinding>? =
        choose(collect(name) { it.suspendFunctions }, NoReceiver, call)

    /** Finds a member by name in this registry or its parents, then falls back to a [fallbackMember] for the receiver. */
    public fun findMember(receiver: Any?, name: String, call: CallShape): Bound<MemberBinding>? {
        choose(collect(name) { it.members }, receiver, call)?.let { return it }
        val fallback = findFallback(receiver) ?: return null
        return Bound(Signature.lenient(name)) { r, args -> fallback.binding(r, name, args) }
    }

    public fun findComposableMember(receiver: Any?, name: String, call: CallShape): Bound<ComposableMemberBinding>? =
        choose(collect(name) { it.composableMembers }, receiver, call)

    public fun findSuspendMember(receiver: Any?, name: String, call: CallShape): Bound<SuspendMemberBinding>? =
        choose(collect(name) { it.suspendMembers }, receiver, call)

    public fun hasValue(name: String): Boolean = name in values || parent?.hasValue(name) == true

    public fun findValue(name: String): Any? = if (name in values) values[name] else parent?.findValue(name)

    public fun findType(name: String): ((Any?) -> Boolean)? = types[name] ?: parent?.findType(name)

    public fun findHint(name: String): String? = hints[name] ?: parent?.findHint(name)

    /** True if [name] is a top-level composable, function, suspend function or value. */
    public fun isGlobalName(name: String): Boolean =
        name in composables || name in functions || name in suspendFunctions || name in values || parent?.isGlobalName(name) == true

    /** True if [receiver] has a member called [name], without calling it. */
    /** The setter of member [name] for [receiver], or null when the member is not assignable. */
    public fun findSetter(receiver: Any?, name: String): SetterBinding? =
        collect(name) { it.setters }.firstOrNull { it.accepts(receiver) }?.binding

    public fun hasMember(receiver: Any?, name: String): Boolean =
        collect(name) { it.members }.any { it.accepts(receiver) } || findFallback(receiver) != null

    /** Top-level names of this registry and its parents, used for "did you mean" suggestions. */
    public fun globalNames(): Set<String> = ownGlobalNames() + parent?.globalNames().orEmpty()

    /** Top-level names registered directly in this registry, without its parents. */
    public fun ownGlobalNames(): Set<String> = composables.keys + functions.keys + suspendFunctions.keys + values.keys

    /** Entries named [name] here and in the parents; copies a list only when both have some. */
    private fun <B> collect(name: String, table: (Registry) -> Map<String, List<Entry<B>>>): List<Entry<B>> {
        val own = table(this)[name]
        val inherited = parent?.collect(name, table)
        return when {
            own.isNullOrEmpty() -> inherited.orEmpty()
            inherited.isNullOrEmpty() -> own
            else -> own + inherited
        }
    }

    /** Picks the first entry for [receiver] whose signature fits [call], or the first entry for [receiver] if none fits. */
    private fun <B> choose(entries: List<Entry<B>>, receiver: Any?, call: CallShape): Bound<B>? {
        var first: Entry<B>? = null
        var count = 0
        for (entry in entries) {
            if (!entry.accepts(receiver)) continue
            if (first == null) first = entry
            count++
        }
        if (count <= 1) return first?.bound
        for (entry in entries) {
            if (entry.accepts(receiver) && entry.signature.accepts(call, ::isA)) return entry.bound
        }
        return first?.bound
    }

    /** Checks [value] against a signature parameter type: Kotlin basics, then names registered with [type]. Unknown types match anything. */
    private fun isA(value: Any?, type: String): Boolean {
        if (value == null) return type.endsWith("?")
        if (value is ErrorValue) return true
        if ("->" in type) return value is LambdaValue || value is FunctionRef || value is ComposableSlot || value is Function<*>
        return when (val name = type.removeSuffix("?").substringBefore('<').trim()) {
            "Any" -> true
            "String" -> value is String
            "Boolean" -> value is Boolean
            "Int" -> value is Int
            "Long" -> value is Long
            "Float" -> value is Float
            "Double" -> value is Double
            "Number" -> value is Number
            "Pair" -> value is Pair<*, *>
            "List" -> value is List<*>
            "Map" -> value is Map<*, *>
            else -> findType(name)?.invoke(value) ?: true
        }
    }

    private fun findFallback(receiver: Any?): FallbackEntry? =
        fallbacks.firstOrNull { it.accepts(receiver) } ?: parent?.findFallback(receiver)

    private companion object {
        val anyReceiver: (Any?) -> Boolean = { true }
    }
}
