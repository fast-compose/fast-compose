package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.Registry

/** Animation specs (`spring`, `snap`, `repeatable`, `keyframes`), more `animate*AsState` and `updateTransition`. */
internal fun Registry.motion() {
    specs()
    keyframeSpecs()
    composable("animateIntAsState(targetValue, animationSpec, label, finishedListener)") { a ->
        animateIntAsState(a.int("targetValue", 0), a.animationSpec(), a.string("label", "IntAnimation"), a.action1OrNull("finishedListener"))
    }
    composable("animateOffsetAsState(targetValue, animationSpec, label, finishedListener)") { a ->
        animateOffsetAsState(
            a.typed("targetValue", Offset.Zero),
            a.animationSpec(),
            a.string("label", "OffsetAnimation"),
            a.action1OrNull("finishedListener"),
        )
    }
    transitions()
}

private fun Registry.specs() {
    function("spring(dampingRatio, stiffness, visibilityThreshold)") { a ->
        spring(a.float("dampingRatio", Spring.DampingRatioNoBouncy), a.float("stiffness", Spring.StiffnessMedium), a["visibilityThreshold"])
    }
    value("Spring", Spring)
    constants<Spring>(
        "DampingRatioHighBouncy" to Spring.DampingRatioHighBouncy,
        "DampingRatioMediumBouncy" to Spring.DampingRatioMediumBouncy,
        "DampingRatioLowBouncy" to Spring.DampingRatioLowBouncy,
        "DampingRatioNoBouncy" to Spring.DampingRatioNoBouncy,
        "StiffnessHigh" to Spring.StiffnessHigh,
        "StiffnessMedium" to Spring.StiffnessMedium,
        "StiffnessMediumLow" to Spring.StiffnessMediumLow,
        "StiffnessLow" to Spring.StiffnessLow,
        "StiffnessVeryLow" to Spring.StiffnessVeryLow,
    )
    function("snap(delayMillis)") { a -> snap<Any?>(a.int("delayMillis", 0)) }
    @Suppress("UNCHECKED_CAST")
    function("repeatable(iterations, animation, repeatMode)") { a ->
        repeatable(
            iterations = a.int("iterations", 1).coerceAtLeast(1),
            animation = a.typed<DurationBasedAnimationSpec<*>>("animation", tween<Any?>()) as DurationBasedAnimationSpec<Any?>,
            repeatMode = a.typed("repeatMode", RepeatMode.Restart),
        )
    }
}

/** `keyframes { durationMillis = 600; 0.5f at 300 using LinearEasing }`. */
private fun Registry.keyframeSpecs() {
    type<KeyframesSpec.KeyframesSpecConfig<*>>("KeyframesSpecConfig")
    type<KeyframesSpec.KeyframeEntity<*>>("KeyframeEntity")
    function("keyframes(init)") { a ->
        val init = a.scopedAction<KeyframesSpec.KeyframesSpecConfig<Any?>>("init")
        keyframes { init() }
    }
    setter<KeyframesSpec.KeyframesSpecConfig<*>>("durationMillis") { config, v -> (v as? Number)?.let { config.durationMillis = it.toInt() } }
    setter<KeyframesSpec.KeyframesSpecConfig<*>>("delayMillis") { config, v -> (v as? Number)?.let { config.delayMillis = it.toInt() } }
    properties<KeyframesSpec.KeyframesSpecConfig<*>>("durationMillis" to { it.durationMillis }, "delayMillis" to { it.delayMillis })
    // `value at time` is a member of the keyframes config, so it is found through the implicit receiver.
    member<Any>("at(timeStamp)") { value, a ->
        val config = a.receiver<KeyframesSpec.KeyframesSpecConfig<Any?>>()
        if (config == null) a.report("'at' must be used inside keyframes { … }") else with(config) { value at a.int("timeStamp", 0) }
    }
    // `using` is also a member of the config, taking the entity as its receiver.
    @Suppress("UNCHECKED_CAST")
    member<Any>("using(easing)") { entity, a ->
        val config = a.receiver<KeyframesSpec.KeyframesSpecConfig<Any?>>()
        val easing = a.typedOrNull<Easing>("easing")
        if (config == null || easing == null || entity !is KeyframesSpec.KeyframeEntity<*>) entity
        else with(config) { (entity as KeyframesSpec.KeyframeEntity<Any?>) using easing }
    }
}

@Suppress("UNCHECKED_CAST")
private fun Registry.transitions() {
    type<Transition<*>>("Transition")
    composable("updateTransition(targetState, label)") { a -> updateTransition(a["targetState"], a.stringOrNull("label")) }
    properties<Transition<*>>(
        "currentState" to { it.currentState },
        "targetState" to { it.targetState },
        "isRunning" to { it.isRunning },
    )
    type<Transition.Segment<*>>("Segment")
    properties<Transition.Segment<*>>("initialState" to { it.initialState }, "targetState" to { it.targetState })
    // `Expanded isTransitioningTo Collapsed` inside transitionSpec, with the segment as implicit receiver.
    member<Any>("isTransitioningTo(targetState)") { state, a ->
        val segment = a.receiver<Transition.Segment<*>>()
        segment != null && state == segment.initialState && a["targetState"] == segment.targetState
    }
    composableMember<Transition<*>>("animateFloat(transitionSpec, label, targetValueByState)") { t, a ->
        val spec = a.segmentSpec<Float>()
        val target = a.composableFunction1("targetValueByState")
        (t as Transition<Any?>).animateFloat({ spec(this) }, a.string("label", "FloatAnimation")) { (target(it) as? Number)?.toFloat() ?: 0f }
    }
    composableMember<Transition<*>>("animateDp(transitionSpec, label, targetValueByState)") { t, a ->
        val spec = a.segmentSpec<Dp>()
        val target = a.composableFunction1("targetValueByState")
        (t as Transition<Any?>).animateDp({ spec(this) }, a.string("label", "DpAnimation")) { target(it) as? Dp ?: 0.dp }
    }
    composableMember<Transition<*>>("animateInt(transitionSpec, label, targetValueByState)") { t, a ->
        val spec = a.segmentSpec<Int>()
        val target = a.composableFunction1("targetValueByState")
        (t as Transition<Any?>).animateInt({ spec(this) }, a.string("label", "IntAnimation")) { (target(it) as? Number)?.toInt() ?: 0 }
    }
    composableMember<Transition<*>>("animateColor(transitionSpec, label, targetValueByState)") { t, a ->
        val spec = a.segmentSpec<Color>()
        val target = a.composableFunction1("targetValueByState")
        (t as Transition<Any?>).animateColor({ spec(this) }, a.string("label", "ColorAnimation")) { target(it) as? Color ?: Color.Transparent }
    }
}

/** The `transitionSpec` lambda, called with the segment as receiver, or a spring when it is missing or returns no spec. */
@Suppress("UNCHECKED_CAST")
private fun <T> Args.segmentSpec(): (Transition.Segment<Any?>) -> FiniteAnimationSpec<T> {
    if (lambda("transitionSpec") == null) return { spring() }
    val spec = scopedCalculation<Transition.Segment<Any?>>("transitionSpec")
    return { segment -> segment.spec() as? FiniteAnimationSpec<T> ?: spring() }
}
