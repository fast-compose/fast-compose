@file:OptIn(ExperimentalMaterial3Api::class)

package ma7moud3ly.fast.compose.bindings.material3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgeDefaults
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.Typography
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import ma7moud3ly.fast.compose.bindings.foundation.TEXT_SIGNATURE
import ma7moud3ly.fast.compose.bindings.foundation.textStyle
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.BindingPack
import ma7moud3ly.fast.compose.interpreter.ErrorBox
import ma7moud3ly.fast.compose.interpreter.Registry

/** Material 3 components, defaults objects and composition locals. Layer it on [ma7moud3ly.fast.compose.bindings.foundation.FoundationBindings]. */
public object Material3Bindings : BindingPack {
    override fun install(registry: Registry): Unit = registry.material3()
}

private fun Registry.material3() {
    materialCore()
    materialExtras()
    icons3()
    lists()
    navigation()
    inputs()
    buttons()
    cards()
    progress()
    chips()
    bars()
    pickers()
    tooltips()
    themes()
    componentExtras()
}

private fun Registry.icons3() {
    // The type of the first argument picks the overload: Icons.Default.Home, painterResource(…) or an ImageBitmap.
    composable("Icon(imageVector: ImageVector, contentDescription, modifier, tint)") { a ->
        val image = a.typedOrNull<ImageVector>("imageVector")
        if (image == null) {
            ErrorBox("Icon needs an ImageVector, a Painter or an ImageBitmap, e.g. Icons.Default.Home")
        } else {
            Icon(image, a.stringOrNull("contentDescription"), a.modifier(), a.color("tint", LocalContentColor.current))
        }
    }
    composable("Icon(painter: Painter, contentDescription, modifier, tint)") { a ->
        val painter = a.typedOrNull<Painter>("painter")
        if (painter == null) {
            ErrorBox("Icon needs a Painter, e.g. painterResource(R.drawable.ic_star)")
        } else {
            Icon(painter, a.stringOrNull("contentDescription"), a.modifier(), a.color("tint", LocalContentColor.current))
        }
    }
    composable("Icon(bitmap: ImageBitmap, contentDescription, modifier, tint)") { a ->
        val bitmap = a.typedOrNull<ImageBitmap>("bitmap")
        if (bitmap == null) {
            ErrorBox("Icon needs an ImageBitmap, e.g. imageResource(Res.drawable.photo)")
        } else {
            Icon(bitmap, a.stringOrNull("contentDescription"), a.modifier(), a.color("tint", LocalContentColor.current))
        }
    }
    composable("IconButton(onClick, modifier, enabled, colors, content)") { a ->
        val content = a.content()
        IconButton(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            colors = a.typed("colors", IconButtonDefaults.iconButtonColors()),
        ) { content() }
    }
}

private fun Registry.lists() {
    composable("ListItem(headlineContent, modifier, overlineContent, supportingContent, leadingContent, trailingContent, colors, tonalElevation, shadowElevation)") { a ->
        ListItem(
            headlineContent = a.content("headlineContent"),
            modifier = a.modifier(),
            overlineContent = a.contentOrNull("overlineContent"),
            supportingContent = a.contentOrNull("supportingContent"),
            leadingContent = a.contentOrNull("leadingContent"),
            trailingContent = a.contentOrNull("trailingContent"),
            colors = a.typed("colors", ListItemDefaults.colors()),
            tonalElevation = a.dp("tonalElevation", ListItemDefaults.Elevation),
            shadowElevation = a.dp("shadowElevation", ListItemDefaults.Elevation),
        )
    }
    composable("VerticalDivider(modifier, thickness, color)") { a ->
        VerticalDivider(modifier = a.modifier(), thickness = a.dp("thickness", 1.dp), color = a.color("color", MaterialTheme.colorScheme.outlineVariant))
    }
}

private fun Registry.navigation() {
    composable("NavigationBar(modifier, containerColor, contentColor, tonalElevation, content)") { a ->
        val content = a.scopedContent<RowScope>()
        val containerColor = a.color("containerColor", NavigationBarDefaults.containerColor)
        NavigationBar(
            modifier = a.modifier(),
            containerColor = containerColor,
            contentColor = a.color("contentColor", contentColorFor(containerColor)),
            tonalElevation = a.dp("tonalElevation", NavigationBarDefaults.Elevation),
        ) { content() }
    }
    composable("NavigationBarItem(selected, onClick, icon, modifier, enabled, label, alwaysShowLabel)") { a ->
        val scope = a.receiver<RowScope>()
        if (scope == null) {
            ErrorBox("NavigationBarItem must be inside NavigationBar")
        } else {
            with(scope) {
                NavigationBarItem(
                    selected = a.boolean("selected", false),
                    onClick = a.action("onClick"),
                    icon = a.content("icon"),
                    modifier = a.modifier(),
                    enabled = a.boolean("enabled", true),
                    label = a.contentOrNull("label"),
                    alwaysShowLabel = a.boolean("alwaysShowLabel", true),
                )
            }
        }
    }
    composable("TopAppBar(title, modifier, navigationIcon, actions, expandedHeight, windowInsets, colors, scrollBehavior)") { a ->
        val actions = a.scopedContent<RowScope>("actions")
        TopAppBar(
            title = a.content("title"),
            modifier = a.modifier(),
            navigationIcon = a.content("navigationIcon"),
            actions = { actions() },
            expandedHeight = a.dp("expandedHeight", TopAppBarDefaults.TopAppBarExpandedHeight),
            windowInsets = a.typed("windowInsets", TopAppBarDefaults.windowInsets),
            colors = a.typed("colors", TopAppBarDefaults.topAppBarColors()),
            scrollBehavior = a.typedOrNull<TopAppBarScrollBehavior>("scrollBehavior"),
        )
    }
    composable("CenterAlignedTopAppBar(title, modifier, navigationIcon, actions, expandedHeight, windowInsets, colors, scrollBehavior)") { a ->
        val actions = a.scopedContent<RowScope>("actions")
        CenterAlignedTopAppBar(
            title = a.content("title"),
            modifier = a.modifier(),
            navigationIcon = a.content("navigationIcon"),
            actions = { actions() },
            expandedHeight = a.dp("expandedHeight", TopAppBarDefaults.TopAppBarExpandedHeight),
            windowInsets = a.typed("windowInsets", TopAppBarDefaults.windowInsets),
            colors = a.typed("colors", TopAppBarDefaults.topAppBarColors()),
            scrollBehavior = a.typedOrNull<TopAppBarScrollBehavior>("scrollBehavior"),
        )
    }
    value("TopAppBarDefaults", TopAppBarDefaults)
    composableMember<TopAppBarDefaults>("topAppBarColors(containerColor, scrolledContainerColor, navigationIconContentColor, titleContentColor, actionIconContentColor)") { _, a ->
        TopAppBarDefaults.topAppBarColors(
            containerColor = a.color("containerColor"),
            scrolledContainerColor = a.color("scrolledContainerColor"),
            navigationIconContentColor = a.color("navigationIconContentColor"),
            titleContentColor = a.color("titleContentColor"),
            actionIconContentColor = a.color("actionIconContentColor"),
        )
    }
    composable("BadgedBox(badge, modifier, content)") { a ->
        val badge = a.scopedContent<BoxScope>("badge")
        val content = a.scopedContent<BoxScope>()
        BadgedBox(badge = { badge() }, modifier = a.modifier()) { content() }
    }
    composable("Badge(modifier, containerColor, contentColor, content)") { a ->
        val content = a.scopedContentOrNull<RowScope>("content")
        val containerColor = a.color("containerColor", BadgeDefaults.containerColor)
        Badge(
            modifier = a.modifier(),
            containerColor = containerColor,
            contentColor = a.color("contentColor", contentColorFor(containerColor)),
            content = content,
        )
    }
    composable("TabRow(selectedTabIndex, modifier, tabs)") { a -> TabRow(a) }
    composable("PrimaryTabRow(selectedTabIndex, modifier, tabs)") { a -> TabRow(a) }
    composable("Tab(selected, onClick, modifier, enabled, text, icon)") { a ->
        Tab(
            selected = a.boolean("selected", false),
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            text = a.contentOrNull("text"),
            icon = a.contentOrNull("icon"),
        )
    }
    composable(
        "AlertDialog(onDismissRequest, confirmButton, modifier, dismissButton, icon, title, text, shape, containerColor, " +
            "iconContentColor, titleContentColor, textContentColor, tonalElevation, properties)",
    ) { a ->
        AlertDialog(
            onDismissRequest = a.action("onDismissRequest"),
            confirmButton = a.content("confirmButton"),
            modifier = a.modifier(),
            dismissButton = a.contentOrNull("dismissButton"),
            icon = a.contentOrNull("icon"),
            title = a.contentOrNull("title"),
            text = a.contentOrNull("text"),
            shape = a.typed<Shape>("shape", AlertDialogDefaults.shape),
            containerColor = a.color("containerColor", AlertDialogDefaults.containerColor),
            iconContentColor = a.color("iconContentColor", AlertDialogDefaults.iconContentColor),
            titleContentColor = a.color("titleContentColor", AlertDialogDefaults.titleContentColor),
            textContentColor = a.color("textContentColor", AlertDialogDefaults.textContentColor),
            tonalElevation = a.dp("tonalElevation", AlertDialogDefaults.TonalElevation),
            properties = a.typed("properties", DialogProperties()),
        )
    }
}

@Composable
private fun TabRow(a: Args) {
    val tabs = a.content("tabs")
    PrimaryTabRow(selectedTabIndex = a.int("selectedTabIndex", 0), modifier = a.modifier()) { tabs() }
}

private fun Registry.inputs() {
    composable("TextField(value, onValueChange, modifier, enabled, readOnly, textStyle, label, placeholder, leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine, maxLines, minLines, *, shape, colors)") { a -> MaterialTextField(a, outlined = false) }
    composable("OutlinedTextField(value, onValueChange, modifier, enabled, readOnly, textStyle, label, placeholder, leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine, maxLines, minLines, *, shape, colors)") { a -> MaterialTextField(a, outlined = true) }
    value("TextFieldDefaults", TextFieldDefaults)
    composableMember<TextFieldDefaults>(
        "colors(focusedTextColor, unfocusedTextColor, focusedContainerColor, unfocusedContainerColor, cursorColor, " +
            "focusedIndicatorColor, unfocusedIndicatorColor, focusedLabelColor, unfocusedLabelColor)",
    ) { _, a -> a.textFieldColors(TextFieldDefaults.colors()) }
    value("OutlinedTextFieldDefaults", OutlinedTextFieldDefaults)
    composableMember<OutlinedTextFieldDefaults>(
        "colors(focusedTextColor, unfocusedTextColor, focusedContainerColor, unfocusedContainerColor, cursorColor, " +
            "focusedBorderColor, unfocusedBorderColor, focusedLabelColor, unfocusedLabelColor)",
    ) { _, a -> a.textFieldColors(OutlinedTextFieldDefaults.colors()) }
    composable("Slider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished, colors)") { a ->
        Slider(
            value = a.float("value", 0f),
            onValueChange = a.action1("onValueChange"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            valueRange = a.floatRange("valueRange"),
            steps = a.int("steps", 0),
            onValueChangeFinished = a.actionOrNull("onValueChangeFinished"),
            colors = a.typed("colors", SliderDefaults.colors()),
        )
    }
    composable("RangeSlider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished, colors)") { a ->
        val onValueChange = a.action1("onValueChange")
        RangeSlider(
            value = a.floatRange("value"),
            onValueChange = { range: ClosedFloatingPointRange<Float> -> onValueChange(range) },
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            valueRange = a.floatRange("valueRange"),
            steps = a.int("steps", 0),
            onValueChangeFinished = a.actionOrNull("onValueChangeFinished"),
            colors = a.typed("colors", SliderDefaults.colors()),
        )
    }
    composable("RadioButton(selected, onClick, modifier, enabled, colors)") { a ->
        RadioButton(
            selected = a.boolean("selected", false),
            onClick = a.actionOrNull("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            colors = a.typed("colors", RadioButtonDefaults.colors()),
        )
    }
}

private fun Registry.buttons() {
    composable("ElevatedButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, content)") { a ->
        val content = a.scopedContent<RowScope>()
        ElevatedButton(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            shape = a.typed<Shape>("shape", ButtonDefaults.elevatedShape),
            colors = a.typed("colors", ButtonDefaults.elevatedButtonColors()),
            elevation = a.nullable("elevation", ButtonDefaults.elevatedButtonElevation()),
            border = a.nullable<BorderStroke>("border", null),
            contentPadding = a.typed<PaddingValues>("contentPadding", ButtonDefaults.ContentPadding),
        ) { content() }
    }
    composable("FilledTonalButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, content)") { a ->
        val content = a.scopedContent<RowScope>()
        FilledTonalButton(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            shape = a.typed<Shape>("shape", ButtonDefaults.filledTonalShape),
            colors = a.typed("colors", ButtonDefaults.filledTonalButtonColors()),
            elevation = a.nullable("elevation", ButtonDefaults.filledTonalButtonElevation()),
            border = a.nullable<BorderStroke>("border", null),
            contentPadding = a.typed<PaddingValues>("contentPadding", ButtonDefaults.ContentPadding),
        ) { content() }
    }
    composable("ExtendedFloatingActionButton(text, icon, onClick, modifier, expanded, shape, containerColor, contentColor, elevation)") { a ->
        val containerColor = a.color("containerColor", FloatingActionButtonDefaults.containerColor)
        ExtendedFloatingActionButton(
            text = a.content("text"),
            icon = a.content("icon"),
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            expanded = a.boolean("expanded", true),
            shape = a.typed<Shape>("shape", FloatingActionButtonDefaults.extendedFabShape),
            containerColor = containerColor,
            contentColor = a.color("contentColor", contentColorFor(containerColor)),
            elevation = a.typed("elevation", FloatingActionButtonDefaults.elevation()),
        )
    }
    composable("ExtendedFloatingActionButton(onClick, modifier, shape, containerColor, contentColor, elevation, content)") { a ->
        val content = a.scopedContent<RowScope>()
        val containerColor = a.color("containerColor", FloatingActionButtonDefaults.containerColor)
        ExtendedFloatingActionButton(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            shape = a.typed<Shape>("shape", FloatingActionButtonDefaults.extendedFabShape),
            containerColor = containerColor,
            contentColor = a.color("contentColor", contentColorFor(containerColor)),
            elevation = a.typed("elevation", FloatingActionButtonDefaults.elevation()),
        ) { content() }
    }
    value("ButtonDefaults", ButtonDefaults)
    composableMember<ButtonDefaults>("buttonColors(containerColor, contentColor)") { _, a ->
        ButtonDefaults.buttonColors(
            containerColor = a.color("containerColor"),
            contentColor = a.color("contentColor"),
        )
    }
    composableMember<ButtonDefaults>("shape") { _, _ -> ButtonDefaults.shape }
    member<ButtonDefaults>("ContentPadding") { _, _ -> ButtonDefaults.ContentPadding }
}

private fun Registry.cards() {
    composable("ElevatedCard(modifier, shape, colors, elevation, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        ElevatedCard(
            modifier = a.modifier(),
            shape = a.typed<Shape>("shape", CardDefaults.elevatedShape),
            colors = a.typed("colors", CardDefaults.elevatedCardColors()),
            elevation = a.typed("elevation", CardDefaults.elevatedCardElevation()),
        ) { content() }
    }
    composable("OutlinedCard(modifier, shape, colors, elevation, border, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        OutlinedCard(
            modifier = a.modifier(),
            shape = a.typed<Shape>("shape", CardDefaults.outlinedShape),
            colors = a.typed("colors", CardDefaults.outlinedCardColors()),
            elevation = a.typed("elevation", CardDefaults.outlinedCardElevation()),
            border = a.typed("border", CardDefaults.outlinedCardBorder()),
        ) { content() }
    }
    value("CardDefaults", CardDefaults)
    composableMember<CardDefaults>("cardColors(containerColor, contentColor)") { _, a ->
        CardDefaults.cardColors(
            containerColor = a.color("containerColor"),
            contentColor = a.color("contentColor"),
        )
    }
    composableMember<CardDefaults>("cardElevation(defaultElevation)") { _, a ->
        CardDefaults.cardElevation(defaultElevation = a.dp("defaultElevation", 1.dp))
    }
    composableMember<CardDefaults>("shape") { _, _ -> CardDefaults.shape }
}

private fun Registry.progress() {
    composable("LinearProgressIndicator(progress, modifier, color, trackColor, strokeCap)") { a -> LinearProgress(a) }
    composable("CircularProgressIndicator(progress, modifier, color, strokeWidth)") { a -> CircularProgress(a) }
}

/** A Material text field that passes on every declared parameter; [outlined] picks `OutlinedTextField`. */
@Composable
private fun MaterialTextField(a: Args, outlined: Boolean) {
    val onValueChange = a.action1("onValueChange")
    val change = { text: String -> onValueChange(text) }
    val singleLine = a.boolean("singleLine", false)
    val maxLines = a.int("maxLines", if (singleLine) 1 else Int.MAX_VALUE).coerceAtLeast(1)
    val minLines = a.int("minLines", 1).coerceAtLeast(1)
    val textStyle = a.typed("textStyle", LocalTextStyle.current)
    val visualTransformation = a.typed("visualTransformation", VisualTransformation.None)
    val keyboardOptions = a.typed("keyboardOptions", KeyboardOptions.Default)
    val keyboardActions = a.typed("keyboardActions", KeyboardActions.Default)
    if (outlined) {
        OutlinedTextField(
            value = a.string("value"), onValueChange = change, modifier = a.modifier(),
            enabled = a.boolean("enabled", true), readOnly = a.boolean("readOnly", false), textStyle = textStyle,
            label = a.contentOrNull("label"), placeholder = a.contentOrNull("placeholder"),
            leadingIcon = a.contentOrNull("leadingIcon"), trailingIcon = a.contentOrNull("trailingIcon"),
            prefix = a.contentOrNull("prefix"), suffix = a.contentOrNull("suffix"),
            supportingText = a.contentOrNull("supportingText"), isError = a.boolean("isError", false),
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            shape = a.typed<Shape>("shape", OutlinedTextFieldDefaults.shape),
            colors = a.typed("colors", OutlinedTextFieldDefaults.colors()),
        )
    } else {
        TextField(
            value = a.string("value"), onValueChange = change, modifier = a.modifier(),
            enabled = a.boolean("enabled", true), readOnly = a.boolean("readOnly", false), textStyle = textStyle,
            label = a.contentOrNull("label"), placeholder = a.contentOrNull("placeholder"),
            leadingIcon = a.contentOrNull("leadingIcon"), trailingIcon = a.contentOrNull("trailingIcon"),
            prefix = a.contentOrNull("prefix"), suffix = a.contentOrNull("suffix"),
            supportingText = a.contentOrNull("supportingText"), isError = a.boolean("isError", false),
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            shape = a.typed<Shape>("shape", TextFieldDefaults.shape),
            colors = a.typed("colors", TextFieldDefaults.colors()),
        )
    }
}

/** [base] with the colors passed by name; colors that are not passed keep their value in [base]. */
private fun Args.textFieldColors(base: TextFieldColors): TextFieldColors = base.copy(
    focusedTextColor = color("focusedTextColor"),
    unfocusedTextColor = color("unfocusedTextColor"),
    focusedContainerColor = color("focusedContainerColor"),
    unfocusedContainerColor = color("unfocusedContainerColor"),
    cursorColor = color("cursorColor"),
    focusedIndicatorColor = color(if (has("focusedBorderColor")) "focusedBorderColor" else "focusedIndicatorColor"),
    unfocusedIndicatorColor = color(if (has("unfocusedBorderColor")) "unfocusedBorderColor" else "unfocusedIndicatorColor"),
    focusedLabelColor = color("focusedLabelColor"),
    unfocusedLabelColor = color("unfocusedLabelColor"),
)

/** A `0f..1f`-style range argument, or `0f..1f` when it is missing. */
@Suppress("UNCHECKED_CAST")
private fun Args.floatRange(name: String): ClosedFloatingPointRange<Float> =
    this[name] as? ClosedFloatingPointRange<Float> ?: 0f..1f

/** `progress` may be a lambda (`{ 0.5f }`), a number, or missing for an indeterminate indicator. */
private fun progressOf(a: Args): (() -> Float)? {
    return when (val progress = a["progress"]) {
        null -> null
        is Number -> ({ progress.toFloat() })
        else -> {
            val calculation = a.calculation("progress")
            ({ (calculation() as? Number)?.toFloat() ?: 0f })
        }
    }
}

@Composable
private fun LinearProgress(a: Args) {
    val progress = progressOf(a)
    val modifier = a.modifier()
    val color = a.color("color", ProgressIndicatorDefaults.linearColor)
    val trackColor = a.color("trackColor", ProgressIndicatorDefaults.linearTrackColor)
    val strokeCap = a.typed("strokeCap", ProgressIndicatorDefaults.LinearStrokeCap)
    if (progress == null) {
        LinearProgressIndicator(modifier = modifier, color = color, trackColor = trackColor, strokeCap = strokeCap)
    } else {
        LinearProgressIndicator(progress = progress, modifier = modifier, color = color, trackColor = trackColor, strokeCap = strokeCap)
    }
}

@Composable
private fun CircularProgress(a: Args) {
    val progress = progressOf(a)
    val modifier = a.modifier()
    val color = a.color("color", ProgressIndicatorDefaults.circularColor)
    val strokeWidth = a.dp("strokeWidth", ProgressIndicatorDefaults.CircularStrokeWidth)
    if (progress == null) {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
    } else {
        CircularProgressIndicator(progress = progress, modifier = modifier, color = color, strokeWidth = strokeWidth)
    }
}

/** Core Material components: Text, buttons, Card, Surface, Scaffold, theme values. */
private fun Registry.materialCore() {
    composable(TEXT_SIGNATURE) { a ->
        val style = a.typed("style", LocalTextStyle.current).merge(a.textStyle())
        val overflow = a.typed("overflow", TextOverflow.Clip)
        val softWrap = a.boolean("softWrap", true)
        val maxLines = a.int("maxLines", Int.MAX_VALUE).coerceAtLeast(1)
        val minLines = a.int("minLines", 1).coerceAtLeast(1)
        val annotated = a["text"] as? AnnotatedString
        if (annotated != null) {
            Text(annotated, a.modifier(), overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = a.action1("onTextLayout"), style = style)
        } else {
            Text(a.string("text"), a.modifier(), overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = a.action1OrNull("onTextLayout"), style = style)
        }
    }
    composable("Button(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)") { a ->
        val content = a.scopedContent<RowScope>()
        Button(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            shape = a.typed<Shape>("shape", ButtonDefaults.shape),
            colors = a.typed("colors", ButtonDefaults.buttonColors()),
            elevation = a.nullable("elevation", ButtonDefaults.buttonElevation()),
            border = a.nullable<BorderStroke>("border", null),
            contentPadding = a.typed("contentPadding", ButtonDefaults.ContentPadding),
            interactionSource = a.typedOrNull<MutableInteractionSource>("interactionSource"),
        ) { content() }
    }
    composable("OutlinedButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, content)") { a ->
        val content = a.scopedContent<RowScope>()
        val enabled = a.boolean("enabled", true)
        OutlinedButton(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = enabled,
            shape = a.typed<Shape>("shape", ButtonDefaults.outlinedShape),
            colors = a.typed("colors", ButtonDefaults.outlinedButtonColors()),
            elevation = a.nullable<ButtonElevation>("elevation", null),
            border = a.nullable("border", ButtonDefaults.outlinedButtonBorder(enabled)),
            contentPadding = a.typed<PaddingValues>("contentPadding", ButtonDefaults.ContentPadding),
        ) { content() }
    }
    composable("TextButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, content)") { a ->
        val content = a.scopedContent<RowScope>()
        TextButton(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            shape = a.typed<Shape>("shape", ButtonDefaults.textShape),
            colors = a.typed("colors", ButtonDefaults.textButtonColors()),
            elevation = a.nullable<ButtonElevation>("elevation", null),
            border = a.nullable<BorderStroke>("border", null),
            contentPadding = a.typed<PaddingValues>("contentPadding", ButtonDefaults.TextButtonContentPadding),
        ) { content() }
    }
    composable("FloatingActionButton(onClick, modifier, shape, containerColor, contentColor, elevation, content)") { a ->
        val content = a.content()
        val containerColor = a.color("containerColor", FloatingActionButtonDefaults.containerColor)
        FloatingActionButton(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            shape = a.typed<Shape>("shape", FloatingActionButtonDefaults.shape),
            containerColor = containerColor,
            contentColor = a.color("contentColor", contentColorFor(containerColor)),
            elevation = a.typed("elevation", FloatingActionButtonDefaults.elevation()),
        ) { content() }
    }
    composable("Card(modifier, shape, colors, elevation, border, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        Card(
            modifier = a.modifier(),
            shape = a.typed<Shape>("shape", CardDefaults.shape),
            colors = a.typed("colors", CardDefaults.cardColors()),
            elevation = a.typed("elevation", CardDefaults.cardElevation()),
            border = a.typedOrNull<BorderStroke>("border"),
        ) { content() }
    }
    composable("Card(onClick, modifier, enabled, shape, colors, elevation, border, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        Card(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            shape = a.typed<Shape>("shape", CardDefaults.shape),
            colors = a.typed("colors", CardDefaults.cardColors()),
            elevation = a.typed("elevation", CardDefaults.cardElevation()),
            border = a.typedOrNull<BorderStroke>("border"),
        ) { content() }
    }
    // Plain, clickable, selectable and toggleable surfaces; named arguments such as `onClick` pick the overload.
    composable("Surface(modifier, shape, color, contentColor, tonalElevation, shadowElevation, border, content)") { a ->
        val content = a.content()
        val color = a.color("color", MaterialTheme.colorScheme.surface)
        Surface(
            modifier = a.modifier(),
            shape = a.typed<Shape>("shape", RectangleShape),
            color = color,
            contentColor = a.color("contentColor", contentColorFor(color)),
            tonalElevation = a.dp("tonalElevation", 0.dp),
            shadowElevation = a.dp("shadowElevation", 0.dp),
            border = a.typedOrNull<BorderStroke>("border"),
        ) { content() }
    }
    composable("Surface(onClick, modifier, enabled, shape, color, contentColor, tonalElevation, shadowElevation, border, content)") { a ->
        val content = a.content()
        val color = a.color("color", MaterialTheme.colorScheme.surface)
        Surface(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            shape = a.typed<Shape>("shape", RectangleShape),
            color = color,
            contentColor = a.color("contentColor", contentColorFor(color)),
            tonalElevation = a.dp("tonalElevation", 0.dp),
            shadowElevation = a.dp("shadowElevation", 0.dp),
            border = a.typedOrNull<BorderStroke>("border"),
        ) { content() }
    }
    composable("Surface(selected, onClick, modifier, enabled, shape, color, contentColor, tonalElevation, shadowElevation, border, content)") { a ->
        val content = a.content()
        val color = a.color("color", MaterialTheme.colorScheme.surface)
        Surface(
            selected = a.boolean("selected", false),
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            shape = a.typed<Shape>("shape", RectangleShape),
            color = color,
            contentColor = a.color("contentColor", contentColorFor(color)),
            tonalElevation = a.dp("tonalElevation", 0.dp),
            shadowElevation = a.dp("shadowElevation", 0.dp),
            border = a.typedOrNull<BorderStroke>("border"),
        ) { content() }
    }
    composable("Surface(checked, onCheckedChange, modifier, enabled, shape, color, contentColor, tonalElevation, shadowElevation, border, content)") { a ->
        val content = a.content()
        val color = a.color("color", MaterialTheme.colorScheme.surface)
        val onCheckedChange = a.action1("onCheckedChange")
        Surface(
            checked = a.boolean("checked", false),
            onCheckedChange = { checked: Boolean -> onCheckedChange(checked) },
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            shape = a.typed<Shape>("shape", RectangleShape),
            color = color,
            contentColor = a.color("contentColor", contentColorFor(color)),
            tonalElevation = a.dp("tonalElevation", 0.dp),
            shadowElevation = a.dp("shadowElevation", 0.dp),
            border = a.typedOrNull<BorderStroke>("border"),
        ) { content() }
    }
    composable("MaterialTheme(colorScheme, shapes, typography, content)") { a ->
        val content = a.content()
        MaterialTheme(
            colorScheme = a.typed("colorScheme", MaterialTheme.colorScheme),
            shapes = a.typed("shapes", MaterialTheme.shapes),
            typography = a.typed("typography", MaterialTheme.typography),
        ) { content() }
    }
    composable("Scaffold(modifier, topBar, bottomBar, snackbarHost, floatingActionButton, floatingActionButtonPosition, containerColor, contentColor, contentWindowInsets, content)") { a ->
        val content = a.content1()
        Scaffold(
            modifier = a.modifier(),
            topBar = a.content("topBar"),
            bottomBar = a.content("bottomBar"),
            floatingActionButton = a.content("floatingActionButton"),
            snackbarHost = a.content("snackbarHost"),
            floatingActionButtonPosition = a.typed("floatingActionButtonPosition", FabPosition.End),
            containerColor = a.color("containerColor", MaterialTheme.colorScheme.background),
            contentColor = a.color("contentColor", MaterialTheme.colorScheme.onBackground),
            contentWindowInsets = a.typed("contentWindowInsets", ScaffoldDefaults.contentWindowInsets),
        ) { content(it) }
    }
    value("FabPosition", FabPosition.Companion)
    constants<FabPosition.Companion>(
        "Start" to FabPosition.Start,
        "Center" to FabPosition.Center,
        "End" to FabPosition.End,
        "EndOverlay" to FabPosition.EndOverlay,
    )
    composable("ProvideTextStyle(value, content)") { a ->
        val content = a.content()
        ProvideTextStyle(a.typed("value", LocalTextStyle.current)) { content() }
    }
    composable("HorizontalDivider(modifier, thickness, color)") { a ->
        HorizontalDivider(modifier = a.modifier(), thickness = a.dp("thickness", 1.dp), color = a.color("color", MaterialTheme.colorScheme.outlineVariant))
    }
    composable("Checkbox(checked, onCheckedChange, modifier, enabled, colors)") { a ->
        val onChange = a.action1OrNull("onCheckedChange")
        Checkbox(
            checked = a.boolean("checked", false),
            onCheckedChange = onChange,
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            colors = a.typed("colors", CheckboxDefaults.colors()),
        )
    }
    composable("Switch(checked, onCheckedChange, modifier, thumbContent, enabled, colors)") { a ->
        Switch(
            checked = a.boolean("checked", false),
            onCheckedChange = a.action1OrNull("onCheckedChange"),
            modifier = a.modifier(),
            thumbContent = a.contentOrNull("thumbContent"),
            enabled = a.boolean("enabled", true),
            colors = a.typed("colors", SwitchDefaults.colors()),
        )
    }
    composable("TriStateCheckbox(state, onClick, modifier, enabled, colors)") { a ->
        TriStateCheckbox(
            state = a.typed("state", ToggleableState.Off),
            onClick = a.actionOrNull("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            colors = a.typed("colors", CheckboxDefaults.colors()),
        )
    }

    value("MaterialTheme", MaterialTheme)
    composableMember<MaterialTheme>("colorScheme") { _, _ -> MaterialTheme.colorScheme }
    composableMember<MaterialTheme>("typography") { _, _ -> MaterialTheme.typography }
    composableMember<MaterialTheme>("shapes") { _, _ -> MaterialTheme.shapes }
    properties<ColorScheme>(
        "primary" to { it.primary },
        "onPrimary" to { it.onPrimary },
        "primaryContainer" to { it.primaryContainer },
        "onPrimaryContainer" to { it.onPrimaryContainer },
        "secondary" to { it.secondary },
        "onSecondary" to { it.onSecondary },
        "secondaryContainer" to { it.secondaryContainer },
        "onSecondaryContainer" to { it.onSecondaryContainer },
        "tertiary" to { it.tertiary },
        "onTertiary" to { it.onTertiary },
        "background" to { it.background },
        "onBackground" to { it.onBackground },
        "surface" to { it.surface },
        "onSurface" to { it.onSurface },
        "surfaceVariant" to { it.surfaceVariant },
        "onSurfaceVariant" to { it.onSurfaceVariant },
        "error" to { it.error },
        "onError" to { it.onError },
        "outline" to { it.outline },
        "outlineVariant" to { it.outlineVariant },
    )
    properties<Typography>(
        "displayLarge" to { it.displayLarge },
        "displayMedium" to { it.displayMedium },
        "displaySmall" to { it.displaySmall },
        "headlineLarge" to { it.headlineLarge },
        "headlineMedium" to { it.headlineMedium },
        "headlineSmall" to { it.headlineSmall },
        "titleLarge" to { it.titleLarge },
        "titleMedium" to { it.titleMedium },
        "titleSmall" to { it.titleSmall },
        "bodyLarge" to { it.bodyLarge },
        "bodyMedium" to { it.bodyMedium },
        "bodySmall" to { it.bodySmall },
        "labelLarge" to { it.labelLarge },
        "labelMedium" to { it.labelMedium },
        "labelSmall" to { it.labelSmall },
    )
    properties<Shapes>(
        "extraSmall" to { it.extraSmall },
        "small" to { it.small },
        "medium" to { it.medium },
        "large" to { it.large },
        "extraLarge" to { it.extraLarge },
    )
    member<TextStyle>("copy(color, fontSize, fontWeight)") { style, a ->
        style.copy(
            color = a.color("color", style.color),
            fontSize = a.sp("fontSize", style.fontSize),
            fontWeight = a.typedOrNull<FontWeight>("fontWeight") ?: style.fontWeight,
        )
    }
}
