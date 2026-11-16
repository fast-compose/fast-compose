package ma7moud3ly.fast.compose.ui

import kotlinx.coroutines.test.runTest
import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.interpreter.SourceFile
import ma7moud3ly.fast.compose.interpreter.SourceLoader
import ma7moud3ly.fast.compose.interpreter.SourceProvider
import ma7moud3ly.fast.compose.parser.Diagnostic
import ma7moud3ly.fast.compose.parser.KotlinParser
import ma7moud3ly.fast.compose.parser.ast.Program
import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewsTest {

    private val registry = Registry().apply { install(PreviewConstants) }

    private fun parse(code: String, path: String? = null): Program {
        val result = KotlinParser.parse(code.trimIndent(), path)
        assertEquals(emptyList<Diagnostic>(), result.diagnostics)
        return result.program
    }

    @Test
    fun multiPreviewAnnotations() {
        val program = parse(
            """
            @Preview(name = "phone", widthDp = 411)
            @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
            annotation class DevicePreviews

            @DevicePreviews
            @Composable
            fun Screen() {}

            @PreviewLightDark
            @Composable
            fun Card() {}

            @Preview
            @Composable
            fun Plain() {}
            """,
        )
        val previews = Interpreter(program, registry).previews(program)
        assertEquals(
            listOf("Screen - phone", "Screen - DevicePreviews", "Card - Light", "Card - Dark", "Plain"),
            previews.map { it.config.name },
        )
        assertEquals(411, previews[0].config.widthDp)
        assertEquals(true, previews[1].config.darkTheme)
        assertEquals(true, previews[3].config.darkTheme)
    }

    @Test
    fun multiPreviewAnnotationsFromOtherFiles() = runTest {
        val annotations = SourceFile(
            "ui/Previews.kt",
            "package app.ui\n\n@Preview(name = \"Light\")\n@Preview(name = \"Dark\", uiMode = Configuration.UI_MODE_NIGHT_YES)\nannotation class ThemePreviews",
        )
        val main = parse(
            """
            package app.screens

            import app.ui.ThemePreviews

            @ThemePreviews
            @Composable
            fun Home() {}
            """,
            "screens/Home.kt",
        )
        val loaded = SourceLoader(SourceProvider { packageName, name ->
            annotations.takeIf { packageName == "app.ui" && name == "ThemePreviews" }
        }).load(main)
        val previews = Interpreter(main, registry, dependencies = loaded.dependencies).previews(main)
        assertEquals(listOf("Home - Light", "Home - Dark"), previews.map { it.config.name })
    }

    @Test
    fun previewParameters() {
        val program = parse(
            """
            class Names : PreviewParameterProvider<String> {
                override val values = sequenceOf("ana", "bo", "cy")
            }

            @Preview
            @Composable
            fun Greeting(@PreviewParameter(Names::class, limit = 2) name: String) {}
            """,
        )
        val previews = Interpreter(program, registry).previews(program)
        assertEquals(listOf("Greeting (1)", "Greeting (2)"), previews.map { it.config.name })
        assertEquals(listOf(listOf<Any?>("ana"), listOf<Any?>("bo")), previews.map { it.arguments })
    }
}
