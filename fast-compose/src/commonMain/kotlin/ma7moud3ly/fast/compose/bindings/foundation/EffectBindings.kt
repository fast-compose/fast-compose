package ma7moud3ly.fast.compose.bindings.foundation

import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.InterpreterException
import ma7moud3ly.fast.compose.interpreter.Registry
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.animation.Animatable as colorAnimatable

/** Side effects and coroutines: LaunchedEffect, DisposableEffect, launch, delay, Animatable and scroll states. */
internal fun Registry.effects() {
    sideEffects()
    coroutines()
    animatables()
    scrollStates()
}

/** Effect keys: the `keys` varargs or named `key1`, `key2`, `key3` arguments, compared as one list. */
private fun Args.effectKeys(): List<Any?> = varargs("keys") + listOf("key1", "key2", "key3").filter { has(it) }.map { this[it] }

private fun Registry.sideEffects() {
    composable("LaunchedEffect(vararg keys, block, *, key1, key2, key3)") { a ->
        val block = a.suspendBlock<CoroutineScope>()
        LaunchedEffect(a.effectKeys()) { block() }
    }
    composable("SideEffect(effect)") { a ->
        val effect = a.action("effect")
        SideEffect { effect() }
    }
    type<DisposableEffectScope>("DisposableEffectScope")
    composable("DisposableEffect(vararg keys, effect, *, key1, key2, key3)") { a ->
        val effect = a.scopedCalculation<DisposableEffectScope>("effect")
        DisposableEffect(a.effectKeys()) {
            effect() as? DisposableEffectResult ?: onDispose { }
        }
    }
    member<DisposableEffectScope>("onDispose(onDisposeEffect)") { scope, a ->
        val onDispose = a.action("onDisposeEffect")
        scope.onDispose { onDispose() }
    }
    composable("produceState(initialValue, vararg keys, producer, *, key1, key2, key3)") { a ->
        val producer = a.suspendBlock<ProduceStateScope<Any?>>("producer")
        produceState(a["initialValue"], a.effectKeys()) { producer() }
    }
    composable("rememberUpdatedState(newValue)") { a -> rememberUpdatedState(a["newValue"]) }
    function("derivedStateOf(calculation)") { a ->
        val calculation = a.calculation()
        derivedStateOf { calculation() }
    }
}

private fun Registry.coroutines() {
    type<CoroutineScope>("CoroutineScope")
    type<Job>("Job")
    composable("rememberCoroutineScope") { rememberCoroutineScope() }
    member<CoroutineScope>("launch(block)") { scope, a ->
        val block = a.suspendBlock<CoroutineScope>()
        scope.launch { block() }
    }
    member<Job>("cancel") { job, _ -> job.cancel() }
    properties<Job>("isActive" to { it.isActive }, "isCompleted" to { it.isCompleted })
    suspendFunction("delay(timeMillis)") { a ->
        delay(a.long("timeMillis", 0L).milliseconds)
        Unit
    }
    suspendFunction("withFrameNanos(onFrame)") { a ->
        val onFrame = a.function1("onFrame")
        withFrameNanos { time -> onFrame(time) }
    }
    suspendFunction("withFrameMillis(onFrame)") { a ->
        val onFrame = a.function1("onFrame")
        withFrameMillis { time -> onFrame(time) }
    }
}

/** Converts a number target to Float for Float animatables, since `animateTo(1)` passes an Int. */
private fun Animatable<*, *>.target(value: Any?): Any? = if (this.value is Float && value is Number) value.toFloat() else value

@Suppress("UNCHECKED_CAST")
private fun Registry.animatables() {
    type<Animatable<*, *>>("Animatable")
    function("Animatable(initialValue)") { a ->
        when (val initial = a["initialValue"]) {
            is Color -> colorAnimatable(initial)
            is Number -> Animatable(initial.toFloat())
            else -> throw InterpreterException("Animatable supports Float and Color values, found ${initial?.let { it::class.simpleName }}")
        }
    }
    properties<Animatable<*, *>>(
        "value" to { it.value },
        "targetValue" to { it.targetValue },
        "isRunning" to { it.isRunning },
    )
    suspendMember<Animatable<*, *>>("animateTo(targetValue, animationSpec)") { animatable, a ->
        (animatable as Animatable<Any?, AnimationVector>).animateTo(
            targetValue = animatable.target(a["targetValue"]),
            animationSpec = a.typed<AnimationSpec<*>>("animationSpec", spring<Any?>()) as AnimationSpec<Any?>,
        )
        Unit
    }
    suspendMember<Animatable<*, *>>("snapTo(targetValue)") { animatable, a ->
        (animatable as Animatable<Any?, AnimationVector>).snapTo(animatable.target(a["targetValue"]))
        Unit
    }
    suspendMember<Animatable<*, *>>("stop") { animatable, _ ->
        animatable.stop()
        Unit
    }
}

private fun Registry.scrollStates() {
    type<LazyListState>("LazyListState")
    type<ScrollState>("ScrollState")
    composable("rememberLazyListState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset)") { a ->
        rememberLazyListState(a.int("initialFirstVisibleItemIndex", 0), a.int("initialFirstVisibleItemScrollOffset", 0))
    }
    properties<LazyListState>(
        "firstVisibleItemIndex" to { it.firstVisibleItemIndex },
        "firstVisibleItemScrollOffset" to { it.firstVisibleItemScrollOffset },
        "isScrollInProgress" to { it.isScrollInProgress },
        "canScrollForward" to { it.canScrollForward },
        "canScrollBackward" to { it.canScrollBackward },
    )
    suspendMember<LazyListState>("animateScrollToItem(index, scrollOffset)") { state, a ->
        state.animateScrollToItem(a.int("index", 0), a.int("scrollOffset", 0))
        Unit
    }
    suspendMember<LazyListState>("scrollToItem(index, scrollOffset)") { state, a ->
        state.scrollToItem(a.int("index", 0), a.int("scrollOffset", 0))
        Unit
    }
    properties<ScrollState>(
        "value" to { it.value },
        "maxValue" to { it.maxValue },
        "isScrollInProgress" to { it.isScrollInProgress },
    )
    suspendMember<ScrollState>("animateScrollTo(value)") { state, a ->
        state.animateScrollTo(a.int("value", 0))
        Unit
    }
    suspendMember<ScrollState>("scrollTo(value)") { state, a ->
        state.scrollTo(a.int("value", 0))
        Unit
    }
}
