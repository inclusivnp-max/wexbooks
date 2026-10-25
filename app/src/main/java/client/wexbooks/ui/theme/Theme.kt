package client.wexbooks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
    lightColorScheme(
        primary = Lake,
        onPrimary = Color.White,
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = Color(0xFFE9EAE5),
        onSurfaceVariant = MutedInk,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFABD3D9),
        onPrimary = Midnight,
        background = Midnight,
        onBackground = Color(0xFFF1EEE8),
        surface = NightPaper,
        onSurface = Color(0xFFF1EEE8),
        surfaceVariant = Color(0xFF2C3742),
        onSurfaceVariant = Color(0xFFA8B0BB),
    )

@Composable
fun WexBooksTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
