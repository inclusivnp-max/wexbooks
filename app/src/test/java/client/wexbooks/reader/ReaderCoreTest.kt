package client.wexbooks.reader

import client.wexbooks.reader.core.*
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReaderCoreTest {
    @Test
    fun boundedCacheEvictsLeastRecentlyUsed() {
        val cache = BoundedCache<String, String>(2, 8) { it.length }
        cache.put("a", "aaa")
        cache.put("b", "bbb")
        assertEquals("aaa", cache.get("a"))
        cache.put("c", "ccc")
        assertNull(cache.get("b"))
        assertEquals(2, cache.stats().entries)
        assertTrue(cache.stats().weight <= 8)
    }

    @Test
    fun oversizedEntryKeepsWorkingSet() {
        val cache = BoundedCache<Int, String>(2, 4) { it.length }
        cache.put(1, "ab")
        cache.put(2, "cdefg")
        assertEquals("ab", cache.get(1))
        assertNull(cache.get(2))
        assertEquals(2, cache.stats().weight)
    }

    @Test
    fun replacingAnEntryUpdatesWeight() {
        val cache = BoundedCache<Int, String>(3, 5) { it.length }
        cache.put(1, "abc")
        cache.put(1, "d")
        assertEquals(1, cache.stats().weight)
        cache.clear()
        assertEquals(0, cache.stats().entries)
    }

    @Test
    fun dispatcherSelectsEngineWithoutKnowingRendererTypes() = runBlocking {
        val epub = StubEngine(setOf(BookFormat.EPUB))
        val native = StubEngine(setOf(BookFormat.TXT, BookFormat.FB2))
        val core = ReaderCore(listOf(epub, native))
        val source = BookSource("id", File("sample.txt"), BookFormat.TXT, "Sample", "Author")
        val session = core.open(source, ReaderPosition("native", 3, 20), ReaderStyle())
        assertSame(native.last, session)
        assertEquals(3, session.position.value?.block)
        assertNull(epub.last)
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateFormatRegistrationIsRejected() {
        ReaderCore(listOf(StubEngine(setOf(BookFormat.EPUB)), StubEngine(setOf(BookFormat.EPUB))))
    }

    private class StubEngine(override val formats: Set<BookFormat>) : ReaderEngine {
        var last: ReaderSession? = null

        override suspend fun open(
            source: BookSource,
            initial: ReaderPosition?,
            style: ReaderStyle,
        ): ReaderSession =
            object : ReaderSession {
                    override val source = source
                    override val position = MutableStateFlow(initial)
                    override val chapters = emptyList<ReaderChapter>()

                    override suspend fun forward() {}

                    override suspend fun backward() {}

                    override suspend fun goTo(position: ReaderPosition) {
                        this.position.value = position
                    }

                    override fun close() {}
                }
                .also { last = it }
    }
}
