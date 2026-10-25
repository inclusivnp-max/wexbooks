package client.wexbooks.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import client.wexbooks.reader.BookImportException
import client.wexbooks.reader.EpubFileRules
import client.wexbooks.reader.ReadiumEngine
import client.wexbooks.reader.core.BookFormat
import client.wexbooks.reader.core.ReaderPosition
import client.wexbooks.reader.nativeengine.NativeReaderEngine
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BookRepository(
    private val context: Context,
    val database: LibraryDatabase,
    val engine: ReadiumEngine,
    private val native: NativeReaderEngine,
) {
    val dao = database.library()
    val books = dao.observeBooks()
    val bookmarks = dao.observeBookmarks()
    private val importMutex = Mutex()
    private val directory = File(context.filesDir, "books").apply { mkdirs() }

    fun file(book: BookEntity): File = File(directory, book.fileName)

    suspend fun importUri(uri: Uri): BookEntity =
        withContext(Dispatchers.IO) {
            val name =
                runCatching {
                    context.contentResolver
                        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                }
                    .getOrNull() ?: "Книга.epub"
            val input =
                context.contentResolver.openInputStream(uri)
                    ?: throw BookImportException(
                        "Провайдер не открыл выбранный файл. Выберите его ещё раз."
                    )
            importStream(input, name)
        }

    suspend fun importStream(input: InputStream, displayName: String): BookEntity =
        withContext(Dispatchers.IO) {
            // Own the stream before waiting for the queue, including cancellation while waiting.
            input.use { stream ->
                importMutex.withLock {
                    val incoming = File.createTempFile("epub-import-", ".epub", context.cacheDir)
                    try {
                        val id =
                            incoming.outputStream().use { EpubFileRules.copyAndHash(stream, it) }
                        val existing = dao.book(id)
                        if (existing != null && file(existing).isFile) return@withLock existing
                        val lowerName = displayName.lowercase()
                        val format =
                            when {
                                lowerName.endsWith(".txt") -> BookFormat.TXT
                                lowerName.endsWith(".fb2") ||
                                    lowerName.endsWith(".fb2.zip") ||
                                    lowerName.endsWith(".zip") -> BookFormat.FB2
                                else -> BookFormat.EPUB
                            }
                        val metadata =
                            if (format == BookFormat.EPUB) {
                                EpubFileRules.validate(incoming)
                                val publication = engine.open(incoming)
                                try {
                                    Pair(
                                        publication.metadata.title?.takeIf { it.isNotBlank() }
                                            ?: displayName.substringBeforeLast('.'),
                                        publication.metadata.authors
                                            .joinToString(", ") { it.name }
                                            .ifBlank { "Автор не указан" },
                                    )
                                } finally {
                                    publication.close()
                                }
                            } else {
                                val document =
                                    native.document(
                                        id,
                                        incoming,
                                        format,
                                        displayName.substringBeforeLast('.'),
                                    )
                                Pair(document.title, document.author)
                            }
                        val target = File(directory, "$id.${format.name.lowercase()}")
                        var created = false
                        try {
                            if (!target.exists()) {
                                Files.move(
                                    incoming.toPath(),
                                    target.toPath(),
                                    StandardCopyOption.ATOMIC_MOVE,
                                )
                                created = true
                            }
                            val book =
                                existing
                                    ?: BookEntity(
                                        id,
                                        metadata.first.take(1024),
                                        metadata.second.take(1024),
                                        target.name,
                                        System.currentTimeMillis(),
                                        format = format.name,
                                    )
                            dao.insert(book)
                            dao.book(id) ?: error("Book insert did not persist")
                        } catch (e: Exception) {
                            if (created) target.delete()
                            throw e
                        }
                    } finally {
                        incoming.delete()
                    }
                }
            }
        }

    suspend fun savePosition(bookId: String, position: ReaderPosition) {
        dao.savePosition(
            bookId,
            ReaderPositionCodec.encode(position),
            position.progression.coerceIn(0.0, 1.0),
        )
    }

    suspend fun addBookmark(bookId: String, position: ReaderPosition, label: String) {
        dao.insertBookmark(
            BookmarkEntity(
                UUID.randomUUID().toString(),
                bookId,
                label,
                ReaderPositionCodec.encode(position),
                System.currentTimeMillis(),
            )
        )
    }

    suspend fun delete(book: BookEntity) =
        withContext(Dispatchers.IO) {
            dao.deleteBook(book.id)
            file(book).delete() // Only our managed copy; never the original content URI.
        }
}
