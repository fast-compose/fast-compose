@file:OptIn(ExperimentalMaterial3Api::class)

package ma7moud3ly.fast.compose.bindings.material3

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.Registry

/** The `Set<SheetValue>` passed as [name], e.g. `setOf(SheetValue.PartiallyExpanded, SheetValue.Expanded)`. */
internal fun Args.sheetValues(name: String, default: Set<SheetValue>): Set<SheetValue> =
    (get(name) as? Collection<*>)?.filterIsInstance<SheetValue>()?.toSet()?.takeIf { it.isNotEmpty() } ?: default

/** The `confirmValueChange` lambda; a missing lambda, or one that returns no Boolean, allows every change. */
internal fun Args.confirmSheetValue(): (SheetValue) -> Boolean {
    if (lambda("confirmValueChange") == null) return { true }
    val confirm = function1("confirmValueChange")
    return { value -> confirm(value) as? Boolean ?: true }
}

/** Marker for the enum `SheetValue`, which has no companion object. */
private object SheetValues

/** `BottomAppBar`, `BottomSheetScaffold` and `SearchBar`. */
internal fun Registry.bars() {
    bottomAppBars()
    bottomSheetScaffold()
    searchBars()
}

private fun Registry.bottomAppBars() {
    composable("BottomAppBar(modifier, containerColor, contentColor, tonalElevation, contentPadding, content)") { a ->
        val content = a.scopedContent<RowScope>()
        val containerColor = a.color("containerColor", BottomAppBarDefaults.containerColor)
        BottomAppBar(
            modifier = a.modifier(),
            containerColor = containerColor,
            contentColor = a.color("contentColor", contentColorFor(containerColor)),
            tonalElevation = a.dp("tonalElevation", BottomAppBarDefaults.ContainerElevation),
            contentPadding = a.typed<PaddingValues>("contentPadding", BottomAppBarDefaults.ContentPadding),
        ) { content() }
    }
    composable("BottomAppBar(actions, modifier, floatingActionButton, containerColor, contentColor, tonalElevation, contentPadding)") { a ->
        val actions = a.scopedContent<RowScope>("actions")
        val containerColor = a.color("containerColor", BottomAppBarDefaults.containerColor)
        BottomAppBar(
            actions = { actions() },
            modifier = a.modifier(),
            floatingActionButton = a.contentOrNull("floatingActionButton"),
            containerColor = containerColor,
            contentColor = a.color("contentColor", contentColorFor(containerColor)),
            tonalElevation = a.dp("tonalElevation", BottomAppBarDefaults.ContainerElevation),
            contentPadding = a.typed<PaddingValues>("contentPadding", BottomAppBarDefaults.ContentPadding),
        )
    }
    value("BottomAppBarDefaults", BottomAppBarDefaults)
}

private fun Registry.bottomSheetScaffold() {
    type<BottomSheetScaffoldState>("BottomSheetScaffoldState")
    composable("rememberBottomSheetState(initialValue, enabledValues, confirmValueChange)") { a ->
        rememberBottomSheetState(
            initialValue = a.typed("initialValue", SheetValue.PartiallyExpanded),
            enabledValues = a.sheetValues("enabledValues", setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)),
            confirmValueChange = a.confirmSheetValue(),
        )
    }
    // Deprecated in Material 3; still accepted for older code, and runs as rememberBottomSheetState.
    composable("rememberStandardBottomSheetState(initialValue, confirmValueChange, skipHiddenState)") { a ->
        a.warn("rememberStandardBottomSheetState is deprecated; use rememberBottomSheetState")
        val skipHidden = a.boolean("skipHiddenState", true)
        rememberBottomSheetState(
            initialValue = a.typed("initialValue", SheetValue.PartiallyExpanded),
            enabledValues = if (skipHidden) {
                setOf(SheetValue.PartiallyExpanded, SheetValue.Expanded)
            } else {
                setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
            },
            confirmValueChange = a.confirmSheetValue(),
        )
    }
    composable("rememberBottomSheetScaffoldState(bottomSheetState, snackbarHostState)") { a ->
        val sheet = a.typedOrNull<SheetState>("bottomSheetState")
        val host = a.typedOrNull<SnackbarHostState>("snackbarHostState")
        when {
            sheet != null && host != null -> rememberBottomSheetScaffoldState(bottomSheetState = sheet, snackbarHostState = host)
            sheet != null -> rememberBottomSheetScaffoldState(bottomSheetState = sheet)
            host != null -> rememberBottomSheetScaffoldState(snackbarHostState = host)
            else -> rememberBottomSheetScaffoldState()
        }
    }
    properties<BottomSheetScaffoldState>("bottomSheetState" to { it.bottomSheetState }, "snackbarHostState" to { it.snackbarHostState })
    value("SheetValue", SheetValues)
    constants<SheetValues>("Hidden" to SheetValue.Hidden, "Expanded" to SheetValue.Expanded, "PartiallyExpanded" to SheetValue.PartiallyExpanded)
    composable(
        "BottomSheetScaffold(sheetContent, modifier, scaffoldState, sheetPeekHeight, sheetMaxWidth, sheetShape, sheetContainerColor, " +
            "sheetContentColor, sheetTonalElevation, sheetShadowElevation, sheetDragHandle, sheetSwipeEnabled, topBar, snackbarHost, " +
            "containerColor, contentColor, content)",
    ) { a -> BottomSheetScaffold(a) }
}

@Composable
private fun BottomSheetScaffold(a: Args) {
    val sheetContent = a.scopedContent<ColumnScope>("sheetContent")
    val content = a.content1()
    val snackbarHost = a.lambda("snackbarHost")?.let { a.content1("snackbarHost") }
    val sheetContainerColor = a.color("sheetContainerColor", BottomSheetDefaults.ContainerColor)
    val containerColor = a.color("containerColor", MaterialTheme.colorScheme.surface)
    val dragHandle: (@Composable () -> Unit)? =
        if (a.has("sheetDragHandle")) a.contentOrNull("sheetDragHandle") else ({ BottomSheetDefaults.DragHandle() })
    BottomSheetScaffold(
        sheetContent = { sheetContent() },
        modifier = a.modifier(),
        scaffoldState = a.typed("scaffoldState", rememberBottomSheetScaffoldState()),
        sheetPeekHeight = a.dp("sheetPeekHeight", BottomSheetDefaults.SheetPeekHeight),
        sheetMaxWidth = a.dp("sheetMaxWidth", BottomSheetDefaults.SheetMaxWidth),
        sheetShape = a.typed<Shape>("sheetShape", BottomSheetDefaults.ExpandedShape),
        sheetContainerColor = sheetContainerColor,
        sheetContentColor = a.color("sheetContentColor", contentColorFor(sheetContainerColor)),
        sheetTonalElevation = a.dp("sheetTonalElevation", 0.dp),
        sheetShadowElevation = a.dp("sheetShadowElevation", BottomSheetDefaults.Elevation),
        sheetDragHandle = dragHandle,
        sheetSwipeEnabled = a.boolean("sheetSwipeEnabled", true),
        topBar = a.contentOrNull("topBar"),
        snackbarHost = { state: SnackbarHostState -> if (snackbarHost != null) snackbarHost(state) else SnackbarHost(state) },
        containerColor = containerColor,
        contentColor = a.color("contentColor", contentColorFor(containerColor)),
    ) { padding -> content(padding) }
}

private fun Registry.searchBars() {
    value("SearchBarDefaults", SearchBarDefaults)
    composableMember<SearchBarDefaults>(
        "InputField(query, onQueryChange, onSearch, expanded, onExpandedChange, modifier, enabled, placeholder, leadingIcon, trailingIcon)",
    ) { _, a ->
        val onQueryChange = a.action1("onQueryChange")
        val onSearch = a.action1("onSearch")
        val onExpandedChange = a.action1("onExpandedChange")
        SearchBarDefaults.InputField(
            query = a.string("query"),
            onQueryChange = { query: String -> onQueryChange(query) },
            onSearch = { query: String -> onSearch(query) },
            expanded = a.boolean("expanded", false),
            onExpandedChange = { expanded: Boolean -> onExpandedChange(expanded) },
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            placeholder = a.contentOrNull("placeholder"),
            leadingIcon = a.contentOrNull("leadingIcon"),
            trailingIcon = a.contentOrNull("trailingIcon"),
        )
    }
    composable("SearchBar(inputField, expanded, onExpandedChange, modifier, shape, colors, tonalElevation, shadowElevation, content)") { a ->
        val content = a.scopedContent<ColumnScope>()
        val onExpandedChange = a.action1("onExpandedChange")
        SearchBar(
            inputField = a.content("inputField"),
            expanded = a.boolean("expanded", false),
            onExpandedChange = { expanded: Boolean -> onExpandedChange(expanded) },
            modifier = a.modifier(),
            shape = a.typed<Shape>("shape", SearchBarDefaults.inputFieldShape),
            colors = a.typed("colors", SearchBarDefaults.colors()),
            tonalElevation = a.dp("tonalElevation", SearchBarDefaults.TonalElevation),
            shadowElevation = a.dp("shadowElevation", SearchBarDefaults.ShadowElevation),
        ) { content() }
    }
}
