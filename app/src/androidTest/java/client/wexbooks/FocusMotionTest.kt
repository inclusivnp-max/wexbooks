@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package client.wexbooks

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import client.wexbooks.data.ReaderPalette
import client.wexbooks.reader.BooksViewModel
import client.wexbooks.reader.core.BookFormat
import client.wexbooks.reader.nativeengine.NativeReaderSession
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FocusMotionTest {
    @get:Rule(order = 0) val reset = ReaderTestSetup()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()
    private val model
        get() = ViewModelProvider(rule.activity)[BooksViewModel::class.java]

    private fun moving() =
        rule
            .onNodeWithTag("reader-viewport")
            .fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription] == "Изменение области чтения"

    private fun top() =
        rule.onNodeWithTag("reader-viewport-content").fetchSemanticsNode().boundsInRoot.top

    private fun open(format: BookFormat) {
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10000)
        rule.runOnIdle {
            model.updatePreferences { it.copy(palette = ReaderPalette.Paper) }
            model.openSample(format)
        }
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 20000)
        if (format == BookFormat.EPUB) {
            rule.onNodeWithContentDescription("Оглавление").performClick()
            rule.onNodeWithContentDescription("Открыть главу: Искусство не спешить").performClick()
            rule.waitForIdle()
        }
    }

    private fun frame(name: String): Bitmap {
        val result = AtomicInteger(-1)
        val bitmap =
            Bitmap.createBitmap(
                rule.activity.window.decorView.width,
                rule.activity.window.decorView.height,
                Bitmap.Config.ARGB_8888,
            )
        rule.runOnIdle {
            PixelCopy.request(
                rule.activity.window,
                bitmap,
                { result.set(it) },
                Handler(Looper.getMainLooper()),
            )
        }
        rule.waitUntil(5000) { result.get() != -1 }
        assertEquals(PixelCopy.SUCCESS, result.get())
        androidx.test.platform.app.InstrumentationRegistry.getArguments()
            .getString("additionalTestOutputDir")
            ?.let {
                File(it, "$name.png").outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            }
        return bitmap
    }

    private fun animatedFocus(format: BookFormat) {
        open(format)
        val oldTop = top()
        val native = model.opened.value!!.reader as? NativeReaderSession
        val anchor = native?.position?.value
        val heights = mutableSetOf<Int>()
        var observations: kotlinx.coroutines.Job? = null
        if (native != null)
            rule.runOnIdle {
                observations =
                    rule.activity.lifecycleScope.launch {
                        native.render.collect { native.layoutKey()?.height?.let { heights += it } }
                    }
            }
        val prefix = format.name.lowercase()
        frame("$prefix-focus-before")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Скрыть панели").performClick()
        rule.waitUntil(5000) {
            rule.mainClock.advanceTimeByFrame()
            moving()
        }
        rule.mainClock.advanceTimeBy(32)
        val middleTop = top()
        assertTrue("Text must start moving, not wait and jump", middleTop < oldTop)
        val intermediate = frame("$prefix-focus-moving")
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.waitUntil(5000) { !moving() }
        val finalTop = top()
        assertTrue("Intermediate placement is distinct from the endpoint", middleTop > finalTop + 1)
        val cutout =
            rule.activity.window.decorView.rootWindowInsets.displayCutout?.safeInsetTop ?: 0
        val gap = 12 * rule.activity.resources.displayMetrics.density
        assertTrue(
            "Camera cutout plus breathing room: top=$finalTop cutout=$cutout",
            finalTop >= cutout + gap - 1,
        )
        val settled = frame("$prefix-focus-after")
        var different = 0
        for (y in settled.height / 2 until settled.height - 60 step 4) for (x in
            80 until settled.width - 80 step 4) {
            if (intermediate.getPixel(x, y) != settled.getPixel(x, y)) different++
        }
        assertTrue(
            "Bottom ink must be revealed over time, not appear in one layout jump",
            different > 100,
        )
        if (native != null) assertEquals(anchor, native.position.value)
        androidx.test.espresso.Espresso.pressBack()
        rule.waitForIdle()
        rule.waitUntil(5000) { !moving() }
        assertEquals(oldTop, top(), 1f)
        if (native != null) assertEquals(anchor, native.position.value)
        rule.onNodeWithText("Aa").assertIsDisplayed()
        if (native != null)
            rule.runOnIdle {
                observations?.cancel()
                assertEquals("Only two endpoint heights, never 60Hz repagination", 2, heights.size)
            }
    }

    @Test fun nativeTextSlidesAndRevealsBelowTheCamera() = animatedFocus(BookFormat.TXT)

    @Test fun epubTextSlidesAndRevealsBelowTheCamera() = animatedFocus(BookFormat.EPUB)

    @Test
    fun landscapeCameraAlsoStaysOutsideTheText() {
        open(BookFormat.TXT)
        try {
            rule.runOnIdle {
                rule.activity.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            rule.waitUntil(10000) {
                rule.activity.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 15000)
            rule.onNodeWithContentDescription("Скрыть панели").performClick()
            rule.waitForIdle()
            rule.waitUntil(5000) { !moving() }
            val cutout = rule.activity.window.decorView.rootWindowInsets.displayCutout
            val bounds =
                rule.onNodeWithTag("reader-viewport-content").fetchSemanticsNode().boundsInRoot
            val gap = 12 * rule.activity.resources.displayMetrics.density
            if ((cutout?.safeInsetLeft ?: 0) > 0)
                assertTrue(bounds.left >= cutout!!.safeInsetLeft + gap - 1)
            if ((cutout?.safeInsetRight ?: 0) > 0)
                assertTrue(
                    bounds.right <=
                        rule.activity.window.decorView.width - cutout!!.safeInsetRight - gap + 1
                )
            frame("landscape-camera-safe")
        } finally {
            rule.runOnIdle {
                rule.activity.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Test
    fun pageTurnDuringCollapseWaitsForTheShortPageBoundary() {
        open(BookFormat.TXT)
        val reader = model.opened.value!!.reader as NativeReaderSession
        val expectedNext = reader.render.value.page!!.end
        val start = reader.position.value
        rule.onNodeWithTag("native-page").performTouchInput { click(center) }
        rule.waitForIdle()
        rule.waitUntil(5000) { !moving() }
        rule.onNodeWithText("Aa").assertDoesNotExist()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5000) {
            rule.mainClock.advanceTimeByFrame()
            moving()
        }
        rule.runOnIdle { model.volumePage(true) }
        rule.mainClock.advanceTimeBy(32)
        assertEquals(
            "Do not turn using the previous fullscreen boundary",
            start,
            reader.position.value,
        )
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.waitUntil(5000) { reader.position.value != start }
        assertEquals(expectedNext, reader.position.value)
    }

    @Test
    fun reducedMotionSnapsToASafeViewport() {
        open(BookFormat.TXT)
        rule.runOnIdle { model.updatePreferences { it.copy(reduceMotion = true) } }
        rule.waitUntil(5000) { model.preferences.value?.reduceMotion == true }
        rule.onNodeWithContentDescription("Скрыть панели").performClick()
        rule.waitForIdle()
        assertFalse(moving())
        val cutout =
            rule.activity.window.decorView.rootWindowInsets.displayCutout?.safeInsetTop ?: 0
        assertTrue(top() >= cutout + 12 * rule.activity.resources.displayMetrics.density - 1)
    }
}
