@file:OptIn(ExperimentalMaterial3Api::class)

package ma7moud3ly.fast.compose.bindings.material3

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import ma7moud3ly.fast.compose.bindings.foundation.TEXT_SIGNATURE
import ma7moud3ly.fast.compose.bindings.foundation.textStyle
import ma7moud3ly.fast.compose.interpreter.ErrorBox
import ma7moud3ly.fast.compose.interpreter.Registry

/** Material menus, bottom sheets, snack bars, navigation drawer and rail, and Material composition locals. */
internal fun Registry.materialExtras() {
    menus()
    sheets()
    snackbars()
    drawers()
    value("LocalContentColor", LocalContentColor)
    value("LocalTextStyle", LocalTextStyle)
}

private fun Registry.menus() {
    composable("DropdownMenu(expanded, onDismissRequest, modifier, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        DropdownMenu(
            expanded = a.boolean("expanded", false),
            onDismissRequest = a.action("onDismissRequest"),
            modifier = a.modifier(),
        ) { content() }
    }
    composable("DropdownMenuItem(text, onClick, modifier, leadingIcon, trailingIcon, enabled)") { a ->
        DropdownMenuItem(
            text = a.content("text"),
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            leadingIcon = a.contentOrNull("leadingIcon"),
            trailingIcon = a.contentOrNull("trailingIcon"),
            enabled = a.boolean("enabled", true),
        )
    }
}

private fun Registry.sheets() {
    type<SheetState>("SheetState")
    // Deprecated in Material 3; still accepted for older code, and runs as rememberBottomSheetState.
    composable("rememberModalBottomSheetState(skipPartiallyExpanded, confirmValueChange)") { a ->
        a.warn("rememberModalBottomSheetState is deprecated; use rememberBottomSheetState(initialValue = SheetValue.Hidden)")
        val skipPartiallyExpanded = a.boolean("skipPartiallyExpanded", false)
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = if (skipPartiallyExpanded) {
                setOf(SheetValue.Hidden, SheetValue.Expanded)
            } else {
                setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
            },
            confirmValueChange = a.confirmSheetValue(),
        )
    }
    composable("ModalBottomSheet(onDismissRequest, modifier, sheetState, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        ModalBottomSheet(
            onDismissRequest = a.action("onDismissRequest"),
            modifier = a.modifier(),
            sheetState = a.typed("sheetState", rememberBottomSheetState(initialValue = SheetValue.Hidden)),
        ) { content() }
    }
    properties<SheetState>("isVisible" to { it.isVisible }, "currentValue" to { it.currentValue })
    suspendMember<SheetState>("show") { state, _ -> state.show() }
    suspendMember<SheetState>("hide") { state, _ -> state.hide() }
    suspendMember<SheetState>("expand") { state, _ -> state.expand() }
    suspendMember<SheetState>("partialExpand") { state, _ -> state.partialExpand() }
}

/** Markers for the enums `SnackbarDuration`, `SnackbarResult` and `DrawerValue`, which have no companion objects. */
private object SnackbarDurations
private object SnackbarResults
private object DrawerValues

private fun Registry.snackbars() {
    type<SnackbarHostState>("SnackbarHostState")
    function("SnackbarHostState") { _ -> SnackbarHostState() }
    composable("SnackbarHost(hostState, modifier)") { a ->
        val state = a.typedOrNull<SnackbarHostState>("hostState")
        if (state == null) ErrorBox("SnackbarHost needs a SnackbarHostState") else SnackbarHost(state, a.modifier())
    }
    composable("Snackbar(modifier, content)") { a ->
        val content = a.content()
        Snackbar(modifier = a.modifier()) { content() }
    }
    suspendMember<SnackbarHostState>("showSnackbar(message, actionLabel, withDismissAction, duration)") { state, a ->
        val actionLabel = a.stringOrNull("actionLabel")
        state.showSnackbar(
            message = a.string("message"),
            actionLabel = actionLabel,
            withDismissAction = a.boolean("withDismissAction", false),
            duration = a.typed("duration", if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite),
        )
    }
    value("SnackbarDuration", SnackbarDurations)
    constants<SnackbarDurations>(
        "Short" to SnackbarDuration.Short,
        "Long" to SnackbarDuration.Long,
        "Indefinite" to SnackbarDuration.Indefinite,
    )
    value("SnackbarResult", SnackbarResults)
    constants<SnackbarResults>("Dismissed" to SnackbarResult.Dismissed, "ActionPerformed" to SnackbarResult.ActionPerformed)
}

private fun Registry.drawers() {
    type<DrawerState>("DrawerState")
    value("DrawerValue", DrawerValues)
    constants<DrawerValues>("Closed" to DrawerValue.Closed, "Open" to DrawerValue.Open)
    composable("rememberDrawerState(initialValue)") { a -> rememberDrawerState(a.typed("initialValue", DrawerValue.Closed)) }
    properties<DrawerState>("isOpen" to { it.isOpen }, "isClosed" to { it.isClosed }, "currentValue" to { it.currentValue })
    suspendMember<DrawerState>("open") { state, _ -> state.open() }
    suspendMember<DrawerState>("close") { state, _ -> state.close() }
    composable("ModalNavigationDrawer(drawerContent, modifier, drawerState, gesturesEnabled, content)") { a ->
        ModalNavigationDrawer(
            drawerContent = a.content("drawerContent"),
            modifier = a.modifier(),
            drawerState = a.typed("drawerState", rememberDrawerState(DrawerValue.Closed)),
            gesturesEnabled = a.boolean("gesturesEnabled", true),
            content = a.content(),
        )
    }
    composable("ModalDrawerSheet(modifier, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        ModalDrawerSheet(modifier = a.modifier()) { content() }
    }
    composable("NavigationDrawerItem(label, selected, onClick, modifier, icon, badge)") { a ->
        NavigationDrawerItem(
            label = a.content("label"),
            selected = a.boolean("selected", false),
            onClick = a.action("onClick"),
            modifier = a.modifier(),
            icon = a.contentOrNull("icon"),
            badge = a.contentOrNull("badge"),
        )
    }
    composable("NavigationRail(modifier, containerColor, contentColor, header, windowInsets, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        val containerColor = a.color("containerColor", NavigationRailDefaults.ContainerColor)
        NavigationRail(
            modifier = a.modifier(),
            containerColor = containerColor,
            contentColor = a.color("contentColor", contentColorFor(containerColor)),
            header = a.scopedContentOrNull<ColumnScope>("header"),
            windowInsets = a.typed("windowInsets", NavigationRailDefaults.windowInsets),
        ) { content() }
    }
    composable("NavigationRailItem(selected, onClick, icon, modifier, enabled, label, alwaysShowLabel)") { a ->
        NavigationRailItem(
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
