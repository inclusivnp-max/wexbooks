package client.wexbooks.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexbooks.WexBooksApplication
import client.wexbooks.data.BookEntity
import client.wexbooks.data.ReaderPositionCodec
import client.wexbooks.data.ReaderPreferences
import client.wexbooks.reader.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class QuoteDraft(
    val position: ReaderPosition,
    val endBlock: Int,
    val endOffset: Int,
    val text: String,
)

class OpenedBook(val book: BookEntity, val reader: ReaderSession) {
    fun close() = reader.close()
}

class BooksViewModel(application: Application) : AndroidViewModel(application) {
    val container = (application as WexBooksApplication).container
    val repository = container.books
    val books =
        repository.books.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    val bookmarks =
        repository.bookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    val annotations =
        repository.annotations.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(0),
            emptyList(),
        )
    private val _preferences = MutableStateFlow<ReaderPreferences?>(null)
    val preferences = _preferences.asStateFlow()
    private val _opened = MutableStateFlow<OpenedBook?>(null)
    val opened = _opened.asStateFlow()
    private val _opening = MutableStateFlow(false)
    val opening = _opening.asStateFlow()
    private val _importing = MutableStateFlow<String?>(null)
    val importing = _importing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val importMutex = Mutex()
    private val positionMutex = Mutex()
    private val positions = Channel<Pair<OpenedBook, ReaderPosition>>(Channel.CONFLATED)
    private var positionJob: Job? = null
    private var openJob: Job? = null
    private var openGeneration = 0L
    private val retired = mutableSetOf<OpenedBook>()

    init {
        viewModelScope.launch {
            container.preferences.preferences.collect { _preferences.value = it }
        }
        viewModelScope.launch {
            for ((session, position) in positions) {
                positionMutex.withLock {
                    if (_opened.value === session)
                        repository.savePosition(session.book.id, position)
                }
                delay(100) // No 60/120Hz Room writes when a scrolling renderer reports every frame.
            }
        }
    }

    fun updatePreferences(transform: (ReaderPreferences) -> ReaderPreferences) {
        viewModelScope.launch { container.preferences.update(transform) }
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importMutex.withLock {
                try {
                    val errors = mutableListOf<String>()
                    uris.forEachIndexed { index, uri ->
                        _importing.value = "Импорт ${index + 1} из ${uris.size}"
                        try {
                            repository.importUri(uri)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errors += readableError(e)
                        }
                    }
                    if (errors.isNotEmpty()) _message.value = errors.distinct().joinToString("\n")
                } finally {
                    _importing.value = null
                }
            }
        }
    }

    fun openSample(format: BookFormat = BookFormat.EPUB) {
        viewModelScope.launch {
            importMutex.withLock {
                _importing.value = "Готовим первую книгу"
                try {
                    val extension = format.name.lowercase()
                    val book =
                        repository.importStream(
                            getApplication<Application>()
                                .assets
                                .open("books/quiet-pages.$extension"),
                            "Тихие страницы.$extension",
                        )
                    openBook(book.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _message.value = readableError(e)
                } finally {
                    _importing.value = null
                }
            }
        }
    }

    fun openBook(id: String, requestedLocator: String? = null) {
        val token = ++openGeneration
        openJob?.cancel()
        openJob = viewModelScope.launch {
            _opening.value = true
            var reader: ReaderSession? = null
            try {
                val book =
                    repository.dao.book(id)
                        ?: throw BookImportException("Книга уже удалена из библиотеки.")
                val prefs = _preferences.value ?: container.preferences.preferences.first()
                reader =
                    container.reader.open(
                        BookSource(
                            book.id,
                            repository.file(book),
                            BookFormat.valueOf(book.format),
                            book.title,
                            book.author,
                        ),
                        ReaderPositionCodec.decode(requestedLocator ?: book.locatorJson),
                        ReaderStyle(
                            prefs.fontSize,
                            prefs.scroll,
                            prefs.darkTheme
                                ?: (getApplication<Application>().resources.configuration.uiMode and
                                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                                    android.content.res.Configuration.UI_MODE_NIGHT_YES),
                        ),
                    )
                ensureActive()
                repository.dao.markOpened(id, System.currentTimeMillis())
                positionJob?.cancel()
                _opened.value?.let(::retire)
                val session = OpenedBook(book, reader)
                _opened.value = session
                reader = null
                positionJob = launch {
                    session.reader.position.filterNotNull().distinctUntilChanged().collect {
                        positions.trySend(session to it)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = readableError(e)
            } finally {
                reader?.close()
                if (token == openGeneration) _opening.value = false
            }
        }
    }

    fun closeBook() {
        if (_opening.value) {
            ++openGeneration
            openJob?.cancel()
            _opening.value = false
        }
        val session = _opened.value ?: return
        val finalPosition = session.reader.position.value
        positionJob?.cancel()
        viewModelScope.launch {
            positionMutex.withLock {
                if (_opened.value !== session) return@withLock
                if (finalPosition != null) repository.savePosition(session.book.id, finalPosition)
                _opened.value = null
            }
            retire(session)
        }
    }

    fun flushPosition() {
        val session = _opened.value ?: return
        val current = session.reader.position.value ?: return
        viewModelScope.launch {
            positionMutex.withLock {
                if (_opened.value === session) repository.savePosition(session.book.id, current)
            }
        }
    }

    fun bookmark(session: OpenedBook) {
        val position = session.reader.position.value ?: return
        val label =
            session.reader.chapters
                .lastOrNull {
                    it.position.block <= position.block &&
                        (position.resource == "native" || it.position.resource == position.resource)
                }
                ?.title ?: "Закладка · ${(position.progression * 100).toInt()}%"
        viewModelScope.launch { repository.addBookmark(session.book.id, position, label) }
    }

    suspend fun saveQuote(session: OpenedBook, draft: QuoteDraft, note: String) {
        require(draft.text.isNotBlank() && draft.text.length <= 12_000)
        repository.dao.insertAnnotation(
            client.wexbooks.data.AnnotationEntity(
                java.util.UUID.randomUUID().toString(),
                session.book.id,
                ReaderPositionCodec.encode(draft.position),
                draft.endBlock,
                draft.endOffset,
                draft.text,
                note.take(2000).trim(),
                System.currentTimeMillis(),
            )
        )
    }

    fun deleteAnnotation(id: String) {
        viewModelScope.launch { repository.dao.deleteAnnotation(id) }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch { repository.delete(book) }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch { repository.dao.deleteBookmark(id) }
    }

    private fun retire(session: OpenedBook) {
        if (!retired.add(session)) return
        viewModelScope.launch {
            delay(if (_preferences.value?.reduceMotion == true) 0 else 320)
            session.close()
            retired -= session
        }
    }

    override fun onCleared() {
        _opened.value?.close()
        retired.forEach { it.close() }
        retired.clear()
        positions.close()
    }

    private fun readableError(e: Exception): String =
        when (e) {
            is BookImportException -> e.message ?: "Ошибка чтения книги."
            is java.io.IOException ->
                "Ошибка чтения файла. Проверьте свободное место и выберите книгу ещё раз."
            else -> "Книга не открылась. Попробуйте другое издание."
        }
}
