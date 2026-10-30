package ma7moud3ly.fast.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import javax.imageio.ImageIO
import ma7moud3ly.fast.compose.ui.rememberComposeCode

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val code = rememberComposeCode(sampleCode)
    // Exceptions thrown while Compose measures or draws a preview are logged instead of closing the window.
    val crashHandler = WindowExceptionHandlerFactory { WindowExceptionHandler { code.reportCrash(it) } }
    val icon = remember { Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use { ImageIO.read(it).toPainter() } }
    CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides crashHandler) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Fast Compose",
            icon = icon,
            state = rememberWindowState(placement = WindowPlacement.Maximized),
        ) {
            ComposeApp(code)
        }
    }
}
