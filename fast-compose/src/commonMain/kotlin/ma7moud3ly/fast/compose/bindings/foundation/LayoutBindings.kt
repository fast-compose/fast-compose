@file:OptIn(ExperimentalLayoutApi::class)

package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.Registry

/** `FlowRow`, `FlowColumn` and custom layouts with `Layout`. */
internal fun Registry.layoutBindings() {
    flowLayouts()
    customLayouts()
}

private fun Registry.flowLayouts() {
    composable("FlowRow(modifier, horizontalArrangement, verticalArrangement, itemVerticalAlignment, maxItemsInEachRow, maxLines, content)") { a ->
        val content = a.scopedContent<FlowRowScope>()
        FlowRow(
            modifier = a.modifier(),
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", Arrangement.Start),
            verticalArrangement = a.typed<Arrangement.Vertical>("verticalArrangement", Arrangement.Top),
            itemVerticalAlignment = a.typed<Alignment.Vertical>("itemVerticalAlignment", Alignment.Top),
            maxItemsInEachRow = a.int("maxItemsInEachRow", Int.MAX_VALUE),
            maxLines = a.int("maxLines", Int.MAX_VALUE),
        ) { content() }
    }
    composable("FlowColumn(modifier, verticalArrangement, horizontalArrangement, itemHorizontalAlignment, maxItemsInEachColumn, maxLines, content)") { a ->
        val content = a.scopedContent<FlowColumnScope>()
        FlowColumn(
            modifier = a.modifier(),
            verticalArrangement = a.typed<Arrangement.Vertical>("verticalArrangement", Arrangement.Top),
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", Arrangement.Start),
            itemHorizontalAlignment = a.typed<Alignment.Horizontal>("itemHorizontalAlignment", Alignment.Start),
            maxItemsInEachColumn = a.int("maxItemsInEachColumn", Int.MAX_VALUE),
            maxLines = a.int("maxLines", Int.MAX_VALUE),
        ) { content() }
    }
}

/**
 * `Layout(content, modifier) { measurables, constraints -> layout(width, height) { placeable.place(x, y) } }`.
 * The measure lambda runs with `MeasureScope` as its receiver; the placement block with `Placeable.PlacementScope`.
 */
private fun Registry.customLayouts() {
    type<Measurable>("Measurable")
    type<Placeable>("Placeable")
    type<Constraints>("Constraints")
    composable("Layout(content, modifier, measurePolicy)") { a ->
        Layout(content = a.content(), modifier = a.modifier(), measurePolicy = a.measurePolicy())
    }
    member<Measurable>("measure(constraints)") { measurable, a -> measurable.measure(a.typed("constraints", Constraints())) }
    properties<Placeable>("width" to { it.width }, "height" to { it.height })
    member<MeasureScope>("layout(width, height, alignmentLines, placementBlock)") { scope, a ->
        val block = a.scopedAction<Placeable.PlacementScope>("placementBlock")
        @Suppress("UNCHECKED_CAST")
        val alignmentLines = a.typed<Map<*, *>>("alignmentLines", emptyMap<AlignmentLine, Int>()) as Map<AlignmentLine, Int>
        scope.layout(a.int("width", 0), a.int("height", 0), alignmentLines) { block() }
    }
    member<Placeable>("place(x, y, zIndex)") { placeable, a ->
        val scope = a.receiver<Placeable.PlacementScope>()
        if (scope == null) a.report("place() must be called inside layout(width, height) { … }")
        else with(scope) { placeable.place(a.int("x", 0), a.int("y", 0), a.float("zIndex", 0f)) }
    }
    member<Placeable>("placeRelative(x, y, zIndex)") { placeable, a ->
        val scope = a.receiver<Placeable.PlacementScope>()
        if (scope == null) a.report("placeRelative() must be called inside layout(width, height) { … }")
        else with(scope) { placeable.placeRelative(a.int("x", 0), a.int("y", 0), a.float("zIndex", 0f)) }
    }
    function("Constraints(minWidth, maxWidth, minHeight, maxHeight)") { a ->
        Constraints(a.int("minWidth", 0), a.int("maxWidth", Constraints.Infinity), a.int("minHeight", 0), a.int("maxHeight", Constraints.Infinity))
    }
    properties<Constraints>(
        "minWidth" to { it.minWidth },
        "maxWidth" to { it.maxWidth },
        "minHeight" to { it.minHeight },
        "maxHeight" to { it.maxHeight },
        "hasBoundedWidth" to { it.hasBoundedWidth },
        "hasBoundedHeight" to { it.hasBoundedHeight },
    )
    member<Constraints>("copy(minWidth, maxWidth, minHeight, maxHeight)") { c, a ->
        c.copy(a.int("minWidth", c.minWidth), a.int("maxWidth", c.maxWidth), a.int("minHeight", c.minHeight), a.int("maxHeight", c.maxHeight))
    }
}

/** Runs the `measurePolicy` lambda with `MeasureScope` as receiver; an error measures to an empty layout. */
private fun Args.measurePolicy(): MeasurePolicy {
    val block = scopedFunction2<MeasureScope>("measurePolicy")
    return MeasurePolicy { measurables, constraints ->
        block(measurables, constraints) as? MeasureResult ?: layout(0, 0) {}
    }
}
