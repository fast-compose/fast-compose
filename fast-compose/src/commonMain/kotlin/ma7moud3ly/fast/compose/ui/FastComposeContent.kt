package ma7moud3ly.fast.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ma7moud3ly.fast.compose.bindings.resources.LocalResourceProvider
import ma7moud3ly.fast.compose.bindings.resources.ResourceProvider
import ma7moud3ly.fast.compose.interpreter.ErrorBox
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.parser.ast.FunDecl
import ma7moud3ly.fast.compose.parser.ast.Program

/**
 * Renders one composable function of [code] as it is, without preview frames, labels or `@Preview` settings.
 * [entry] names a top-level `@Composable` function that takes no arguments; when null, the first `@Preview`
 * function is used. Images and strings from `R`/`Res` come from [resources]. Runtime errors are shown where they occur
 * and collected in [ComposeCode.logs].
 */
@Composable
public fun FastComposeContent(
    code: ComposeCode,
    modifier: Modifier = Modifier,
    entry: String? = null,
    registry: Registry = rememberFullRegistry(),
    resources: ResourceProvider = LocalResourceProvider.current,
) {
    val program = code.program
    Box(modifier) {
        if (program == null) {
            ErrorBox("Fix the errors to see the content")
        } else {
            val interpreter = rememberInterpreter(code, program, registry)
            val function = remember(program, entry) { program.entry(entry) }
            when {
                function != null -> key(function) {
                    CompositionLocalProvider(LocalResourceProvider provides resources) { interpreter.Render(function) }
                }
                entry != null -> ErrorBox("No top-level @Composable function named '$entry'")
                else -> ErrorBox("Add @Preview to a @Composable function, or pass the entry name")
            }
        }
    }
}

/** The composable named [name], or the first `@Preview` function when [name] is null. */
private fun Program.entry(name: String?): FunDecl? =
    if (name == null) {
        previews.firstOrNull()?.first
    } else {
        functions.firstOrNull { fn -> fn.name == name && fn.receiver == null && fn.annotations.any { it.name == "Composable" } }
    }
