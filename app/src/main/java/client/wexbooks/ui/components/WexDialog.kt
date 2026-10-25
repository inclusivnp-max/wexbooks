package client.wexbooks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import client.wexbooks.ui.glass.LocalReducedTransparency
import client.wexbooks.ui.glass.wexGlass
import client.wexbooks.ui.motion.LocalReducedMotion
import client.wexbooks.ui.motion.WexMotion
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** App-owned dialog chrome: no AlertDialog surface, default buttons or floating field outline. */
@Composable
fun WexDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmLabel: String,
    onConfirm: suspend () -> Boolean = { true },
    dismissLabel: String? = null,
    confirmTag: String = "dialog-confirm",
    content: @Composable ColumnScope.(Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val reduceTransparency = LocalReducedTransparency.current
    val dark = colors.background.luminance() < 0.5f
    val backdrop = rememberCanvasBackdrop { drawRect(colors.background) }
    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, WexMotion.response(reduced)) }
    fun dismiss() {
        if (busy || closing) return
        closing = true
        scope.launch {
            entrance.animateTo(0f, WexMotion.response(reduced))
            onDismissRequest()
        }
    }
    Dialog(
        onDismissRequest = ::dismiss,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            val bodyMaxHeight = (maxHeight - 210.dp).coerceAtLeast(90.dp)
            Column(
                Modifier.widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = entrance.value
                        scaleX = 0.96f + entrance.value * 0.04f
                        scaleY = scaleX
                        translationY = (1f - entrance.value) * 16.dp.toPx()
                    }
                    .wexGlass(backdrop, dark, reduceTransparency, 32.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .padding(24.dp)
                    .testTag("wex-dialog"),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(
                    Modifier.size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.primary.copy(alpha = 0.65f))
                )
                Text(
                    title,
                    color = colors.onSurface,
                    fontSize = 26.sp,
                    letterSpacing = (-0.7).sp,
                    lineHeight = 30.sp,
                )
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = bodyMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    content(!busy && !closing)
                    if (error)
                        Text(
                            "Изменение пока не сохранилось. Попробуйте ещё раз.",
                            color = colors.error,
                            modifier = Modifier.testTag("dialog-error"),
                            fontSize = 13.sp,
                        )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (dismissLabel != null)
                        WexDialogButton(
                            dismissLabel,
                            Modifier.weight(1f),
                            enabled = !busy && !closing,
                            primary = false,
                            onClick = ::dismiss,
                        )
                    WexDialogButton(
                        if (busy) "Сохраняем…" else confirmLabel,
                        Modifier.weight(1f).testTag(confirmTag),
                        enabled = !busy && !closing,
                        primary = true,
                        busy = busy,
                    ) {
                        busy = true
                        error = false
                        scope.launch {
                            try {
                                val done = onConfirm()
                                busy = false
                                if (done) dismiss()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.e("WexDialog", "Dialog action failed", e)
                                busy = false
                                error = true
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WexDialogButton(
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    primary: Boolean,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed && enabled) 0.97f else 1f,
            WexMotion.response(LocalReducedMotion.current),
            label = "dialog press",
        )
    val ink = if (primary) colors.onPrimary else colors.onSurface
    Row(
        modifier
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(if (primary) colors.primary else colors.onSurface.copy(alpha = 0.055f))
            .border(
                0.7.dp,
                if (primary) Color.Transparent else colors.onSurface.copy(alpha = 0.09f),
                RoundedCornerShape(18.dp),
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = source,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), color = ink, strokeWidth = 1.5.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = ink.copy(alpha = if (enabled || busy) 1f else 0.45f), fontSize = 15.sp)
    }
}
