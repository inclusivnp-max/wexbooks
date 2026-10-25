@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package client.wexbooks

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.pressBack
import client.wexbooks.reader.BooksViewModel
import client.wexbooks.reader.core.BookFormat
import client.wexbooks.reader.nativeengine.MarkKind
import client.wexbooks.reader.nativeengine.NativeReaderSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderFeaturesTest {
    @get:Rule(order = 0) val reset = ReaderTestSetup()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()
    private val model
        get() = ViewModelProvider(rule.activity)[BooksViewModel::class.java]

    private val native
        get() = model.opened.value!!.reader as NativeReaderSession

    private fun open(format: BookFormat) {
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10_000)
        rule.runOnIdle { model.openSample(format) }
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 20_000)
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val instrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val root =
            androidx.test.platform.app.InstrumentationRegistry.getArguments()
                .getString("additionalTestOutputDir")
                ?.let { java.io.File(it) }
                ?: java.io.File(rule.activity.getExternalFilesDir(null), "reading-qa")
        root.mkdirs()
        val image = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(root, "$name.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }

    @Test
    fun fb2FootnoteAndZoomLeaveReadingPositionUntouched() {
        open(BookFormat.FB2)
        rule.runOnIdle { model.updatePreferences { it.copy(darkTheme = true) } }
        rule.waitUntil(5000) { model.preferences.value?.darkTheme == true }
        rule.waitUntil(5000) { native.imageCacheStats().entries > 0 }
        screenshot("fb2-dark")
        val anchor = native.position.value
        val fragment =
            native.render.value.page!!.fragments.first { f ->
                native.document.blocks[f.block].marks.any { it.kind == MarkKind.Link }
            }
        val link = native.document.blocks[fragment.block].marks.first { it.kind == MarkKind.Link }
        val bounds = fragment.layout.getBoundingBox(link.start)
        rule.onNodeWithTag("native-page").performTouchInput {
            click(
                Offset(
                    bounds.center.x,
                    bounds.center.y + fragment.top - fragment.layout.getLineTop(fragment.firstLine),
                )
            )
        }
        rule.waitUntilExactlyOneExists(hasTestTag("footnote-sheet"), 5000)
        rule.onNodeWithText("Небольшое примечание").assertIsDisplayed()
        screenshot("footnote-dark")
        rule.runOnIdle { model.updatePreferences { it.copy(darkTheme = false) } }
        rule.waitUntil(5000) { model.preferences.value?.darkTheme == false }
        screenshot("footnote-light")
        rule.onNodeWithText("К чтению").performClick()
        rule.waitUntilDoesNotExist(hasTestTag("footnote-sheet"), 5000)
        assertEquals(anchor, native.position.value)
        rule.onNodeWithTag("native-image-quiet-lake").performClick()
        rule.onNodeWithTag("image-viewer").assertIsDisplayed()
        screenshot("image-viewer")
        rule.onNodeWithTag("image-viewer").performTouchInput { doubleClick(center) }
        rule.onNodeWithText("Сбросить масштаб").performClick()
        rule.onNodeWithText("Закрыть").performClick()
        rule.waitUntilDoesNotExist(hasTestTag("image-viewer"), 5000)
        assertEquals(anchor, native.position.value)
        assertTrue(native.imageCacheStats().weight <= 8 * 1024 * 1024)
    }

    @Test
    fun longPressQuoteWithNotePersistsAndReturnsToExactAnchor() {
        open(BookFormat.TXT)
        val page = native.render.value.page!!
        val fragment = page.fragments.first { it.layout.layoutInput.text.length > 40 }
        val bounds =
            fragment.layout.getBoundingBox(fragment.layout.getLineStart(fragment.firstLine) + 2)
        val point =
            Offset(
                bounds.center.x,
                bounds.center.y + fragment.top - fragment.layout.getLineTop(fragment.firstLine),
            )
        rule.onNodeWithTag("native-page").performTouchInput { longClick(point) }
        rule.waitUntilExactlyOneExists(hasTestTag("quote-selection"), 5000)
        rule.onNodeWithTag("quote-selection").performClick()
        rule.onNodeWithTag("quote-note").performTextInput("Моя мысль")
        screenshot("quote-editor")
        rule.onNodeWithTag("save-quote").performClick()
        rule.waitUntil(5000) { runBlocking { model.repository.annotations.first().isNotEmpty() } }
        rule.waitUntilDoesNotExist(hasTestTag("save-quote"), 5000)
        val saved = runBlocking { model.repository.annotations.first().single() }
        assertEquals("Моя мысль", saved.note)
        assertTrue(saved.quote.isNotBlank())
        rule.onNodeWithContentDescription("Назад в библиотеку").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10_000)
        rule.onNodeWithTag("tab-1").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("saved-quote-${saved.id}"), 5000)
        rule.onNodeWithText("Моя мысль").assertIsDisplayed()
        screenshot("saved-quote")
        rule.onNodeWithTag("saved-quote-${saved.id}").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 15000)
        assertEquals(
            client.wexbooks.data.ReaderPositionCodec.decode(saved.locatorJson),
            native.position.value,
        )
        val bookId = model.opened.value!!.book.id
        runBlocking { model.repository.dao.deleteBook(bookId) }
        assertTrue(runBlocking { model.repository.annotations.first().isEmpty() })
    }

    @Test
    fun longPressDragSelectsAcrossVisibleParagraphs() {
        open(BookFormat.TXT)
        val fragments = native.render.value.page!!.fragments
        val first = fragments.first { it.layout.layoutInput.text.length > 40 }
        val last = fragments.last()
        assertTrue(last.block > first.block)
        val start =
            first.layout.getBoundingBox(first.layout.getLineStart(first.firstLine) + 1).center +
                Offset(0f, first.top - first.layout.getLineTop(first.firstLine))
        val end =
            last.layout
                .getBoundingBox((last.layout.getLineEnd(last.lastLine, true) - 2).coerceAtLeast(0))
                .center + Offset(0f, last.top - last.layout.getLineTop(last.firstLine))
        rule.onNodeWithTag("native-page").performTouchInput {
            down(start)
            advanceEventTime(700)
            moveTo(end, delayMillis = 200)
            up()
        }
        rule.onNodeWithTag("quote-selection").performClick()
        rule.onNodeWithTag("save-quote").performClick()
        rule.waitUntil(5000) { runBlocking { model.repository.annotations.first().isNotEmpty() } }
        val saved = runBlocking { model.repository.annotations.first().single() }
        assertTrue(saved.quote.contains("\n\n"))
        assertTrue(
            saved.endBlock >
                client.wexbooks.data.ReaderPositionCodec.decode(saved.locatorJson)!!.block
        )
    }

    @Test
    fun selectionCanBeCopiedAndCancelledWithoutPageTurn() {
        open(BookFormat.TXT)
        val anchor = native.position.value
        val actions =
            rule
                .onNodeWithTag("native-page")
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        rule.runOnIdle {
            assertTrue(actions.first { it.label == "Выделить видимый абзац" }.action())
        }
        rule.onNodeWithTag("copy-selection").performClick()
        rule.waitUntilDoesNotExist(hasTestTag("copy-selection"), 5000)
        val clipboard = rule.activity.getSystemService(android.content.ClipboardManager::class.java)
        rule.runOnIdle { assertTrue(clipboard.primaryClip!!.getItemAt(0).text.isNotBlank()) }
        assertEquals(anchor, native.position.value)
    }

    @Test
    fun epubExplainsHowToSaveAnActualSelection() {
        open(BookFormat.EPUB)
        rule.onNodeWithContentDescription("Сохранить выделение").performClick()
        rule.onNodeWithText("Цитаты из книги").assertIsDisplayed()
        pressBack()
        rule.onNodeWithTag("epub-reader").assertIsDisplayed()
    }
}
