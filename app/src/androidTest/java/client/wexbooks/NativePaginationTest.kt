package client.wexbooks

import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import client.wexbooks.reader.nativeengine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class NativePaginationTest {
    @Test
    fun pagesHaveContiguousAnchorsAndBoundedCaches() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val document =
            NativeDocumentParser.parseText(
                context.assets.open("books/quiet-pages.txt"),
                "Тихие страницы",
            )
        val resolver = createFontFamilyResolver(context)
        withContext(Dispatchers.Default) {
            val paginator =
                NativePaginator(
                    document,
                    LayoutKey(650, 900, 22, 2f, 1f, LayoutDirection.Ltr),
                    resolver,
                )
            var page = paginator.pageAt(document.position(0, 0))
            repeat(30) {
                if (page.hasNext) {
                    val end = page.end
                    val next = paginator.pageAt(end)
                    assertEquals(end.block, next.start.block)
                    assertEquals(end.offset, next.start.offset)
                    assertTrue(next.end.progression > page.end.progression)
                    page = next
                }
            }
            val hits = paginator.stats().hits
            paginator.pageAt(page.start)
            assertTrue(paginator.stats().hits > hits)
            assertTrue(paginator.stats().entries <= 5)
            assertTrue(paginator.stats().weight <= 128_000)
        }
    }
}
