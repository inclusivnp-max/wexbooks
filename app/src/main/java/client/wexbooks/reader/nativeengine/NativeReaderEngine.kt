package client.wexbooks.reader.nativeengine

import androidx.compose.ui.text.font.FontFamily
import client.wexbooks.reader.core.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NativeRenderState(
    val page: NativePage? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val forward: Boolean = true,
)

class NativeReaderEngine : ReaderEngine {
    override val formats = setOf(BookFormat.TXT, BookFormat.FB2)
    private val loading = Mutex()
    private val documents =
        BoundedCache<String, NativeDocument>(2, 8_000_000) {
            it.characterCount * 2 +
                it.notes.values.sumOf { note -> (note.title.length + note.text.length) * 2 } +
                it.blocks.size * 128 +
                it.images.values.sumOf { image -> image.bytes.size }
        }

    suspend fun document(
        id: String,
        file: java.io.File,
        format: BookFormat,
        title: String,
    ): NativeDocument =
        withContext(Dispatchers.IO) {
            documents.get(id)
                ?: loading.withLock {
                    documents.get(id)
                        ?: NativeDocumentParser.parse(file, format, title).also {
                            documents.put(id, it)
                        }
                }
        }

    override suspend fun open(
        source: BookSource,
        initial: ReaderPosition?,
        style: ReaderStyle,
    ): ReaderSession =
        NativeReaderSession(
            source,
            document(source.id, source.file, source.format, source.title),
            initial,
        )
}

class NativeReaderSession(
    override val source: BookSource,
    val document: NativeDocument,
    initial: ReaderPosition?,
) : ReaderSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val worker = Dispatchers.Default.limitedParallelism(1)
    private val closed = AtomicBoolean(false)
    private val _position = MutableStateFlow<ReaderPosition?>(initial ?: document.position(0, 0))
    override val position = _position.asStateFlow()
    override val chapters =
        document.chapters.map { ReaderChapter(it.title, document.position(it.block, 0)) }
    private val _render = MutableStateFlow(NativeRenderState())
    val render = _render.asStateFlow()
    private val imageLoading = Mutex()
    private val bitmaps =
        BoundedCache<String, android.graphics.Bitmap>(3, 8 * 1024 * 1024) { it.allocationByteCount }

    suspend fun image(id: String): android.graphics.Bitmap? =
        withContext(Dispatchers.IO) {
            imageLoading.withLock {
                if (closed.get()) return@withLock null
                bitmaps.get(id)?.let {
                    return@withLock it
                }
                val image = document.images[id] ?: return@withLock null
                var sample = 1
                while (
                    maxOf(image.width, image.height) / sample > 1440 ||
                        image.width.toLong() * image.height / (sample.toLong() * sample) > 1_500_000
                ) sample *= 2
                val options =
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap =
                    android.graphics.BitmapFactory.decodeByteArray(
                        image.bytes,
                        0,
                        image.bytes.size,
                        options,
                    )
                if (bitmap != null && !closed.get()) bitmaps.put(id, bitmap)
                bitmap
            }
        }

    fun imageCacheStats() = bitmaps.stats()

    private var paginator: NativePaginator? = null
    private var request: Job? = null
    private var prefetch: Job? = null
    private var generation = 0L
    private var geometryPending = false
    private var viewportChanging = false

    override fun setViewportTransitioning(changing: Boolean) {
        viewportChanging = changing
    }

    fun prepareReflow() {
        if (closed.get()) return
        geometryPending = true
        _render.value = _render.value.copy(loading = true)
    }

    private suspend fun awaitGeometry(): Boolean {
        while ((geometryPending || viewportChanging) && !closed.get()) delay(16)
        return !closed.get()
    }

    suspend fun configure(key: LayoutKey, resolver: FontFamily.Resolver) {
        if (closed.get() || key.width < 24 || key.height < 24) return
        if (paginator?.key == key) {
            geometryPending = false
            _render.value = _render.value.copy(loading = false)
            return
        }
        request?.cancelAndJoin()
        prefetch?.cancelAndJoin()
        paginator?.let { old -> withContext(worker) { old.clear() } }
        paginator = withContext(worker) { NativePaginator(document, key, resolver) }
        goTo(_position.value ?: document.position(0, 0))
    }

    override suspend fun forward() {
        if (!awaitGeometry()) return
        val page = _render.value.page ?: return
        if (page.hasNext) requestPage(page.end, false)
    }

    override suspend fun backward() {
        if (!awaitGeometry()) return
        val page = _render.value.page ?: return
        if (page.hasPrevious) requestPage(page.start, true)
    }

    override suspend fun goTo(position: ReaderPosition) {
        requestPage(position, false, preserveAnchor = true)
    }

    private suspend fun requestPage(
        anchor: ReaderPosition,
        backwards: Boolean,
        preserveAnchor: Boolean = false,
    ) {
        if (closed.get()) return
        val layout = paginator ?: return
        val token = ++generation
        prefetch?.cancelAndJoin()
        request?.cancelAndJoin()
        val job = scope.launch {
            try {
                val page =
                    withContext(worker) {
                        if (backwards) layout.pageBefore(anchor) else layout.pageAt(anchor)
                    }
                if (generation != token || closed.get()) return@launch
                geometryPending = false
                _render.value = NativeRenderState(page, loading = false, forward = !backwards)
                _position.value =
                    if (preserveAnchor) document.position(anchor.block, anchor.offset)
                    else page.start
                prefetch = scope.launch {
                    withContext(worker) {
                        if (page.hasNext) {
                            ensureActive()
                            layout.pageAt(page.end)
                        }
                        // Only one page ahead; reverse navigation uses bounded layout/history
                        // caches.
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                geometryPending = false
                _render.value =
                    _render.value.copy(
                        loading = false,
                        error = "Попробуйте другой размер шрифта или откройте книгу заново.",
                    )
            }
        }
        request = job
        job.join()
    }

    fun cacheStats() = paginator?.stats()

    fun layoutKey() = paginator?.key

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel()
        bitmaps.clear()
        // Release cache references without touching the worker's mutable caches from another
        // thread.
        paginator = null
    }
}
