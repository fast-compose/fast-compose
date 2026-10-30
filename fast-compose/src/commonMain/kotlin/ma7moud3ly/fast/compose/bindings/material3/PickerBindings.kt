@file:OptIn(ExperimentalMaterial3Api::class)

package ma7moud3ly.fast.compose.bindings.material3

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.MultiChoiceSegmentedButtonRowScope
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.window.DialogProperties
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.ErrorBox
import ma7moud3ly.fast.compose.interpreter.Registry

/** `DatePicker`, `DatePickerDialog` and segmented buttons. */
internal fun Registry.pickers() {
    datePickers()
    segmentedButtons()
}

private fun Registry.datePickers() {
    type<DatePickerState>("DatePickerState")
    composable("rememberDatePickerState(initialSelectedDateMillis, initialDisplayedMonthMillis, yearRange, initialDisplayMode)") { a ->
        val selected = a.typedOrNull<Number>("initialSelectedDateMillis")?.toLong()
        val displayedMonth = if (a.has("initialDisplayedMonthMillis")) a.typedOrNull<Number>("initialDisplayedMonthMillis")?.toLong() else selected
        rememberDatePickerState(
            initialSelectedDateMillis = selected,
            initialDisplayedMonthMillis = displayedMonth,
            yearRange = a.typed("yearRange", DatePickerDefaults.YearRange),
            initialDisplayMode = a.typed("initialDisplayMode", DisplayMode.Picker),
        )
    }
    properties<DatePickerState>(
        "selectedDateMillis" to { it.selectedDateMillis },
        "displayedMonthMillis" to { it.displayedMonthMillis },
        "displayMode" to { it.displayMode },
    )
    value("DisplayMode", DisplayMode.Companion)
    constants<DisplayMode.Companion>("Picker" to DisplayMode.Picker, "Input" to DisplayMode.Input)
    value("DatePickerDefaults", DatePickerDefaults)
    composable("DatePicker(state, modifier, colors, title, headline, showModeToggle)") { a ->
        val state = a.typedOrNull<DatePickerState>("state")
        if (state == null) ErrorBox("DatePicker needs a DatePickerState, e.g. rememberDatePickerState()") else DatePickerWithSlots(a, state)
    }
    composable("DatePickerDialog(onDismissRequest, confirmButton, modifier, dismissButton, shape, tonalElevation, colors, properties, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        DatePickerDialog(
            onDismissRequest = a.action("onDismissRequest"),
            confirmButton = a.content("confirmButton"),
            modifier = a.modifier(),
            dismissButton = a.contentOrNull("dismissButton"),
            shape = a.typed<Shape>("shape", DatePickerDefaults.shape),
            tonalElevation = a.dp("tonalElevation", DatePickerDefaults.TonalElevation),
            colors = a.typed("colors", DatePickerDefaults.colors()),
            properties = a.typed("properties", DialogProperties(usePlatformDefaultWidth = false)),
        ) { content() }
    }
}

/** Keeps Compose's default `title` and `headline` slots unless they are passed. */
@Composable
private fun DatePickerWithSlots(a: Args, state: DatePickerState) {
    val modifier = a.modifier()
    val colors = a.typed("colors", DatePickerDefaults.colors())
    val showModeToggle = a.boolean("showModeToggle", true)
    val title = a.contentOrNull("title")
    val headline = a.contentOrNull("headline")
    when {
        a.has("title") && a.has("headline") ->
            DatePicker(state, modifier, colors = colors, title = title, headline = headline, showModeToggle = showModeToggle)
        a.has("title") -> DatePicker(state, modifier, colors = colors, title = title, showModeToggle = showModeToggle)
        a.has("headline") -> DatePicker(state, modifier, colors = colors, headline = headline, showModeToggle = showModeToggle)
        else -> DatePicker(state, modifier, colors = colors, showModeToggle = showModeToggle)
    }
}

private fun Registry.segmentedButtons() {
    composable("SingleChoiceSegmentedButtonRow(modifier, space, content)") { a ->
        val content = a.scopedContent<SingleChoiceSegmentedButtonRowScope>()
        SingleChoiceSegmentedButtonRow(modifier = a.modifier(), space = a.dp("space", SegmentedButtonDefaults.BorderWidth)) { content() }
    }
    composable("MultiChoiceSegmentedButtonRow(modifier, space, content)") { a ->
        val content = a.scopedContent<MultiChoiceSegmentedButtonRowScope>()
        MultiChoiceSegmentedButtonRow(modifier = a.modifier(), space = a.dp("space", SegmentedButtonDefaults.BorderWidth)) { content() }
    }
    composable("SegmentedButton(selected, onClick, shape, modifier, enabled, colors, icon, label)") { a ->
        val scope = a.receiver<SingleChoiceSegmentedButtonRowScope>()
        if (scope == null) ErrorBox("SegmentedButton(selected, onClick, …) must be inside SingleChoiceSegmentedButtonRow") else scope.SingleChoiceButton(a)
    }
    composable("SegmentedButton(checked, onCheckedChange, shape, modifier, enabled, colors, icon, label)") { a ->
        val scope = a.receiver<MultiChoiceSegmentedButtonRowScope>()
        if (scope == null) ErrorBox("SegmentedButton(checked, onCheckedChange, …) must be inside MultiChoiceSegmentedButtonRow") else scope.MultiChoiceButton(a)
    }
    value("SegmentedButtonDefaults", SegmentedButtonDefaults)
    composableMember<SegmentedButtonDefaults>("itemShape(index, count, baseShape)") { _, a ->
        val base = a.typedOrNull<CornerBasedShape>("baseShape")
        if (base != null) {
            SegmentedButtonDefaults.itemShape(a.int("index", 0), a.int("count", 1), base)
        } else {
            SegmentedButtonDefaults.itemShape(a.int("index", 0), a.int("count", 1))
        }
    }
}

/** Keeps Compose's default check icon unless `icon` is passed. */
@Composable
private fun SingleChoiceSegmentedButtonRowScope.SingleChoiceButton(a: Args) {
    val shape = a.typed<Shape>("shape", SegmentedButtonDefaults.itemShape(0, 1))
    val colors = a.typed("colors", SegmentedButtonDefaults.colors())
    val icon = a.contentOrNull("icon")
    val label = a.content("label")
    val selected = a.boolean("selected", false)
    val onClick = a.action("onClick")
    val enabled = a.boolean("enabled", true)
    if (icon != null) {
        SegmentedButton(selected = selected, onClick = onClick, shape = shape, modifier = a.modifier(), enabled = enabled, colors = colors, icon = icon, label = label)
    } else {
        SegmentedButton(selected = selected, onClick = onClick, shape = shape, modifier = a.modifier(), enabled = enabled, colors = colors, label = label)
    }
}

@Composable
private fun MultiChoiceSegmentedButtonRowScope.MultiChoiceButton(a: Args) {
    val shape = a.typed<Shape>("shape", SegmentedButtonDefaults.itemShape(0, 1))
    val colors = a.typed("colors", SegmentedButtonDefaults.colors())
    val icon = a.contentOrNull("icon")
    val label = a.content("label")
    val checked = a.boolean("checked", false)
    val onChange = a.action1("onCheckedChange")
    val onCheckedChange = { value: Boolean -> onChange(value) }
    val enabled = a.boolean("enabled", true)
    if (icon != null) {
        SegmentedButton(checked = checked, onCheckedChange = onCheckedChange, shape = shape, modifier = a.modifier(), enabled = enabled, colors = colors, icon = icon, label = label)
    } else {
        SegmentedButton(checked = checked, onCheckedChange = onCheckedChange, shape = shape, modifier = a.modifier(), enabled = enabled, colors = colors, label = label)
    }
}
