package client.wexbooks.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import client.wexbooks.data.ReaderPreferences
import client.wexbooks.ui.motion.TextMorph
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Measure the book only at the two endpoint sizes. Animate placement and a soft reveal, not
 * padding: repagination/WebView layout never run once per animation frame. On collapse retain the
 * larger page until its extra lines have actually disappeared behind the moving edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderViewport(
    controlsVisible: Boolean,
    preferences: ReaderPreferences,
    horizontalMargin: Dp,
    normalBottom: Dp,
    contentReady: (IntSize) -> Boolean = { true },
    awaitVisualReady: suspend () -> Unit = {},
    onMotionChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val paper = MaterialTheme.colorScheme.background
    val status = WindowInsets.statusBarsIgnoringVisibility.getTop(density)
    val navigation = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    val cutoutInsets = WindowInsets.displayCutout
    val cutout = cutoutInsets.getTop(density)
    val cutoutLeft = cutoutInsets.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val cutoutRight = cutoutInsets.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val ready by rememberUpdatedState(contentReady)
    val visualReady by rememberUpdatedState(awaitVisualReady)
    val reportMotion by rememberUpdatedState(onMotionChanged)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val side = with(density) { horizontalMargin.roundToPx() }
        val breathingRoom = with(density) { 12.dp.roundToPx() }
        val left = readerSafePadding(side, cutoutLeft, breathingRoom)
        val right = readerSafePadding(side, cutoutRight, breathingRoom)
        val top =
            with(density) {
                if (controlsVisible) 80.dp.toPx() + status
                else
                    readerSafePadding(22.dp.roundToPx(), cutout, breathingRoom).toFloat() +
                        if (preferences.immersive) 0 else (status - cutout).coerceAtLeast(0)
            }
        val bottom =
            with(density) {
                if (controlsVisible) normalBottom.toPx() + navigation
                else 22.dp.toPx() + if (preferences.immersive) 0 else navigation
            }
        val targetHeight = (height - top - bottom).roundToInt().coerceAtLeast(1)
        val targetWidth = (width - left - right).coerceAtLeast(1)
        val y = remember(width, height) { Animatable(top) }
        val reveal = remember(width, height) { Animatable(targetHeight.toFloat()) }
        var measuredHeight by remember(width, height) { mutableIntStateOf(targetHeight) }
        var changing by remember(width, height) { mutableStateOf(false) }
        val reduced = preferences.reduceMotion

        LaunchedEffect(top, targetHeight, width, height, reduced) {
            if (
                y.value == top &&
                    reveal.value == targetHeight.toFloat() &&
                    measuredHeight == targetHeight
            )
                return@LaunchedEffect
            changing = true
            reportMotion(true)
            try {
                if (reduced) {
                    measuredHeight = targetHeight
                    y.snapTo(top)
                    reveal.snapTo(targetHeight.toFloat())
                    return@LaunchedEffect
                }
                val expanding = targetHeight > reveal.value
                if (expanding) measuredHeight = targetHeight
                coroutineScope {
                    launch { y.animateTo(top, tween(320, easing = TextMorph.Easing)) }
                    if (expanding) {
                        // Let the endpoint layout reach the renderer before revealing new ink.
                        repeat(2) { withFrameNanos {} }
                        withTimeoutOrNull(2000) {
                            while (!ready(IntSize(targetWidth, targetHeight))) withFrameNanos {}
                            visualReady()
                        }
                    }
                    reveal.animateTo(targetHeight.toFloat(), tween(320, easing = TextMorph.Easing))
                }
                if (!expanding) {
                    measuredHeight = targetHeight
                    repeat(2) { withFrameNanos {} }
                    withTimeoutOrNull(2000) {
                        while (!ready(IntSize(targetWidth, targetHeight))) withFrameNanos {}
                        visualReady()
                    }
                }
            } finally {
                changing = false
                reportMotion(false)
            }
        }

        Layout(
            modifier =
                Modifier.fillMaxSize().testTag("reader-viewport").semantics {
                    stateDescription = if (changing) "Изменение области чтения" else "Страница"
                },
            content = {
                Box(
                    Modifier.testTag("reader-viewport-content").drawWithContent {
                        val visible = reveal.value.coerceAtLeast(0f)
                        clipRect(bottom = visible) { this@drawWithContent.drawContent() }
                        if (changing && visible < size.height - 1f) {
                            val feather = 18.dp.toPx().coerceAtMost(visible)
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, paper),
                                    startY = visible - feather,
                                    endY = visible,
                                ),
                                topLeft = Offset(0f, visible - feather),
                                size = Size(size.width, feather),
                            )
                        }
                    }
                ) {
                    content()
                }
            },
        ) { measurables, _ ->
            val child = measurables.single().measure(Constraints.fixed(targetWidth, measuredHeight))
            layout(width, height) { child.place(left, y.value.roundToInt()) }
        }
    }
}

/** Physical inset on any edge, including a side camera after rotation. */
internal fun readerSafePadding(preferred: Int, cutout: Int, breathingRoom: Int): Int =
    maxOf(preferred, if (cutout > 0) cutout + breathingRoom else 0)
