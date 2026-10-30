package ma7moud3ly.fast.compose.ui

import androidx.compose.ui.graphics.Color
import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.parser.Diagnostic
import ma7moud3ly.fast.compose.parser.KotlinParser
import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewConfigTest {

    @Test
    fun previewConfigs() {
        val code = """
            @Preview(name = "Dark", widthDp = 200, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, backgroundColor = 0xFF000000)
            @Preview(device = Devices.PIXEL_4, fontScale = 1.5f)
            @Composable
            fun A() {}
        """.trimIndent()
        val result = KotlinParser.parse(code)
        assertEquals(emptyList<Diagnostic>(), result.diagnostics)
        val interpreter = Interpreter(result.program, Registry().apply { install(PreviewConstants) })
        val configs = result.program.previews.map { (function, annotation) -> interpreter.previewConfig(function, annotation) }
        assertEquals(
            PreviewConfig(name = "Dark", widthDp = 200, showBackground = true, backgroundColor = Color(0xFF000000), darkTheme = true),
            configs[0],
        )
        assertEquals(PreviewConfig(name = "A", widthDp = 393, heightDp = 851, fontScale = 1.5f), configs[1])
    }
}
