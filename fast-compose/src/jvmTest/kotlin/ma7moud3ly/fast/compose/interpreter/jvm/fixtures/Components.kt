package ma7moud3ly.fast.compose.interpreter.jvm.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What the library composables below did, in order, for JarResolverTest. */
object Recorded {
    val lines = mutableListOf<String>()
}

@Composable
fun Greeting(name: String, punctuation: String = "!") {
    Recorded.lines += "Hello $name$punctuation"
}

@Composable
fun Card(title: String, content: @Composable () -> Unit) {
    Recorded.lines += "[$title]"
    content()
}

/** A scope, like `RowScope`, that the content of [Lane] runs in. */
class LaneScope {
    fun mark(text: String) {
        Recorded.lines += "lane $text"
    }
}

@Composable
fun Lane(content: @Composable LaneScope.() -> Unit) {
    LaneScope().content()
}

/** Value class parameters: compiled as `long` and `float`, with a mangled name. */
@Composable
fun Swatch(color: Color, size: Dp = 4.dp) {
    Recorded.lines += "swatch ${color == Color.Red} $size"
}

@Composable
fun Counter(onCount: (Int) -> Unit) {
    SideEffect { onCount(3) }
}

/** Shares its name with the `Box` binding: imported from here, this one runs. */
@Composable
fun Box(label: String) {
    Recorded.lines += "box $label"
}
