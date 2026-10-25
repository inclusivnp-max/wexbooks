package client.wexbooks.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import client.wexbooks.reader.QuoteDraft
import client.wexbooks.reader.core.ReaderPosition
import client.wexbooks.reader.nativeengine.*
import kotlin.math.roundToInt

/** Character coordinates survive font changes and never store screen pixels. */
data class NativeSelection(val start: ReaderPosition, val end: ReaderPosition) {
    fun range(block: Int, length: Int): IntRange? {
        if (block !in start.block..end.block) return null
        val left = if (block == start.block) start.offset.coerceIn(0, length) else 0
        val right = if (block == end.block) end.offset.coerceIn(0, length) else length
        return if (left < right) left until right else null
    }

    fun draft(document: NativeDocument): QuoteDraft {
        val text =
            (start.block..end.block)
                .mapNotNull { block ->
                    val item = document.blocks[block]
                    if (item.kind == BlockKind.Image) null
                    else
                        range(block, item.text.length)?.let {
                            item.text.substring(it.first, it.last + 1)
                        }
                }
                .joinToString("\n\n")
        return QuoteDraft(start, end.block, end.offset, text)
    }
}

private fun NativePage.hit(
    point: Offset,
    document: NativeDocument,
    clamp: Boolean = false,
): ReaderPosition? {
    val fragment =
        fragments.firstOrNull { point.y >= it.top && point.y <= it.top + it.height }
            ?: (if (clamp)
                fragments.minByOrNull { kotlin.math.abs(point.y - (it.top + it.height / 2)) }
            else null)
            ?: return null
    val local =
        Offset(
            point.x,
            (point.y - fragment.top).coerceIn(0f, fragment.height - 1) +
                fragment.layout.getLineTop(fragment.firstLine),
        )
    val line =
        fragment.layout
            .getLineForVerticalPosition(local.y)
            .coerceIn(fragment.firstLine, fragment.lastLine)
    if (!clamp && point.x !in fragment.layout.getLineLeft(line)..fragment.layout.getLineRight(line))
        return null
    val char =
        fragment.layout
            .getOffsetForPosition(local)
            .coerceIn(
                fragment.layout.getLineStart(fragment.firstLine),
                fragment.layout.getLineEnd(fragment.lastLine, true),
            )
    return document.position(fragment.block, char)
}

@Composable
fun NativePageContent(
    engine: NativeReaderSession,
    page: NativePage,
    selection: NativeSelection?,
    saved: List<NativeSelection>,
    onSelection: (NativeSelection?) -> Unit,
    onLink: (String) -> Unit,
    onImage: (String) -> Unit,
    motion: client.wexbooks.ui.motion.NativeTextMorphState? = null,
    onShowControls: () -> Unit = {},
    onPageTap: (Float) -> Unit = {},
    onPageSwipe: (Boolean) -> Unit = {},
) {
    val document = engine.document
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val visibleText =
        remember(page) {
            page.fragments.joinToString("\n\n") {
                it.layout.layoutInput.text.text.substring(
                    it.layout.getLineStart(it.firstLine),
                    it.layout.getLineEnd(it.lastLine, true),
                )
            }
        }
    val currentSelection by rememberUpdatedState(selection)
    val select by rememberUpdatedState(onSelection)
    val link by rememberUpdatedState(onLink)
    val tap by rememberUpdatedState(onPageTap)
    val showControls by rememberUpdatedState(onShowControls)
    val swipe by rememberUpdatedState(onPageSwipe)
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize()
                .testTag("native-page")
                .semantics {
                    text = AnnotatedString(visibleText)
                    stateDescription =
                        if (motion?.isRunning == true) "Переход текста" else "Страница"
                    customActions = buildList {
                        add(
                            CustomAccessibilityAction("Показать панели чтения") {
                                showControls()
                                true
                            }
                        )
                        page.fragments.firstOrNull()?.let { first ->
                            add(
                                CustomAccessibilityAction("Выделить видимый абзац") {
                                    select(
                                        NativeSelection(
                                            document.position(
                                                first.block,
                                                first.layout.getLineStart(first.firstLine),
                                            ),
                                            document.position(
                                                first.block,
                                                first.layout.getLineEnd(first.lastLine, true),
                                            ),
                                        )
                                    )
                                    true
                                }
                            )
                        }
                        page.fragments
                            .flatMap { document.blocks[it.block].marks }
                            .filter { it.kind == MarkKind.Link }
                            .distinctBy { it.target }
                            .forEach { mark ->
                                add(
                                    CustomAccessibilityAction("Открыть сноску ${mark.target}") {
                                        mark.target?.let(link)
                                        true
                                    }
                                )
                            }
                    }
                }
                .pointerInput(page) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var pointer = down
                        var swiping = false
                        var cancelled = false
                        val completed =
                            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val next = event.changes.firstOrNull { it.id == down.id }
                                    if (next == null || next.isConsumed || event.changes.size > 1) {
                                        cancelled = true
                                        break
                                    }
                                    pointer = next
                                    if (!pointer.pressed) break
                                    if (
                                        (pointer.position - down.position).getDistance() >
                                            viewConfiguration.touchSlop
                                    ) {
                                        swiping = true
                                        break
                                    }
                                }
                                true
                            }
                        val hold = if (completed == null && !cancelled) pointer else null
                        if (hold == null) {
                            if (swiping && !cancelled) {
                                pointer.consume()
                                while (pointer.pressed) {
                                    val event = awaitPointerEvent()
                                    val next =
                                        event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (next.isConsumed || event.changes.size > 1) {
                                        cancelled = true
                                        break
                                    }
                                    pointer = next
                                    pointer.consume()
                                }
                            }
                            val up = pointer.takeUnless { it.pressed || cancelled }
                            val delta = pointer.position - down.position
                            if (
                                swiping &&
                                    up != null &&
                                    kotlin.math.abs(delta.x) > 48.dp.toPx() &&
                                    kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y) * 1.4f
                            ) {
                                if (currentSelection == null) swipe(delta.x < 0)
                            } else if (
                                up != null &&
                                    up.changedToUp() &&
                                    (up.position - down.position).getDistance() <=
                                        viewConfiguration.touchSlop
                            ) {
                                if (currentSelection != null) select(null)
                                else {
                                    val target =
                                        page.hit(up.position, document)?.let { anchor ->
                                            document.blocks[anchor.block]
                                                .marks
                                                .firstOrNull {
                                                    it.kind == MarkKind.Link &&
                                                        anchor.offset in it.start until it.end
                                                }
                                                ?.target
                                        }
                                    if (target != null) link(target)
                                    else
                                        tap(
                                            (up.position.x / size.width.coerceAtLeast(1)).coerceIn(
                                                0f,
                                                1f,
                                            )
                                        )
                                }
                                up.consume()
                            }
                        } else {
                            val initial =
                                page.hit(hold.position, document)?.let { anchor ->
                                    val fragment = page.fragments.first { it.block == anchor.block }
                                    val word =
                                        fragment.layout.getWordBoundary(
                                            anchor.offset.coerceAtMost(
                                                (document.blocks[anchor.block].text.length - 1)
                                                    .coerceAtLeast(0)
                                            )
                                        )
                                    NativeSelection(
                                        document.position(
                                            anchor.block,
                                            word.start.coerceAtLeast(
                                                fragment.layout.getLineStart(fragment.firstLine)
                                            ),
                                        ),
                                        document.position(
                                            anchor.block,
                                            word.end.coerceAtMost(
                                                fragment.layout.getLineEnd(fragment.lastLine, true)
                                            ),
                                        ),
                                    )
                                }
                            if (initial != null) {
                                select(initial)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            hold.consume()
                            drag(hold.id) { change ->
                                val target = page.hit(change.position, document, clamp = true)
                                if (initial != null && target != null) {
                                    val before =
                                        target.block < initial.start.block ||
                                            target.block == initial.start.block &&
                                                target.offset < initial.start.offset
                                    select(
                                        if (before) NativeSelection(target, initial.end)
                                        else
                                            NativeSelection(
                                                initial.start,
                                                if (
                                                    target.block == initial.end.block &&
                                                        target.offset < initial.end.offset
                                                )
                                                    initial.end
                                                else target,
                                            )
                                    )
                                }
                                change.consume()
                            }
                            currentEvent.changes.forEach { if (it.changedToUp()) it.consume() }
                        }
                    }
                }
        ) {
            page.fragments.forEach { fragment ->
                clipRect(top = fragment.top, bottom = fragment.top + fragment.height) {
                    translate(top = fragment.top - fragment.layout.getLineTop(fragment.firstLine)) {
                        fun highlight(range: NativeSelection, active: Boolean) {
                            range
                                .range(fragment.block, fragment.layout.layoutInput.text.length)
                                ?.let {
                                    drawPath(
                                        fragment.layout.getPathForRange(it.first, it.last + 1),
                                        colors.primary.copy(alpha = if (active) 0.30f else 0.14f),
                                    )
                                }
                        }
                        saved.forEach { highlight(it, false) }
                        selection?.let { highlight(it, true) }
                    }
                }
            }
            val animated = motion?.run { draw(colors.onBackground) } ?: false
            if (!animated)
                page.fragments.forEach { fragment ->
                    clipRect(top = fragment.top, bottom = fragment.top + fragment.height) {
                        drawText(
                            fragment.layout,
                            color = colors.onBackground,
                            topLeft =
                                Offset(
                                    0f,
                                    fragment.top - fragment.layout.getLineTop(fragment.firstLine),
                                ),
                        )
                    }
                }
        }
        page.images.forEach { image ->
            NativeIllustration(
                engine,
                image.id,
                Modifier.offset { IntOffset(image.left.roundToInt(), image.top.roundToInt()) }
                    .size(
                        with(density) { image.width.toDp() },
                        with(density) { image.height.toDp() },
                    )
                    .clickable { onImage(image.id) }
                    .testTag("native-image-${image.id}"),
            )
        }
    }
}

@Composable
private fun NativeIllustration(
    engine: NativeReaderSession,
    id: String,
    modifier: Modifier = Modifier,
) {
    val result by
        produceState<Pair<Boolean, android.graphics.Bitmap?>>(false to null, engine, id) {
            value = true to engine.image(id)
        }
    val bitmap = result.second
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null)
            Image(
                bitmap.asImageBitmap(),
                contentDescription = "Иллюстрация",
                modifier = Modifier.fillMaxSize(),
            )
        else
            Text(
                when {
                    id !in engine.document.images -> "Иллюстрация отсутствует в файле"
                    !result.first -> "Загружаем иллюстрацию…"
                    else -> "Иллюстрация повреждена"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
fun NativeImageViewer(engine: NativeReaderSession, id: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ReadingDialogWindowEffects()
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .testTag("image-viewer")
        ) {
            NativeIllustration(
                engine,
                id,
                Modifier.fillMaxSize()
                    .padding(20.dp)
                    .onSizeChanged { viewport = it }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    }
                    .transformable(
                        rememberTransformableState { centroid, zoom, offset, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            val x = viewport.width * (newScale - 1) / 2f
                            val y = viewport.height * (newScale - 1) / 2f
                            val ratio = newScale / scale
                            val focus =
                                if (centroid.x.isFinite() && centroid.y.isFinite())
                                    centroid - Offset(viewport.width / 2f, viewport.height / 2f)
                                else Offset.Zero
                            val moved = pan * ratio + focus * (1f - ratio) + offset
                            pan = Offset(moved.x.coerceIn(-x, x), moved.y.coerceIn(-y, y))
                            scale = newScale
                        }
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = if (scale > 1f) 1f else 2f
                                pan = Offset.Zero
                            }
                        )
                    },
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Text("Закрыть")
            }
            TextButton(
                onClick = {
                    scale = 1f
                    pan = Offset.Zero
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            ) {
                Text("Сбросить масштаб")
            }
        }
    }
}
