package ma7moud3ly.fast.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma7moud3ly.fast.compose.parser.Diagnostic

/**
 * Shows the parse errors of [code] (when [showDiagnostics] is true) followed by its logs, scrolled to the newest line.
 * Text is selectable. Clicking an error with a source line of the previewed file calls [onLineClick] with that line, e.g. to
 * move the editor there.
 * Colors come from [MaterialTheme].
 */
@Composable
public fun FastComposeLogs(
    code: ComposeCode,
    modifier: Modifier = Modifier,
    title: String = "Logs",
    showDiagnostics: Boolean = true,
    onLineClick: ((line: Int) -> Unit)? = null,
) {
    val diagnostics = if (showDiagnostics) {
        code.diagnostics.map {
            val isError = it.severity == Diagnostic.Severity.ERROR
            LogLine("Line ${it.span.line}:${it.span.column}: ${it.message}", isError = isError, line = it.span.line, isWarning = !isError)
        }
    } else {
        emptyList()
    }
    val entries = diagnostics + code.logs
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }
    val colors = MaterialTheme.colorScheme
    Column(modifier.background(colors.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { code.clearLogs() }) { Text("Clear", fontSize = 12.sp) }
        }
        SelectionContainer {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                items(entries) { entry ->
                    val line = entry.line.takeIf { entry.file == null }
                    Text(
                        text = entry.text,
                        color = when {
                            entry.isError -> colors.error
                            entry.isWarning -> colors.tertiary
                            else -> colors.onSurface
                        },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = if (line != null && onLineClick != null) Modifier.clickable { onLineClick(line) } else Modifier,
                    )
                }
            }
        }
    }
}
