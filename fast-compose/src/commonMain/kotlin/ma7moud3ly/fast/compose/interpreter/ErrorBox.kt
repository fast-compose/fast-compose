package ma7moud3ly.fast.compose.interpreter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Inline marker for a runtime error, shown where the failing code would render. */
@Composable
public fun ErrorBox(message: String) {
    BasicText(
        text = "⚠ $message",
        style = TextStyle(color = Color(0xFFB00020), fontSize = 12.sp),
        modifier = Modifier.background(Color(0x1FB00020)).padding(4.dp),
    )
}
