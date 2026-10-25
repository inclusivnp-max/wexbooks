package client.wexbooks.reader.nativeengine

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import client.wexbooks.reader.core.BoundedCache
import client.wexbooks.reader.core.ReaderPosition
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

data class LayoutKey(
    val width: Int,
    val height: Int,
    val fontSize: Int,
    val density: Float,
    val fontScale: Float,
    val direction: LayoutDirection,
)

data class NativeFragment(
    val block: Int,
    val layout: TextLayoutResult,
    val firstLine: Int,
    val lastLine: Int,
    val top: Float,
    val height: Float,
)

data class NativePage(
    val start: ReaderPosition,
    val end: ReaderPosition,
    val fragments: List<NativeFragment>,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)

/** Thread-confined paginator. Only a handful of pages/paragraph layouts ever live in RAM. */
class NativePaginator(
    val document: NativeDocument,
    val key: LayoutKey,
    resolver: FontFamily.Resolver,
) {
    private val measurer =
        TextMeasurer(resolver, Density(key.density, key.fontScale), key.direction, cacheSize = 0)
    private val layouts =
        BoundedCache<Int, TextLayoutResult>(24, 2_000_000) { it.layoutInput.text.length * 16 + 512 }
    private val pages =
        BoundedCache<Pair<Int, Int>, NativePage>(5, 128_000) { it.fragments.size * 96 + 128 }
    private val previous = BoundedCache<Pair<Int, Int>, ReaderPosition>(4096, 131_072) { 32 }
    private val gap: Float
        get() = key.fontSize * key.density * key.fontScale * 0.6f

    fun stats() = pages.stats()

    fun clear() {
        layouts.clear()
        pages.clear()
        previous.clear()
    }

    suspend fun pageAt(position: ReaderPosition): NativePage {
        val block = position.block.coerceIn(0, document.blocks.lastIndex)
        val offset = position.offset.coerceIn(0, document.blocks[block].text.length)
        val cacheKey = block to offset
        pages.get(cacheKey)?.let {
            return it
        }
        val fragments = ArrayList<NativeFragment>()
        var index = block
        var character = offset
        var top = 0f
        var end = document.position(block, offset)
        while (index < document.blocks.size) {
            coroutineContext.ensureActive()
            val layout = layout(index)
            val firstLine =
                layout.getLineForOffset(
                    character.coerceAtMost(
                        (document.blocks[index].text.length - 1).coerceAtLeast(0)
                    )
                )
            val lineTop = layout.getLineTop(firstLine)
            val spacing =
                if (fragments.isEmpty() || document.blocks[index].continuation) 0f else gap
            val available = key.height - top - spacing
            val minimum = layout.getLineBottom(firstLine) - lineTop
            if (
                fragments.isNotEmpty() &&
                    (minimum > available ||
                        (document.blocks[index].kind == BlockKind.Heading &&
                            available < layout.size.height - lineTop + minimum * 2))
            )
                break
            var lastLine = firstLine
            while (
                lastLine + 1 < layout.lineCount &&
                    layout.getLineBottom(lastLine + 1) - lineTop <= available
            ) lastLine++
            if (
                lastLine < layout.lineCount - 1 &&
                    lastLine - firstLine >= 2 &&
                    layout.lineCount - lastLine == 2
            )
                lastLine--
            val height =
                (layout.getLineBottom(lastLine) - lineTop).coerceAtMost(
                    available.coerceAtLeast(minimum)
                )
            fragments += NativeFragment(index, layout, firstLine, lastLine, top + spacing, height)
            top += spacing + height
            val next = layout.getLineEnd(lastLine, visibleEnd = false)
            if (lastLine < layout.lineCount - 1) {
                end = document.position(index, next)
                break
            }
            index++
            character = 0
            end =
                if (index < document.blocks.size) document.position(index, 0)
                else
                    document.position(document.blocks.lastIndex, document.blocks.last().text.length)
            if (top + minimum >= key.height) break
        }
        val first = fragments.first()
        val start = document.position(first.block, first.layout.getLineStart(first.firstLine))
        val atEnd =
            end.block == document.blocks.lastIndex &&
                end.offset >= document.blocks.last().text.length
        return NativePage(start, end, fragments, start.block > 0 || start.offset > 0, !atEnd).also {
            pages.put(cacheKey, it)
            pages.put(start.block to start.offset, it)
            if (it.hasNext) previous.put(end.block to end.offset, start)
        }
    }

    suspend fun pageBefore(position: ReaderPosition): NativePage {
        previous.get(position.block to position.offset)?.let {
            return pageAt(it)
        }
        var index = position.block.coerceIn(0, document.blocks.lastIndex)
        var offset = position.offset
        var remaining = key.height.toFloat()
        var start = document.position(index, offset)
        while (index >= 0) {
            coroutineContext.ensureActive()
            val text = document.blocks[index].text
            if (offset <= 0) {
                index--
                if (index >= 0) offset = document.blocks[index].text.length
                continue
            }
            val result = layout(index)
            var line = result.getLineForOffset((offset - 1).coerceIn(0, text.lastIndex))
            while (line >= 0) {
                val height = result.getLineBottom(line) - result.getLineTop(line)
                if (remaining < height) return pageAt(start)
                remaining -= height
                start = document.position(index, result.getLineStart(line))
                line--
            }
            remaining -= gap
            index--
            if (index >= 0) offset = document.blocks[index].text.length
        }
        return pageAt(start)
    }

    private fun layout(index: Int): TextLayoutResult {
        layouts.get(index)?.let {
            return it
        }
        val block = document.blocks[index]
        val text =
            AnnotatedString.Builder(block.text)
                .apply {
                    block.marks.forEach { mark ->
                        addStyle(
                            if (mark.kind == MarkKind.Strong)
                                SpanStyle(fontWeight = FontWeight.Bold)
                            else SpanStyle(fontStyle = FontStyle.Italic),
                            mark.start,
                            mark.end,
                        )
                    }
                }
                .toAnnotatedString()
        val font =
            if (block.kind == BlockKind.Heading) key.fontSize * 1.35f else key.fontSize.toFloat()
        return measurer
            .measure(
                text,
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = font.sp,
                    lineHeight = (font * 1.55f).sp,
                    fontWeight =
                        if (block.kind == BlockKind.Heading) FontWeight.Medium
                        else FontWeight.Normal,
                ),
                constraints = Constraints(maxWidth = key.width),
                skipCache = true,
            )
            .also { layouts.put(index, it) }
    }
}
