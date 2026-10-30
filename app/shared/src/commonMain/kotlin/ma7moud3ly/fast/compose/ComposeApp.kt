package ma7moud3ly.fast.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ma7moud3ly.nemo.NemoCodeEditor
import io.ma7moud3ly.nemo.model.EditorSettings
import io.ma7moud3ly.nemo.model.EditorThemes
import io.ma7moud3ly.nemo.model.Language
import io.ma7moud3ly.nemo.model.rememberCodeState
import ma7moud3ly.fast.compose.ui.ComposeCode
import ma7moud3ly.fast.compose.ui.FastComposeLogs
import ma7moud3ly.fast.compose.ui.FastComposePreview
import ma7moud3ly.fast.compose.ui.rememberComposeCode
import ma7moud3ly.fast.compose.ui.rememberFullRegistry


/** What the demo shows: the code, the preview, or both. */
private enum class ViewMode(val icon: ImageVector, val description: String) {
    CODE(Icons.Default.Code, "Code"),
    SPLIT(Icons.Default.ViewAgenda, "Code and preview"),
    PREVIEW(Icons.Default.Visibility, "Preview"),
}

@Composable
@Preview
fun ComposeApp(code: ComposeCode = rememberComposeCode(sampleCode)) {
    MaterialTheme {
        val state = rememberCodeState(code = sampleCode, language = Language.KOTLIN)
        var fontSize by remember { mutableIntStateOf(DEFAULT_FONT_SIZE) }
        val settings = remember(fontSize) {
            EditorSettings(
                theme = EditorThemes.VS_CODE_LIGHT,
                fontSize = fontSize,
                enableAutocomplete = true,
            )
        }
        val registry = rememberFullRegistry()
        val editorText = state.code
        var mode by remember { mutableStateOf(ViewMode.SPLIT) }
        var runs by remember { mutableIntStateOf(0) }
        val problems = code.diagnostics.size + code.logs.count { it.isError || it.isWarning }
        var showLogs by remember { mutableStateOf(false) }

        LaunchedEffect(editorText) {
            code.update(editorText)
        }
        LaunchedEffect(code.diagnostics, code.errorLines) {
            state.clearHighlights()
            (code.diagnostics.map { it.span.line } + code.errorLines).distinct().forEach {
                state.toggleHighlight(it, 0x40E53935L)
            }
        }
        // The logs open when problems appear and close when they are fixed.
        LaunchedEffect(problems > 0) {
            showLogs = problems > 0
        }

        val editor = @Composable { modifier: Modifier ->
            NemoCodeEditor(state = state, settings = settings, modifier = modifier)
        }
        // A new key restarts the previews, resetting their state and animations.
        val preview = @Composable { modifier: Modifier ->
            key(runs) {
                FastComposePreview(
                    code = code,
                    modifier = modifier.background(Color.White),
                    registry = registry,
                )
            }
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val vertical = maxWidth < 700.dp
                val compact = maxWidth < 480.dp
                val showTitle = maxWidth >= 360.dp
                Column(Modifier.fillMaxSize()) {
                    Toolbar(
                        mode = mode,
                        vertical = vertical,
                        compact = compact,
                        showTitle = showTitle,
                        problems = problems,
                        showLogs = showLogs,
                        onMode = { mode = it },
                        onRefresh = {
                            code.clearLogs()
                            runs++
                        },
                        onShowLogs = { showLogs = it },
                        onFontSize = { step -> fontSize = (fontSize + step).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE) },
                    )
                    HorizontalDivider()
                    // Below the toolbar, keep clear of the navigation bar and the keyboard.
                    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    Column(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(insets)) {
                        val content = Modifier.weight(1f).fillMaxWidth()
                        when {
                            mode == ViewMode.CODE -> editor(content)
                            mode == ViewMode.PREVIEW -> preview(content)
                            vertical -> Column(content) {
                                editor(Modifier.weight(1f).fillMaxWidth())
                                HorizontalDivider()
                                preview(Modifier.weight(1f).fillMaxWidth())
                            }

                            else -> Row(content) {
                                editor(Modifier.weight(1f).fillMaxHeight())
                                VerticalDivider()
                                preview(Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                        if (showLogs) {
                            HorizontalDivider()
                            FastComposeLogs(
                                code = code,
                                modifier = Modifier.fillMaxWidth().height(if (vertical) 140.dp else 180.dp),
                                onLineClick = { line ->
                                    if (mode == ViewMode.PREVIEW) mode = ViewMode.SPLIT
                                    lineStart(state.code, line).let { state.setSelection(it, it) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Title, view mode switch, and buttons to run the previews again, change the editor font size and show the logs. */
@Composable
private fun Toolbar(
    mode: ViewMode,
    vertical: Boolean,
    compact: Boolean,
    showTitle: Boolean,
    problems: Int,
    showLogs: Boolean,
    onMode: (ViewMode) -> Unit,
    onRefresh: () -> Unit,
    onShowLogs: (Boolean) -> Unit,
    onFontSize: (step: Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // On very narrow screens only the buttons are shown.
        if (showTitle) {
            Text(
                "Fast Compose",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        ModeSwitch(mode = mode, vertical = vertical, onMode = onMode)
        // On phones the less used actions move into a menu, so the toolbar fits on one line.
        if (compact) {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(ICON_SIZE))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(Icons.Default.Refresh, "Run again") {
                        menu = false
                        onRefresh()
                    }
                    MenuItem(Icons.Default.TextIncrease, "Larger font") { onFontSize(FONT_STEP) }
                    MenuItem(Icons.Default.TextDecrease, "Smaller font") { onFontSize(-FONT_STEP) }
                }
            }
        } else {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Run again", modifier = Modifier.size(ICON_SIZE))
            }
            IconButton(onClick = { onFontSize(-FONT_STEP) }) {
                Icon(
                    Icons.Default.TextDecrease,
                    contentDescription = "Smaller font",
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
            IconButton(onClick = { onFontSize(FONT_STEP) }) {
                Icon(
                    Icons.Default.TextIncrease,
                    contentDescription = "Larger font",
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
        }
        IconToggleButton(checked = showLogs, onCheckedChange = onShowLogs) {
            BadgedBox(badge = { if (problems > 0) Badge { Text(problems.toString()) } }) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = if (showLogs) "Hide logs" else "Show logs",
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
        }
    }
}

/** A pill of icon tabs that picks the [ViewMode]; the split icon turns sideways when the panes are side by side. */
@Composable
private fun ModeSwitch(mode: ViewMode, vertical: Boolean, onMode: (ViewMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.selectableGroup()
            .clip(CircleShape)
            .background(colors.surfaceContainerHighest)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ViewMode.entries.forEach { value ->
            val selected = mode == value
            Box(
                Modifier.size(width = 36.dp, height = 28.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onMode(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    value.icon,
                    contentDescription = value.description,
                    tint = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    modifier = Modifier.size(ICON_SIZE).rotate(if (value == ViewMode.SPLIT && !vertical) 90f else 0f),
                )
            }
        }
    }
}

/** A toolbar menu item; the font items keep the menu open, so the size can be changed several times in a row. */
@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

private val DEFAULT_FONT_SIZE = if (getPlatform().isAndroid) 8 else 14
private val ICON_SIZE = 18.dp
private const val MIN_FONT_SIZE = 8
private const val MAX_FONT_SIZE = 32
private const val FONT_STEP = 2

/** Offset of the first character of 1-based [line] in [text]. */
private fun lineStart(text: String, line: Int): Int =
    text.lineSequence().take(line - 1).sumOf { it.length + 1 }.coerceAtMost(text.length)
