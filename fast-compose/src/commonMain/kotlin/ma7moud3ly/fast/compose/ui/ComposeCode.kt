package ma7moud3ly.fast.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import ma7moud3ly.fast.compose.interpreter.RuntimeError
import ma7moud3ly.fast.compose.interpreter.SourceLoader
import ma7moud3ly.fast.compose.interpreter.SourceProvider
import ma7moud3ly.fast.compose.parser.Diagnostic
import ma7moud3ly.fast.compose.parser.KotlinParser
import ma7moud3ly.fast.compose.parser.ast.Program
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * One line of preview output: a `println` line, or a runtime error at source [line] when known. [file] is the path of
 * another project file the line is about; null for the previewed file.
 */
public data class LogLine(
    val text: String,
    val isError: Boolean = false,
    val line: Int? = null,
    val isWarning: Boolean = false,
    val file: String? = null,
)

/**
 * Kotlin Compose source code and its parsed form, for [FastComposePreview] and [BasicComposePreview].
 * Call [update] with new source; it is parsed in the background after a short pause in typing.
 * [program] keeps the last version without parse errors, so previews do not disappear while code is being edited.
 *
 * With [sources], the code may use declarations from other files of a project: after each parse the files it reaches
 * through its imports and package are loaded into [dependencies]. [path] is the file's own path in that project.
 * Without [sources], imports are ignored and the code must declare everything it uses.
 */
@Stable
public class ComposeCode internal constructor(
    initialSource: String,
    private val scope: CoroutineScope,
    public val path: String? = null,
    sources: SourceProvider? = null,
) {

    public var source: String by mutableStateOf(initialSource)
        private set

    /** Last parsed program without errors, or null if the code has never parsed cleanly. */
    public var program: Program? by mutableStateOf(null)
        private set

    /** Parse errors of the current [source]. */
    public var diagnostics: List<Diagnostic> by mutableStateOf(emptyList())
        private set

    /** Other project files that [program] uses, loaded through the [SourceProvider]; empty without one. */
    public var dependencies: List<Program> by mutableStateOf(emptyList())
        private set

    /** True while the files of the first [program] are being loaded. */
    public var isLoading: Boolean by mutableStateOf(sources != null)
        private set

    private val loader = sources?.let { SourceLoader(it) }
    private var reloads by mutableStateOf(0)
    private var parsedText: String? = null
    private var parsed: Pair<Program, List<Diagnostic>>? = null

    /** `println` output and runtime errors of the running previews, oldest first. Errors are cleared when [program] changes. */
    public val logs: SnapshotStateList<LogLine> = mutableStateListOf<LogLine>()

    /** Source lines with runtime errors since [program] last changed. */
    public var errorLines: Set<Int> by mutableStateOf(emptySet())
        private set

    init {
        val result = KotlinParser.parse(initialSource, path)
        parsedText = initialSource
        parsed = result.program to result.diagnostics
        diagnostics = result.diagnostics
        if (loader == null) accept(result.program, emptyList(), emptyList())
    }

    public fun update(newSource: String) {
        source = newSource
    }

    /** Loads [dependencies] again, e.g. after another project file changed. Does nothing without a [SourceProvider]. */
    public fun reloadSources() {
        if (loader != null) reloads++
    }

    public fun clearLogs() {
        pendingLogs.clear()
        logs.clear()
        errorLines = emptySet()
    }

    /** Lines logged since the last [flushLogs]; only the newest [MAX_LINES] are kept. */
    private val pendingLogs = ArrayList<LogLine>()

    /**
     * Adds a log line after the current composition; a line equal to one of the recent lines is skipped.
     * Lines logged together, e.g. by `println` in a loop, are added in one batch.
     */
    internal fun log(text: String, isError: Boolean = false, line: Int? = null, isWarning: Boolean = false, file: String? = null) {
        val schedule = pendingLogs.isEmpty()
        pendingLogs += LogLine(text, isError, line, isWarning, file)
        if (pendingLogs.size > MAX_LINES) pendingLogs.removeAt(0)
        if (schedule) scope.launch { flushLogs() }
    }

    private fun flushLogs() {
        val entries = pendingLogs.toList()
        pendingLogs.clear()
        val newErrorLines = entries.mapNotNullTo(mutableSetOf()) { if (it.isError && it.file == null) it.line else null }
        if (newErrorLines.isNotEmpty()) errorLines = errorLines + newErrorLines
        for (entry in entries) {
            if (entry in logs.takeLast(RECENT_LINES)) continue
            logs += entry
        }
        if (logs.size > MAX_LINES) logs.removeRange(0, logs.size - MAX_LINES)
    }

    internal fun logError(error: RuntimeError) =
        log(error.toString(), isError = !error.isWarning, line = error.line, isWarning = error.isWarning, file = error.file)

    /** Logs an exception thrown outside the interpreter, e.g. by a Compose component while measuring or drawing a preview. */
    public fun reportCrash(error: Throwable) {
        val name = error::class.simpleName ?: "Error"
        log("Preview crashed: " + (error.message?.let { "$name: $it" } ?: name), isError = true)
    }

    internal suspend fun parseChanges() {
        snapshotFlow { source to reloads }.drop(if (loader == null) 1 else 0).collectLatest { (text, _) ->
            val current = parsed.takeIf { text == parsedText } ?: run {
                delay(PARSE_DELAY_MS.milliseconds)
                val result = withContext(Dispatchers.Default) { KotlinParser.parse(text, path) }
                parsedText = text
                (result.program to result.diagnostics).also { parsed = it }
            }
            val (newProgram, errors) = current
            diagnostics = errors
            if (errors.any { it.severity == Diagnostic.Severity.ERROR }) {
                isLoading = false
                return@collectLatest
            }
            if (loader == null) return@collectLatest accept(newProgram, emptyList(), emptyList())
            val loaded = withContext(Dispatchers.Default) { loader.load(newProgram) }
            val problems = loaded.missing.map { LogLine(it, isWarning = true) } +
                loaded.diagnostics.flatMap { (file, fileErrors) ->
                    fileErrors.map { LogLine("${file.substringAfterLast('/')}:${it.span.line}: ${it.message}", isError = true, line = it.span.line, file = file) }
                }
            accept(newProgram, loaded.dependencies, problems)
            isLoading = false
        }
    }

    private fun accept(newProgram: Program, newDependencies: List<Program>, problems: List<LogLine>) {
        program = newProgram
        dependencies = newDependencies
        logs.removeAll { it.isError || it.isWarning }
        logs += problems
        errorLines = emptySet()
    }

    private companion object {
        const val PARSE_DELAY_MS = 150L
        const val RECENT_LINES = 10
        const val MAX_LINES = 500
    }
}

/**
 * Remembers a [ComposeCode] for [initialSource] and keeps it parsed as it changes. Pass [sources] to let the code use
 * declarations from other files of a project, and [path] to identify its own file there.
 */
@Composable
public fun rememberComposeCode(initialSource: String, path: String? = null, sources: SourceProvider? = null): ComposeCode {
    val scope = rememberCoroutineScope()
    val code = remember { ComposeCode(initialSource, scope, path, sources) }
    LaunchedEffect(code) { code.parseChanges() }
    return code
}
