@file:OptIn(
    androidx.compose.ui.test.ExperimentalTestApi::class,
    org.readium.r2.shared.ExperimentalReadiumApi::class,
)

package client.wexbooks

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import client.wexbooks.reader.BooksViewModel
import client.wexbooks.reader.EpubReaderSession
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EpubQuoteTest {
    @get:Rule(order = 0) val reset = ReaderTestSetup()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun actualDomSelectionIsSavedDecoratedAndReopened() {
        val model = ViewModelProvider(rule.activity)[BooksViewModel::class.java]
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10000)
        rule.runOnIdle { model.openSample() }
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 20000)
        fun views(view: View): List<WebView> =
            when (view) {
                is WebView -> listOf(view)
                is ViewGroup -> (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
                else -> emptyList()
            }
        val selected = AtomicInteger()
        rule.runOnIdle {
            views(rule.activity.window.decorView)
                .filter { it.isShown }
                .forEach { web ->
                    // Exercise the navigator's real DOM selection API, not a fake quote callback.
                    web.evaluateJavascript(
                        """(() => {
                    const p = Array.from(document.querySelectorAll('p')).find(p => p.textContent.length > 40);
                    if (!p) return false;
                    const range = document.createRange(); range.selectNodeContents(p);
                    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
                    return true;
                })()"""
                    ) {
                        if (it == "true") selected.incrementAndGet()
                    }
                }
        }
        rule.waitUntil(5000) { selected.get() > 0 }
        rule.onNodeWithContentDescription("Сохранить выделение").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("save-quote"), 5000)
        rule.onNodeWithTag("quote-note").performTextInput("Мысль из EPUB")
        rule.onNodeWithTag("save-quote").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.OnClick
        ) {
            it()
        }
        rule.waitUntil(5000) { runBlocking { model.repository.annotations.first().size == 1 } }
        rule.waitUntilDoesNotExist(hasTestTag("save-quote"), 5000)
        val saved = runBlocking { model.repository.annotations.first().single() }
        assertTrue(saved.quote.length > 40)
        assertEquals("Мысль из EPUB", saved.note)
        rule.waitUntil(5000) {
            (model.opened.value?.reader as? EpubReaderSession)
                ?.rendition
                ?.controller
                ?.decorations
                ?.get("quotes")
                ?.size == 1
        }
        rule.onNodeWithContentDescription("Назад в библиотеку").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10000)
        rule.runOnIdle { model.openBook(saved.bookId, saved.locatorJson) }
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 20000)
        rule.waitUntil(5000) {
            (model.opened.value?.reader as? EpubReaderSession)
                ?.rendition
                ?.controller
                ?.decorations
                ?.get("quotes")
                ?.size == 1
        }
    }
}
