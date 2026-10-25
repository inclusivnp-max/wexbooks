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

    @Test
    fun illustrationPagesAdvanceToTheEndWithoutSplittingImages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val document =
            NativeDocument(
                "Images",
                "Author",
                listOf(
                    NativeBlock("Before ".repeat(30), BlockKind.Paragraph, "before"),
                    NativeBlock("\uFFFC", BlockKind.Image, "image", imageId = "pic"),
                    NativeBlock("After ".repeat(30), BlockKind.Paragraph, "after"),
                    NativeBlock("\uFFFC", BlockKind.Image, "last-image", imageId = "pic"),
                ),
                emptyList(),
                images = mapOf("pic" to NativeImage(byteArrayOf(), 800, 450)),
            )
        val resolver = createFontFamilyResolver(context)
        withContext(Dispatchers.Default) {
            val paginator =
                NativePaginator(
                    document,
                    LayoutKey(300, 280, 22, 1f, 1f, LayoutDirection.Ltr),
                    resolver,
                )
            var page = paginator.pageAt(document.position(0, 0))
            var images = 0
            var count = 0
            while (true) {
                count++
                assertTrue("Pagination must terminate", count < 50)
                images += page.images.size
                page.images.forEach {
                    assertTrue(it.top >= 0 && it.top + it.height <= 280)
                    assertEquals(800f / 450, it.width / it.height, 0.001f)
                }
                if (!page.hasNext) break
                val old = page
                page = paginator.pageAt(old.end)
                assertEquals(old.end, page.start)
                assertTrue(page.end.progression > old.end.progression)
                assertEquals(old.start, paginator.pageBefore(page.start).start)
            }
            assertEquals(2, images)
            assertEquals(1.0, page.end.progression, 0.0)
        }
    }
}
