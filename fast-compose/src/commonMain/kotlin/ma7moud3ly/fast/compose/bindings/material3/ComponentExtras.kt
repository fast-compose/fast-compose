@file:OptIn(ExperimentalMaterial3Api::class)

package ma7moud3ly.fast.compose.bindings.material3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.state.ToggleableState
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.ErrorBox
import ma7moud3ly.fast.compose.interpreter.Registry

/** Marker for the enum `ToggleableState`, which has no companion object. */
private object ToggleableStates

/**
 * More Material components: icon buttons, small and large FABs, clickable cards, scrollable and secondary tabs,
 * medium and large top app bars with scroll behaviors, exposed dropdown menus and permanent or dismissible drawers.
 */
internal fun Registry.componentExtras() {
    iconButtons()
    clickableCards()
    tabs()
    topAppBars()
    exposedDropdownMenus()
    drawerExtras()
    value("ToggleableState", ToggleableStates)
    constants<ToggleableStates>("On" to ToggleableState.On, "Off" to ToggleableState.Off, "Indeterminate" to ToggleableState.Indeterminate)
    function("ToggleableState(value)") { a -> ToggleableState(a.boolean("value", false)) }
    composableMember<CheckboxDefaults>("colors(checkedColor, uncheckedColor, checkmarkColor)") { _, a ->
        CheckboxDefaults.colors(
            checkedColor = a.color("checkedColor"),
            uncheckedColor = a.color("uncheckedColor"),
            checkmarkColor = a.color("checkmarkColor"),
        )
    }
    value("CheckboxDefaults", CheckboxDefaults)
    function("ripple(bounded, radius, color)") { a -> ripple(a.boolean("bounded", true), a.dp("radius"), a.color("color")) }
}

private fun Registry.iconButtons() {
    composable("FilledIconButton(onClick, modifier, enabled, shape, colors, content)") { a ->
        val content = a.content()
        FilledIconButton(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            shape = a.typed<Shape>("shape", IconButtonDefaults.filledShape),
            colors = a.typed("colors", IconButtonDefaults.filledIconButtonColors()),
        ) { content() }
    }
    composable("FilledTonalIconButton(onClick, modifier, enabled, shape, colors, content)") { a ->
        val content = a.content()
        FilledTonalIconButton(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            shape = a.typed<Shape>("shape", IconButtonDefaults.filledShape),
            colors = a.typed("colors", IconButtonDefaults.filledTonalIconButtonColors()),
        ) { content() }
    }
    composable("OutlinedIconButton(onClick, modifier, enabled, shape, colors, border, content)") { a ->
        val content = a.content()
        val enabled = a.boolean("enabled", true)
        OutlinedIconButton(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = enabled,
            shape = a.typed<Shape>("shape", IconButtonDefaults.outlinedShape),
            colors = a.typed("colors", IconButtonDefaults.outlinedIconButtonColors()),
            border = a.nullable("border", IconButtonDefaults.outlinedIconButtonBorder(enabled)),
        ) { content() }
    }
    composable("IconToggleButton(checked, onCheckedChange, modifier, enabled, colors, content)") { a ->
        val content = a.content()
        val onCheckedChange = a.action1("onCheckedChange")
        IconToggleButton(
            checked = a.boolean("checked", false),
            onCheckedChange = { checked: Boolean -> onCheckedChange(checked) },
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            colors = a.typed("colors", IconButtonDefaults.iconToggleButtonColors()),
        ) { content() }
    }
    composable("SmallFloatingActionButton(onClick, modifier, shape, containerColor, contentColor, elevation, content)") { a ->
        val content = a.content()
        val containerColor = a.color("containerColor", FloatingActionButtonDefaults.containerColor)
        SmallFloatingActionButton(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            shape = a.typed<Shape>("shape", FloatingActionButtonDefaults.smallShape),
            containerColor = containerColor,
            contentColor = a.color("contentColor", contentColorFor(containerColor)),
            elevation = a.typed("elevation", FloatingActionButtonDefaults.elevation()),
        ) { content() }
    }
    composable("LargeFloatingActionButton(onClick, modifier, shape, containerColor, contentColor, elevation, content)") { a ->
        val content = a.content()
        val containerColor = a.color("containerColor", FloatingActionButtonDefaults.containerColor)
        LargeFloatingActionButton(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            shape = a.typed<Shape>("shape", FloatingActionButtonDefaults.largeShape),
            containerColor = containerColor,
            contentColor = a.color("contentColor", contentColorFor(containerColor)),
            elevation = a.typed("elevation", FloatingActionButtonDefaults.elevation()),
        ) { content() }
    }
    value("IconButtonDefaults", IconButtonDefaults)
}

private fun Registry.clickableCards() {
    composable("ElevatedCard(onClick, modifier, enabled, shape, colors, elevation, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        ElevatedCard(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            shape = a.typed<Shape>("shape", CardDefaults.elevatedShape),
            colors = a.typed("colors", CardDefaults.elevatedCardColors()),
            elevation = a.typed("elevation", CardDefaults.elevatedCardElevation()),
        ) { content() }
    }
    composable("OutlinedCard(onClick, modifier, enabled, shape, colors, elevation, border, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        val enabled = a.boolean("enabled", true)
        OutlinedCard(
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            enabled = enabled,
            shape = a.typed<Shape>("shape", CardDefaults.outlinedShape),
            colors = a.typed("colors", CardDefaults.outlinedCardColors()),
            elevation = a.typed("elevation", CardDefaults.outlinedCardElevation()),
            border = a.typed<BorderStroke>("border", CardDefaults.outlinedCardBorder(enabled)),
        ) { content() }
    }
}

private fun Registry.tabs() {
    // ScrollableTabRow is the older name; both use the current PrimaryScrollableTabRow.
    composable("ScrollableTabRow(selectedTabIndex, modifier, containerColor, contentColor, edgePadding, tabs)") { a -> ScrollableTabs(a) }
    composable("PrimaryScrollableTabRow(selectedTabIndex, modifier, scrollState, containerColor, contentColor, edgePadding, tabs)") { a -> ScrollableTabs(a) }
    composable("SecondaryTabRow(selectedTabIndex, modifier, containerColor, contentColor, tabs)") { a ->
        val tabs = a.content("tabs")
        SecondaryTabRow(
            selectedTabIndex = a.int("selectedTabIndex", 0),
            modifier = a.modifier(),
            containerColor = a.color("containerColor", TabRowDefaults.secondaryContainerColor),
            contentColor = a.color("contentColor", TabRowDefaults.secondaryContentColor),
        ) { tabs() }
    }
}

@Composable
private fun ScrollableTabs(a: Args) {
    val tabs = a.content("tabs")
    PrimaryScrollableTabRow(
        selectedTabIndex = a.int("selectedTabIndex", 0),
        modifier = a.modifier(),
        scrollState = a.typed("scrollState", rememberScrollState()),
        containerColor = a.color("containerColor", TabRowDefaults.primaryContainerColor),
        contentColor = a.color("contentColor", TabRowDefaults.primaryContentColor),
        edgePadding = a.dp("edgePadding", TabRowDefaults.ScrollableTabRowEdgeStartPadding),
    ) { tabs() }
}

private fun Registry.topAppBars() {
    composable("MediumTopAppBar(title, modifier, navigationIcon, actions, collapsedHeight, expandedHeight, windowInsets, colors, scrollBehavior)") { a ->
        val actions = a.scopedContent<RowScope>("actions")
        MediumTopAppBar(
            title = a.content("title"),
            modifier = a.modifier(),
            navigationIcon = a.content("navigationIcon"),
            actions = { actions() },
            collapsedHeight = a.dp("collapsedHeight", TopAppBarDefaults.MediumAppBarCollapsedHeight),
            expandedHeight = a.dp("expandedHeight", TopAppBarDefaults.MediumAppBarExpandedHeight),
            windowInsets = a.typed("windowInsets", TopAppBarDefaults.windowInsets),
            colors = a.typed("colors", TopAppBarDefaults.topAppBarColors()),
            scrollBehavior = a.typedOrNull<TopAppBarScrollBehavior>("scrollBehavior"),
        )
    }
    composable("LargeTopAppBar(title, modifier, navigationIcon, actions, collapsedHeight, expandedHeight, windowInsets, colors, scrollBehavior)") { a ->
        val actions = a.scopedContent<RowScope>("actions")
        LargeTopAppBar(
            title = a.content("title"),
            modifier = a.modifier(),
            navigationIcon = a.content("navigationIcon"),
            actions = { actions() },
            collapsedHeight = a.dp("collapsedHeight", TopAppBarDefaults.LargeAppBarCollapsedHeight),
            expandedHeight = a.dp("expandedHeight", TopAppBarDefaults.LargeAppBarExpandedHeight),
            windowInsets = a.typed("windowInsets", TopAppBarDefaults.windowInsets),
            colors = a.typed("colors", TopAppBarDefaults.topAppBarColors()),
            scrollBehavior = a.typedOrNull<TopAppBarScrollBehavior>("scrollBehavior"),
        )
    }
    type<TopAppBarScrollBehavior>("TopAppBarScrollBehavior")
    composableMember<TopAppBarDefaults>("pinnedScrollBehavior()") { _, _ -> TopAppBarDefaults.pinnedScrollBehavior() }
    composableMember<TopAppBarDefaults>("enterAlwaysScrollBehavior()") { _, _ -> TopAppBarDefaults.enterAlwaysScrollBehavior() }
    composableMember<TopAppBarDefaults>("exitUntilCollapsedScrollBehavior()") { _, _ -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior() }
    properties<TopAppBarScrollBehavior>("nestedScrollConnection" to { it.nestedScrollConnection }, "state" to { it.state })
}

private fun Registry.exposedDropdownMenus() {
    composable("ExposedDropdownMenuBox(expanded, onExpandedChange, modifier, content)") { a ->
        val content = a.scopedContent<ExposedDropdownMenuBoxScope>()
        val onExpandedChange = a.action1("onExpandedChange")
        ExposedDropdownMenuBox(
            expanded = a.boolean("expanded", false),
            onExpandedChange = { expanded: Boolean -> onExpandedChange(expanded) },
            modifier = a.modifier(),
        ) { content() }
    }
    composable("ExposedDropdownMenu(expanded, onDismissRequest, modifier, content)") { a ->
        val scope = a.receiver<ExposedDropdownMenuBoxScope>()
        val content = a.scopedContent<ColumnScope>()
        if (scope == null) {
            ErrorBox("ExposedDropdownMenu must be inside ExposedDropdownMenuBox")
        } else {
            with(scope) {
                ExposedDropdownMenu(
                    expanded = a.boolean("expanded", false),
                    onDismissRequest = a.action("onDismissRequest"),
                    modifier = a.modifier(),
                    // Only the current overload has this parameter; the deprecated one takes `focusable`.
                    matchAnchorWidth = true,
                ) { content() }
            }
        }
    }
    member<Modifier>("menuAnchor(type, enabled)") { m, a ->
        val scope = a.receiver<ExposedDropdownMenuBoxScope>()
        if (scope == null) {
            a.report("menuAnchor() must be used inside ExposedDropdownMenuBox")
            m
        } else {
            with(scope) { m.menuAnchor(a.typed("type", ExposedDropdownMenuAnchorType.PrimaryNotEditable), a.boolean("enabled", true)) }
        }
    }
    value("ExposedDropdownMenuAnchorType", ExposedDropdownMenuAnchorType.Companion)
    constants<ExposedDropdownMenuAnchorType.Companion>(
        "PrimaryNotEditable" to ExposedDropdownMenuAnchorType.PrimaryNotEditable,
        "PrimaryEditable" to ExposedDropdownMenuAnchorType.PrimaryEditable,
        "SecondaryEditable" to ExposedDropdownMenuAnchorType.SecondaryEditable,
    )
    value("ExposedDropdownMenuDefaults", ExposedDropdownMenuDefaults)
    composableMember<ExposedDropdownMenuDefaults>("TrailingIcon(expanded, modifier)") { _, a ->
        ExposedDropdownMenuDefaults.TrailingIcon(expanded = a.boolean("expanded", false), modifier = a.modifier())
    }
}

private fun Registry.drawerExtras() {
    composable("PermanentNavigationDrawer(drawerContent, modifier, content)") { a ->
        PermanentNavigationDrawer(drawerContent = a.content("drawerContent"), modifier = a.modifier(), content = a.content())
    }
    composable("DismissibleNavigationDrawer(drawerContent, modifier, drawerState, gesturesEnabled, content)") { a ->
        DismissibleNavigationDrawer(
            drawerContent = a.content("drawerContent"),
            modifier = a.modifier(),
            drawerState = a.typed("drawerState", rememberDrawerState(DrawerValue.Closed)),
            gesturesEnabled = a.boolean("gesturesEnabled", true),
            content = a.content(),
        )
    }
    composable("PermanentDrawerSheet(modifier, drawerShape, drawerContainerColor, drawerContentColor, drawerTonalElevation, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        val containerColor = a.color("drawerContainerColor", DrawerDefaults.standardContainerColor)
        PermanentDrawerSheet(
            modifier = a.modifier(),
            drawerShape = a.typed<Shape>("drawerShape", RectangleShape),
            drawerContainerColor = containerColor,
            drawerContentColor = a.color("drawerContentColor", contentColorFor(containerColor)),
            drawerTonalElevation = a.dp("drawerTonalElevation", DrawerDefaults.PermanentDrawerElevation),
        ) { content() }
    }
    composable("DismissibleDrawerSheet(modifier, drawerShape, drawerContainerColor, drawerContentColor, drawerTonalElevation, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        val containerColor = a.color("drawerContainerColor", DrawerDefaults.standardContainerColor)
        DismissibleDrawerSheet(
            modifier = a.modifier(),
            drawerShape = a.typed<Shape>("drawerShape", RectangleShape),
            drawerContainerColor = containerColor,
            drawerContentColor = a.color("drawerContentColor", contentColorFor(containerColor)),
            drawerTonalElevation = a.dp("drawerTonalElevation", DrawerDefaults.DismissibleDrawerElevation),
        ) { content() }
    }
}
