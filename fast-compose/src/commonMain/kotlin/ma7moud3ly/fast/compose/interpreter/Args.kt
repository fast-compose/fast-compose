package ma7moud3ly.fast.compose.interpreter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * Arguments of one call to a binding, matched by name against the binding's [Signature].
 *
 * Getters never throw: a missing argument gives the default, and a value of the wrong type is reported and
 * replaced by the default. Lambda getters turn interpreted lambdas into real Kotlin functions; errors inside
 * them are reported instead of thrown. [receivers] are the implicit receivers at the call site.
 */
public class Args internal constructor(
    public val signature: Signature,
    private val values: Map<String, Any?>,
    public val receivers: List<Any?>,
    private val interpreter: Interpreter,
) {
    public fun has(name: String): Boolean = name in values

    /** Raw argument value, or null when missing or invalid. */
    public operator fun get(name: String): Any? = values[name].let { if (it is ErrorValue) null else it }

    /** Values of a `vararg` parameter. */
    public fun varargs(name: String): List<Any?> = get(name) as? List<*> ?: emptyList()

    public inline fun <reified T> typed(name: String, default: T): T {
        if (!has(name)) return default
        val value = get(name)
        if (value is T) return value
        invalid(name, value)
        return default
    }

    public inline fun <reified T> typedOrNull(name: String): T? = typed<T?>(name, null)

    @PublishedApi
    internal fun invalid(name: String, value: Any?) {
        report("Invalid value for '$name' of ${signature.name}: ${typeName(value)}")
    }

    /** Reports a problem to the preview's error log without failing the call. */
    public fun report(message: String): Unit = interpreter.report(message)

    /** Reports a warning, such as a missing resource file, which is not a mistake in the code. */
    public fun warn(message: String): Unit = interpreter.warn(message)

    public fun string(name: String, default: String = ""): String = if (has(name)) interpreter.stringify(get(name)) else default

    public fun stringOrNull(name: String): String? = if (has(name)) get(name)?.let { interpreter.stringify(it) } else null

    public fun int(name: String, default: Int): Int = typed<Number>(name, default).toInt()

    public fun long(name: String, default: Long): Long = typed<Number>(name, default).toLong()

    public fun float(name: String, default: Float): Float = typed<Number>(name, default).toFloat()

    public fun boolean(name: String, default: Boolean): Boolean = typed(name, default)

    public fun dp(name: String, default: Dp = Dp.Unspecified): Dp = typed(name, default)

    public fun sp(name: String, default: TextUnit = TextUnit.Unspecified): TextUnit = typed(name, default)

    public fun color(name: String, default: Color = Color.Unspecified): Color = typed(name, default)

    public fun modifier(name: String = "modifier"): Modifier = typed<Modifier>(name, Modifier)

    /** First implicit receiver of type [T], e.g. `RowScope` for `Modifier.weight`. */
    public inline fun <reified T> receiver(): T? = receivers.firstOrNull { it is T } as T?

    // ---------- Lambdas ----------

    /** Raw function value of a parameter, or null when it was not passed. */
    public fun lambda(name: String): Any? = get(name)

    /** Composable slot, e.g. `content: @Composable () -> Unit`. A missing slot renders nothing. */
    public fun content(name: String = "content"): @Composable () -> Unit {
        val fn = lambda(name) ?: return {}
        return { interpreter.invokeComposable(fn, NoReceiver, emptyList()) }
    }

    /** Optional composable slot, e.g. `label: (@Composable () -> Unit)?`; null when it was not passed. */
    public fun contentOrNull(name: String): (@Composable () -> Unit)? = if (lambda(name) == null) null else content(name)

    /** Composable slot with one parameter, e.g. Scaffold's `content: @Composable (PaddingValues) -> Unit`. */
    public fun content1(name: String = "content"): @Composable (Any?) -> Unit {
        val fn = lambda(name) ?: return {}
        return { interpreter.invokeComposable(fn, NoReceiver, listOf(it)) }
    }

    /**
     * Composable slot that wraps inner content, e.g. BasicTextField's `decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit`.
     * The interpreted lambda receives the inner content as a callable value. A missing slot shows the inner content as is.
     */
    public fun decorator(name: String): @Composable (@Composable () -> Unit) -> Unit {
        val fn = lambda(name) ?: return { inner -> inner() }
        return { inner -> interpreter.invokeComposable(fn, NoReceiver, listOf(ComposableSlot(inner))) }
    }

    /** Composable slot with a receiver scope, e.g. `content: @Composable RowScope.() -> Unit`. */
    public fun <S> scopedContent(name: String = "content"): @Composable S.() -> Unit {
        val fn = lambda(name) ?: return {}
        return { interpreter.invokeComposable(fn, this, emptyList()) }
    }

    /** Optional composable slot with a receiver scope; null when it was not passed. */
    public fun <S> scopedContentOrNull(name: String): (@Composable S.() -> Unit)? = if (lambda(name) == null) null else scopedContent(name)

    /** Composable slot with a receiver scope and one parameter, e.g. `itemContent: @Composable LazyItemScope.(index: Int) -> Unit`. */
    public fun <S> scopedContent1(name: String = "content"): @Composable S.(Any?) -> Unit {
        val fn = lambda(name) ?: return {}
        return { interpreter.invokeComposable(fn, this, listOf(it)) }
    }

    /** Composable slot with a receiver scope and two parameters, e.g. `@Composable LazyItemScope.(index: Int, item: T) -> Unit`. */
    public fun <S> scopedContent2(name: String = "content"): @Composable S.(Any?, Any?) -> Unit {
        val fn = lambda(name) ?: return { _, _ -> }
        return { first, second -> interpreter.invokeComposable(fn, this, listOf(first, second)) }
    }

    /** Event handler, e.g. `onClick: () -> Unit`. */
    public fun action(name: String): () -> Unit {
        val fn = lambda(name) ?: return {}
        return { interpreter.invokeSafely(fn, NoReceiver, emptyList()) }
    }

    /** Optional event handler, e.g. `onClick: (() -> Unit)?`; null when it was not passed. */
    public fun actionOrNull(name: String): (() -> Unit)? = if (lambda(name) == null) null else action(name)

    /** Event handler with one parameter, e.g. `onCheckedChange: (Boolean) -> Unit`. */
    public fun action1(name: String): (Any?) -> Unit {
        val fn = lambda(name) ?: return {}
        return { interpreter.invokeSafely(fn, NoReceiver, listOf(it)) }
    }

    /** Optional event handler with one parameter; null when it was not passed. */
    public fun action1OrNull(name: String): ((Any?) -> Unit)? = if (lambda(name) == null) null else action1(name)

    /** Event handler with two parameters, e.g. `onDrag: (change, dragAmount) -> Unit`. */
    public fun action2(name: String): (Any?, Any?) -> Unit {
        val fn = lambda(name) ?: return { _, _ -> }
        return { first, second -> interpreter.invokeSafely(fn, NoReceiver, listOf(first, second)) }
    }

    /** Non-composable lambda with a receiver scope, e.g. LazyColumn's `content: LazyListScope.() -> Unit`. */
    public fun <S> scopedAction(name: String = "content"): S.() -> Unit {
        val fn = lambda(name) ?: return {}
        return { interpreter.invokeSafely(fn, this, emptyList()) }
    }

    /** Lambda returning a value, e.g. the calculation of `remember { … }`. Errors become [ErrorValue]. */
    public fun calculation(name: String = "calculation"): () -> Any? {
        val fn = lambda(name) ?: return { null }
        return { interpreter.invokeSafely(fn, NoReceiver, emptyList()) }
    }

    /** Lambda with one parameter that returns a value, e.g. `withFrameNanos { time -> … }`. */
    public fun function1(name: String): (Any?) -> Any? {
        val fn = lambda(name) ?: return { null }
        return { interpreter.invokeSafely(fn, NoReceiver, listOf(it)) }
    }

    /** Lambda with two parameters that returns a value, e.g. itemsIndexed's `key = { index, item -> … }`. */
    public fun function2(name: String): (Any?, Any?) -> Any? {
        val fn = lambda(name) ?: return { _, _ -> null }
        return { first, second -> interpreter.invokeSafely(fn, NoReceiver, listOf(first, second)) }
    }

    /** Composable lambda with one parameter that returns a value, e.g. `targetValueByState: @Composable (state) -> Float`. */
    public fun composableFunction1(name: String): @Composable (Any?) -> Any? {
        val fn = lambda(name) ?: return { null }
        return { interpreter.invokeComposable(fn, NoReceiver, listOf(it)) }
    }

    /** Lambda with a receiver scope that returns a value, e.g. DisposableEffect's `DisposableEffectScope.() -> DisposableEffectResult`. */
    public fun <S> scopedCalculation(name: String): S.() -> Any? {
        val fn = lambda(name) ?: return { null }
        return { interpreter.invokeSafely(fn, this, emptyList()) }
    }

    /** Lambda with a receiver scope and one parameter that returns a value, e.g. a grid's `span: LazyGridItemSpanScope.(item) -> GridItemSpan`. */
    public fun <S> scopedFunction1(name: String): S.(Any?) -> Any? {
        val fn = lambda(name) ?: return { null }
        return { interpreter.invokeSafely(fn, this, listOf(it)) }
    }

    /** Lambda with a receiver scope and two parameters that returns a value, e.g. Layout's `MeasureScope.(measurables, constraints) -> MeasureResult`. */
    public fun <S> scopedFunction2(name: String): S.(Any?, Any?) -> Any? {
        val fn = lambda(name) ?: return { _, _ -> null }
        return { first, second -> interpreter.invokeSafely(fn, this, listOf(first, second)) }
    }

    /** Suspend lambda with one parameter, e.g. the collector of `flow.collect { value -> … }`. Cancellation passes through. */
    public fun suspendAction1(name: String): suspend (Any?) -> Unit {
        val fn = lambda(name) ?: return {}
        return { interpreter.invokeSuspendSafely(fn, NoReceiver, listOf(it)) }
    }

    /** Suspend lambda with a receiver scope, e.g. LaunchedEffect's `block: suspend CoroutineScope.() -> Unit`. Cancellation passes through. */
    public fun <S> suspendBlock(name: String = "block"): suspend S.() -> Unit {
        val fn = lambda(name) ?: return {}
        return { interpreter.invokeSuspendSafely(fn, this, emptyList()) }
    }
}
