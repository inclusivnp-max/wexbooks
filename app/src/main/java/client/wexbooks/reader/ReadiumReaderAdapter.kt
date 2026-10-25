@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package client.wexbooks.reader

import android.app.Application
import client.wexbooks.reader.core.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.navigator.web.reflowable.ReflowableWebGoLocation
import org.readium.navigator.web.reflowable.ReflowableWebRenditionFactory
import org.readium.navigator.web.reflowable.ReflowableWebRenditionState
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.getOrElse

class ReadiumReaderAdapter(
    private val application: Application,
    private val engine: ReadiumEngine,
) : ReaderEngine {
    override val formats = setOf(BookFormat.EPUB)

    override suspend fun open(
        source: BookSource,
        initial: ReaderPosition?,
        style: ReaderStyle,
    ): ReaderSession {
        var publication: Publication? = null
        try {
            withContext(Dispatchers.IO) { publication = engine.open(source.file) }
            val pub = checkNotNull(publication)
            val locator =
                initial?.payload?.let {
                    runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
                }
            val state =
                withContext(Dispatchers.Main) {
                    val factory =
                        ReflowableWebRenditionFactory(application, pub)
                            ?: throw BookImportException(
                                "Для этого издания нужен другой режим вёрстки."
                            )
                    factory
                        .createRenditionState(
                            initialPreferences = style.readiumPreferences(),
                            initialLocation = locator?.let(::ReflowableWebGoLocation),
                        )
                        .getOrElse {
                            throw BookImportException(
                                "Открытие книги прервалось. Попробуйте ещё раз."
                            )
                        }
                }
            return EpubReaderSession(source, pub, state).also { publication = null }
        } finally {
            publication?.close()
        }
    }
}

class EpubReaderSession(
    override val source: BookSource,
    val publication: Publication,
    val rendition: ReflowableWebRenditionState,
) : ReaderSession {
    private val closed = AtomicBoolean(false)
    private var viewportChanging = false

    override fun setViewportTransitioning(changing: Boolean) {
        viewportChanging = changing
    }

    private suspend fun awaitViewport(): Boolean {
        while (viewportChanging && !closed.get()) kotlinx.coroutines.delay(16)
        return !closed.get()
    }

    private val _position = MutableStateFlow<ReaderPosition?>(null)
    override val position = _position.asStateFlow()
    override val chapters: List<ReaderChapter> = flatten(publication.tableOfContents)

    fun publish(locator: Locator) {
        _position.value =
            ReaderPosition(
                locator.href.toString(),
                progression = locator.locations.totalProgression ?: 0.0,
                payload = locator.toJSON().toString(),
            )
    }

    override suspend fun forward() {
        if (!awaitViewport()) return
        rendition.controller?.moveForward()
    }

    override suspend fun backward() {
        if (!awaitViewport()) return
        rendition.controller?.moveBackward()
    }

    override suspend fun goTo(position: ReaderPosition) {
        position.payload
            ?.let { Locator.fromJSON(JSONObject(it)) }
            ?.let { rendition.controller?.goTo(ReflowableWebGoLocation(it)) }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) publication.close()
    }

    private fun flatten(links: List<Link>, depth: Int = 0): List<ReaderChapter> = links.flatMap {
        val locator =
            Locator(
                href = it.url(),
                mediaType = it.mediaType ?: org.readium.r2.shared.util.mediatype.MediaType.XHTML,
            )
        listOf(
            ReaderChapter(
                it.title ?: "Глава",
                ReaderPosition(it.href.toString(), payload = locator.toJSON().toString()),
                depth,
            )
        ) + flatten(it.children, depth + 1)
    }
}
