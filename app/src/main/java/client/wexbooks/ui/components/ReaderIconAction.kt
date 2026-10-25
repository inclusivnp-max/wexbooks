package client.wexbooks.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Toolbar actions own their surface/disabled/selected colors, independent of their parent. */
@Composable
fun ReaderIconAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick,
        modifier = modifier,
        enabled = enabled,
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = if (selected) colors.primary else colors.onSurface,
                disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
            ),
    ) {
        Icon(icon, description)
    }
}
