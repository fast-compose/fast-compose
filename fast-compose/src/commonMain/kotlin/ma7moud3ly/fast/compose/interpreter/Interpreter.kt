package ma7moud3ly.fast.compose.interpreter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import ma7moud3ly.fast.compose.parser.Span
import ma7moud3ly.fast.compose.parser.ast.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.yield

/** Evaluated call arguments. [trailing] is the trailing lambda, if any. */
internal class CallArgs(
    val positional: List<Any?>,
    val named: Map<String, Any?> = emptyMap(),
    val trailing: Any? = null,
) {
    /** First argument matching [predicate], without copying the arguments into one list. */
    inline fun firstOrNull(predicate: (Any?) -> Boolean): Any? {
        for (value in positional) if (predicate(value)) return value
        for (value in named.values) if (predicate(value)) return value
        return trailing?.takeIf(predicate)
    }

    val shape = CallShape(positional, named, trailing)

    companion object {
        val Empty = CallArgs(emptyList())
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
 */
public class Interpreter(
    public val program: Program,
    public val registry: Registry,
    private val onError: (RuntimeError) -> Unit = {},
    private val onLog: (String) -> Unit = { println(it) },
    dependencies: List<Program> = emptyList(),
    private val maxRunTime: Duration = DEFAULT_MAX_RUN_TIME,
) {
    private val main = FileScope(program)
    private val scopes: List<FileScope> =
        listOf(main) + dependencies.filter { it !== program }.map(::FileScope)
    private val root = main.root
    private val builtins = Builtins(this)
    private val extensionProperties: List<PropertyDecl> =
        scopes.flatMap { scope ->
            scope.program.declarations.filterIsInstance<PropertyDecl>()
                .filter { it.receiver != null }
        }
    private var depth = 0

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
        showIfError(callFunctionC(entry, NoReceiver, CallArgs.Empty, null))
    }

    /** Calls a top-level non-composable function and returns its result. */
    public fun call(name: String, vararg args: Any?): Any? {
        val callArgs = CallArgs(args.toList())
        val decl = resolveFunction(name, callArgs, extension = false, main)
            ?: throw InterpreterException("Unresolved reference '$name'")
        return callFunction(decl, NoReceiver, callArgs, null)
    }

    /** Reports a problem to [onError], at the line of the code being evaluated, without failing. */
    public fun report(message: String): Unit =
        onError(RuntimeError(message, location?.line, file = locationFile))

    /** Reports a warning to [onError], at the line of the code being evaluated, e.g. for a missing resource file. */
    public fun warn(message: String): Unit =
        onError(RuntimeError(message, location?.line, isWarning = true, file = locationFile))

    internal fun log(message: String) = onLog(message)

    /** Evaluates an expression at top level, e.g. an annotation argument. */
    public fun evaluate(expr: Expr): Any? = eval(expr, root)

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
    internal fun invokeComposable(fn: Any?, receiver: Any?, args: List<Any?>): Any? = when (fn) {
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

    /** Invokes a function value from plain code such as an event handler; errors are reported and returned as [ErrorValue]. */
    internal fun invokeSafely(fn: Any?, receiver: Any?, args: List<Any?>): Any? = guard {
        if (fn is LambdaValue) invokeLambda(fn, receiver, args) else invokeValue(fn, CallArgs(args))
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
        val candidates =
            scope.functions[name]?.filter { (it.receiver != null) == extension } ?: return null
        return candidates.firstOrNull { accepts(it.params, args) } ?: candidates.firstOrNull()
    }

    private fun accepts(params: List<Param>, args: CallArgs): Boolean {
        if (params.any { it.isVararg }) return true
        if (args.named.keys.any { n -> params.none { it.name == n } }) return false
        val supplied =
            args.positional.size + args.named.size + (if (args.trailing != null) 1 else 0)
        val required = params.count { it.default == null }
        return supplied in required..params.size
    }

    /** Binds arguments into [env] and returns the parameters that need their default value evaluated. */
    private fun bindParams(
        params: List<Param>,
        args: CallArgs,
        env: Env,
        owner: String
    ): List<Param> {
        val missing = mutableListOf<Param>()
        var next = 0
        var trailingUsed = false
        params.forEachIndexed { i, p ->
            when {
                p.isVararg -> {
                    env.define(p.name, args.positional.drop(next), mutable = false)
                    next = args.positional.size
                }

                p.name in args.named -> env.define(p.name, args.named[p.name], mutable = false)
                next < args.positional.size -> env.define(
                    p.name,
                    args.positional[next++],
                    mutable = false
                )

                i == params.lastIndex && args.trailing != null -> {
                    env.define(p.name, args.trailing, mutable = false)
                    trailingUsed = true
                }

                p.default != null -> missing += p
                else -> throw InterpreterException("No value passed for parameter '${p.name}' of '$owner'")
            }
        }
        if (next < args.positional.size || (args.trailing != null && !trailingUsed)) throw InterpreterException(
            "Too many arguments for '$owner'"
        )
        args.named.keys.firstOrNull { n -> params.none { it.name == n } }?.let {
            throw InterpreterException("Cannot find a parameter with this name: $it")
        }
        return missing
    }

    private fun enter(name: String) {
        if (++depth > MAX_DEPTH) {
            depth = 0
            throw InterpreterException("Stack overflow in '$name'")
        }
    }

    private fun functionResult(result: Any?): Any? = when {
        result is Jump && result.kind == Jump.Kind.RETURN -> result.value
        result is Jump -> throw InterpreterException("'break' and 'continue' are only allowed inside a loop")
        else -> Unit
    }

    internal fun callFunction(decl: FunDecl, receiver: Any?, args: CallArgs, closure: Env?): Any? {
        val body = decl.body ?: throw InterpreterException("Function '${decl.name}' has no body")
        enter(decl.name)
        try {
            val env = (closure ?: scopeOf(decl).root).child(receiver)
            for (p in bindParams(decl.params, args, env, decl.name)) {
                val default = p.default ?: continue
                env.define(p.name, eval(default, env), mutable = false)
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
        showIfError(runFunctionC(decl, receiver, args, closure))
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
        val body = decl.body ?: return showIfError(fail("Function '${decl.name}' has no body"))
        if (depth >= MAX_DEPTH) return showIfError(fail("Stack overflow in '${decl.name}'"))
        val env = (closure ?: scopeOf(decl).root).child(receiver)
        val missing = guard { bindParams(decl.params, args, env, decl.name) }
        if (missing is ErrorValue) return showIfError(missing)
        for (p in missing as List<*>) {
            p as Param
            val default = p.default
            if (default != null) env.define(p.name, evalC(default, env), mutable = false)
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
        val env = scopeOf(decl).root.child(value)
        for (p in bindParams(decl.constructorParams.map { it.param }, args, env, decl.name)) {
            val default = p.default ?: continue
            env.define(p.name, eval(default, env), mutable = false)
        }
        for (pp in decl.constructorParams) if (pp.isProperty) value.fields[pp.param.name] =
            env.get(pp.param.name)
        for (member in decl.members) {
            if (member !is PropertyDecl) continue
            val delegate = member.delegate
            val initializer = member.initializer
            when {
                delegate != null -> value.fields[member.name] = Delegated(eval(delegate, env))
                initializer != null -> value.fields[member.name] = eval(initializer, env)
            }
        }
        for (block in decl.initBlocks) evalBlock(block, env.child())
    }

    private fun objectInstance(decl: ClassDecl): ObjectValue {
        val objects = scopeOf(decl).objects
        objects[decl.id]?.let { return it }
        val obj = ObjectValue(decl)
        objects[decl.id] = obj
        initialize(obj, CallArgs.Empty)
        return obj
    }

    private fun enumEntries(decl: ClassDecl): List<EnumValue> =
        scopeOf(decl).enumValues.getOrPut(decl.id) {
            val root = scopeOf(decl).root
            decl.enumEntries.mapIndexed { i, entry ->
                val value = EnumValue(decl, entry.name, i)
                val positional = mutableListOf<Any?>()
                val named = mutableMapOf<String, Any?>()
                for (arg in entry.args) {
                    val argName = arg.name
                    if (argName != null) named[argName] =
                        eval(arg.value, root) else positional += eval(arg.value, root)
                }
                initialize(value, CallArgs(positional, named))
                value
            }
        }

    /** Finds a method in a user class or its user-declared supertypes. */
    private fun findMethod(decl: ClassDecl, name: String, args: CallArgs): FunDecl? {
        val methods =
            decl.members.filterIsInstance<FunDecl>().filter { it.name == name && it.body != null }
        methods.firstOrNull { accepts(it.params, args) }?.let { return it }
        methods.firstOrNull()?.let { return it }
        for (superType in decl.superTypes) {
            val superDecl = scopeOf(decl).classes[baseTypeName(superType.text)] ?: continue
            if (superDecl !== decl) findMethod(superDecl, name, args)?.let { return it }
        }
        return null
    }

    private fun findGetter(decl: ClassDecl, name: String): PropertyDecl? =
        decl.members.filterIsInstance<PropertyDecl>()
            .firstOrNull { it.name == name && it.getter != null }

    private fun evalGetter(property: PropertyDecl, receiver: Any?): Any? {
        val getter = property.getter
            ?: throw InterpreterException("Property '${property.name}' has no getter")
        return functionResult(evalBlock(getter, scopeOf(property).root.child(receiver)))
    }

    private fun topLevelProperty(decl: PropertyDecl): Any? {
        if (decl.getter != null) return evalGetter(decl, NoReceiver)
        val scope = scopeOf(decl)
        val root = scope.root
        val propertyValues = scope.propertyValues
        if (decl.name !in propertyValues) {
            propertyValues[decl.name] = Unresolved
            val delegate = decl.delegate
            val initializer = decl.initializer
            propertyValues[decl.name] = when {
                delegate != null -> Delegated(eval(delegate, root))
                initializer != null -> eval(initializer, root)
                else -> null
            }
        }
        return when (val value = propertyValues[decl.name]) {
            Unresolved -> throw InterpreterException("Property '${decl.name}' depends on itself")
            is Delegated -> value.get()
            else -> value
        }
    }

    // ---------- Name and member resolution ----------

    /** File whose code runs in [this] scope. */
    private val Env.file: FileScope get() = scope ?: main

    /** File that declares [decl]. */
    private fun scopeOf(decl: Decl): FileScope =
        if (scopes.size == 1) main else scopes.firstOrNull { it.owns(decl) } ?: main

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
        env.find(name).let { if (it !== Unresolved) return Target.Value(it) }
        for (receiver in env.receivers) resolveMemberCall(
            receiver,
            name,
            args,
            env
        )?.let { return it }
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
        return null
    }

    private fun resolveMemberCall(receiver: Any?, name: String, args: CallArgs, env: Env): Target? {
        when (receiver) {
            is UserValue -> {
                findMethod(receiver.decl, name, args)?.let {
                    return Target.UserFunction(
                        it,
                        receiver,
                        null
                    )
                }
                if (receiver.hasField(name)) return Target.Value(receiver.read(name))
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

            is LambdaValue, is FunctionRef -> if (name == "invoke") return Target.Value(receiver)
        }
        resolveExtension(name, receiver, args, env)?.let {
            return Target.UserFunction(
                it,
                receiver,
                null
            )
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
        val named = params.associateWith { value.read(it) }.toMutableMap()
        args.positional.forEachIndexed { i, v -> named[params[i]] = v }
        named.putAll(args.named)
        return instantiate(value.decl, CallArgs(emptyList(), named))
    }

    /** Matches call arguments to a binding's [signature]; problems such as unknown parameter names are reported. */
    private fun toArgs(args: CallArgs, env: Env, signature: Signature): Args {
        val match = signature.match(args.positional, args.named, args.trailing)
        for (problem in match.problems) report(problem)
        return Args(signature, match.values, env.receivers, this)
    }

    private fun invokeTarget(target: Target?, args: CallArgs, name: String): Any? = when (target) {
        null -> throw unresolved(name)
        is Target.Done -> target.value
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
    }

    @Composable
    private fun invokeTargetC(target: Any?, args: CallArgs, env: Env, name: String): Any? =
        when (target) {
            null -> showIfError(fail(unresolved(name).message ?: name))
            is ErrorValue -> showIfError(target)
            is Target.Done -> target.value
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
            else -> showIfError(fail("Cannot call '$name'"))
        }

    private fun unresolved(name: String): InterpreterException {
        registry.findHint(name)?.let { return InterpreterException(it) }
        val known = registry.globalNames() + main.functions.keys + main.classes.keys
        val suggestion =
            known.filter { editDistance(it, name) <= 2 }.minByOrNull { editDistance(it, name) }
        return InterpreterException("Unresolved reference '$name'" + (suggestion?.let { ". Did you mean '$it'?" }
            ?: ""))
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
        else -> builtins.property(receiver, name) !== Unresolved
    }

    /** Value of an identifier: local variable, implicit receiver member, top-level declaration, registry value or builtin. */
    private fun lookup(name: String, env: Env): Any? {
        env.find(name).let { if (it !== Unresolved) return it }
        for (receiver in env.receivers) {
            val value = tryGetMember(receiver, name)
            if (value !== Unresolved) return value
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
        throw unresolved(name)
    }

    private fun tryGetMember(receiver: Any?, name: String): Any? {
        when (receiver) {
            is UserValue -> {
                if (receiver.hasField(name)) return receiver.read(name)
                findGetter(receiver.decl, name)?.let { return evalGetter(it, receiver) }
                if (receiver is EnumValue && name == "name") return receiver.name
                if (receiver is EnumValue && name == "ordinal") return receiver.ordinal
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
            .firstOrNull { p ->
                p.name == name && p.receiver?.let {
                    isInstance(
                        receiver,
                        it.text
                    )
                } == true
            }
            ?.let { return evalGetter(it, receiver) }
        registry.findMember(receiver, name, CallShape.None)
            ?.let { return it.binding(receiver, Args(it.signature, emptyMap(), emptyList(), this)) }
        return builtins.property(receiver, name)
    }

    private fun getMember(receiver: Any?, name: String): Any? {
        val value = tryGetMember(receiver, name)
        if (value === Unresolved) throw InterpreterException(
            "Unresolved reference '$name' on ${typeName(receiver)}"
        )
        return value
    }

    private fun callMember(receiver: Any?, name: String, args: CallArgs, env: Env): Any? {
        val target = resolveMemberCall(receiver, name, args, env)
            ?: throw InterpreterException("Unresolved reference '$name' on ${typeName(receiver)}")
        return invokeTarget(target, args, name)
    }

    // ---------- Intrinsics: scope functions and loops whose lambdas may contain composable calls ----------

    private fun isIterable(value: Any?) =
        value is Iterable<*> || value is Map<*, *> || value is CharSequence

    private fun iterate(value: Any?): Iterable<Any?> = when (value) {
        is Iterable<*> -> value
        is Map<*, *> -> value.entries
        is CharSequence -> value.asIterable()
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

            else -> throw unresolved(target.name)
        }
    }

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
            if (result is Jump) {
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
        topLevelProperty(property)
        val propertyValues = scopeOf(property).propertyValues
        val current = propertyValues[name]
        if (current is Delegated) current.set(value) else propertyValues[name] = value
    }

    private fun setMember(receiver: Any?, name: String, value: Any?) {
        if (receiver is UserValue && receiver.hasField(name)) return receiver.write(name, value)
        if (builtins.setProperty(receiver, name, value)) return
        registry.findSetter(receiver, name)?.let { return it(receiver, value) }
        throw InterpreterException("Cannot assign '$name' on ${typeName(receiver)}")
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
                    if (getter != null) {
                        getter.binding(receiver, toArgs(CallArgs.Empty, env, getter.signature))
                    } else {
                        showIfError(guard { getMember(receiver, expr.name) })
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
        else -> showIfError(guard { eval(expr, env) })
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
        for (branch in expr.branches) {
            val matches = branch.conditions.isEmpty() || branch.conditions.any { condition ->
                when (condition) {
                    is WhenCondition.Value -> eval(
                        condition.expr,
                        env
                    ).let { if (subjectExpr == null) truthy(it) else it == subject }

                    is WhenCondition.Is -> isInstance(
                        subject,
                        condition.type.text
                    ) != condition.isNegated

                    is WhenCondition.In -> contains(
                        eval(condition.expr, env),
                        subject,
                        env
                    ) != condition.isNegated
                }
            }
            if (matches) return evalBlock(branch.body, env.child())
        }
        return Unit
    }

    @Composable
    private fun evalWhenC(expr: WhenExpr, env: Env): Any? {
        val subjectExpr = expr.subject
        val subject = if (subjectExpr != null) evalC(subjectExpr, env) else null
        if (isStop(subject)) return subject
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
                        is WhenCondition.Value -> evalC(condition.expr, env)
                        is WhenCondition.In -> evalC(condition.expr, env)
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
                    if (matched is ErrorValue) stop =
                        showIfError(matched) else if (matched == true) chosen = i
                }
            }
            i++
        }
        if (stop != null) return stop
        return if (chosen >= 0) evalBlockC(expr.branches[chosen].body, env.child()) else Unit
    }

    private fun evalCall(call: Call, env: Env): Any? {
        val label = calleeName(call.callee)
        val positional = mutableListOf<Any?>()
        val named = mutableMapOf<String, Any?>()
        for (arg in call.args) {
            val value =
                arg.value.let { if (it is Lambda) LambdaValue(it, env, label) else eval(it, env) }
            if (value is Jump) return value
            val name = arg.name
            when {
                name != null -> named[name] = value
                arg.isSpread -> positional.addAll(spread(value))
                else -> positional += value
            }
        }
        val args =
            CallArgs(positional, named, call.trailingLambda?.let { LambdaValue(it, env, label) })
        locate(call.span, env)
        return when (val callee = call.callee) {
            is Ident -> invokeTarget(resolveCall(callee.name, args, env), args, callee.name)
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
        val positional = mutableListOf<Any?>()
        val named = mutableMapOf<String, Any?>()
        for (arg in call.args) {
            val argExpr = arg.value
            val value =
                if (argExpr is Lambda) LambdaValue(argExpr, env, label) else evalC(argExpr, env)
            val name = arg.name
            when {
                name != null -> named[name] = value
                arg.isSpread && !isStop(value) -> positional.addAll(spread(value))
                else -> positional += value
            }
        }
        val args =
            CallArgs(positional, named, call.trailingLambda?.let { LambdaValue(it, env, label) })
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
                                resolveMemberCall(
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
        val receiver = eval(receiverExpr, env)
        if (expr.name == "class") return receiver
        val decl = (receiver as? UserValue)?.let { findMethod(it.decl, expr.name, CallArgs.Empty) }
            ?: throw InterpreterException("Unsupported callable reference '::${expr.name}'")
        return FunctionRef(decl, receiver, null)
    }

    private fun thisValue(env: Env): Any? {
        val receivers = env.receivers
        if (receivers.isEmpty()) throw InterpreterException("'this' is not defined in this context")
        return receivers.first()
    }

    private fun index(receiver: Any?, indices: List<Any?>, env: Env): Any? {
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
        val value = Operators.binary(op, left, right)
        if (value !== Unresolved) return value
        val name =
            Operators.functionName(op) ?: throw InterpreterException("Unsupported operator '$op'")
        val target = resolveMemberCall(left, name, CallArgs(listOf(right)), root)
            ?: throw InterpreterException(
                "Operator '$op' is not defined for ${typeName(left)} and ${typeName(right)}"
            )
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

    private fun contains(container: Any?, element: Any?, env: Env?): Boolean {
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

    private fun unaryValue(op: String, operand: Any?): Any? = when (op) {
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
            ?: throw InterpreterException("Condition must be Boolean, found ${typeName(value)}")

    // ---------- Coroutines: bodies of LaunchedEffect, launch { } and suspend functions ----------

    /** Calls a top-level suspend or plain function and returns its result. */
    public suspend fun callSuspend(name: String, vararg args: Any?): Any? {
        val callArgs = CallArgs(args.toList())
        val decl = resolveFunction(name, callArgs, extension = false, main)
            ?: throw InterpreterException("Unresolved reference '$name'")
        return callFunctionS(decl, NoReceiver, callArgs, null)
    }

    /** Invokes a function value inside a coroutine; errors other than cancellation are reported and return null. */
    internal suspend fun invokeSuspendSafely(fn: Any?, receiver: Any?, args: List<Any?>): Any? =
        try {
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
        val body = decl.body ?: throw InterpreterException("Function '${decl.name}' has no body")
        val env = (closure ?: scopeOf(decl).root).child(receiver)
        for (p in bindParams(decl.params, args, env, decl.name)) {
            val default = p.default ?: continue
            env.define(p.name, evalS(default, env), mutable = false)
        }
        return functionResult(evalBlockS(body, env))
    }

    private suspend fun invokeTargetS(target: Target?, args: CallArgs, name: String): Any? =
        when (target) {
            is Target.Suspend -> target.call()
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
        for (branch in expr.branches) {
            var matches = branch.conditions.isEmpty()
            for (condition in branch.conditions) {
                if (matches) break
                matches = when (condition) {
                    is WhenCondition.Value -> evalS(
                        condition.expr,
                        env
                    ).let { if (subjectExpr == null) truthy(it) else it == subject }

                    is WhenCondition.Is -> isInstance(
                        subject,
                        condition.type.text
                    ) != condition.isNegated

                    is WhenCondition.In -> contains(
                        evalS(condition.expr, env),
                        subject,
                        env
                    ) != condition.isNegated
                }
            }
            if (matches) return evalBlockS(branch.body, env.child())
        }
        return Unit
    }

    private suspend fun evalCallS(call: Call, env: Env): Any? {
        val label = calleeName(call.callee)
        val positional = mutableListOf<Any?>()
        val named = mutableMapOf<String, Any?>()
        for (arg in call.args) {
            val argExpr = arg.value
            val value =
                if (argExpr is Lambda) LambdaValue(argExpr, env, label) else evalS(argExpr, env)
            if (value is Jump) return value
            val name = arg.name
            when {
                name != null -> named[name] = value
                arg.isSpread -> positional.addAll(spread(value))
                else -> positional += value
            }
        }
        val args =
            CallArgs(positional, named, call.trailingLambda?.let { LambdaValue(it, env, label) })
        locate(call.span, env)
        return when (val callee = call.callee) {
            is Ident -> invokeTargetS(resolveCall(callee.name, args, env), args, callee.name)
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
                val target = resolveMemberCall(receiver, callee.name, args, env)
                    ?: throw InterpreterException(
                        "Unresolved reference '${callee.name}' on ${typeName(receiver)}"
                    )
                invokeTargetS(target, args, callee.name)
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
        if ("->" in text) return value is LambdaValue || value is FunctionRef || value is ComposableSlot || value is Function<*>
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
        /** Default for [maxRunTime]: long enough for any real preview, short enough to avoid a frozen app. */
        val DEFAULT_MAX_RUN_TIME: Duration = 2.seconds
        val PAUSE = 50.milliseconds
        val GLOBAL_INTRINSICS = setOf("repeat", "run", "with")
        val MEMBER_INTRINSICS = setOf("let", "also", "apply", "run", "forEach", "forEachIndexed")
        val ITERATING_INTRINSICS = setOf("forEach", "forEachIndexed")
    }
}
