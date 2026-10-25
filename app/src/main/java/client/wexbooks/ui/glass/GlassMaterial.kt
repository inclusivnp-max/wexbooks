package client.wexbooks.ui.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

enum class GlassRendering {
    Solid,
    Frosted,
    Liquid,
}

fun glassRenderingFor(sdkInt: Int, reduceTransparency: Boolean): GlassRendering =
    when {
        reduceTransparency || sdkInt < 31 -> GlassRendering.Solid
        sdkInt < 33 -> GlassRendering.Frosted
        else -> GlassRendering.Liquid
    }

/** A single optics layer for all app controls; book text never uses this modifier. */
@Composable
fun Modifier.wexGlass(
    backdrop: Backdrop,
    darkTheme: Boolean,
    reduceTransparency: Boolean,
    cornerRadius: Dp = 28.dp,
): Modifier {
    val rendering = glassRenderingFor(Build.VERSION.SDK_INT, reduceTransparency)
    val shape = RoundedCornerShape(cornerRadius)
    val base = if (darkTheme) Color(0xFF242D37) else Color(0xFFFEFDFB)
    val material =
        if (rendering == GlassRendering.Solid) {
            Modifier.background(base, shape)
        } else {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    if (rendering == GlassRendering.Liquid) {
                        lens(12.dp.toPx(), 24.dp.toPx())
                    }
                },
                onDrawSurface = {
                    drawRect(base.copy(alpha = if (darkTheme) 0.72f else 0.62f))
                },
            )
        }
    return this.shadow(
            16.dp,
            shape,
            ambientColor = Color.Black.copy(alpha = 0.08f),
            spotColor = Color.Black.copy(alpha = 0.08f),
        )
        .then(material)
        .border(0.8.dp, Color.White.copy(alpha = if (darkTheme) 0.15f else 0.8f), shape)
}
