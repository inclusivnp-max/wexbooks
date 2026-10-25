package client.wexbooks.ui.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import client.wexbooks.reader.core.ReaderPosition
import client.wexbooks.reader.nativeengine.*

/** Telegram-like identity-preserving text substitution, with one accessibility value. */
@Composable
fun WexAnimatedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val width = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx().toInt() }
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize)
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val layout =
        remember(text, style, density, direction) {
            measurer.measure(
                AnnotatedString(text),
                style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    val page =
        remember(text, layout) {
            val anchor = ReaderPosition(text)
            NativePage(
                anchor,
                anchor,
                listOf(NativeFragment(0, layout, 0, 0, 0f, layout.size.height.toFloat())),
                false,
                false,
            )
        }
    val key =
        LayoutKey(
            width,
            layout.size.height,
            fontSize.value.toInt(),
            density.density,
            density.fontScale,
            direction,
        )
    val motion = rememberNativeTextMorph(page, key, LocalReducedMotion.current, forward = true)
    Canvas(
        modifier
            .size(
                with(density) { layout.size.width.toDp() },
                with(density) { layout.size.height.toDp() },
            )
            .semantics { this.text = AnnotatedString(text) }
    ) {
        if (!motion.run { draw(color) }) drawText(layout, color = color)
    }
}
