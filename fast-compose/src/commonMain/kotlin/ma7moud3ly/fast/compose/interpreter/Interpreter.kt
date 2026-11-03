package ma7moud3ly.fast.compose.interpreter

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import ma7moud3ly.fast.compose.parser.Span
import ma7moud3ly.fast.compose.parser.ast.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.yield

/** Evaluated call arguments. [trailing] is the trailing lambda, if any. */
internal class CallArgs private constructor(val shape: CallShape) {
    constructor(positional: List<Any?>, named: Map<String, Any?> = emptyMap(), trailing: Any? = null) :
        this(CallShape(positional, named, trailing))

    val positional: List<Any?> get() = shape.positional
    val named: Map<String, Any?> get() = shape.named
    val trailing: Any? get() = shape.trailing

    /** First argument matching [predicate], without copying the arguments into one list. */
    inline fun firstOrNull(predicate: (Any?) -> Boolean): Any? {
        for (value in positional) if (predicate(value)) return value
        for (value in named.values) if (predicate(value)) return value
        return trailing?.takeIf(predicate)
    }

    /** The same arguments, in the same order, with [trailing] as the trailing lambda. */
    fun withTrailing(trailing: Any?): CallArgs =
        shape.arguments?.let { CallArgs(CallShape(it, trailing)) } ?: CallArgs(positional, named, trailing)

    companion object {
        val Empty = CallArgs(emptyList())

        fun ordered(arguments: List<EvaluatedArgument>, trailing: Any?): CallArgs =
            if (arguments.none { it.name != null }) CallArgs(arguments.map { it.value }, trailing = trailing)
            else CallArgs(CallShape(arguments, trailing))
    }
}

/** Marks a receiver that is a package prefix of a qualified name. */
private object PackagePath

/** What a call resolves to; invoked differently inside composition and in plain code. */
internal sealed interface Target {
    class UserFunction(val decl: FunDecl, val receiver: Any?, val closure: Env?) : Target
    class Constructor(val decl: ClassDecl) : Target
    class RegistryComposable(val bound: Registry.Bound<ComposableBinding>) : Target
    class Value(val fn: Any?) : Target
    class Intrinsic(val name: String, val receiver: Any?) : Target
    class Plain(val call: () -> Any?) : Target
    class Done(val value: Any?) : Target
    class Suspend(val call: suspend () -> Any?) : Target

    /** A composable from library code, through the host's [ExternalResolver]; [receiver] for a member or extension. */
    class ExternalComposable(val symbol: ExternalSymbol, val receiver: Any?) : Target
}

/**
 * Runs a parsed [Program]. Composable code runs inside composition and calls real Compose APIs through [registry];
 * other code (event handlers, `remember` calculations, plain functions) runs as ordinary Kotlin.
 *
 * Inside composition errors never throw: they render an error box where they occur and are passed to [onError]
 * with their source line. In plain code errors throw [InterpreterException]. Output of `println` goes to [onLog].
 *
 * `dependencies` are other files of the project that [program] uses, e.g. from [SourceLoader]. Each file sees its
 * own declarations, its imports and its package. Without dependencies imports are ignored.
 *
 * Code that runs for longer than [maxRunTime] without a pause, e.g. nested loops over large ranges, is stopped so the
 * host app stays responsive; `Duration.INFINITE` turns the limit off.
 *
 * Library names without a binding run through [externals] when the host gives one, e.g. real library code on the JVM,
 * and as stand-ins otherwise.
 */
public class Interpreter(
    public val program: Program,
    public val registry: Registry,
    private val onError: (RuntimeError) -> Unit = {},
    private val onLog: (String) -> Unit = { println(it) },
    dependencies: List<Program> = emptyList(),
    private val maxRunTime: Duration = DEFAULT_MAX_RUN_TIME,
    private val externals: ExternalResolver? = null,
) {
    private val main = FileScope(program)
    private val scopes: List<FileScope> =
        listOf(main) + dependencies.filter { it !== program }.map(::FileScope)
    private val root = main.root
    private val builtins = Builtins(this)

    /** Values the host gives to bindings that run outside composition, e.g. the resources for `Font(R.font.x)`. */
    internal val hostValues: MutableMap<String, Any?> = mutableMapOf()

    /**
     * Checks added by bindings that ran while a resource was still loading, e.g. a top-level `FontFamily`; each is
     * true once its resource is done. The host then runs the code again, since such values are built only once.
     */
    internal val loadingChecks: SnapshotStateList<() -> Boolean> = mutableStateListOf()
    private val extensionProperties: List<PropertyDecl> =
        scopes.flatMap { scope ->
            scope.program.declarations.filterIsInstance<PropertyDecl>()
                .filter { it.receiver != null }
        }
    private var depth = 0

    /** Keys of warnings already shown, so each is shown once: stand-ins, functions without a body. */
    private val warnedOnce = mutableSetOf<String>()

    /** Whether an unknown name may run as a stand-in (no hint, no close known name), by name. */
    private val standInNames = mutableMapOf<String, Boolean>()

    /** Span of the expression being evaluated; errors are reported at its line. */
    private var location: Span? = null

    /** Path of the file with [location] when it is not [program]'s file. */
    private var locationFile: String? = null

    init {
        scopes.forEach { it.link(scopes) }
    }

    // ---------- Entry points ----------

    /** Renders [entry], a no-arg composable function. */
    @Composable
    public fun Render(entry: FunDecl) {
        // A new composition of the preview; also clears a count left behind by a composition that crashed.
        running = 0
        startRun()
        running++
        showIfError(callFunctionC(entry, NoReceiver, CallArgs.Empty, null))
        running--
    }

    /** Calls a top-level non-composable function and returns its result. */
    public fun call(name: String, vararg args: Any?): Any? {
        val callArgs = CallArgs(args.toList())
        val decl = resolveFunction(name, callArgs, extension = false, main)
            ?: throw InterpreterException("Unresolved reference '$name'")
        return entry { callFunction(decl, NoReceiver, callArgs, null) }
    }

    /** Reports a problem to [onError], at the line of the code being evaluated, without failing. */
    public fun report(message: String): Unit =
        onError(RuntimeError(message, location?.line, file = locationFile))

    /** Reports a warning to [onError], at the line of the code being evaluated, e.g. for a missing resource file. */
    public fun warn(message: String): Unit =
        onError(RuntimeError(message, location?.line, isWarning = true, file = locationFile))

    internal fun log(message: String) = onLog(message)

    /** Evaluates an expression at top level, e.g. an annotation argument. */
    public fun evaluate(expr: Expr): Any? = entry { eval(expr, root) }

    private fun fail(message: String): ErrorValue {
        val error = RuntimeError(message, location?.line, file = locationFile)
        onError(error)
        return ErrorValue(error.toString())
    }

    /** Runs plain code inside composition, turning exceptions into [ErrorValue]. */
    private inline fun guard(block: () -> Any?): Any? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        fail(describe(e))
    }

    @Composable
    private fun showIfError(value: Any?): Any? {
        if (value is ErrorValue && !value.shown) {
            value.shown = true
            ErrorBox(value.message)
        }
        return value
    }

    private fun isStop(value: Any?) = value is Jump || value is ErrorValue

    internal fun stringify(value: Any?): String = value?.toString() ?: "null"

    // ---------- Function values ----------

    /** Invokes a function value inside composition. */
    @Composable
    internal fun invokeComposable(fn: Any?, receiver: Any?, args: List<Any?>): Any? {
        // Compose calls content lambdas during composition and also later, e.g. lazy list items during layout.
        if (running == 0) startRun()
        running++
        val result = when (fn) {
            is LambdaValue -> invokeLambdaC(fn, receiver, args)
            is ComposableSlot -> fn.content()
            is FunctionRef ->
                if (fn.decl.isComposable) callFunctionC(
                    fn.decl,
                    fn.receiver,
                    CallArgs(args),
                    fn.closure
                )
                else showIfError(guard { invokeValue(fn, CallArgs(args)) })

            else -> showIfError(guard { invokeValue(fn, CallArgs(args)) })
        }
        running--
        return result
    }

    /** Invokes a function value from plain code such as an event handler; errors are reported and returned as [ErrorValue]. */
    internal fun invokeSafely(fn: Any?, receiver: Any?, args: List<Any?>): Any? = entry {
        guard {
            if (fn is LambdaValue) invokeLambda(fn, receiver, args) else invokeValue(fn, CallArgs(args))
        }
    }

    internal fun invokeValue(fn: Any?, args: CallArgs): Any? = when (fn) {
        is LambdaValue -> invokeLambda(
            fn,
            NoReceiver,
            args.positional + listOfNotNull(args.trailing)
        )

        is FunctionRef -> {
            if (fn.decl.isComposable) throw composableFromPlain(fn.decl.name)
            callFunction(fn.decl, fn.receiver, args, fn.closure)
        }

        is ClassRef -> instantiate(fn.decl, args)
        is MemberRef -> invokeMemberRef(fn, args)
        is PrecomputedLambda -> fn.result(args.positional)
        is Function1<*, *> -> @Suppress("UNCHECKED_CAST") (fn as (Any?) -> Any?)(args.positional.firstOrNull())
        is StandIn -> fn
        is ComposableSlot -> throw composableFromPlain(fn.toString())
        is Function0<*> -> fn()
        else -> throw InterpreterException("Expression of type ${typeName(fn)} cannot be invoked as a function")
    }

    private fun invokeLambda(fn: LambdaValue, receiver: Any?, args: List<Any?>): Any? {
        val env = fn.closure.child(receiver)
        bindLambdaParams(fn, env, args)
        return lambdaResult(fn, evalBlock(fn.lambda.body, env))
    }

    @Composable
    private fun invokeLambdaC(fn: LambdaValue, receiver: Any?, args: List<Any?>): Any? {
        val env = fn.closure.child(receiver)
        val bound = guard { bindLambdaParams(fn, env, args) }
        if (bound is ErrorValue) return showIfError(bound)
        return lambdaResult(fn, evalBlockC(fn.lambda.body, env))
    }

    private fun bindLambdaParams(fn: LambdaValue, env: Env, args: List<Any?>) {
        val params = fn.lambda.params
        if (params.isEmpty()) {
            if (args.size == 1) env.define("it", args[0], mutable = false)
        } else {
            params.forEachIndexed { i, name ->
                if (name != "_") env.define(
                    name,
                    args.getOrNull(i),
                    mutable = false
                )
            }
            fn.lambda.destructured.forEach { (i, names) ->
                destructure(
                    args.getOrNull(i),
                    names,
                    env,
                    mutable = false
                )
            }
        }
    }

    /** Defines [names] from the components of [value]; `_` skips a component. */
    private fun destructure(value: Any?, names: List<String>, env: Env, mutable: Boolean) {
        names.forEachIndexed { i, name ->
            if (name != "_") env.define(
                name,
                component(value, i + 1, env),
                mutable
            )
        }
    }

    /** `componentN()` of [value]: pairs, triples, map entries, lists, data classes and user `operator fun componentN`. */
    private fun component(value: Any?, n: Int, env: Env): Any? = when (value) {
        is Pair<*, *> -> value.toList().getOrElse(n - 1) { throw noComponent(value, n) }
        is Triple<*, *, *> -> value.toList().getOrElse(n - 1) { throw noComponent(value, n) }
        is Map.Entry<*, *> -> when (n) {
            1 -> value.key
            2 -> value.value
            else -> throw noComponent(value, n)
        }

        is IndexedValue<*> -> when (n) {
            1 -> value.index
            2 -> value.value
            else -> throw noComponent(value, n)
        }

        is List<*> -> value.getOrElse(n - 1) { throw IndexOutOfBoundsException("Index ${n - 1} out of bounds for length ${value.size}") }
        else -> callMember(value, "component$n", CallArgs.Empty, env)
    }

    private fun noComponent(value: Any?, n: Int) =
        InterpreterException("${typeName(value)} has no component$n()")

    private fun bindLoopVariable(stmt: ForStmt, item: Any?, env: Env) {
        val names = stmt.destructured
        if (names == null) env.define(stmt.variable, item, mutable = false) else destructure(
            item,
            names,
            env,
            mutable = false
        )
    }

    /** `return@label` ends the lambda; other jumps propagate to the enclosing function. */
    private fun lambdaResult(fn: LambdaValue, result: Any?): Any? =
        if (result is Jump && result.kind == Jump.Kind.RETURN && result.label != null && result.label == fn.label) result.value else result

    private fun suspendFromPlain(name: String) =
        InterpreterException("Suspend function '$name' should be called only from a coroutine, e.g. inside LaunchedEffect or launch { }")

    private fun composableFromPlain(name: String) =
        InterpreterException("@Composable invocations can only happen from the context of a @Composable function: '$name'")

    // ---------- Functions and classes ----------

    private fun resolveFunction(
        name: String,
        args: CallArgs,
        extension: Boolean,
        scope: FileScope
    ): FunDecl? {
        val all = scope.functions[name]?.filter { (it.receiver != null) == extension } ?: return null
        // With both an expect and its actual loaded, the actual (with a body) runs.
        val candidates = all.filter { !it.isExpect }.ifEmpty { all }
        return candidates.firstOrNull { accepts(paramsOf(it), args) } ?: candidates.firstOrNull()
    }

    private fun accepts(params: List<Param>, args: CallArgs): Boolean {
        if (args.named.isEmpty()) {
            var next = 0
            var trailingUsed = false
            params.forEachIndexed { i, p ->
                when {
                    p.isVararg -> next = args.positional.size
                    next < args.positional.size -> next++
                    i == params.lastIndex && args.trailing != null -> trailingUsed = true
                    p.default == null -> return false
                }
            }
            return next == args.positional.size && (args.trailing == null || trailingUsed)
        }
        val match = matchParams(params, args, "function")
        return match.problems.isEmpty() && params.all { it.isVararg || it.default != null || it.name in match.values }
    }

    private fun matchParams(params: List<Param>, args: CallArgs, owner: String): Signature.Match =
        Signature.forParameters(owner, params.map { it.name }, params.firstOrNull { it.isVararg }?.name).match(args.shape)

    /** Binds arguments into [env] and returns the parameters that need their default value evaluated. */
    private fun bindParams(
        params: List<Param>,
        args: CallArgs,
        env: Env,
        owner: String,
        lenient: Boolean = false,
    ): List<Param> {
        if (args.named.isEmpty()) return bindPositionalParams(params, args, env, owner, lenient)
        val match = matchParams(params, args, owner)
        match.problems.firstOrNull()?.let { throw InterpreterException(it) }
        val missing = mutableListOf<Param>()
        for (p in params) {
            when {
                p.name in match.values -> env.define(p.name, match.values[p.name], mutable = false)
                p.default != null -> missing += p
                lenient -> env.define(p.name, missingActualDefault(p, owner), mutable = false)
                else -> throw InterpreterException("No value passed for parameter '${p.name}' of '$owner'")
            }
        }
        return missing
    }

    /** Empty value, with a warning, for a parameter of an `actual` function whose `expect` (with its default) isn't loaded. */
    private fun missingActualDefault(param: Param, owner: String): Any? =
        emptyDefault(param, "it is on the expect declaration of '$owner', which is not loaded")

    /** Positional-only calls need no intermediate signature or parameter map. */
    private fun bindPositionalParams(params: List<Param>, args: CallArgs, env: Env, owner: String, lenient: Boolean): List<Param> {
        val missing = mutableListOf<Param>()
        var next = 0
        var trailingUsed = false
        params.forEachIndexed { i, p ->
            when {
                p.isVararg -> {
                    env.define(p.name, args.positional.drop(next), mutable = false)
                    next = args.positional.size
                }
                next < args.positional.size -> env.define(p.name, args.positional[next++], mutable = false)
                i == params.lastIndex && args.trailing != null -> {
                    env.define(p.name, args.trailing, mutable = false)
                    trailingUsed = true
                }
                p.default != null -> missing += p
                // An actual function whose expect (with the default values) is not loaded: the empty value is used.
                lenient -> env.define(p.name, missingActualDefault(p, owner), mutable = false)
                else -> throw InterpreterException("No value passed for parameter '${p.name}' of '$owner'")
            }
        }
        if (next < args.positional.size || (args.trailing != null && !trailingUsed)) {
            throw InterpreterException("Too many arguments for '$owner'")
        }
        return missing
    }

    private fun enter(name: String) {
        if (++depth > MAX_DEPTH) {
            depth = 0
            throw InterpreterException("Stack overflow in '$name'")
        }
    }

    /**
     * Calls a function without a body, such as an `expect` function whose `actual` is not loaded: one that returns
     * nothing does nothing, with a warning the first time; one that returns a value fails.
     */
    private fun callWithoutBody(decl: FunDecl): Any? {
        val reason = "'${decl.name}' has no body here, e.g. an expect function without its actual"
        if (returnsValue(decl)) throw InterpreterException("$reason, so it can't run in previews")
        if (warnedOnce.add("body:${decl.name}")) warn("$reason; it does nothing in previews")
        return Unit
    }

    /** Value of [param]'s [default]; if it fails, the empty value of its type, so sample data still builds. */
    private fun defaultValue(param: Param, default: Expr, env: Env): Any? = try {
        eval(default, env)
    } catch (e: InterpreterException) {
        emptyDefault(param, e.message)
    }

    /** Empty value for [param]'s type, used when its default fails with [error]: `0`, `""`, `false`, `null`, … */
    private fun emptyDefault(param: Param, error: String?): Any? {
        val type = param.type?.text?.trim()
        val value: Any? = when {
            type == null || type.endsWith("?") -> null
            else -> when (baseTypeName(type)) {
                "Int", "Short", "Byte" -> 0
                "Long" -> 0L
                "Float" -> 0f
                "Double" -> 0.0
                "Boolean" -> false
                "String", "CharSequence" -> ""
                "List", "Collection", "Iterable" -> emptyList<Any?>()
                "MutableList", "ArrayList" -> mutableListOf<Any?>()
                "Set" -> emptySet<Any?>()
                "MutableSet" -> mutableSetOf<Any?>()
                "Map" -> emptyMap<Any?, Any?>()
                "MutableMap" -> mutableMapOf<Any?, Any?>()
                else -> StandIn(type)
            }
        }
        warn("Default value of '${param.name}' failed: $error; an empty value is used")
        return value
    }

    /** Empty mutable collection that `buildList`, `buildSet` or `buildMap` ([name]) fills through its lambda. */
    private fun builderCollection(name: String): Any = when (name) {
        "buildSet" -> mutableSetOf<Any?>()
        "buildMap" -> mutableMapOf<Any?, Any?>()
        else -> mutableListOf<Any?>()
    }

    /**
     * Parameters of [decl]; for an `actual fun`, with the default values of its `expect` declaration, where Kotlin
     * writes them.
     */
    private fun paramsOf(decl: FunDecl): List<Param> {
        if (!decl.isActual) return decl.params
        val expect = expectOf(decl) ?: return decl.params
        return decl.params.mapIndexed { i, param ->
            if (param.default != null) param else param.copy(default = expect.params.getOrNull(i)?.default)
        }
    }

    /** Expect declarations of actual functions, by the actual's id and span; null when none is loaded. */
    private val expects = mutableMapOf<Pair<Int, Span>, FunDecl?>()

    private fun expectOf(decl: FunDecl): FunDecl? = expects.getOrPut(decl.id to decl.span) {
        val packageName = scopeOf(decl).program.packageName
        scopes.filter { it.program.packageName == packageName }
            .flatMap { it.program.declarations.filterIsInstance<FunDecl>() }
            .firstOrNull { it.isExpect && it.name == decl.name && it.params.size == decl.params.size }
    }

    private fun functionResult(result: Any?): Any? = when {
        result is Jump && result.kind == Jump.Kind.RETURN -> result.value
        result is Jump -> throw InterpreterException("'break' and 'continue' are only allowed inside a loop")
        else -> Unit
    }

    internal fun callFunction(decl: FunDecl, receiver: Any?, args: CallArgs, closure: Env?): Any? {
        val body = decl.body ?: return callWithoutBody(decl)
        enter(decl.name)
        try {
            val env = (closure ?: scopeOf(decl).root).child(receiver)
            for (p in bindParams(paramsOf(decl), args, env, decl.name, decl.isActual)) {
                val default = p.default ?: continue
                env.define(p.name, defaultValue(p, default, env), mutable = false)
            }
            return functionResult(evalBlock(body, env))
        } finally {
            depth--
        }
    }

    /**
     * Calls a composable user function. Functions that return nothing run in their own restart scope, like compiled
     * composables, so a state change inside one recomposes only that call instead of its caller.
     */
    @Composable
    private fun callFunctionC(decl: FunDecl, receiver: Any?, args: CallArgs, closure: Env?): Any? {
        if (!returnsValue(decl)) {
            RestartableCall(decl, receiver, args, closure)
            return Unit
        }
        return runFunctionC(decl, receiver, args, closure)
    }

    @Composable
    private fun RestartableCall(decl: FunDecl, receiver: Any?, args: CallArgs, closure: Env?) {
        // Compose may recompose this call alone, outside any other interpreted code.
        if (running == 0) startRun()
        running++
        showIfError(runFunctionC(decl, receiver, args, closure))
        running--
    }

    /** True for a declared return type other than `Unit`, or an expression body such as `= MaterialTheme.colorScheme.primary`. */
    private fun returnsValue(decl: FunDecl): Boolean {
        val type = decl.returnType?.text?.trim()
        if (type != null) return type != "Unit"
        val statement = decl.body?.statements?.singleOrNull()
        return statement is ReturnStmt && statement.value != null
    }

    @Composable
    private fun runFunctionC(decl: FunDecl, receiver: Any?, args: CallArgs, closure: Env?): Any? {
        val body = decl.body ?: return showIfError(guard { callWithoutBody(decl) })
        if (depth >= MAX_DEPTH) return showIfError(fail("Stack overflow in '${decl.name}'"))
        val env = (closure ?: scopeOf(decl).root).child(receiver)
        val missing = guard { bindParams(paramsOf(decl), args, env, decl.name, decl.isActual) }
        if (missing is ErrorValue) return showIfError(missing)
        for (p in missing as List<*>) {
            p as Param
            val default = p.default ?: continue
            val value = evalC(default, env)
            env.define(p.name, if (value is ErrorValue) emptyDefault(p, value.message) else value, mutable = false)
        }
        depth++
        val result = evalBlockC(body, env)
        depth--
        if (result is ErrorValue) return result
        return showIfError(guard { functionResult(result) })
    }

    internal fun instantiate(decl: ClassDecl, args: CallArgs): ObjectValue {
        when (decl.kind) {
            ClassKind.INTERFACE -> throw InterpreterException("Interface '${decl.name}' does not have constructors")
            ClassKind.OBJECT -> throw InterpreterException("Object '${decl.name}' cannot be called")
            ClassKind.ENUM -> throw InterpreterException("Enum class '${decl.name}' cannot be instantiated")
            else -> Unit
        }
        val obj = ObjectValue(decl)
        initialize(obj, args)
        return obj
    }

    private fun initialize(value: UserValue, args: CallArgs) {
        val decl = value.decl
        val env = ((value as? ObjectValue)?.closure ?: scopeOf(decl).root).child(value)
        for (p in bindParams(decl.constructorParams.map { it.param }, args, env, decl.name)) {
            val default = p.default ?: continue
            env.define(p.name, defaultValue(p, default, env), mutable = false)
        }
        for (pp in decl.constructorParams) if (pp.isProperty) value.fields[pp.param.name] =
            env.get(pp.param.name)
        // Object properties are initialized on first read, so one that fails doesn't block the others.
        val isObject = decl.kind == ClassKind.OBJECT
        for (member in decl.members) {
            if (member !is PropertyDecl) continue
            val delegate = member.delegate
            val initializer = member.initializer
            val compute: (() -> Any?)? = when {
                delegate != null -> { { Delegated(eval(delegate, env)) } }
                initializer != null -> { { eval(initializer, env) } }
                else -> null
            }
            when {
                compute != null -> value.fields[member.name] = if (isObject) LazyField(compute) else compute()
                // A stored property assigned later, e.g. a `lateinit var` or a `val` set in `init`.
                member.getter == null -> value.fields[member.name] = Uninitialized
            }
        }
        for (block in decl.initBlocks) evalBlock(block, env.child())
    }

    /** Instance of an anonymous `object : … { … }`; its members see the scope [env] it is created in. */
    private fun createObject(decl: ClassDecl, env: Env): ObjectValue {
        env.scope?.let { anonymousScopes[decl.id to decl.span] = it }
        val obj = ObjectValue(decl)
        obj.closure = env
        initialize(obj, CallArgs.Empty)
        return obj
    }

    /** Scope that members of [receiver]'s class see when [member] is one of them and [receiver] is anonymous. */
    private fun memberClosure(receiver: Any?, member: Decl): Env? {
        val obj = receiver as? ObjectValue ?: return null
        val closure = obj.closure ?: return null
        return closure.takeIf { obj.decl.members.any { it === member } }
    }

    private fun objectInstance(decl: ClassDecl): ObjectValue {
        val objects = scopeOf(decl).objects
        objects[decl.id]?.let { return it }
        val obj = ObjectValue(decl)
        objects[decl.id] = obj
        try {
            initialize(obj, CallArgs.Empty)
        } catch (e: InterpreterException) {
            // A failed `init` block runs again on the next use and fails with its real error, not missing fields.
            objects.remove(decl.id)
            throw e
        }
        return obj
    }

    private fun enumEntries(decl: ClassDecl): List<EnumValue> =
        scopeOf(decl).enumValues.getOrPut(decl.id) {
            val root = scopeOf(decl).root
            decl.enumEntries.mapIndexed { i, entry ->
                val value = EnumValue(decl, entry.name, i)
                val arguments = entry.args.map { arg ->
                    EvaluatedArgument(arg.name, eval(arg.value, root))
                }
                initialize(value, CallArgs.ordered(arguments, trailing = null))
                value
            }
        }

    /**
     * Finds a method in a user class or its user-declared supertypes, or else one of the class's own methods without a
     * body, e.g. in an `expect object`.
     */
    /** True when a variable holding [value] can be called: a function value, a class, a stand-in or an `invoke` operator. */
    private fun isInvocable(value: Any?, args: CallArgs): Boolean = when (value) {
        is LambdaValue, is FunctionRef, is MemberRef, is ClassRef, is ComposableSlot, is StandIn, is StandInObject,
        is PrecomputedLambda, is ErrorValue, is Function<*> -> true
        is UserValue -> findMethod(value.decl, "invoke", args) != null
        else -> false
    }

    private fun findMethod(decl: ClassDecl, name: String, args: CallArgs): FunDecl? =
        findMethodWithBody(decl, name, args)
            ?: decl.members.filterIsInstance<FunDecl>().firstOrNull { it.name == name && it.body == null }

    private fun findMethodWithBody(decl: ClassDecl, name: String, args: CallArgs): FunDecl? {
        val methods =
            decl.members.filterIsInstance<FunDecl>().filter { it.name == name && it.body != null }
        methods.firstOrNull { accepts(it.params, args) }?.let { return it }
        methods.firstOrNull()?.let { return it }
        for (superType in decl.superTypes) {
            val superDecl = scopeOf(decl).classes[baseTypeName(superType.text)] ?: continue
            if (superDecl !== decl) findMethodWithBody(superDecl, name, args)?.let { return it }
        }
        return null
    }

    private fun findGetter(decl: ClassDecl, name: String): PropertyDecl? =
        decl.members.filterIsInstance<PropertyDecl>()
            .firstOrNull { it.name == name && it.getter != null }

    private fun evalGetter(property: PropertyDecl, receiver: Any?): Any? {
        val getter = property.getter
            ?: throw InterpreterException("Property '${property.name}' has no getter")
        val base = memberClosure(receiver, property) ?: scopeOf(property).root
        return functionResult(evalBlock(getter, base.child(receiver)))
    }

    private fun topLevelProperty(decl: PropertyDecl): Any? {
        if (decl.getter != null) return evalGetter(decl, NoReceiver)
        initializeTopLevel(decl)
        return when (val value = scopeOf(decl).propertyValues[decl.name]) {
            Unresolved -> throw InterpreterException("Property '${decl.name}' depends on itself")
            Uninitialized -> throw notInitialized(decl.name)
            is Delegated -> value.get()
            else -> value
        }
    }

    /**
     * Computes a top-level property's value on first use. Without an initializer a `var` (e.g. `lateinit var`) starts
     * unset, and a `val` (e.g. an `expect val` without its `actual`) is null.
     */
    private fun initializeTopLevel(decl: PropertyDecl) {
        val scope = scopeOf(decl)
        val propertyValues = scope.propertyValues
        if (decl.name in propertyValues) return
        propertyValues[decl.name] = Unresolved
        val delegate = decl.delegate
        val initializer = decl.initializer
        propertyValues[decl.name] = when {
            delegate != null -> Delegated(eval(delegate, scope.root))
            initializer != null -> eval(initializer, scope.root)
            decl.isVar -> Uninitialized
            else -> null
        }
    }

    // ---------- Name and member resolution ----------

    /** File whose code runs in [this] scope. */
    private val Env.file: FileScope get() = scope ?: main

    /** File that declares [decl]. */
    private fun scopeOf(decl: Decl): FileScope =
        if (scopes.size == 1) {
            main
        } else {
            scopes.firstOrNull { it.owns(decl) } ?: anonymousScopes[decl.id to decl.span] ?: main
        }

    /** Files of anonymous object classes, which no file owns as a declaration, by the class's id and span. */
    private val anonymousScopes = mutableMapOf<Pair<Int, Span>, FileScope>()

    private fun locate(span: Span, env: Env) {
        location = span
        locationFile = env.scope?.takeIf { it !== main }?.program?.path
    }

    // ---------- Time budget ----------

    private enum class Budget { OK, EXCEEDED, HALTED }

    private var steps = 0
    private var runStart = TimeSource.Monotonic.markNow()
    private var lastCheck = runStart

    /** True after code ran too long, until the thread is free again; all evaluation stops meanwhile. */
    private var halted = false

    /**
     * Interpreted calls running now that were entered from outside: a composition, a draw or layout callback or an
     * event handler. A new run of [maxRunTime] starts when this is 0, so code that runs once per frame, such as a
     * continuous animation, is never stopped; only one entry that runs too long is.
     */
    private var running = 0

    /** Starts a new run of [maxRunTime]: the thread was free before this entry, e.g. between two frames. */
    private fun startRun() {
        val now = TimeSource.Monotonic.markNow()
        runStart = now
        lastCheck = now
        halted = false
    }

    /** Runs [block], entered from outside the interpreter; a new run starts unless interpreted code is running. */
    private inline fun <T> entry(block: () -> T): T {
        if (running == 0) startRun()
        running++
        try {
            return block()
        } finally {
            running--
        }
    }

    /**
     * Calls a suspend binding such as `delay` or `withFrameNanos`. When it really waited, the thread was free, so the
     * code after it starts a new run; a call that returns at once, like `delay(0)`, does not.
     */
    private suspend fun suspending(call: suspend () -> Any?): Any? {
        val before = TimeSource.Monotonic.markNow()
        val result = call()
        if (running == 0 && before.elapsedNow() >= MIN_WAIT) startRun()
        return result
    }

    /** Ends a composable function early while [halted], so composition finishes quickly. */
    private val haltJump = Jump(Jump.Kind.RETURN)

    /**
     * Counts one evaluation step. Code that runs for longer than [maxRunTime] without a pause, e.g. nested loops over
     * large ranges, is stopped so the host app stays responsive. The clock is read every [CHECK_EVERY] steps; a gap of
     * more than [PAUSE] between two readings means the thread was free, so a new run starts.
     */
    private fun tick(): Budget {
        if (halted) {
            val now = TimeSource.Monotonic.markNow()
            val paused = now - lastCheck > PAUSE
            lastCheck = now
            if (!paused) return Budget.HALTED
            halted = false
            runStart = now
            return Budget.OK
        }
        if (++steps % CHECK_EVERY != 0) return Budget.OK
        val now = TimeSource.Monotonic.markNow()
        if (now - lastCheck > PAUSE) runStart = now
        lastCheck = now
        if (now - runStart <= maxRunTime) return Budget.OK
        halted = true
        return Budget.EXCEEDED
    }

    private val tooLong: String
        get() = "Code ran for more than ${maxRunTime.inWholeSeconds} seconds without a pause and was stopped"

    private fun calleeName(callee: Expr): String? = when (callee) {
        is Ident -> callee.name
        is Member -> callee.name
        else -> null
    }

    private fun resolveCall(name: String, args: CallArgs, env: Env): Target? {
        // As in Kotlin, a variable is called only when it can be invoked; `items(…)` inside `LazyColumn` with a
        // parameter `items: LazyPagingItems` calls the scope's `items`.
        env.find(name).let { if (it !== Unresolved && isInvocable(it, args)) return Target.Value(it) }
        for (receiver in env.receivers) resolveMemberCall(
            receiver,
            name,
            args,
            env
        )?.let { return it }
        // Members of a library scope, e.g. a call inside `LibraryRow { … }` whose receiver comes from library code.
        if (externals != null) {
            for (receiver in env.receivers) externalMemberTarget(receiver, name, args, env, imported = false)?.let { return it }
        }
        resolveFunction(name, args, extension = false, env.file)?.let {
            return Target.UserFunction(
                it,
                NoReceiver,
                null
            )
        }
        env.file.classes[name]?.let { return Target.Constructor(it) }
        if (name in GLOBAL_INTRINSICS) return Target.Intrinsic(name, NoReceiver)
        registry.findComposable(name, args.shape)?.let { return Target.RegistryComposable(it) }
        registry.findFunction(name, args.shape)?.let { bound ->
            return Target.Plain {
                bound.binding(
                    toArgs(
                        args,
                        env,
                        bound.signature
                    )
                )
            }
        }
        registry.findSuspendFunction(name, args.shape)?.let { bound ->
            return Target.Suspend {
                bound.binding(
                    toArgs(
                        args,
                        env,
                        bound.signature
                    )
                )
            }
        }
        val value = builtins.global(name, args)
        if (value !== Unresolved) return Target.Done(value)
        env.file.memberImports[name]?.let { import -> importedMemberCall(import, args, env)?.let { return it } }
        importedExternal(name, env.file)?.let { symbol ->
            return if (symbol.isComposable) Target.ExternalComposable(symbol, null) else Target.Plain { externalCall(symbol, null, args, name) }
        }
        importedStandIn(name, env.file)?.let { return Target.Done(it) }
        unknownStandIn(name)?.let { return Target.Done(it) }
        return null
    }

    /** Target of a call to a member imported by name, e.g. `isReady()` after `import …Manager.isReady`, or null. */
    private fun importedMemberCall(import: Import, args: CallArgs, env: Env): Target? {
        val owner = importedValue(import, import.memberPath.dropLast(1), env)
        if (owner === Unresolved) return null
        val name = import.memberPath.last()
        resolveMemberCall(owner, name, args, env)?.let { return it }
        return when (val value = tryGetMember(owner, name)) {
            Unresolved -> null
            is ClassRef -> Target.Constructor(value.decl)
            else -> Target.Value(value)
        }
    }

    /**
     * Value reached from the declaration an import names through [members], e.g. `Devices` then `DESKTOP` for
     * `import …Devices.DESKTOP`; [Unresolved] if a step is not found. The declaration may be in another project file,
     * in this file, or bound in the registry.
     */
    private fun importedValue(import: Import, members: List<String>, env: Env): Any? {
        val ownerName = import.declarationName
        val decl = env.file.memberImportOwners[ownerName] ?: env.file.classes[ownerName]
        var value: Any? = when {
            decl != null -> if (decl.kind == ClassKind.OBJECT) objectInstance(decl) else ClassRef(decl)
            registry.hasValue(ownerName) -> registry.findValue(ownerName)
            else -> builtins.globalValue(ownerName)
        }
        for (member in members) {
            if (value === Unresolved) break
            value = tryGetMember(value, member)
        }
        return value
    }

    private fun resolveMemberCall(receiver: Any?, name: String, args: CallArgs, env: Env): Target? {
        when (receiver) {
            is StandIn -> return Target.Done(if (name == "not") true else StandIn("${receiver.name}.$name"))
            is StandInObject -> return Target.Done(
                if (name == "copy") StandInObject(receiver.name, receiver.properties + args.named) else StandIn("${receiver.name}.$name"),
            )
            is UserValue -> {
                findMethod(receiver.decl, name, args)?.let {
                    return Target.UserFunction(
                        it,
                        receiver,
                        memberClosure(receiver, it)
                    )
                }
                if (receiver.hasField(name)) return Target.Value(receiver.read(name))
                // Inside a class, functions of its companion object can be called without a qualifier.
                companion(receiver.decl)?.takeIf { it !== receiver }?.let { companion ->
                    findMethod(companion.decl, name, args)?.let { return Target.UserFunction(it, companion, null) }
                }
                if (receiver is ObjectValue && receiver.decl.kind == ClassKind.DATA_CLASS) {
                    if (name == "copy") return Target.Plain { copy(receiver, args) }
                    if (name.startsWith("component")) {
                        name.removePrefix("component").toIntOrNull()?.let { n ->
                            return Target.Done(
                                receiver.dataValues().getOrNull(n - 1)
                            )
                        }
                    }
                }
            }

            is ClassRef -> {
                val decl = receiver.decl
                nestedClass(decl, name)?.let { return Target.Constructor(it) }
                if (decl.kind == ClassKind.ENUM && name == "values") return Target.Done(
                    enumEntries(
                        decl
                    )
                )
                if (decl.kind == ClassKind.ENUM && name == "valueOf") {
                    return Target.Plain {
                        enumEntries(decl).firstOrNull { it.name == args.positional.firstOrNull() }
                            ?: throw InterpreterException("No enum constant ${decl.name}.${args.positional.firstOrNull()}")
                    }
                }
                companion(decl)?.let { companion ->
                    resolveMemberCall(
                        companion,
                        name,
                        args,
                        env
                    )?.let { return it }
                }
            }

            is LambdaValue, is FunctionRef, is MemberRef -> if (name == "invoke") return Target.Value(receiver)
        }
        resolveExtension(name, receiver, args, env)?.let {
            return Target.UserFunction(
                it,
                receiver,
                null
            )
        }
        if (name in LAMBDA_COLLECTION_FUNCTIONS && args.trailing is LambdaValue && isIterable(receiver)) {
            return Target.Intrinsic(name, receiver)
        }
        if (name in SINGLE_LAMBDA_FUNCTIONS && args.trailing is LambdaValue && receiver !is StandIn) {
            return Target.Intrinsic(name, receiver)
        }
        if (name in MEMBER_INTRINSICS && (args.trailing != null || args.positional.size == 1)) {
            if (name !in ITERATING_INTRINSICS || isIterable(receiver)) return Target.Intrinsic(
                name,
                receiver
            )
        }
        registry.findSuspendMember(receiver, name, args.shape)?.let { bound ->
            return Target.Suspend { bound.binding(receiver, toArgs(args, env, bound.signature)) }
        }
        registry.findMember(receiver, name, args.shape)?.let { bound ->
            return Target.Plain { bound.binding(receiver, toArgs(args, env, bound.signature)) }
        }
        val value = builtins.member(receiver, name, args)
        if (value !== Unresolved) return Target.Done(value)
        return null
    }

    private fun resolveExtension(name: String, receiver: Any?, args: CallArgs, env: Env): FunDecl? =
        env.file.functions[name]?.filter { decl ->
            decl.receiver?.let {
                isInstance(
                    receiver,
                    it.text
                )
            } == true
        }
            ?.let { candidates ->
                candidates.firstOrNull { accepts(it.params, args) } ?: candidates.firstOrNull()
            }

    private fun nestedClass(decl: ClassDecl, name: String): ClassDecl? =
        decl.members.filterIsInstance<ClassDecl>()
            .firstOrNull { it.name == name && it.kind != ClassKind.OBJECT }

    private fun companion(decl: ClassDecl): ObjectValue? =
        decl.members.filterIsInstance<ClassDecl>()
            .firstOrNull { it.name == "Companion" && it.kind == ClassKind.OBJECT }
            ?.let { objectInstance(it) }

    private fun copy(value: ObjectValue, args: CallArgs): ObjectValue {
        val params = value.decl.constructorParams.map { it.param.name }
        val match = Signature.forParameters("copy", params, null).match(args.shape)
        match.problems.firstOrNull()?.let { throw InterpreterException(it) }
        val named = params.associateWith { value.read(it) }.toMutableMap()
        named.putAll(match.values)
        return instantiate(value.decl, CallArgs(emptyList(), named))
    }

    /** Matches call arguments to a binding's [signature]; problems such as unknown parameter names are reported. */
    private fun toArgs(args: CallArgs, env: Env, signature: Signature): Args {
        val match = signature.match(args.shape)
        // The argument is ignored or replaced by a default, so the call still runs.
        for (problem in match.problems) warn(problem)
        return Args(signature, match.values, env.receivers, this)
    }

    private fun invokeTarget(target: Target?, args: CallArgs, name: String): Any? = when (target) {
        null -> throw unresolved(name)
        is Target.Done -> standInResult(target.value, name, args)
        is Target.UserFunction -> when {
            target.decl.isComposable -> throw composableFromPlain(name)
            target.decl.isSuspend -> throw suspendFromPlain(name)
            else -> callFunction(target.decl, target.receiver, args, target.closure)
        }

        is Target.Constructor -> instantiate(target.decl, args)
        is Target.RegistryComposable -> throw composableFromPlain(name)
        is Target.Plain -> target.call()
        is Target.Value -> invokeValue(target.fn, args)
        is Target.Intrinsic -> intrinsic(target, args)
        is Target.Suspend -> throw suspendFromPlain(name)
        is Target.ExternalComposable -> throw composableFromPlain(name)
    }

    @Composable
    private fun invokeTargetC(target: Any?, args: CallArgs, env: Env, name: String): Any? =
        when (target) {
            null -> showIfError(fail(unresolved(name, env.file).message ?: name))
            is ErrorValue -> showIfError(target)
            is Target.Done -> standInResult(target.value, name, args).also { value ->
                // A stand-in for a library container, e.g. `PullToRefreshBox(…) { content() }`, still shows its
                // content, in a Box, so a wrapper from an unbound library doesn't hide the screen.
                val content = args.trailing
                if ((value is StandIn || value is StandInObject) && name.first().isUpperCase() && content is LambdaValue && content.lambda.params.isEmpty()) {
                    Box { showIfError(invokeComposable(content, this, emptyList())) }
                }
            }
            is Target.UserFunction ->
                if (target.decl.isComposable) callFunctionC(
                    target.decl,
                    target.receiver,
                    args,
                    target.closure
                )
                else showIfError(guard { invokeTarget(target, args, name) })

            is Target.Constructor -> showIfError(guard { instantiate(target.decl, args) })
            is Target.RegistryComposable -> target.bound.binding(
                toArgs(
                    args,
                    env,
                    target.bound.signature
                )
            )

            is Target.Plain -> showIfError(guard { target.call() })
            is Target.Value -> invokeComposable(
                target.fn,
                NoReceiver,
                args.positional + listOfNotNull(args.trailing)
            )

            is Target.Intrinsic -> intrinsicC(target, args)
            is Target.Suspend -> showIfError(fail(suspendFromPlain(name).message ?: name))
            is Target.ExternalComposable -> when (val content = externalComposable(target.symbol, target.receiver, args, name)) {
                null -> StandIn(name)
                else -> content()
            }
            else -> showIfError(fail("Cannot call '$name'"))
        }

    /** Error for a name not found; names imported in [file] are reported with the package they come from. */
    private fun unresolved(name: String, file: FileScope? = null): InterpreterException {
        registry.findHint(name)?.let { return InterpreterException(it) }
        file?.program?.imports?.firstOrNull { !it.isStar && it.visibleName == name }?.let {
            return InterpreterException("Unresolved reference '$name' (imported from ${it.declarationPackage})")
        }
        val suggestion = suggestionFor(name)
        return InterpreterException("Unresolved reference '$name'" + (suggestion?.let { ". Did you mean '$it'?" }
            ?: ""))
    }

    /** A known name within two edits of [name], e.g. `Text` for `Txet`; null if none. */
    private fun suggestionFor(name: String): String? {
        // Kotlin type names are never typos, e.g. `String` in `String.format(…)` is not `spring`.
        if (name in KOTLIN_TYPES) return null
        val known = registry.globalNames() + main.functions.keys + main.classes.keys
        return known.filter { it != name && editDistance(it, name) <= 2 }.minByOrNull { editDistance(it, name) }
    }

    /** True for a package prefix like `androidx.compose.ui` in `androidx.compose.ui.Modifier`: lowercase names that resolve to nothing. */
    private fun isPackagePath(expr: Expr, env: Env): Boolean = when (expr) {
        is Ident -> expr.name.first().isLowerCase() && !isKnownName(expr.name, env)
        is Member -> !expr.isSafe && expr.name.first().isLowerCase() && isPackagePath(
            expr.receiver,
            env
        )

        else -> false
    }

    private fun isKnownName(name: String, env: Env): Boolean =
        env.contains(name) || name in env.file.properties || name in env.file.classes || name in env.file.functions ||
                registry.isGlobalName(name) ||
                builtins.globalValue(name) !== Unresolved || env.receivers.any {
            hasProperty(
                it,
                name
            )
        }

    /** Checks for a property without evaluating it. */
    private fun hasProperty(receiver: Any?, name: String): Boolean = when {
        receiver is UserValue && (receiver.hasField(name) || findGetter(
            receiver.decl,
            name
        ) != null) -> true

        registry.hasMember(receiver, name) -> true
        extensionProperties.any { it.name == name && isReceiverOf(receiver, it) } -> true
        receiver is UserValue && hasClassMember(receiver, name) -> true
        // A property of a value from library code, e.g. `background` inside `fun EditorTheme.toColorScheme()`.
        externals != null && receiver != null && isForeign(receiver) &&
            externalLookup(name) { externals.member(receiver, name) } != null -> true
        else -> builtins.property(receiver, name) !== Unresolved
    }

    /**
     * True when [name] is a member of [value]'s companion object, or, for a companion object, an entry or nested class
     * of its class: names visible without a qualifier inside the class.
     */
    private fun hasClassMember(value: UserValue, name: String): Boolean {
        val companion = companion(value.decl)?.takeIf { it !== value }
        if (companion != null && (companion.hasField(name) || findGetter(companion.decl, name) != null)) return true
        val outer = outerClassOf(value.decl) ?: return false
        return staticMember(outer, name) != null
    }

    /** True when [value] fits [property]'s receiver type; unknown receiver types don't match. */
    private fun isReceiverOf(value: Any?, property: PropertyDecl): Boolean {
        val type = property.receiver?.text ?: return false
        return try {
            isInstance(value, type)
        } catch (_: InterpreterException) {
            false
        }
    }

    /** An enum entry, `entries`, or a nested class or object of [decl] named [name]; null if none. */
    private fun staticMember(decl: ClassDecl, name: String): Any? {
        if (decl.kind == ClassKind.ENUM) {
            enumEntries(decl).firstOrNull { it.name == name }?.let { return it }
            if (name == "entries") return enumEntries(decl)
        }
        return decl.members.filterIsInstance<ClassDecl>().firstOrNull { it.name == name }
            ?.let { if (it.kind == ClassKind.OBJECT) objectInstance(it) else ClassRef(it) }
    }

    /** Classes that declare an object as a member, e.g. a companion's class, by the object's id and span. */
    private val outerClasses = mutableMapOf<Pair<Int, Span>, ClassDecl?>()

    /** The class that declares the object [decl] as a member, or null for top-level and non-object classes. */
    private fun outerClassOf(decl: ClassDecl): ClassDecl? {
        if (decl.kind != ClassKind.OBJECT) return null
        val key = decl.id to decl.span
        if (key in outerClasses) return outerClasses[key]
        fun find(decls: List<Decl>): ClassDecl? {
            for (d in decls) {
                if (d !is ClassDecl) continue
                if (d.members.any { it === decl }) return d
                find(d.members)?.let { return it }
            }
            return null
        }
        return find(scopeOf(decl).program.declarations).also { outerClasses[key] = it }
    }

    /** Value of an identifier: local variable, implicit receiver member, top-level declaration, registry value or builtin. */
    private fun lookup(name: String, env: Env): Any? {
        env.find(name).let { if (it !== Unresolved) return it }
        for (receiver in env.receivers) {
            val value = tryGetMember(receiver, name)
            if (value !== Unresolved) return value
        }
        // A property of a library receiver, e.g. `amount` inside `fun Money.label()` where `Money` comes from a library.
        if (externals != null) {
            for (receiver in env.receivers) {
                val value = externalProperty(receiver, name)
                if (value !== Unresolved) return value
            }
        }
        env.file.properties[name]?.let { return topLevelProperty(it) }
        env.file.classes[name]?.let {
            return if (it.kind == ClassKind.OBJECT) objectInstance(it) else ClassRef(
                it
            )
        }
        if (registry.hasValue(name)) return registry.findValue(name)
        val value = builtins.globalValue(name)
        if (value !== Unresolved) return value
        env.file.memberImports[name]?.let { import ->
            val imported = importedValue(import, import.memberPath, env)
            if (imported !== Unresolved) return imported
        }
        importedExternal(name, env.file)?.let { return externalRead(it, null, name) }
        importedStandIn(name, env.file)?.let { return it }
        unknownStandIn(name)?.let { return it }
        throw unresolved(name, env.file)
    }

    /**
     * Stand-in for a name nothing provides, e.g. a Kotlin or platform API without a binding such as `IntArray`, with
     * one warning. Null, so the name stays an error, when it has a hint or is close to a known name, like a typo of
     * `Text`.
     */
    private fun unknownStandIn(name: String): StandIn? {
        val allowed = standInNames.getOrPut(name) { registry.findHint(name) == null && suggestionFor(name) == null }
        if (!allowed) return null
        if (warnedOnce.add("name:$name")) {
            warn("'$name' is not available in previews; a stand-in is used, so the preview may differ")
        }
        return StandIn(name)
    }

    /**
     * Stand-in for [name] when [file] imports it explicitly but nothing provides it, such as a library API without a
     * binding, or when it is a `java.lang` class that Kotlin imports implicitly on the JVM, such as `Thread`; warns
     * once per name. Other names stay unresolved.
     */
    private fun importedStandIn(name: String, file: FileScope): StandIn? {
        val import = file.program.imports.firstOrNull { !it.isStar && it.visibleName == name }
        val packageName = import?.declarationPackage ?: if (name in IMPLICIT_JAVA_LANG) "java.lang" else return null
        if (warnedOnce.add("import:${import?.path ?: "java.lang.$name"}")) {
            if (name in INJECTION_CALLS) {
                warn(
                    "'$name' ($packageName) can't create a ViewModel or injected object in previews; a stand-in is " +
                        "used. Preview the composable that takes the screen's state as parameters instead",
                )
            } else {
                warn("'$name' ($packageName) has no binding; a stand-in is used, so the preview may differ")
            }
        }
        return StandIn(name)
    }

    private fun tryGetMember(receiver: Any?, name: String): Any? {
        when (receiver) {
            is StandIn -> return StandIn("${receiver.name}.$name")
            is StandInObject -> return if (receiver.properties.containsKey(name)) receiver.properties[name] else StandIn("${receiver.name}.$name")
            is UserValue -> {
                if (receiver.hasField(name)) return receiver.read(name)
                findGetter(receiver.decl, name)?.let { return evalGetter(it, receiver) }
                if (receiver is EnumValue && name == "name") return receiver.name
                if (receiver is EnumValue && name == "ordinal") return receiver.ordinal
                // Inside a class, members of its companion object are visible without a qualifier, and inside a
                // companion object, the entries and nested classes of its class.
                companion(receiver.decl)?.takeIf { it !== receiver }?.let { companion ->
                    val value = tryGetMember(companion, name)
                    if (value !== Unresolved) return value
                }
                outerClassOf(receiver.decl)?.let { outer -> staticMember(outer, name)?.let { return it } }
            }

            is ClassRef -> {
                val decl = receiver.decl
                if (decl.kind == ClassKind.ENUM) {
                    enumEntries(decl).firstOrNull { it.name == name }?.let { return it }
                    if (name == "entries") return enumEntries(decl)
                }
                decl.members.filterIsInstance<ClassDecl>().firstOrNull { it.name == name }?.let {
                    return if (it.kind == ClassKind.OBJECT) objectInstance(it) else ClassRef(it)
                }
                companion(decl)?.let { companion ->
                    val value = tryGetMember(companion, name)
                    if (value !== Unresolved) return value
                }
            }
        }
        extensionProperties
            .firstOrNull { p -> p.name == name && isReceiverOf(receiver, p) }
            ?.let { return evalGetter(it, receiver) }
        registry.findMember(receiver, name, CallShape.None)
            ?.let { return it.binding(receiver, Args(it.signature, emptyMap(), emptyList(), this)) }
        return builtins.property(receiver, name)
    }

    private fun getMember(receiver: Any?, name: String): Any? {
        val value = tryGetMember(receiver, name).let { if (it === Unresolved) externalProperty(receiver, name) else it }
        return if (value === Unresolved) missingMember(receiver, name) else value
    }

    private fun callMember(receiver: Any?, name: String, args: CallArgs, env: Env): Any? =
        invokeTarget(memberTarget(receiver, name, args, env), args, name)

    /** Target of a member call; a member nothing provides gives [missingMember] instead of failing. */
    private fun memberTarget(receiver: Any?, name: String, args: CallArgs, env: Env): Target =
        resolveMemberCall(receiver, name, args, env)
            ?: externalMemberTarget(receiver, name, args, env)
            ?: Target.Done(missingMember(receiver, name))

    // ---------- Library code from the host's ExternalResolver ----------

    /** Declarations [externals] found, by package and name; null when it has none. Asked once per name. */
    private val externalSymbols = HashMap<Pair<String, String>, ExternalSymbol?>()

    private fun externalSymbol(packageName: String, name: String): ExternalSymbol? {
        val resolver = externals ?: return null
        return externalSymbols.getOrPut(packageName to name) { externalLookup(name) { resolver.find(packageName, name) } }
    }

    /**
     * The library declaration [name] that [file] imports (or `java.lang` provides implicitly), from [externals]; null
     * when there is no resolver or it doesn't have it. Imports of members (`a.B.c`) are not looked up.
     */
    private fun importedExternal(name: String, file: FileScope): ExternalSymbol? {
        if (externals == null) return null
        val import = file.program.imports.firstOrNull { !it.isStar && it.visibleName == name }
        return when {
            import != null && import.memberPath.isEmpty() -> externalSymbol(import.declarationPackage, import.declarationName)
            import == null && name in IMPLICIT_JAVA_LANG -> externalSymbol("java.lang", name)
            else -> null
        }
    }

    /** True for values library code may know: anything but the interpreter's own values. */
    private fun isForeign(value: Any): Boolean = value !is UserValue && value !is StandIn && value !is StandInObject &&
        value !is LambdaValue && value !is FunctionRef && value !is MemberRef && value !is ClassRef &&
        value !is ComposableSlot && value !is PrecomputedLambda

    /**
     * A member call on a value from library code, or (when [imported]) an extension function the file imports from a
     * library. Calls on implicit receivers look up members only.
     */
    private fun externalMemberTarget(receiver: Any?, name: String, args: CallArgs, env: Env, imported: Boolean = true): Target? {
        val resolver = externals ?: return null
        if (receiver == null || !isForeign(receiver)) return null
        val symbol = (if (imported) importedExternal(name, env.file) else null)
            ?: externalLookup(name) { resolver.member(receiver, name) }
            ?: return null
        if (symbol.isComposable) return Target.ExternalComposable(symbol, receiver)
        return Target.Plain { externalCall(symbol, receiver, args, name) }
    }

    /** A property of a value from library code, or [Unresolved]. */
    private fun externalProperty(receiver: Any?, name: String): Any? {
        val resolver = externals ?: return Unresolved
        if (receiver == null || !isForeign(receiver)) return Unresolved
        val symbol = externalLookup(name) { resolver.member(receiver, name) } ?: return Unresolved
        return externalRead(symbol, receiver, name)
    }

    /** Runs a lookup in [externals]; a failure is logged as a warning and counts as not found. */
    private inline fun externalLookup(name: String, lookup: () -> ExternalSymbol?): ExternalSymbol? = try {
        lookup()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        warn("'$name' could not be looked up in library code: ${e.message ?: e::class.simpleName}")
        null
    }

    private fun externalRead(symbol: ExternalSymbol, receiver: Any?, name: String): Any? =
        externalRun(name) { symbol.read(receiver) }

    /** Calls [symbol] with [args]; a trailing lambda is the last positional argument, as in Kotlin. */
    private fun externalCall(symbol: ExternalSymbol, receiver: Any?, args: CallArgs, name: String): Any? {
        val positional = (args.positional + listOfNotNull(args.trailing)).map(::toExternal)
        val named = args.named.mapValues { toExternal(it.value) }
        return externalRun(name) { symbol.call(receiver, positional, named) }
    }

    /**
     * Prepares a library composable's call: its content to run in composition, or null after reporting why the
     * arguments don't fit. Lambdas are passed as [InterpretedLambda]s, which the host makes composable where needed.
     */
    private fun externalComposable(symbol: ExternalSymbol, receiver: Any?, args: CallArgs, name: String): (@Composable () -> Unit)? {
        val positional = (args.positional + listOfNotNull(args.trailing)).map(::toInterpretedLambda)
        val named = args.named.mapValues { toInterpretedLambda(it.value) }
        return try {
            symbol.composable(receiver, positional, named)
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterpreterException) {
            throw e
        } catch (e: Throwable) {
            report("'$name' can't run from library code: ${e.message ?: e::class.simpleName}")
            null
        }
    }

    private fun toInterpretedLambda(value: Any?): Any? = when (value) {
        is LambdaValue -> InterpretedLambda(this, value, value.lambda.params.size, value.lambda.usesIt())
        is FunctionRef -> InterpretedLambda(this, value, value.decl.params.size, usesIt = false)
        else -> value
    }

    /** Runs library code; a failure is reported where it happened and the result is a stand-in. */
    private inline fun externalRun(name: String, block: () -> Any?): Any? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterpreterException) {
        throw e
    } catch (e: Throwable) {
        report("'$name' failed in library code: ${e.message ?: e::class.simpleName}")
        StandIn(name)
    }

    /**
     * [value] as library code takes it: interpreted lambdas and function references become Kotlin functions with their
     * number of parameters; a lambda without declared parameters takes one when it uses `it`.
     */
    private fun toExternal(value: Any?): Any? = when (value) {
        is LambdaValue -> externalFunction(this, value, value.lambda.params.size.takeIf { it > 0 } ?: if (value.lambda.usesIt()) 1 else 0)
        is FunctionRef -> externalFunction(this, value, value.decl.params.size)
        is MemberRef -> externalFunction(this, value, 1)
        else -> value
    }

    /**
     * Value of a member nothing provides, such as a Compose or library API without a binding, with one warning per
     * type and name: a `Modifier` stays unchanged, so the rest of its chain still applies; anything else gives a
     * stand-in.
     */
    private fun missingMember(receiver: Any?, name: String): Any? {
        val type = typeName(receiver)
        if (warnedOnce.add("member:$type.$name")) {
            warn("'$name' on $type has no binding; " +
                if (receiver is Modifier) "it is skipped" else "a stand-in is used, so the preview may differ")
        }
        return if (receiver is Modifier) receiver else StandIn("$type.$name")
    }

    // ---------- Intrinsics: scope functions and loops whose lambdas may contain composable calls ----------

    private fun isIterable(value: Any?) =
        value is Iterable<*> || value is Map<*, *> || value is CharSequence

    private fun iterate(value: Any?): Iterable<Any?> = when (value) {
        is Iterable<*> -> value
        is Map<*, *> -> value.entries
        is CharSequence -> value.asIterable()
        is StandIn -> emptyList()
        else -> throw InterpreterException("For-loop range must be iterable, found ${typeName(value)}")
    }

    private fun intrinsic(target: Target.Intrinsic, args: CallArgs): Any? {
        val fn = args.trailing ?: args.positional.last()
        val receiver = target.receiver
        fun invoke(r: Any?, vararg a: Any?) =
            if (fn is LambdaValue) invokeLambda(fn, r, a.toList()) else invokeValue(
                fn,
                CallArgs(a.toList())
            )
        return when (target.name) {
            "repeat" -> {
                val times = args.positional.firstOrNull() as? Int
                    ?: throw InterpreterException("repeat() needs an Int")
                for (i in 0 until times) invoke(NoReceiver, i).let { if (it is Jump) return it }
                Unit
            }

            "run" -> if (receiver === NoReceiver) invoke(NoReceiver) else invoke(receiver)
            "with" -> invoke(args.positional.firstOrNull())
            "buildList", "buildSet", "buildMap" -> {
                val collection = builderCollection(target.name)
                invoke(collection).let { if (it is Jump) it else collection }
            }
            "let" -> invoke(NoReceiver, receiver)
            "also" -> invoke(NoReceiver, receiver).let { if (it is Jump) it else receiver }
            "apply" -> invoke(receiver).let { if (it is Jump) it else receiver }
            "forEach" -> {
                for (item in iterate(receiver)) invoke(
                    NoReceiver,
                    item
                ).let { if (it is Jump) return it }
                Unit
            }

            "forEachIndexed" -> {
                for ((i, item) in iterate(receiver).withIndex()) invoke(
                    NoReceiver,
                    i,
                    item
                ).let { if (it is Jump) return it }
                Unit
            }

            in LAMBDA_COLLECTION_FUNCTIONS, in SINGLE_LAMBDA_FUNCTIONS -> builtinMember(receiver, target.name, args)
            else -> throw unresolved(target.name)
        }
    }

    /**
     * The value of calling a stand-in class [name] from a library: with named arguments only, e.g.
     * `EditorTheme(background = Color.Black)`, an object that keeps them as properties, so reading them back works
     * without the library; otherwise the stand-in itself.
     */
    private fun standInResult(value: Any?, name: String, args: CallArgs): Any? =
        if (value is StandIn && name.first().isUpperCase() && args.named.isNotEmpty() && args.positional.isEmpty()) {
            StandInObject(name, args.named)
        } else {
            value
        }

    /** What `ifEmpty`, `ifBlank`, `takeIf`, `takeUnless` or `getOrElse` does: give a value, or run its lambda. */
    private sealed interface SingleLambda {
        class Result(val value: Any?) : SingleLambda

        /** Runs the lambda with [args]; its value turns into the call's value through [result]. */
        class Run(val args: List<Any?>, val result: (Any?) -> Any? = { it }) : SingleLambda
    }

    /**
     * Decides, as Kotlin does, whether a function from [SINGLE_LAMBDA_FUNCTIONS] runs its lambda, so the caller's
     * evaluator can run it (composable or suspend calls are allowed in these inline lambdas).
     */
    private fun singleLambdaPlan(receiver: Any?, name: String, args: CallArgs): SingleLambda = when (name) {
        "ifEmpty" -> {
            val empty = when (receiver) {
                is CharSequence -> receiver.isEmpty()
                is Collection<*> -> receiver.isEmpty()
                is Map<*, *> -> receiver.isEmpty()
                else -> false
            }
            if (empty) SingleLambda.Run(emptyList()) else SingleLambda.Result(receiver)
        }

        "ifBlank" -> if (receiver is CharSequence && receiver.isBlank()) SingleLambda.Run(emptyList()) else SingleLambda.Result(receiver)
        "takeIf" -> SingleLambda.Run(listOf(receiver)) { if (it == true) receiver else null }
        "takeUnless" -> SingleLambda.Run(listOf(receiver)) { if (it == true) null else receiver }
        else -> {
            val key = args.positional.firstOrNull()
            when {
                receiver is List<*> && key is Int ->
                    if (key in receiver.indices) SingleLambda.Result(receiver[key]) else SingleLambda.Run(listOf(key))

                receiver is Map<*, *> ->
                    if (receiver.containsKey(key)) SingleLambda.Result(receiver[key]) else SingleLambda.Run(emptyList())

                else -> SingleLambda.Result(builtinMember(receiver, name, args))
            }
        }
    }

    /** A builtin member such as `map { }`, or a missing member when the builtins don't have it. */
    private fun builtinMember(receiver: Any?, name: String, args: CallArgs): Any? {
        val value = builtins.member(receiver, name, args)
        return if (value === Unresolved) missingMember(receiver, name) else value
    }

    /**
     * Arguments of a collection function with its trailing lambda replaced by results already computed for [items],
     * in order. Lets the builtin run its own logic while the lambda ran in the composable or suspend evaluator, where
     * Kotlin allows composable and suspend calls inside inline lambdas.
     */
    private fun withResults(args: CallArgs, name: String, items: List<Any?>, results: List<Any?>): CallArgs =
        args.withTrailing(PrecomputedLambda(items, results, name in INDEXED_LAMBDAS))

    @Composable
    private fun intrinsicC(target: Target.Intrinsic, args: CallArgs): Any? {
        val fn = args.trailing ?: args.positional.last()
        val receiver = target.receiver
        return when (target.name) {
            "repeat" -> {
                val times = args.positional.firstOrNull() as? Int
                    ?: return showIfError(fail("repeat() needs an Int"))
                loopC(List(times) { it }) { _, i -> invokeComposable(fn, NoReceiver, listOf(i)) }
            }

            "run" -> invokeComposable(
                fn,
                if (receiver === NoReceiver) NoReceiver else receiver,
                emptyList()
            )

            "with" -> invokeComposable(fn, args.positional.firstOrNull(), emptyList())
            "buildList", "buildSet", "buildMap" -> {
                val collection = builderCollection(target.name)
                invokeComposable(fn, collection, emptyList()).let { if (isStop(it)) it else collection }
            }
            "let" -> invokeComposable(fn, NoReceiver, listOf(receiver))
            "also" -> invokeComposable(
                fn,
                NoReceiver,
                listOf(receiver)
            ).let { if (isStop(it)) it else receiver }

            "apply" -> invokeComposable(
                fn,
                receiver,
                emptyList()
            ).let { if (isStop(it)) it else receiver }

            "forEach", "forEachIndexed" -> {
                val items = guard { iterate(receiver).toList() }
                if (items is ErrorValue) return showIfError(items)
                val indexed = target.name == "forEachIndexed"
                loopC(items as List<*>) { item, i ->
                    invokeComposable(
                        fn,
                        NoReceiver,
                        if (indexed) listOf(i, item) else listOf(item)
                    )
                }
            }

            in LAMBDA_COLLECTION_FUNCTIONS -> {
                val items = guard { iterate(receiver).toList() }
                if (items is ErrorValue) return showIfError(items)
                val indexed = target.name in INDEXED_LAMBDAS
                val results = ArrayList<Any?>()
                val stop = loopC(items as List<*>) { item, i ->
                    invokeComposable(fn, NoReceiver, if (indexed) listOf(i, item) else listOf(item)).also { results += it }
                }
                if (stop is Jump || stop is ErrorValue) return stop
                showIfError(guard { builtinMember(receiver, target.name, withResults(args, target.name, items, results)) })
            }

            in SINGLE_LAMBDA_FUNCTIONS -> when (val plan = singleLambdaPlan(receiver, target.name, args)) {
                is SingleLambda.Result -> plan.value
                is SingleLambda.Run -> invokeComposable(fn, NoReceiver, plan.args).let {
                    if (it is Jump || it is ErrorValue) showIfError(it) else plan.result(it)
                }
            }

            else -> showIfError(fail("Unresolved reference '${target.name}'"))
        }
    }

    /** Runs [body] for each item inside composition, keyed by index. Stops at `break` or `return`. */
    @Composable
    private inline fun loopC(
        items: List<*>,
        body: @Composable (item: Any?, index: Int) -> Any?
    ): Any? {
        var result: Any? = Unit
        var i = 0
        while (i < items.size) {
            val r = key(i) { body(items[i], i) }
            if (r is Jump && r.kind != Jump.Kind.CONTINUE) {
                if (r.kind == Jump.Kind.RETURN) result = r
                i = items.size
            } else if (r is ErrorValue) {
                result = r
                i = items.size
            }
            i++
        }
        return result
    }

    // ---------- Statements ----------

    internal fun evalBlock(block: Block, env: Env): Any? {
        var last: Any? = Unit
        for (statement in block.statements) {
            val result = exec(statement, env)
            if (result is Jump) return result
            last = if (statement is ExprStmt) result else Unit
        }
        return last
    }

    @Composable
    private fun evalBlockC(block: Block, env: Env): Any? {
        var last: Any? = Unit
        var stopped: Any? = null
        val statements = block.statements
        var i = 0
        while (i < statements.size) {
            val statement = statements[i]
            val result = execC(statement, env)
            // A `return` whose value failed stops the block with the error, so callers don't get `Unit`.
            if (result is Jump || (statement is ReturnStmt && result is ErrorValue)) {
                stopped = result
                i = statements.size
            }
            last = if (statement is ExprStmt) result else Unit
            i++
        }
        return stopped ?: last
    }

    private fun exec(stmt: Stmt, env: Env): Any? = when (stmt) {
        is ExprStmt -> eval(stmt.expr, env)
        is PropertyDecl -> {
            val delegate = stmt.delegate
            val initializer = stmt.initializer
            when {
                delegate != null -> eval(
                    delegate,
                    env
                ).let { if (it is Jump) it else env.defineDelegated(stmt.name, it) }

                initializer != null -> eval(
                    initializer,
                    env
                ).let { if (it is Jump) it else env.define(stmt.name, it, stmt.isVar) }

                else -> env.define(stmt.name, null, mutable = true)
            }
        }

        is DestructuringDecl -> eval(
            stmt.initializer,
            env
        ).let { if (it is Jump) it else destructure(it, stmt.names, env, stmt.isVar) }

        is Assign -> eval(stmt.value, env).let {
            if (it is Jump) it else assign(
                stmt.target,
                stmt.op,
                it,
                env
            )
        }

        is ForStmt -> {
            val iterable = eval(stmt.iterable, env)
            if (iterable is Jump) iterable else forLoop(stmt, iterate(iterable), env)
        }

        is WhileStmt -> whileLoop(stmt, env)
        is ReturnStmt -> eval(stmt, env)
        is BreakStmt -> Jump(Jump.Kind.BREAK)
        is ContinueStmt -> Jump(Jump.Kind.CONTINUE)
        is FunDecl -> env.define(stmt.name, FunctionRef(stmt, NoReceiver, env), mutable = false)
        is ClassDecl -> throw InterpreterException("Local classes are not supported")
    }

    @Composable
    private fun execC(stmt: Stmt, env: Env): Any? = when (stmt) {
        is ExprStmt -> evalC(stmt.expr, env)
        is PropertyDecl -> declareC(stmt, env)
        is DestructuringDecl -> {
            val value = evalC(stmt.initializer, env)
            when (value) {
                is Jump -> value
                is ErrorValue -> {
                    for (name in stmt.names) if (name != "_") env.define(name, value, stmt.isVar)
                    Unit
                }

                else -> showIfError(guard { destructure(value, stmt.names, env, stmt.isVar) })
            }
        }

        is Assign -> {
            val value = evalC(stmt.value, env)
            if (isStop(value)) value else showIfError(guard {
                assign(
                    stmt.target,
                    stmt.op,
                    value,
                    env
                )
            })
        }

        is ForStmt -> {
            val iterable = evalC(stmt.iterable, env)
            if (isStop(iterable)) {
                iterable
            } else {
                val items = guard {
                    iterate(iterable).toList()
                        .also { if (it.size > MAX_ITERATIONS) throw InterpreterException("Too many loop iterations") }
                }
                if (items is ErrorValue) {
                    showIfError(items)
                } else {
                    loopC(items as List<*>) { item, _ ->
                        val loopEnv = env.child()
                        val bound = guard { bindLoopVariable(stmt, item, loopEnv) }
                        if (bound is ErrorValue) showIfError(bound) else evalBlockC(
                            stmt.body,
                            loopEnv
                        )
                    }
                }
            }
        }

        is WhileStmt -> whileC(stmt, env)
        is ReturnStmt -> evalC(stmt, env)
        is BreakStmt -> Jump(Jump.Kind.BREAK)
        is ContinueStmt -> Jump(Jump.Kind.CONTINUE)
        is FunDecl -> env.define(stmt.name, FunctionRef(stmt, NoReceiver, env), mutable = false)
        is ClassDecl -> showIfError(fail("Local classes are not supported"))
    }

    @Composable
    private fun declareC(stmt: PropertyDecl, env: Env): Any {
        val delegate = stmt.delegate
        val initializer = stmt.initializer
        val value = when {
            delegate != null -> evalC(delegate, env)
            initializer != null -> evalC(initializer, env)
            else -> null
        }
        if (value is Jump) return value
        when {
            value is ErrorValue -> env.define(stmt.name, value, stmt.isVar)
            delegate != null -> env.defineDelegated(stmt.name, value)
            // `val x: T` assigned later, e.g. in the branches of a `when`, as in plain code.
            initializer == null -> env.define(stmt.name, null, mutable = true)
            else -> env.define(stmt.name, value, stmt.isVar)
        }
        return Unit
    }

    private fun forLoop(stmt: ForStmt, items: Iterable<Any?>, env: Env): Any {
        var count = 0
        for (item in items) {
            if (++count > MAX_ITERATIONS) throw InterpreterException("Too many loop iterations")
            val loopEnv = env.child()
            bindLoopVariable(stmt, item, loopEnv)
            val result = evalBlock(stmt.body, loopEnv)
            if (result is Jump) {
                if (result.kind == Jump.Kind.BREAK) break
                if (result.kind == Jump.Kind.RETURN) return result
            }
        }
        return Unit
    }

    private fun whileLoop(stmt: WhileStmt, env: Env): Any {
        var count = 0
        var first = stmt.isDoWhile
        while (true) {
            if (!first) {
                val condition = eval(stmt.condition, env)
                if (condition is Jump) return condition
                if (!truthy(condition)) break
            }
            first = false
            if (++count > MAX_ITERATIONS) throw InterpreterException("Too many loop iterations")
            val result = evalBlock(stmt.body, env.child())
            if (result is Jump) {
                if (result.kind == Jump.Kind.BREAK) break
                if (result.kind == Jump.Kind.RETURN) return result
            }
        }
        return Unit
    }

    @Composable
    private fun whileC(stmt: WhileStmt, env: Env): Any? {
        var result: Any? = Unit
        var iteration = 0
        var running = true
        while (running) {
            if (iteration > 0 || !stmt.isDoWhile) {
                val condition = evalC(stmt.condition, env)
                val proceed = if (isStop(condition)) condition
                else showIfError(guard { truthy(condition) })
                if (proceed != true) {
                    if (proceed is Jump || proceed is ErrorValue) result = proceed
                    running = false
                }
            }
            if (running) {
                if (iteration >= MAX_ITERATIONS) {
                    result = showIfError(fail("Too many loop iterations"))
                    running = false
                } else {
                    val r = key(iteration) { evalBlockC(stmt.body, env.child()) }
                    if (r is Jump && r.kind != Jump.Kind.CONTINUE) {
                        if (r.kind == Jump.Kind.RETURN) result = r
                        running = false
                    }
                    iteration++
                }
            }
        }
        return result
    }

    private fun assign(target: Expr, op: String, value: Any?, env: Env): Any {
        val newValue = if (op == "=") {
            value
        } else {
            val current = eval(target, env)
            if (current is MutableCollection<*> && (op == "+=" || op == "-=")) {
                @Suppress("UNCHECKED_CAST")
                val collection = current as MutableCollection<Any?>
                val items = value as? Iterable<Any?> ?: listOf(value)
                if (op == "+=") collection.addAll(items) else collection.removeAll(items.toSet())
                return Unit
            }
            binary(op.dropLast(1), current, value)
        }
        when (target) {
            is Ident -> assignName(target.name, newValue, env)
            is Member -> {
                val receiver = eval(target.receiver, env)
                if (target.isSafe && receiver == null) return Unit
                setMember(receiver, target.name, newValue)
            }

            is Index -> {
                val receiver = eval(target.receiver, env)
                val indices = target.indices.map { eval(it, env) }
                if (!builtins.setIndex(receiver, indices, newValue)) callMember(
                    receiver,
                    "set",
                    CallArgs(indices + newValue),
                    env
                )
            }

            else -> throw InterpreterException("Invalid assignment target")
        }
        return Unit
    }

    private fun assignName(name: String, value: Any?, env: Env) {
        if (env.contains(name)) return env.set(name, value)
        for (receiver in env.receivers) {
            if (receiver is UserValue && receiver.hasField(name)) return receiver.write(name, value)
            if (receiver !is UserValue && builtins.setProperty(receiver, name, value)) return
            if (receiver !is UserValue) registry.findSetter(receiver, name)
                ?.let { return it(receiver, value) }
        }
        val property = env.file.properties[name] ?: throw unresolved(name)
        if (!property.isVar) throw InterpreterException("Val cannot be reassigned: '$name'")
        initializeTopLevel(property)
        val propertyValues = scopeOf(property).propertyValues
        val current = propertyValues[name]
        if (current is Delegated) current.set(value) else propertyValues[name] = value
    }

    private fun setMember(receiver: Any?, name: String, value: Any?) {
        if (receiver is UserValue && receiver.hasField(name)) return receiver.write(name, value)
        if (builtins.setProperty(receiver, name, value)) return
        registry.findSetter(receiver, name)?.let { return it(receiver, value) }
        if (receiver is UserValue) throw InterpreterException("Cannot assign '$name' on ${typeName(receiver)}")
        // A library value without a binding for this property: the assignment is skipped.
        if (warnedOnce.add("set:${typeName(receiver)}.$name")) {
            warn("Assigning '$name' on ${typeName(receiver)} has no binding; it is skipped")
        }
    }

    // ---------- Expressions ----------

    internal fun eval(expr: Expr, env: Env): Any? {
        locate(expr.span, env)
        if (tick() != Budget.OK) throw InterpreterException(tooLong)
        return when (expr) {
            is IntLit -> expr.value
            is LongLit -> expr.value
            is FloatLit -> expr.value
            is DoubleLit -> expr.value
            is CharLit -> expr.value
            is BoolLit -> expr.value
            is NullLit -> null
            is ErrorExpr -> throw InterpreterException("Invalid code")
            is ObjectExpr -> createObject(expr.decl, env)
            is ThisExpr -> thisValue(env)
            is StringTemplate -> {
                val sb = StringBuilder()
                for (part in expr.parts) {
                    when (part) {
                        is StringPart.Text -> sb.append(part.value)
                        is StringPart.Template -> {
                            val value = eval(part.expr, env)
                            if (value is Jump) return value
                            sb.append(stringify(value))
                        }
                    }
                }
                sb.toString()
            }

            is Ident -> lookup(expr.name, env)
            is Member -> {
                if (isPackagePath(expr.receiver, env)) return lookup(expr.name, env)
                val receiver = eval(expr.receiver, env)
                if (receiver is Jump) return receiver
                if (expr.isSafe && receiver == null) null else getMember(receiver, expr.name)
            }

            is CallableRef -> callableRef(expr, env)
            is Call -> evalCall(expr, env)
            is Index -> {
                val receiver = eval(expr.receiver, env)
                if (receiver is Jump) return receiver
                index(receiver, expr.indices.map { eval(it, env) }, env)
            }

            is Lambda -> LambdaValue(expr, env, null)
            is Binary -> {
                val left = eval(expr.left, env)
                if (left is Jump) return left
                when (expr.op) {
                    "&&" -> return if (!truthy(left)) false else eval(
                        expr.right,
                        env
                    ).let { if (it is Jump) it else truthy(it) }

                    "||" -> return if (truthy(left)) true else eval(
                        expr.right,
                        env
                    ).let { if (it is Jump) it else truthy(it) }

                    "?:" -> return left ?: eval(expr.right, env)
                }
                val right = eval(expr.right, env)
                if (right is Jump) right else binary(expr.op, left, right)
            }

            is Unary -> unary(expr, env)
            is TypeCheck -> eval(expr.expr, env).let {
                if (it is Jump) it else isInstance(
                    it,
                    expr.type.text
                ) != expr.isNegated
            }

            is Cast -> eval(expr.expr, env).let {
                if (it is Jump) it else cast(
                    it,
                    expr.type.text,
                    expr.isSafe
                )
            }

            is IfExpr -> {
                val condition = eval(expr.condition, env)
                if (condition is Jump) return condition
                val otherwise = expr.otherwise
                when {
                    truthy(condition) -> evalBlock(expr.then, env.child())
                    otherwise != null -> evalBlock(otherwise, env.child())
                    else -> Unit
                }
            }

            is WhenExpr -> evalWhen(expr, env)
            is TryExpr -> evalTry(expr, env)
            is ThrowExpr -> eval(
                expr.value,
                env
            ).let { if (it is Jump) it else throw throwable(it) }

            is ReturnStmt -> {
                val valueExpr = expr.value
                val value = if (valueExpr != null) eval(valueExpr, env) else Unit
                if (value is Jump) value else Jump(Jump.Kind.RETURN, value, expr.label)
            }

            is BreakStmt -> Jump(Jump.Kind.BREAK)
            is ContinueStmt -> Jump(Jump.Kind.CONTINUE)
        }
    }

    @Composable
    private fun evalC(expr: Expr, env: Env): Any? {
        locate(expr.span, env)
        when (tick()) {
            Budget.OK -> Unit
            Budget.EXCEEDED -> return haltJump.also { showIfError(fail(tooLong)) }
            Budget.HALTED -> return haltJump
        }
        return evalExprC(expr, env)
    }

    @Composable
    private fun evalExprC(expr: Expr, env: Env): Any? = when (expr) {
        is Call -> key(expr.id) { evalCallC(expr, env) }
        is Member -> {
            val receiver =
                if (isPackagePath(expr.receiver, env)) PackagePath else evalC(expr.receiver, env)
            when {
                receiver === PackagePath -> showIfError(guard { lookup(expr.name, env) })
                isStop(receiver) -> receiver
                expr.isSafe && receiver == null -> null
                else -> {
                    val getter = registry.findComposableMember(receiver, expr.name, CallShape.None)
                    val userGetter = userGetter(receiver, expr.name)
                    when {
                        getter != null -> getter.binding(receiver, toArgs(CallArgs.Empty, env, getter.signature))
                        userGetter != null -> evalGetterC(userGetter, receiver)
                        else -> showIfError(guard { getMember(receiver, expr.name) })
                    }
                }
            }
        }

        is StringTemplate -> {
            val values = mutableListOf<Any?>()
            for (part in expr.parts) {
                values += when (part) {
                    is StringPart.Text -> part.value
                    is StringPart.Template -> evalC(part.expr, env)
                }
            }
            values.firstOrNull { isStop(it) } ?: values.joinToString("") { stringify(it) }
        }

        is Binary -> evalBinaryC(expr, env)
        is Unary -> {
            if (expr.op == "++" || expr.op == "--") {
                showIfError(guard { unary(expr, env) })
            } else {
                val operand = evalC(expr.operand, env)
                if (isStop(operand)) operand else showIfError(guard {
                    unaryValue(
                        expr.op,
                        operand
                    )
                })
            }
        }

        is TypeCheck -> {
            val value = evalC(expr.expr, env)
            if (isStop(value)) value else showIfError(guard {
                isInstance(
                    value,
                    expr.type.text
                ) != expr.isNegated
            })
        }

        is Cast -> {
            val value = evalC(expr.expr, env)
            if (isStop(value)) value else showIfError(guard {
                cast(
                    value,
                    expr.type.text,
                    expr.isSafe
                )
            })
        }

        is Index -> {
            val receiver = evalC(expr.receiver, env)
            val indices = mutableListOf<Any?>()
            for (e in expr.indices) indices += evalC(e, env)
            (listOf(receiver) + indices).firstOrNull { isStop(it) } ?: showIfError(guard {
                index(
                    receiver,
                    indices,
                    env
                )
            })
        }

        is IfExpr -> evalIfC(expr, env)
        is WhenExpr -> evalWhenC(expr, env)
        is ReturnStmt -> {
            val valueExpr = expr.value
            val value = if (valueExpr != null) evalC(valueExpr, env) else Unit
            if (isStop(value)) value else Jump(Jump.Kind.RETURN, value, expr.label)
        }

        is Lambda -> LambdaValue(expr, env, null)
        is Ident -> {
            val property = getterRead(expr.name, env)
            if (property != null) {
                evalGetterC(property.first, property.second)
            } else {
                showIfError(guard { eval(expr, env) })
            }
        }

        else -> showIfError(guard { eval(expr, env) })
    }

    /**
     * Property with a getter that [name] reads in [env], with its receiver: a member of an implicit receiver or a
     * top-level property. Null when [name] is something else, e.g. a local value or a stored property.
     */
    private fun getterRead(name: String, env: Env): Pair<PropertyDecl, Any?>? {
        if (env.find(name) !== Unresolved) return null
        for (receiver in env.receivers) {
            userGetter(receiver, name)?.let { return it to receiver }
            if (hasProperty(receiver, name)) return null
        }
        val decl = env.file.properties[name] ?: return null
        return if (decl.getter != null) decl to NoReceiver else null
    }

    /** Getter of a user class member or a user extension property that reads [name] on [receiver], if any. */
    private fun userGetter(receiver: Any?, name: String): PropertyDecl? {
        if (receiver is UserValue) {
            if (receiver.hasField(name)) return null
            findGetter(receiver.decl, name)?.let { return it }
        }
        return extensionProperties.firstOrNull { p -> p.name == name && isReceiverOf(receiver, p) }
    }

    /**
     * Runs a property's getter inside composition, so a `@Composable get()` can call composables such as
     * `MaterialTheme.colorScheme`.
     */
    @Composable
    private fun evalGetterC(property: PropertyDecl, receiver: Any?): Any? {
        val getter = property.getter ?: return showIfError(fail("Property '${property.name}' has no getter"))
        val base = memberClosure(receiver, property) ?: scopeOf(property).root
        val result = evalBlockC(getter, base.child(receiver))
        if (result is ErrorValue) return result
        return showIfError(guard { functionResult(result) })
    }

    private fun evalTry(expr: TryExpr, env: Env): Any? = try {
        try {
            evalBlock(expr.body, env.child())
        } catch (e: Throwable) {
            val clause = catchClause(expr, e) ?: throw e
            evalBlock(
                clause.body,
                env.child().apply { define(clause.name, caught(e), mutable = false) })
        }
    } finally {
        expr.finallyBlock?.let { evalBlock(it, env.child()) }
    }

    private suspend fun evalTryS(expr: TryExpr, env: Env): Any? = try {
        try {
            evalBlockS(expr.body, env.child())
        } catch (e: Throwable) {
            val clause = catchClause(expr, e) ?: throw e
            evalBlockS(
                clause.body,
                env.child().apply { define(clause.name, caught(e), mutable = false) })
        }
    } finally {
        expr.finallyBlock?.let { evalBlockS(it, env.child()) }
    }

    /** The first catch clause for [e]. Interpreter errors such as unresolved names are never caught. */
    private fun catchClause(expr: TryExpr, e: Throwable): CatchClause? {
        if (e is InterpreterException) return null
        return expr.catches.firstOrNull { isInstance(caught(e), it.type.text) }
    }

    /** The value a catch clause sees: the thrown user object, or the Kotlin exception. */
    private fun caught(e: Throwable): Any = if (e is ThrownValue) e.value else e

    /** The exception to throw for `throw value`. */
    private fun throwable(value: Any?): Throwable = when (value) {
        is Throwable -> value
        is UserValue -> ThrownValue(value)
        else -> InterpreterException("Only exceptions can be thrown, found ${typeName(value)}")
    }

    @Composable
    private fun evalBinaryC(expr: Binary, env: Env): Any? {
        val left = evalC(expr.left, env)
        if (isStop(left)) return left
        when (expr.op) {
            "&&", "||" -> {
                val l = guard { truthy(left) }
                if (l is ErrorValue) return showIfError(l)
                if ((expr.op == "&&" && l == false) || (expr.op == "||" && l == true)) return l
                val right = evalC(expr.right, env)
                return if (isStop(right)) right else showIfError(guard { truthy(right) })
            }

            "?:" -> return left ?: evalC(expr.right, env)
        }
        val right = evalC(expr.right, env)
        if (isStop(right)) return right
        return showIfError(guard { binary(expr.op, left, right) })
    }

    @Composable
    private fun evalIfC(expr: IfExpr, env: Env): Any? {
        val condition = evalC(expr.condition, env)
        if (isStop(condition)) return condition
        val test = guard { truthy(condition) }
        if (test is ErrorValue) return showIfError(test)
        val otherwise = expr.otherwise
        return if (test == true) {
            evalBlockC(expr.then, env.child())
        } else if (otherwise != null) {
            evalBlockC(otherwise, env.child())
        } else {
            Unit
        }
    }

    private fun evalWhen(expr: WhenExpr, env: Env): Any? {
        val subjectExpr = expr.subject
        val subject = if (subjectExpr != null) eval(subjectExpr, env) else null
        if (subject is Jump) return subject
        val scope = whenScope(expr, subject, env)
        for (branch in expr.branches) {
            val matches = branch.conditions.isEmpty() || branch.conditions.any { condition ->
                when (condition) {
                    is WhenCondition.Value -> eval(
                        condition.expr,
                        scope
                    ).let { if (subjectExpr == null) truthy(it) else it == subject }

                    is WhenCondition.Is -> isInstance(
                        subject,
                        condition.type.text
                    ) != condition.isNegated

                    is WhenCondition.In -> contains(
                        eval(condition.expr, scope),
                        subject,
                        scope
                    ) != condition.isNegated
                }
            }
            val guard = branch.guard
            if (matches && (guard == null || truthy(eval(guard, scope)))) return evalBlock(branch.body, scope.child())
        }
        return Unit
    }

    /** The scope of a `when`'s conditions and branches: [env], plus the subject's name in `when (val name = …)`. */
    private fun whenScope(expr: WhenExpr, subject: Any?, env: Env): Env {
        val name = expr.subjectName ?: return env
        return env.child().also { it.define(name, subject, mutable = false) }
    }

    @Composable
    private fun evalWhenC(expr: WhenExpr, env: Env): Any? {
        val subjectExpr = expr.subject
        val subject = if (subjectExpr != null) evalC(subjectExpr, env) else null
        if (isStop(subject)) return subject
        val scope = whenScope(expr, subject, env)
        var chosen = -1
        var stop: Any? = null
        var i = 0
        while (chosen < 0 && stop == null && i < expr.branches.size) {
            val conditions = expr.branches[i].conditions
            if (conditions.isEmpty()) {
                chosen = i
            } else {
                val values = mutableListOf<Any?>()
                for (condition in conditions) {
                    values += when (condition) {
                        is WhenCondition.Value -> evalC(condition.expr, scope)
                        is WhenCondition.In -> evalC(condition.expr, scope)
                        is WhenCondition.Is -> null
                    }
                }
                stop = values.firstOrNull { isStop(it) }
                if (stop == null) {
                    val matched = guard {
                        conditions.indices.any { c ->
                            when (val condition = conditions[c]) {
                                is WhenCondition.Value -> if (subjectExpr == null) truthy(values[c]) else values[c] == subject
                                is WhenCondition.Is -> isInstance(
                                    subject,
                                    condition.type.text
                                ) != condition.isNegated

                                is WhenCondition.In -> contains(
                                    values[c],
                                    subject,
                                    null
                                ) != condition.isNegated
                            }
                        }
                    }
                    val guard = expr.branches[i].guard
                    when {
                        matched is ErrorValue -> stop = showIfError(matched)
                        matched != true -> Unit
                        guard == null -> chosen = i
                        else -> {
                            val passed = evalC(guard, scope)
                            if (isStop(passed)) stop = passed else if (truthy(passed)) chosen = i
                        }
                    }
                }
            }
            i++
        }
        if (stop != null) return stop
        return if (chosen >= 0) evalBlockC(expr.branches[chosen].body, scope.child()) else Unit
    }

    private fun evalCall(call: Call, env: Env): Any? {
        val label = calleeName(call.callee)
        val arguments = mutableListOf<EvaluatedArgument>()
        for (arg in call.args) {
            val value =
                arg.value.let { if (it is Lambda) LambdaValue(it, env, label) else eval(it, env) }
            if (value is Jump) return value
            val name = arg.name
            when {
                name != null -> arguments += EvaluatedArgument(name, value)
                arg.isSpread -> spread(value).forEach { arguments += EvaluatedArgument(null, it) }
                else -> arguments += EvaluatedArgument(null, value)
            }
        }
        val args =
            CallArgs.ordered(arguments, call.trailingLambda?.let { LambdaValue(it, env, label) })
        locate(call.span, env)
        return when (val callee = call.callee) {
            is Ident -> invokeTarget(
                resolveCall(callee.name, args, env) ?: throw unresolved(callee.name, env.file),
                args,
                callee.name,
            )
            is Member -> {
                if (isPackagePath(
                        callee.receiver,
                        env
                    )
                ) return invokeTarget(resolveCall(callee.name, args, env), args, callee.name)
                val receiver = eval(callee.receiver, env)
                if (receiver is Jump) return receiver
                locate(call.span, env)
                if (callee.isSafe && receiver == null) null else callMember(
                    receiver,
                    callee.name,
                    args,
                    env
                )
            }

            else -> eval(callee, env).let { if (it is Jump) it else invokeValue(it, args) }
        }
    }

    @Composable
    private fun evalCallC(call: Call, env: Env): Any? {
        val label = calleeName(call.callee)
        val arguments = mutableListOf<EvaluatedArgument>()
        for (arg in call.args) {
            val argExpr = arg.value
            val value =
                if (argExpr is Lambda) LambdaValue(argExpr, env, label) else evalC(argExpr, env)
            val name = arg.name
            when {
                name != null -> arguments += EvaluatedArgument(name, value)
                arg.isSpread && !isStop(value) -> spread(value).forEach { arguments += EvaluatedArgument(null, it) }
                else -> arguments += EvaluatedArgument(null, value)
            }
        }
        val args =
            CallArgs.ordered(arguments, call.trailingLambda?.let { LambdaValue(it, env, label) })
        args.firstOrNull { isStop(it) }?.let { return it }
        locate(call.span, env)
        return when (val callee = call.callee) {
            is Ident -> invokeTargetC(
                guard { resolveCall(callee.name, args, env) },
                args,
                env,
                callee.name
            )

            is Member -> {
                val receiver = if (isPackagePath(callee.receiver, env)) PackagePath else evalC(
                    callee.receiver,
                    env
                )
                locate(call.span, env)
                when {
                    receiver === PackagePath -> invokeTargetC(guard {
                        resolveCall(
                            callee.name,
                            args,
                            env
                        )
                    }, args, env, callee.name)

                    isStop(receiver) -> receiver
                    callee.isSafe && receiver == null -> null
                    else -> {
                        val composableMember =
                            registry.findComposableMember(receiver, callee.name, args.shape)
                        if (composableMember != null) {
                            composableMember.binding(
                                receiver,
                                toArgs(args, env, composableMember.signature)
                            )
                        } else {
                            invokeTargetC(guard {
                                memberTarget(
                                    receiver,
                                    callee.name,
                                    args,
                                    env
                                )
                            }, args, env, callee.name)
                        }
                    }
                }
            }

            else -> {
                val fn = evalC(callee, env)
                if (isStop(fn)) fn else invokeComposable(
                    fn,
                    NoReceiver,
                    args.positional + listOfNotNull(args.trailing)
                )
            }
        }
    }

    private fun spread(value: Any?): List<Any?> = when (value) {
        is Iterable<*> -> value.toList()
        is Array<*> -> value.toList()
        else -> throw InterpreterException("Spread operator needs a list, found ${typeName(value)}")
    }

    private fun callableRef(expr: CallableRef, env: Env): Any? {
        val receiverExpr = expr.receiver
        if (receiverExpr == null) {
            env.find(expr.name).let { if (it !== Unresolved) return it }
            val decl = env.file.functions[expr.name]?.firstOrNull() ?: throw unresolved(expr.name)
            return FunctionRef(decl, NoReceiver, null)
        }
        if (expr.name != "class" && receiverExpr is Ident && isTypeReference(receiverExpr.name, env)) {
            return MemberRef(receiverExpr.name, expr.name, env)
        }
        val receiver = eval(receiverExpr, env)
        if (expr.name == "class") return receiver
        if (receiver is ClassRef) return MemberRef(receiver.decl.name, expr.name, env)
        val decl = (receiver as? UserValue)?.let { findMethod(it.decl, expr.name, CallArgs.Empty) }
            ?: throw InterpreterException("Unsupported callable reference '::${expr.name}'")
        return FunctionRef(decl, receiver, null)
    }

    /** True when [name] before `::` is a Kotlin or bound type rather than a value, e.g. `Char` in `Char::lowercase`. */
    private fun isTypeReference(name: String, env: Env): Boolean =
        env.find(name) === Unresolved && (name in KOTLIN_TYPES || registry.findType(name) != null)

    /** Calls `Type::name` on its first argument: a property when there are no other arguments, else a member call. */
    private fun invokeMemberRef(ref: MemberRef, args: CallArgs): Any? {
        val all = args.positional + listOfNotNull(args.trailing)
        if (all.isEmpty()) throw InterpreterException("'$ref' needs a receiver")
        val receiver = all.first()
        val rest = CallArgs(all.drop(1))
        if (rest.positional.isEmpty()) {
            val value = tryGetMember(receiver, ref.name)
            if (value !== Unresolved) return value
        }
        return callMember(receiver, ref.name, rest, ref.env)
    }

    private fun thisValue(env: Env): Any? {
        val receivers = env.receivers
        if (receivers.isEmpty()) throw InterpreterException("'this' is not defined in this context")
        return receivers.first()
    }

    private fun index(receiver: Any?, indices: List<Any?>, env: Env): Any? {
        if (receiver is StandIn) return StandIn("${receiver.name}[]")
        val value = builtins.index(receiver, indices)
        return if (value !== Unresolved) value else callMember(
            receiver,
            "get",
            CallArgs(indices),
            env
        )
    }

    internal fun binary(op: String, left: Any?, right: Any?): Any? {
        when (op) {
            "==" -> return left == right
            "!=" -> return left != right
            "===" -> return left === right
            "!==" -> return left !== right
            "in" -> return contains(right, left, null)
            "!in" -> return !contains(right, left, null)
        }
        // Comparisons with a stand-in are false; other operations give a stand-in, except adding it to a String.
        if ((left is StandIn || right is StandIn) && !(op == "+" && left is String)) {
            return if (op in setOf("<", ">", "<=", ">=")) false else left as? StandIn ?: right
        }
        val value = Operators.binary(op, left, right)
        if (value !== Unresolved) return value
        val name =
            Operators.functionName(op) ?: throw InterpreterException("Unsupported operator '$op'")
        val target = resolveMemberCall(left, name, CallArgs(listOf(right)), root)
            ?: return missingOperator(op, left, right)
        val result = invokeTarget(target, CallArgs(listOf(right)), name)
        if (name != "compareTo") return result
        val c = result as? Int ?: throw InterpreterException("compareTo must return Int")
        return when (op) {
            "<" -> c < 0
            ">" -> c > 0
            "<=" -> c <= 0
            else -> c >= 0
        }
    }

    /**
     * Result of an operator nothing provides. On your own classes it is an error; on library values, e.g. a
     * Compose type without a binding for it, a stand-in with one warning (a comparison is false).
     */
    private fun missingOperator(op: String, left: Any?, right: Any?): Any? {
        val message = "Operator '$op' is not defined for ${typeName(left)} and ${typeName(right)}"
        if (left is UserValue) throw InterpreterException(message)
        if (warnedOnce.add("operator:$op:${typeName(left)}:${typeName(right)}")) {
            warn("$message; a stand-in is used, so the preview may differ")
        }
        return if (op in setOf("<", ">", "<=", ">=")) false else StandIn("${typeName(left)} $op ${typeName(right)}")
    }

    private fun contains(container: Any?, element: Any?, env: Env?): Boolean {
        if (container is StandIn) return false
        val value = Operators.contains(container, element)
        if (value is Boolean) return value
        return callMember(container, "contains", CallArgs(listOf(element)), env ?: root) as? Boolean
            ?: throw InterpreterException("contains must return Boolean")
    }

    private fun unary(expr: Unary, env: Env): Any? {
        if (expr.op == "++" || expr.op == "--") {
            val current = eval(expr.operand, env)
            val updated = binary(if (expr.op == "++") "+" else "-", current, 1)
            assign(expr.operand, "=", updated, env)
            return if (expr.isPrefix) updated else current
        }
        val operand = eval(expr.operand, env)
        if (operand is Jump) return operand
        return unaryValue(expr.op, operand)
    }

    private fun unaryValue(op: String, operand: Any?): Any? = if (operand is StandIn && op != "!!") {
        // A stand-in condition is false, so its negation is true and an if/else-if pair shows exactly one side.
        if (op == "!") true else operand
    } else when (op) {
        "-" -> Operators.negate(operand).let {
            if (it !== Unresolved) it else callMember(
                operand,
                "unaryMinus",
                CallArgs.Empty,
                root
            )
        }

        "+" -> operand
        "!" -> !truthy(operand)
        "!!" -> operand ?: throw InterpreterException("NullPointerException: value is null")
        else -> throw InterpreterException("Unsupported operator '$op'")
    }

    private fun truthy(value: Any?): Boolean =
        value as? Boolean
            ?: if (value is StandIn) false
            else throw InterpreterException("Condition must be Boolean, found ${typeName(value)}")

    // ---------- Coroutines: bodies of LaunchedEffect, launch { } and suspend functions ----------

    /** Calls a top-level suspend or plain function and returns its result. */
    public suspend fun callSuspend(name: String, vararg args: Any?): Any? {
        val callArgs = CallArgs(args.toList())
        val decl = resolveFunction(name, callArgs, extension = false, main)
            ?: throw InterpreterException("Unresolved reference '$name'")
        if (running == 0) startRun()
        return callFunctionS(decl, NoReceiver, callArgs, null)
    }

    /** Invokes a function value inside a coroutine; errors other than cancellation are reported and return null. */
    internal suspend fun invokeSuspendSafely(fn: Any?, receiver: Any?, args: List<Any?>): Any? =
        try {
            // A coroutine starting or resuming runs between frames, outside other interpreted code.
            if (running == 0) startRun()
            invokeSuspend(fn, receiver, args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            report(describe(e))
            null
        }

    private suspend fun invokeSuspend(fn: Any?, receiver: Any?, args: List<Any?>): Any? =
        when (fn) {
            is LambdaValue -> {
                val env = fn.closure.child(receiver)
                bindLambdaParams(fn, env, args)
                lambdaResult(fn, evalBlockS(fn.lambda.body, env))
            }

            is FunctionRef -> callFunctionS(fn.decl, fn.receiver, CallArgs(args), fn.closure)
            else -> invokeValue(fn, CallArgs(args))
        }

    /** Runs a user function; suspend functions are evaluated with suspension support, others as plain code. */
    private suspend fun callFunctionS(
        decl: FunDecl,
        receiver: Any?,
        args: CallArgs,
        closure: Env?
    ): Any? {
        if (decl.isComposable) throw composableFromPlain(decl.name)
        if (!decl.isSuspend) return callFunction(decl, receiver, args, closure)
        val body = decl.body ?: return callWithoutBody(decl)
        val env = (closure ?: scopeOf(decl).root).child(receiver)
        for (p in bindParams(paramsOf(decl), args, env, decl.name, decl.isActual)) {
            val default = p.default ?: continue
            val value = try {
                evalS(default, env)
            } catch (e: InterpreterException) {
                emptyDefault(p, e.message)
            }
            env.define(p.name, value, mutable = false)
        }
        return functionResult(evalBlockS(body, env))
    }

    private suspend fun invokeTargetS(target: Target?, args: CallArgs, name: String): Any? =
        when (target) {
            is Target.Suspend -> suspending { target.call() }
            is Target.UserFunction -> callFunctionS(
                target.decl,
                target.receiver,
                args,
                target.closure
            )

            is Target.Value -> invokeSuspend(
                target.fn,
                NoReceiver,
                args.positional + listOfNotNull(args.trailing)
            )

            is Target.Intrinsic -> intrinsicS(target, args)
            else -> invokeTarget(target, args, name)
        }

    private suspend fun evalBlockS(block: Block, env: Env): Any? {
        var last: Any? = Unit
        for (statement in block.statements) {
            val result = execS(statement, env)
            if (result is Jump) return result
            last = if (statement is ExprStmt) result else Unit
        }
        return last
    }

    private suspend fun execS(stmt: Stmt, env: Env): Any? = when (stmt) {
        is ExprStmt -> evalS(stmt.expr, env)
        is PropertyDecl -> {
            val delegate = stmt.delegate
            val initializer = stmt.initializer
            when {
                delegate != null -> evalS(
                    delegate,
                    env
                ).let { if (it is Jump) it else env.defineDelegated(stmt.name, it) }

                initializer != null -> evalS(
                    initializer,
                    env
                ).let { if (it is Jump) it else env.define(stmt.name, it, stmt.isVar) }

                else -> env.define(stmt.name, null, mutable = true)
            }
        }

        is DestructuringDecl -> evalS(
            stmt.initializer,
            env
        ).let { if (it is Jump) it else destructure(it, stmt.names, env, stmt.isVar) }

        is Assign -> evalS(stmt.value, env).let {
            if (it is Jump) it else assign(
                stmt.target,
                stmt.op,
                it,
                env
            )
        }

        is ForStmt -> {
            val iterable = evalS(stmt.iterable, env)
            if (iterable is Jump) iterable else forLoopS(stmt, iterate(iterable), env)
        }

        is WhileStmt -> whileLoopS(stmt, env)
        is ReturnStmt -> evalS(stmt, env)
        is BreakStmt -> Jump(Jump.Kind.BREAK)
        is ContinueStmt -> Jump(Jump.Kind.CONTINUE)
        is FunDecl -> env.define(stmt.name, FunctionRef(stmt, NoReceiver, env), mutable = false)
        is ClassDecl -> throw InterpreterException("Local classes are not supported")
    }

    /** Loops in coroutines may run forever (e.g. a ticking clock), so instead of an iteration limit they yield regularly. */
    private suspend fun forLoopS(stmt: ForStmt, items: Iterable<Any?>, env: Env): Any {
        var count = 0
        for (item in items) {
            if (++count % YIELD_EVERY == 0) yield()
            val loopEnv = env.child()
            bindLoopVariable(stmt, item, loopEnv)
            val result = evalBlockS(stmt.body, loopEnv)
            if (result is Jump) {
                if (result.kind == Jump.Kind.BREAK) break
                if (result.kind == Jump.Kind.RETURN) return result
            }
        }
        return Unit
    }

    private suspend fun whileLoopS(stmt: WhileStmt, env: Env): Any {
        var count = 0
        var first = stmt.isDoWhile
        while (true) {
            if (!first) {
                val condition = evalS(stmt.condition, env)
                if (condition is Jump) return condition
                if (!truthy(condition)) break
            }
            first = false
            if (++count % YIELD_EVERY == 0) yield()
            val result = evalBlockS(stmt.body, env.child())
            if (result is Jump) {
                if (result.kind == Jump.Kind.BREAK) break
                if (result.kind == Jump.Kind.RETURN) return result
            }
        }
        return Unit
    }

    private suspend fun evalS(expr: Expr, env: Env): Any? {
        locate(expr.span, env)
        if (tick() != Budget.OK) throw InterpreterException(tooLong)
        return when (expr) {
            is Call -> evalCallS(expr, env)
            is Member -> {
                if (isPackagePath(expr.receiver, env)) return lookup(expr.name, env)
                val receiver = evalS(expr.receiver, env)
                if (receiver is Jump) return receiver
                if (expr.isSafe && receiver == null) null else getMember(receiver, expr.name)
            }

            is StringTemplate -> {
                val sb = StringBuilder()
                for (part in expr.parts) {
                    when (part) {
                        is StringPart.Text -> sb.append(part.value)
                        is StringPart.Template -> {
                            val value = evalS(part.expr, env)
                            if (value is Jump) return value
                            sb.append(stringify(value))
                        }
                    }
                }
                sb.toString()
            }

            is Binary -> {
                val left = evalS(expr.left, env)
                if (left is Jump) return left
                when (expr.op) {
                    "&&" -> return if (!truthy(left)) false else evalS(
                        expr.right,
                        env
                    ).let { it as? Jump ?: truthy(it) }

                    "||" -> return if (truthy(left)) true else evalS(
                        expr.right,
                        env
                    ).let { it as? Jump ?: truthy(it) }

                    "?:" -> return left ?: evalS(expr.right, env)
                }
                val right = evalS(expr.right, env)
                right as? Jump ?: binary(expr.op, left, right)
            }

            is Unary -> {
                if (expr.op == "++" || expr.op == "--") return unary(expr, env)
                val operand = evalS(expr.operand, env)
                operand as? Jump ?: unaryValue(expr.op, operand)
            }

            is Index -> {
                val receiver = evalS(expr.receiver, env)
                if (receiver is Jump) return receiver
                val indices = mutableListOf<Any?>()
                for (e in expr.indices) indices += evalS(e, env)
                index(receiver, indices, env)
            }

            is TypeCheck -> evalS(expr.expr, env).let {
                it as? Jump
                    ?: (isInstance(
                        it,
                        expr.type.text
                    ) != expr.isNegated)
            }

            is Cast -> evalS(expr.expr, env).let {
                it as? Jump
                    ?: cast(
                        it,
                        expr.type.text,
                        expr.isSafe
                    )
            }

            is IfExpr -> {
                val condition = evalS(expr.condition, env)
                if (condition is Jump) return condition
                val otherwise = expr.otherwise
                when {
                    truthy(condition) -> evalBlockS(expr.then, env.child())
                    otherwise != null -> evalBlockS(otherwise, env.child())
                    else -> Unit
                }
            }

            is WhenExpr -> evalWhenS(expr, env)
            is TryExpr -> evalTryS(expr, env)
            is ThrowExpr -> evalS(
                expr.value,
                env
            ).let { it as? Jump ?: throw throwable(it) }

            is ReturnStmt -> {
                val valueExpr = expr.value
                val value = if (valueExpr != null) evalS(valueExpr, env) else Unit
                value as? Jump ?: Jump(Jump.Kind.RETURN, value, expr.label)
            }

            else -> eval(expr, env)
        }
    }

    private suspend fun evalWhenS(expr: WhenExpr, env: Env): Any? {
        val subjectExpr = expr.subject
        val subject = if (subjectExpr != null) evalS(subjectExpr, env) else null
        if (subject is Jump) return subject
        val scope = whenScope(expr, subject, env)
        for (branch in expr.branches) {
            var matches = branch.conditions.isEmpty()
            for (condition in branch.conditions) {
                if (matches) break
                matches = when (condition) {
                    is WhenCondition.Value -> evalS(
                        condition.expr,
                        scope
                    ).let { if (subjectExpr == null) truthy(it) else it == subject }

                    is WhenCondition.Is -> isInstance(
                        subject,
                        condition.type.text
                    ) != condition.isNegated

                    is WhenCondition.In -> contains(
                        evalS(condition.expr, scope),
                        subject,
                        scope
                    ) != condition.isNegated
                }
            }
            val guard = branch.guard
            if (matches && (guard == null || truthy(evalS(guard, scope)))) return evalBlockS(branch.body, scope.child())
        }
        return Unit
    }

    private suspend fun evalCallS(call: Call, env: Env): Any? {
        val label = calleeName(call.callee)
        val arguments = mutableListOf<EvaluatedArgument>()
        for (arg in call.args) {
            val argExpr = arg.value
            val value =
                if (argExpr is Lambda) LambdaValue(argExpr, env, label) else evalS(argExpr, env)
            if (value is Jump) return value
            val name = arg.name
            when {
                name != null -> arguments += EvaluatedArgument(name, value)
                arg.isSpread -> spread(value).forEach { arguments += EvaluatedArgument(null, it) }
                else -> arguments += EvaluatedArgument(null, value)
            }
        }
        val args =
            CallArgs.ordered(arguments, call.trailingLambda?.let { LambdaValue(it, env, label) })
        locate(call.span, env)
        return when (val callee = call.callee) {
            is Ident -> invokeTargetS(
                resolveCall(callee.name, args, env) ?: throw unresolved(callee.name, env.file),
                args,
                callee.name,
            )
            is Member -> {
                if (isPackagePath(
                        callee.receiver,
                        env
                    )
                ) return invokeTargetS(resolveCall(callee.name, args, env), args, callee.name)
                val receiver = evalS(callee.receiver, env)
                if (receiver is Jump) return receiver
                locate(call.span, env)
                if (callee.isSafe && receiver == null) return null
                invokeTargetS(memberTarget(receiver, callee.name, args, env), args, callee.name)
            }

            else -> evalS(callee, env).let {
                if (it is Jump) it else invokeSuspend(
                    it,
                    NoReceiver,
                    args.positional + listOfNotNull(args.trailing)
                )
            }
        }
    }

    private suspend fun intrinsicS(target: Target.Intrinsic, args: CallArgs): Any? {
        val fn = args.trailing ?: args.positional.last()
        val receiver = target.receiver
        return when (target.name) {
            "repeat" -> {
                val times = args.positional.firstOrNull() as? Int
                    ?: throw InterpreterException("repeat() needs an Int")
                for (i in 0 until times) invokeSuspend(
                    fn,
                    NoReceiver,
                    listOf(i)
                ).let { if (it is Jump) return it }
                Unit
            }

            "run" -> invokeSuspend(fn, receiver, emptyList())
            "with" -> invokeSuspend(fn, args.positional.firstOrNull(), emptyList())
            "buildList", "buildSet", "buildMap" -> {
                val collection = builderCollection(target.name)
                invokeSuspend(fn, collection, emptyList()).let { if (it is Jump) it else collection }
            }
            "let" -> invokeSuspend(fn, NoReceiver, listOf(receiver))
            "also" -> invokeSuspend(
                fn,
                NoReceiver,
                listOf(receiver)
            ).let { if (it is Jump) it else receiver }

            "apply" -> invokeSuspend(
                fn,
                receiver,
                emptyList()
            ).let { if (it is Jump) it else receiver }

            "forEach" -> {
                for (item in iterate(receiver)) invokeSuspend(
                    fn,
                    NoReceiver,
                    listOf(item)
                ).let { if (it is Jump) return it }
                Unit
            }

            "forEachIndexed" -> {
                for ((i, item) in iterate(receiver).withIndex()) invokeSuspend(
                    fn,
                    NoReceiver,
                    listOf(i, item)
                ).let { if (it is Jump) return it }
                Unit
            }

            in LAMBDA_COLLECTION_FUNCTIONS -> {
                val items = iterate(receiver).toList()
                val indexed = target.name in INDEXED_LAMBDAS
                val results = ArrayList<Any?>(items.size)
                for ((i, item) in items.withIndex()) {
                    val result = invokeSuspend(fn, NoReceiver, if (indexed) listOf(i, item) else listOf(item))
                    if (result is Jump) return result
                    results += result
                }
                builtinMember(receiver, target.name, withResults(args, target.name, items, results))
            }

            in SINGLE_LAMBDA_FUNCTIONS -> when (val plan = singleLambdaPlan(receiver, target.name, args)) {
                is SingleLambda.Result -> plan.value
                is SingleLambda.Run -> invokeSuspend(fn, NoReceiver, plan.args).let { if (it is Jump) it else plan.result(it) }
            }

            else -> throw unresolved(target.name)
        }
    }

    // ---------- Types ----------

    private fun baseTypeName(text: String): String =
        text.substringBefore('<').substringBefore('(').trim().removeSuffix("?")
            .substringAfterLast('.').trim()

    /** `value is Type` for Kotlin types, user classes and registry types. */
    internal fun isInstance(value: Any?, typeText: String): Boolean {
        val text = typeText.trim().let { t -> if (t.startsWith("@")) t.substringAfter(' ') else t }
        if (value == null) return text.endsWith("?")
        if (value is StandIn) return false
        if (value is StandInObject) return value.name == baseTypeName(text)
        if ("->" in text) return value is LambdaValue || value is FunctionRef || value is MemberRef || value is ComposableSlot || value is Function<*>
        return when (val name = baseTypeName(text)) {
            "Any" -> true
            "Int" -> value is Int
            "Long" -> value is Long
            "Float" -> value is Float
            "Double" -> value is Double
            "Number" -> value is Number
            "String", "CharSequence" -> value is String
            "Char" -> value is Char
            "Boolean" -> value is Boolean
            "Unit" -> value == Unit
            "List", "MutableList", "Collection", "Iterable", "ArrayList" -> value is List<*>
            "Set", "MutableSet" -> value is Set<*>
            "Map", "MutableMap" -> value is Map<*, *>
            "Pair" -> value is Pair<*, *>
            "IntRange" -> value is IntRange
            else -> {
                if (isExceptionType(name)) return if (value is Throwable) value.isA(name) else value is UserValue && isSubclass(
                    value.decl,
                    name
                )
                registry.findType(name)?.let { return it(value) }
                if (value is UserValue) return isSubclass(value.decl, name)
                if (scopes.any { name in it.classes }) return false
                // A value from library code, e.g. the receiver of `fun EditorTheme.toColorScheme()`.
                if (value::class.simpleName == name) return true
                externals?.let { resolver -> runCatching { resolver.isInstance(value, name) }.getOrNull()?.let { return it } }
                throw InterpreterException("Unsupported type '$name'")
            }
        }
    }

    private fun isSubclass(decl: ClassDecl, name: String): Boolean {
        if (decl.name == name) return true
        return decl.superTypes.any { superType ->
            val superName = baseTypeName(superType.text)
            superName == name || exceptionExtends(superName, name) ||
                    scopeOf(decl).classes[superName]?.let {
                        it !== decl && isSubclass(
                            it,
                            name
                        )
                    } == true
        }
    }

    private fun cast(value: Any?, typeText: String, isSafe: Boolean): Any? {
        if (value is StandIn) return value
        val matches = try {
            isInstance(value, typeText)
        } catch (_: InterpreterException) {
            true // unknown types are not checked
        }
        return when {
            matches -> value
            isSafe -> null
            else -> throw InterpreterException("${typeName(value)} cannot be cast to ${typeText.trim()}")
        }
    }

    private companion object {
        const val MAX_DEPTH = 200
        const val MAX_ITERATIONS = 100_000
        const val YIELD_EVERY = 1_000
        const val CHECK_EVERY = 4_096

        /** A suspend call that took at least this long really waited, so the thread was free meanwhile. */
        val MIN_WAIT = 4.milliseconds
        /** Default for [maxRunTime]: long enough for any real preview, short enough to avoid a frozen app. */
        val DEFAULT_MAX_RUN_TIME: Duration = 2.seconds
        val PAUSE = 50.milliseconds
        /** Kotlin types that can appear before `::` in a member reference. */
        val KOTLIN_TYPES = setOf(
            "Any", "String", "CharSequence", "Char", "Int", "Long", "Short", "Byte", "Float", "Double", "Number",
            "Boolean", "List", "MutableList", "Collection", "Iterable", "Set", "MutableSet", "Map", "MutableMap", "Pair",
            "Triple", "IntRange",
        )
        val GLOBAL_INTRINSICS = setOf("repeat", "run", "with", "buildList", "buildSet", "buildMap")

        /** `java.lang` classes that platform code uses without an import; unbound ones run as stand-ins. */
        val IMPLICIT_JAVA_LANG = setOf(
            "System", "Math", "StrictMath", "Thread", "Runtime", "Integer", "Character", "Process", "ProcessBuilder",
            "Class", "ClassLoader", "Object",
        )
        val MEMBER_INTRINSICS = setOf("let", "also", "apply", "run", "forEach", "forEachIndexed")
        val ITERATING_INTRINSICS = setOf("forEach", "forEachIndexed")

        /**
         * Collection functions whose trailing lambda runs in the caller's evaluator, so it may make composable or
         * suspend calls, as Kotlin allows in inline lambdas; the builtins do the rest.
         */
        val LAMBDA_COLLECTION_FUNCTIONS = setOf(
            "map", "mapIndexed", "mapNotNull", "flatMap", "filter", "filterNot", "filterIndexed", "any", "all", "none",
            "count", "sumOf", "maxByOrNull", "minByOrNull", "sortedBy", "sortedByDescending", "groupBy", "associateWith",
            "associateBy", "joinToString", "first", "last", "firstOrNull", "lastOrNull", "find",
        )

        /** Functions that run their trailing lambda once or not at all, also in the caller's evaluator. */
        val SINGLE_LAMBDA_FUNCTIONS = setOf("ifEmpty", "ifBlank", "takeIf", "takeUnless", "getOrElse")

        /** Calls that create a ViewModel or an injected object, which previews can't do. */
        val INJECTION_CALLS = setOf("viewModel", "hiltViewModel", "koinViewModel", "koinInject", "inject", "get")

        /** Of [LAMBDA_COLLECTION_FUNCTIONS], those whose lambda takes the index first. */
        val INDEXED_LAMBDAS = setOf("mapIndexed", "filterIndexed")
    }
}
