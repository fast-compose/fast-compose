package ma7moud3ly.fast.compose.interpreter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * Inline marker for a runtime error, shown where the failing code would render. It takes about the space of a small
 * component, so siblings in a `Row` keep theirs; long messages are cut, and the full ones are in the logs. The
 * background is opaque, so the top one stays readable where several are placed at the same spot, e.g. in a `Box`.
 */
@Composable
public fun ErrorBox(message: String) {
    BasicText(
        text = "⚠ $message",
        style = TextStyle(color = Color(0xFFB00020), fontSize = 12.sp),
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        // Drawn above later siblings, e.g. a full-size Scaffold composed after the error in the same Box.
        modifier = Modifier.zIndex(1f).widthIn(max = 240.dp).background(Color(0xFFFCE8EC)).padding(4.dp),
    )
}
