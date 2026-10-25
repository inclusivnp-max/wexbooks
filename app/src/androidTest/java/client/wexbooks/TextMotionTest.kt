@file:OptIn(
    androidx.compose.ui.test.ExperimentalTestApi::class,
    org.readium.r2.shared.ExperimentalReadiumApi::class,
)

package client.wexbooks

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import client.wexbooks.reader.BooksViewModel
import client.wexbooks.reader.core.BookFormat
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TextMotionTest {
    @get:Rule(order = 0) val reset = ReaderTestSetup()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()
    private val model
        get() = ViewModelProvider(rule.activity)[BooksViewModel::class.java]

    private fun open(format: BookFormat) {
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10000)
        rule.runOnIdle { model.openSample(format) }
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 20000)
    }

    private fun saveFrame(name: String, frame: ImageBitmap) {
        val path =
            androidx.test.platform.app.InstrumentationRegistry.getArguments()
                .getString("additionalTestOutputDir") ?: return
        java.io.File(path, "$name.png").outputStream().use {
            frame.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun nativeGlyphsReallyMorphAndReducedMotionStopsThem() {
        open(BookFormat.TXT)
        saveFrame("morph-before", rule.onNodeWithTag("native-page").captureToImage())
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Следующая страница").performClick()
        rule.waitUntil(5000) {
            rule.mainClock.advanceTimeByFrame()
            rule
                .onNodeWithTag("native-page")
                .fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription] == "Переход текста"
        }
        saveFrame("morph-000", rule.onNodeWithTag("native-page").captureToImage())
        rule.mainClock.advanceTimeBy(32)
        val moving = rule.onNodeWithTag("native-page").captureToImage()
        saveFrame("morph-032", moving)
        for (time in listOf(64, 96, 128, 160, 192, 224, 256)) {
            rule.mainClock.advanceTimeBy(32)
            saveFrame(
                "morph-${time.toString().padStart(3, '0')}",
                rule.onNodeWithTag("native-page").captureToImage(),
            )
        }
        rule.mainClock.advanceTimeBy(100)
        rule
            .onNodeWithTag("native-page")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Страница"))
        val settled = rule.onNodeWithTag("native-page").captureToImage()
        saveFrame("morph-after", settled)
        val a = moving.toPixelMap()
        val b = settled.toPixelMap()
        var changed = 0
        for (y in 0 until minOf(a.height, b.height) step 4) for (x in
            0 until minOf(a.width, b.width) step 4) if (a[x, y] != b[x, y]) changed++
        assertTrue("Intermediate glyph positions must differ from the settled page", changed > 100)
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { model.updatePreferences { it.copy(reduceMotion = true) } }
        rule.waitUntil(5000) { model.preferences.value?.reduceMotion == true }
        rule.onNodeWithContentDescription("Следующая страница").performClick()
        rule.waitForIdle()
        rule
            .onNodeWithTag("native-page")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Страница"))
    }

    @Test
    fun glyphCellsDoNotStretchToTheViewportEdge() {
        open(BookFormat.TXT)
        val reader =
            model.opened.value!!.reader as client.wexbooks.reader.nativeengine.NativeReaderSession
        val state = client.wexbooks.ui.motion.NativeTextMorphState()
        rule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                state.update(reader.render.value.page!!, reader.layoutKey()!!, false, true)
            }
        }
        val key = reader.layoutKey()!!
        assertTrue(state.maxCharacterCellWidth < key.fontSize * key.density * key.fontScale * 3)
        assertEquals(key.width * key.height, state.atlasBytes)
        rule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                state.update(reader.render.value.page!!, key, true, true)
            }
        }
        assertEquals(0, state.atlasBytes)
    }

    @Test
    fun aNewPageInterruptsThePreviousMorphWithoutLosingTheTarget() {
        open(BookFormat.TXT)
        val reader = model.opened.value!!.reader
        rule.mainClock.autoAdvance = false
        repeat(4) {
            val before = reader.position.value
            rule.onNodeWithContentDescription("Следующая страница").performClick()
            rule.waitUntil(5000) {
                rule.mainClock.advanceTimeByFrame()
                reader.position.value != before
            }
            rule.mainClock.advanceTimeBy(32)
        }
        val target = reader.position.value
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(target, reader.position.value)
        rule
            .onNodeWithTag("native-page")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Страница"))
    }

    private fun webViews(view: View): List<WebView> =
        when (view) {
            is WebView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) }
            else -> emptyList()
        }

    private fun js(expression: String): List<String> {
        val result = AtomicReference<List<String>?>(null)
        rule.runOnIdle {
            val webs =
                webViews(rule.activity.window.decorView).filter {
                    it.isShown && it.getGlobalVisibleRect(android.graphics.Rect())
                }
            val values = mutableListOf<String>()
            webs.forEach {
                it.evaluateJavascript(expression) { value ->
                    values += value
                    if (values.size == webs.size) result.set(values.toList())
                }
            }
            if (webs.isEmpty()) result.set(emptyList())
        }
        rule.waitUntil(5000) { result.get() != null }
        return result.get()!!
    }

    @Test
    fun epubMorphCleansOverlayAndPreservesRealPublicationText() {
        open(BookFormat.EPUB)
        rule.waitUntil(5000) {
            js("window.__wexMorphGlyphCount || 0").any { (it.toIntOrNull() ?: 0) > 0 }
        }
        val before = js("document.body.textContent")
        rule.onNodeWithContentDescription("Следующая страница").performClick()
        rule.waitUntil(5000) {
            js("window.__wexMorphCount || 0").any { it.toIntOrNull()?.let { n -> n > 0 } == true }
        }
        rule.waitUntil(5000) { js("Boolean(window.__wexMorphPlaying)").all { it == "false" } }
        println("EPUB morph timings: " + js("JSON.stringify(window.__wexMorphTimings)"))
        assertEquals(before, js("document.body.textContent"))
        assertTrue(js("document.querySelectorAll('[data-wex-morph]').length").all { it == "0" })
        assertTrue(js("window.__wexMorphGlyphCount || 0").all { (it.toIntOrNull() ?: 0) <= 900 })
        rule.runOnIdle { model.updatePreferences { it.copy(reduceMotion = true) } }
        rule.waitUntil(5000) { model.preferences.value?.reduceMotion == true }
        val count = js("window.__wexMorphCount || 0")
        rule.onNodeWithContentDescription("Следующая страница").performClick()
        rule.waitForIdle()
        assertEquals(count, js("window.__wexMorphCount || 0"))
    }
}
