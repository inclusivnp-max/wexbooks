@file:OptIn(
    androidx.compose.ui.test.ExperimentalTestApi::class,
    org.readium.r2.shared.ExperimentalReadiumApi::class,
)

package client.wexbooks

import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.pressBack
import client.wexbooks.data.*
import client.wexbooks.reader.BooksViewModel
import client.wexbooks.reader.EpubReaderSession
import client.wexbooks.reader.core.BookFormat
import client.wexbooks.reader.nativeengine.NativeReaderSession
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReadingComfortTest {
    @get:Rule(order = 0) val reset = ReaderTestSetup()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()
    private val model
        get() = ViewModelProvider(rule.activity)[BooksViewModel::class.java]

    private val native
        get() = model.opened.value!!.reader as NativeReaderSession

    private fun open(format: BookFormat) {
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10000)
        rule.runOnIdle { model.openSample(format) }
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 20000)
    }

    private fun scroll(tag: String) {
        if (tag.startsWith("comfort-tab-") || tag == "comfort-done") return
        rule.onNodeWithTag("comfort-list").performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).performScrollTo()
    }

    private fun click(tag: String) {
        scroll(tag)
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun slider(tag: String, value: Float) {
        scroll(tag)
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }

    private fun shot(tag: String, name: String) {
        val dir =
            androidx.test.platform.app.InstrumentationRegistry.getArguments()
                .getString("additionalTestOutputDir") ?: return
        val frame = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        java.io.File(dir, "$name.png").outputStream().use {
            frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun typographyActuallyReflowsWithoutLosingTheCharacterAnchor() {
        open(BookFormat.TXT)
        repeat(3) {
            val before = native.position.value
            rule.onNodeWithContentDescription("Следующая страница").performClick()
            rule.waitUntil(5000) { native.position.value != before }
        }
        val anchor = native.position.value
        rule.onNodeWithText("Aa").performClick()
        click("reader-font-Mono")
        slider("line-height", 180f)
        slider("page-margins", 40f)
        slider("paragraph-spacing", 100f)
        slider("letter-spacing", 5f)
        click("first-line-indent")
        click("text-justify")
        rule.waitUntil(5000) {
            native.layoutKey()?.let {
                it.fontFamily == "monospace" &&
                    it.lineHeight == 180 &&
                    it.justify &&
                    it.firstLineIndent
            } == true
        }
        assertEquals(anchor, native.position.value)
        val geometry = native.layoutKey()
        rule.runOnIdle { model.updatePreferences { it.copy(palette = ReaderPalette.Sepia) } }
        rule.waitUntil(5000) { model.preferences.value?.palette == ReaderPalette.Sepia }
        assertEquals(geometry, native.layoutKey())
        click("comfort-done")
        rule.waitUntilDoesNotExist(hasTestTag("reading-comfort"), 5000)
        rule.waitForIdle()
        shot("native-reader", "comfort-sepia-page")
        assertEquals(anchor, native.position.value)
        assertTrue(native.cacheStats()!!.entries <= 5)
        assertEquals(40, model.preferences.value!!.horizontalMargin)
        assertEquals(100, native.layoutKey()!!.paragraphSpacing)
        assertEquals(5, native.layoutKey()!!.letterSpacing)
    }

    @Test
    fun centerTapFocusAndSwipeHaveAReversibleReadingFlow() {
        open(BookFormat.TXT)
        val anchor = native.position.value
        val height = native.layoutKey()!!.height
        rule.onNodeWithContentDescription("Скрыть панели").performClick()
        rule.waitUntilDoesNotExist(hasText("Aa"), 5000)
        rule.waitUntil(5000) { native.layoutKey()!!.height > height }
        assertEquals(anchor, native.position.value)
        shot("native-reader", "comfort-focus-page")
        pressBack()
        rule.waitUntilExactlyOneExists(hasText("Aa"), 5000)
        rule.waitUntil(5000) {
            native.layoutKey()!!.height == height && !native.render.value.loading
        }
        val before = native.position.value
        rule.onNodeWithTag("native-page").performTouchInput { swipeLeft() }
        rule.waitUntil(5000) { native.position.value != before }
        val after = native.position.value
        rule.onNodeWithTag("native-page").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * .08f, height * .5f))
        }
        rule.waitUntil(5000) { native.position.value != after }
        assertEquals(anchor, native.position.value)
    }

    @Test
    fun brightnessIsWindowScopedAndRestoredAfterLeavingTheBook() {
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10000)
        val original = rule.activity.window.attributes.screenBrightness
        val system =
            Settings.System.getInt(rule.activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        open(BookFormat.TXT)
        rule.onNodeWithText("Aa").performClick()
        click("comfort-tab-1")
        slider("reader-brightness", 25f)
        rule.waitUntil(5000) {
            model.preferences.value?.brightness == 25 &&
                rule.activity.window.attributes.screenBrightness == .25f
        }
        val windows =
            android.view.inspector.WindowInspector.getGlobalWindowViews().mapNotNull {
                it.layoutParams as? WindowManager.LayoutParams
            }
        assertTrue(windows.size >= 2)
        assertTrue(windows.all { it.screenBrightness == .25f })
        click("brightness-gesture")
        shot("reading-comfort", "comfort-light-controls")
        click("comfort-done")
        rule.waitUntilDoesNotExist(hasTestTag("reading-comfort"), 5000)
        val before = model.preferences.value!!.brightness
        rule.onNodeWithTag("brightness-edge").performTouchInput {
            swipe(start = center, end = androidx.compose.ui.geometry.Offset(center.x, height * .2f))
        }
        rule.waitUntil(5000) { model.preferences.value!!.brightness > before }
        rule.onNodeWithContentDescription("Назад в библиотеку").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10000)
        assertEquals(original, rule.activity.window.attributes.screenBrightness)
        assertEquals(
            system,
            Settings.System.getInt(
                rule.activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            ),
        )
    }

    @Test
    fun volumeKeysPageTheBookWithoutChangingMusicVolume() {
        open(BookFormat.TXT)
        val audio = rule.activity.getSystemService(android.media.AudioManager::class.java)
        val level = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        rule.runOnIdle { model.updatePreferences { it.copy(volumePaging = true) } }
        rule.waitUntil(5000) { model.preferences.value?.volumePaging == true }
        val start = native.position.value
        rule.runOnIdle {
            assertTrue(
                rule.activity.dispatchKeyEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)
                )
            )
            assertTrue(
                rule.activity.dispatchKeyEvent(
                    KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN)
                )
            )
        }
        rule.waitUntil(5000) { native.position.value != start }
        rule.runOnIdle {
            rule.activity.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)
            )
            rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP))
        }
        rule.waitUntil(5000) { native.position.value == start }
        assertEquals(level, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
        rule.onNodeWithText("Aa").performClick()
        rule.waitUntil(5000) { model.readerInputBlocked }
    }

    @Test
    fun readingChoicesPersistAcrossActivityRecreation() {
        open(BookFormat.TXT)
        rule.onNodeWithText("Aa").performClick()
        click("preset-large")
        click("comfort-tab-1")
        click("palette-Black")
        click("comfort-tab-2")
        click("volume-paging")
        click("text-morph-enabled")
        rule.waitUntil(5000) {
            model.preferences.value?.let {
                it.fontSize == 28 &&
                    it.palette == ReaderPalette.Black &&
                    it.volumePaging &&
                    !it.animateText
            } == true
        }
        val saved = model.preferences.value!!
        rule.activityRule.scenario.recreate()
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 15000)
        assertEquals(saved, model.preferences.value)
        assertEquals("sans-serif", native.layoutKey()!!.fontFamily)
        rule.onNodeWithText("Aa").performClick()
        click("comfort-tab-0")
        shot("reading-comfort", "comfort-typography-controls")
    }

    private fun epubStyle(): List<org.json.JSONObject> {
        fun webViews(view: android.view.View): List<android.webkit.WebView> =
            when (view) {
                is android.webkit.WebView -> listOf(view)
                is android.view.ViewGroup ->
                    (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) }
                else -> emptyList()
            }
        val result = java.util.concurrent.atomic.AtomicReference<List<org.json.JSONObject>?>(null)
        rule.runOnIdle {
            val views = webViews(rule.activity.window.decorView).filter { it.isShown }
            val values = mutableListOf<org.json.JSONObject>()
            views.forEach { view ->
                view.evaluateJavascript(
                    """
                    (() => {
                        const p = Array.from(document.querySelectorAll('p')).find(p => p.textContent.length > 40);
                        if (!p) return null;
                        const s = getComputedStyle(p);
                        return JSON.stringify({font:s.fontFamily, line:parseFloat(s.lineHeight)/parseFloat(s.fontSize), indent:parseFloat(s.textIndent)});
                    })()
                    """
                        .trimIndent()
                ) { value ->
                    val decoded = org.json.JSONTokener(value).nextValue()
                    values +=
                        if (decoded is String) org.json.JSONObject(decoded)
                        else org.json.JSONObject()
                    if (values.size == views.size) result.set(values.toList())
                }
            }
            if (views.isEmpty()) result.set(emptyList())
        }
        rule.waitUntil(5000) { result.get() != null }
        return result.get()!!
    }

    @Test
    fun epubReceivesTheSameTypographyAndScrollSettings() {
        open(BookFormat.EPUB)
        rule.onNodeWithText("Aa").performClick()
        click("reader-font-Mono")
        slider("line-height", 180f)
        click("first-line-indent")
        rule.waitUntil(5000) {
            (model.opened.value!!.reader as EpubReaderSession)
                .rendition
                .controller
                ?.preferences
                ?.let { it.lineHeight == 1.8 && it.paragraphIndent == 1.2 } == true
        }
        val style =
            (model.opened.value!!.reader as EpubReaderSession).rendition.controller!!.preferences
        assertEquals("monospace", style.fontFamily?.name)
        assertEquals(1.2, style.paragraphIndent!!, 0.0)
        click("reading-scroll")
        rule.waitUntil(5000) {
            (model.opened.value!!.reader as EpubReaderSession)
                .rendition
                .controller
                ?.preferences
                ?.scroll == true
        }
        click("comfort-done")
        rule.waitUntilDoesNotExist(hasTestTag("reading-comfort"), 5000)
        rule.onNodeWithTag("epub-reader").assertIsDisplayed()
        rule.waitUntil(10000) {
            epubStyle().any {
                it.optString("font").contains("monospace") &&
                    kotlin.math.abs(it.optDouble("line") - 1.8) < .02 &&
                    it.optDouble("indent") > 0
            }
        }
    }
}
