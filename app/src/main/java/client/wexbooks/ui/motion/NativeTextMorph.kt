package client.wexbooks.ui.motion

import android.icu.text.BreakIterator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.*
import client.wexbooks.reader.nativeengine.LayoutKey
import client.wexbooks.reader.nativeengine.NativePage
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InkGlyph(val text: String, val rect: Rect, val line: Int, val fontKey: String)

private class InkFrame(val image: ImageBitmap, val glyphs: List<InkGlyph>, val key: LayoutKey)

private data class InkMove(val from: InkGlyph?, val to: InkGlyph?)

private class InkTransition(
    val old: InkFrame,
    val new: InkFrame,
    val moves: List<InkMove>,
    val forward: Boolean,
)

/**
 * Two viewport-sized alpha atlases, one Canvas, no composable or text measurement per glyph/frame.
 */
@Stable
class NativeTextMorphState {
    private var frame: InkFrame? = null
    private var transition by mutableStateOf<InkTransition?>(null)
    private val progress = Animatable(1f)
    val isRunning: Boolean
        get() = transition != null

    val atlasBytes: Int
        get() =
            (frame?.let { it.image.width * it.image.height } ?: 0) +
                (transition?.old?.let { it.image.width * it.image.height } ?: 0)

    val maxCharacterCellWidth: Float
        get() =
            frame
                ?.glyphs
                ?.filter { it.text.length <= 8 && !it.fontKey.startsWith("line:") }
                ?.maxOfOrNull { it.rect.width } ?: 0f

    suspend fun update(page: NativePage, key: LayoutKey, reduced: Boolean, forward: Boolean) {
        if (reduced || key.width.toLong() * key.height > 3_000_000L) {
            progress.snapTo(1f)
            transition = null
            frame = null
            return
        }
        // Paragraph paint happens on Main once, not concurrently with another paragraph draw.
        val next = raster(page, key)
        val old = frame
        frame = next
        if (old == null || old.key != key) {
            transition = null
            progress.snapTo(1f)
            return
        }
        val moves =
            withContext(Dispatchers.Default) {
                val oldLines = old.glyphs.groupBy { it.line }
                val newLines = next.glyphs.groupBy { it.line }
                (oldLines.keys + newLines.keys).sorted().flatMap { line ->
                    val a = oldLines[line].orEmpty()
                    val b = newLines[line].orEmpty()
                    TextMorph.match(a.map { it.text + it.fontKey }, b.map { it.text + it.fontKey })
                        .map { match ->
                            InkMove(match.old?.let { a[it] }, match.new?.let { b[it] })
                        }
                }
            }
        progress.snapTo(0f)
        transition = InkTransition(old, next, coalesce(moves), forward)
        try {
            progress.animateTo(1f, tween(TextMorph.DurationMillis, easing = TextMorph.Easing))
        } finally {
            transition = null
        }
    }

    fun DrawScope.draw(ink: Color): Boolean {
        val current = transition ?: return false
        val t = progress.value
        val tint = ColorFilter.tint(ink)
        val direction = if (current.forward) 1f else -1f
        current.moves.forEach { move ->
            val from = move.from
            val to = move.to
            val glyph = to ?: from ?: return@forEach
            val origin = from?.rect ?: to!!.rect
            val target = to?.rect ?: from!!.rect
            val x = origin.left + (target.left - origin.left) * t
            val y =
                origin.top +
                    (target.top - origin.top) * t +
                    when {
                        from == null -> -target.height * 0.3f * (1f - t) * direction
                        to == null -> origin.height * 0.3f * t * direction
                        else -> 0f
                    }
            val alpha = if (from == null) t else if (to == null) 1f - t else 1f
            val image = if (to != null) current.new.image else current.old.image
            val rect = glyph.rect
            drawImage(
                image,
                srcOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
                srcSize = IntSize(rect.width.toInt(), rect.height.toInt()),
                dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                dstSize = IntSize(rect.width.toInt(), rect.height.toInt()),
                alpha = alpha,
                colorFilter = tint,
                filterQuality = FilterQuality.Low,
            )
        }
        return true
    }

    private fun coalesce(moves: List<InkMove>): List<InkMove> {
        val result = arrayListOf<InkMove>()
        fun adjacent(a: InkGlyph?, b: InkGlyph?): Boolean =
            when {
                a == null && b == null -> true
                a == null || b == null -> false
                else ->
                    a.line == b.line &&
                        a.fontKey == b.fontKey &&
                        a.rect.right == b.rect.left &&
                        a.rect.top == b.rect.top &&
                        a.rect.bottom == b.rect.bottom
            }
        fun merge(a: InkGlyph?, b: InkGlyph?): InkGlyph? =
            if (a != null && b != null)
                InkGlyph(a.text + b.text, a.rect.expand(b.rect), a.line, a.fontKey)
            else null
        moves.forEach { move ->
            val previous = result.lastOrNull()
            if (
                previous != null &&
                    adjacent(previous.from, move.from) &&
                    adjacent(previous.to, move.to)
            ) {
                result[result.lastIndex] =
                    InkMove(merge(previous.from, move.from), merge(previous.to, move.to))
            } else result += move
        }
        return result
    }

    private fun raster(page: NativePage, key: LayoutKey): InkFrame {
        val image = ImageBitmap(key.width, key.height, ImageBitmapConfig.Alpha8)
        CanvasDrawScope().draw(
            Density(key.density, key.fontScale),
            key.direction,
            Canvas(image),
            Size(key.width.toFloat(), key.height.toFloat()),
        ) {
            page.fragments.forEach { fragment ->
                clipRect(top = fragment.top, bottom = fragment.top + fragment.height) {
                    drawText(
                        fragment.layout,
                        color = Color.White,
                        topLeft =
                            Offset(
                                0f,
                                fragment.top - fragment.layout.getLineTop(fragment.firstLine),
                            ),
                    )
                }
            }
        }
        val glyphs = arrayListOf<InkGlyph>()
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        var lineIndex = 0
        page.fragments.forEach { fragment ->
            val layout = fragment.layout
            val text = layout.layoutInput.text.text
            iterator.setText(text)
            val fontKey =
                "${layout.layoutInput.style.fontSize}:${layout.layoutInput.style.fontWeight}"
            for (line in fragment.firstLine..fragment.lastLine) {
                val start = layout.getLineStart(line)
                val end = layout.getLineEnd(line, true)
                val chunks = arrayListOf<Pair<String, Rect>>()
                if (glyphs.size >= TextMorph.MaxGlyphs) {
                    val top =
                        floor(
                                fragment.top + layout.getLineTop(line) -
                                    layout.getLineTop(fragment.firstLine)
                            )
                            .coerceAtLeast(0f)
                    val bottom =
                        ceil(
                                fragment.top + layout.getLineBottom(line) -
                                    layout.getLineTop(fragment.firstLine)
                            )
                            .coerceAtMost(key.height.toFloat())
                    if (bottom > top)
                        glyphs +=
                            InkGlyph(
                                text.substring(start, end),
                                Rect(0f, top, key.width.toFloat(), bottom),
                                lineIndex,
                                "line:$fontKey",
                            )
                    lineIndex++
                    continue
                }
                var offset = start
                while (offset < end && glyphs.size + chunks.size < TextMorph.MaxGlyphs) {
                    val next = iterator.following(offset).coerceAtMost(end)
                    if (next <= offset) break
                    var box = layout.getBoundingBox(offset)
                    for (i in offset + 1 until next) box = box.expand(layout.getBoundingBox(i))
                    chunks += text.substring(offset, next) to box
                    offset = next
                }
                // Partition the raster into non-overlapping cells; ligatures/combining clusters
                // keep the shaped source pixels rather than being independently re-shaped.
                if (offset < end)
                    chunks +=
                        text.substring(offset, end) to
                            Rect(layout.getBoundingBox(offset).left, 0f, key.width.toFloat(), 0f)
                val sorted = chunks.sortedBy { it.second.left }
                sorted.forEachIndexed { i, (textPart, box) ->
                    val left = floor(box.left).coerceAtLeast(0f)
                    val right =
                        (if (i == sorted.lastIndex) ceil(box.right)
                            else floor(sorted[i + 1].second.left))
                            .coerceAtMost(key.width.toFloat())
                    val top =
                        floor(
                                fragment.top + layout.getLineTop(line) -
                                    layout.getLineTop(fragment.firstLine)
                            )
                            .coerceAtLeast(0f)
                    val bottom =
                        ceil(
                                fragment.top + layout.getLineBottom(line) -
                                    layout.getLineTop(fragment.firstLine)
                            )
                            .coerceAtMost(key.height.toFloat())
                    if (right > left && bottom > top)
                        glyphs +=
                            InkGlyph(
                                textPart,
                                Rect(left, top, right, bottom),
                                lineIndex,
                                if (box.height == 0f) "line:$fontKey" else fontKey,
                            )
                }
                lineIndex++
            }
        }
        return InkFrame(image, glyphs, key)
    }
}

private fun Rect.expand(other: Rect) =
    Rect(
        minOf(left, other.left),
        minOf(top, other.top),
        maxOf(right, other.right),
        maxOf(bottom, other.bottom),
    )

@Composable
fun rememberNativeTextMorph(
    page: NativePage?,
    key: LayoutKey?,
    reduced: Boolean,
    forward: Boolean,
): NativeTextMorphState {
    val state = remember { NativeTextMorphState() }
    LaunchedEffect(page?.start, key, reduced) {
        if (page != null && key != null) state.update(page, key, reduced, forward)
    }
    return state
}
