@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package client.wexbooks

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.pressBack
import client.wexbooks.reader.BooksViewModel
import client.wexbooks.reader.core.BookFormat
import client.wexbooks.reader.nativeengine.NativeReaderSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NativeReaderTest {
    @get:Rule(order = 0) val reset = ReaderTestSetup()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()
    private val model
        get() = ViewModelProvider(rule.activity)[BooksViewModel::class.java]

    private val native
        get() = model.opened.value?.reader as? NativeReaderSession

    @Before
    fun awaitLibrary() {
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10_000)
    }

    private fun open(format: BookFormat) {
        rule.runOnIdle { model.openSample(format) }
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 20_000)
        rule.onNodeWithTag("native-reader").assertIsDisplayed()
    }

    @Test
    fun nativeTextAnchorsSurviveFontThemeAndReopen() {
        open(BookFormat.TXT)
        repeat(3) {
            val before = native!!.position.value
            rule.onNodeWithContentDescription("Следующая страница").performClick()
            rule.waitUntil(5_000) { native!!.position.value != before }
        }
        val anchor = native!!.position.value!!
        val id = model.opened.value!!.book.id
        rule.onNodeWithText("Aa").performClick()
        rule.onNodeWithTag("font-increase").performClick()
        rule.waitUntil(5_000) {
            native?.layoutKey()?.fontSize == 24 && native?.render?.value?.loading == false
        }
        assertEquals(anchor, native!!.position.value)
        val layout = native!!.layoutKey()
        pressBack()
        rule.onNodeWithContentDescription("Сменить тему").performClick()
        rule.waitUntil(5_000) { model.preferences.value?.darkTheme != null }
        assertEquals("Theme must not invalidate text geometry", layout, native!!.layoutKey())
        rule.onNodeWithContentDescription("Назад в библиотеку").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10_000)
        val saved = runBlocking { model.repository.dao.book(id)!! }
        assertNotNull(saved.locatorJson)
        rule.onNodeWithTag("book-$id").performScrollTo().performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 15_000)
        assertEquals(anchor, native!!.position.value)
        assertTrue(native!!.cacheStats()!!.entries <= 5)
    }

    @Test
    fun fb2TableOfContentsAndBookmarkAreReal() {
        open(BookFormat.FB2)
        assertEquals(5, native!!.chapters.size)
        rule.onNodeWithContentDescription("Добавить закладку").performClick()
        rule.waitUntil(5_000) { runBlocking { model.repository.bookmarks.first().isNotEmpty() } }
        val bookmark = runBlocking { model.repository.bookmarks.first().single() }
        rule.onNodeWithContentDescription("Назад в библиотеку").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10_000)
        rule.onNodeWithTag("tab-1").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("saved-bookmark-${bookmark.id}"), 5_000)
        rule.onNodeWithTag("saved-bookmark-${bookmark.id}").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 15_000)
        assertEquals(BookFormat.FB2, native!!.source.format)
        assertEquals(0, native!!.position.value!!.block)
    }

    @Test
    fun repeatedImportDoesNotDuplicateManagedBook() {
        open(BookFormat.TXT)
        val firstId = model.opened.value!!.book.id
        rule.onNodeWithContentDescription("Назад в библиотеку").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10_000)
        rule.runOnIdle { model.openSample(BookFormat.TXT) }
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 15_000)
        assertEquals(firstId, model.opened.value!!.book.id)
        assertEquals(1, runBlocking { model.repository.books.first().size })
    }
}
