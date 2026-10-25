package client.wexbooks.reader.core

import java.io.Closeable
import java.io.File
import kotlinx.coroutines.flow.StateFlow

enum class BookFormat {
    EPUB,
    TXT,
    FB2,
}

data class BookSource(
    val id: String,
    val file: File,
    val format: BookFormat,
    val title: String,
    val author: String,
)

data class ReaderStyle(
    val fontSize: Int = 22,
    val scroll: Boolean = false,
    val darkTheme: Boolean = false,
    val fontFamily: String = "serif",
    val lineHeight: Double = 1.55,
    val horizontalMargin: Int = 26,
    val paragraphSpacing: Double = 0.6,
    val letterSpacing: Double = 0.0,
    val firstLineIndent: Boolean = false,
    val justify: Boolean = false,
    val backgroundColor: Int? = null,
    val textColor: Int? = null,
)

/** Stable document coordinates; renderer-specific data stays opaque to the core. */
data class ReaderPosition(
    val resource: String,
    val block: Int = 0,
    val offset: Int = 0,
    val progression: Double = 0.0,
    val payload: String? = null,
)

data class ReaderChapter(val title: String, val position: ReaderPosition, val depth: Int = 0)

interface ReaderSession : Closeable {
    val source: BookSource
    val position: StateFlow<ReaderPosition?>
    val chapters: List<ReaderChapter>

    suspend fun forward()

    suspend fun backward()

    suspend fun goTo(position: ReaderPosition)
}

interface ReaderEngine {
    val formats: Set<BookFormat>

    suspend fun open(
        source: BookSource,
        initial: ReaderPosition?,
        style: ReaderStyle,
    ): ReaderSession
}

/** Routing is independent of Compose, Room, Readium and any text-layout implementation. */
class ReaderCore(engines: List<ReaderEngine>) {
    private val routes = buildMap {
        engines.forEach { engine ->
            engine.formats.forEach { format ->
                check(put(format, engine) == null) { "Two engines registered for $format" }
            }
        }
    }

    suspend fun open(
        source: BookSource,
        initial: ReaderPosition?,
        style: ReaderStyle,
    ): ReaderSession =
        checkNotNull(routes[source.format]) { "No reader for ${source.format}" }
            .open(source, initial, style)
}
