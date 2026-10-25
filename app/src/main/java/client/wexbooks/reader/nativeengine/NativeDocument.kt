package client.wexbooks.reader.nativeengine

import client.wexbooks.reader.core.ReaderPosition

enum class BlockKind {
    Paragraph,
    Heading,
    Verse,
    Image,
}

enum class MarkKind {
    Strong,
    Emphasis,
    Link,
}

data class InlineMark(val start: Int, val end: Int, val kind: MarkKind, val target: String? = null)

data class NativeBlock(
    val text: String,
    val kind: BlockKind,
    val id: String,
    val marks: List<InlineMark> = emptyList(),
    val continuation: Boolean = false,
    val imageId: String? = null,
)

data class NativeChapter(val title: String, val block: Int)

data class NativeNote(val title: String, val text: String)

/** Encoded resources are bounded at import; bitmaps are sampled only when visible. */
class NativeImage(val bytes: ByteArray, val width: Int, val height: Int)

class NativeDocument(
    val title: String,
    val author: String,
    val blocks: List<NativeBlock>,
    val chapters: List<NativeChapter>,
    val notes: Map<String, NativeNote> = emptyMap(),
    val images: Map<String, NativeImage> = emptyMap(),
    val targets: Map<String, Int> = emptyMap(),
) {
    private val offsets =
        IntArray(blocks.size + 1).apply {
            blocks.forEachIndexed { i, b -> this[i + 1] = this[i] + b.text.length }
        }
    val characterCount: Int
        get() = offsets.last()

    fun position(block: Int, offset: Int): ReaderPosition {
        val index = block.coerceIn(0, blocks.lastIndex)
        val character = offset.coerceIn(0, blocks[index].text.length)
        return ReaderPosition(
            "native",
            index,
            character,
            (offsets[index] + character).toDouble() / characterCount.coerceAtLeast(1),
        )
    }
}
