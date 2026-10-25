package client.wexbooks.reader

import client.wexbooks.data.*
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class ReadingComfortTest {
    @Test
    fun oldDefaultsRemainReadableAndBrightnessStaysSystemControlled() {
        val prefs = ReaderPreferences()
        assertEquals(22, prefs.fontSize)
        assertEquals(155, prefs.lineHeight)
        assertEquals(26, prefs.horizontalMargin)
        assertEquals(ReaderFont.Serif, prefs.font)
        assertEquals(-1, prefs.brightness)
        assertFalse(prefs.volumePaging)
        assertTrue(prefs.keepScreenOn)
    }

    @Test
    fun allStoredDimensionsAreBounded() {
        val p =
            ReaderPreferences(
                    fontSize = 999,
                    lineHeight = -1,
                    horizontalMargin = -100,
                    paragraphSpacing = 1000,
                    letterSpacing = -50,
                    brightness = 0,
                )
                .normalized()
        assertEquals(40, p.fontSize)
        assertEquals(125, p.lineHeight)
        assertEquals(12, p.horizontalMargin)
        assertEquals(120, p.paragraphSpacing)
        assertEquals(0, p.letterSpacing)
        assertEquals(5, p.brightness)
        assertEquals(-1, p.copy(brightness = -500).normalized().brightness)
    }

    @Test
    fun tapLayoutsHaveAnUnambiguousControlZone() {
        assertEquals(ReaderTap.Previous, readerTap(.1f, TapLayout.Classic))
        assertEquals(ReaderTap.Next, readerTap(.9f, TapLayout.Classic))
        assertEquals(ReaderTap.Controls, readerTap(.5f, TapLayout.Classic))
        assertEquals(ReaderTap.Next, readerTap(.6f, TapLayout.RightHand))
        assertEquals(ReaderTap.Next, readerTap(.4f, TapLayout.LeftHand))
        for (x in listOf(0f, .5f, 1f)) assertEquals(
            ReaderTap.Controls,
            readerTap(x, TapLayout.ButtonsOnly),
        )
    }

    @Test
    fun typographyResetKeepsPersonalControlsAndLight() {
        val before =
            ReaderPreferences(
                fontSize = 38,
                font = ReaderFont.Mono,
                palette = ReaderPalette.Black,
                brightness = 15,
                volumePaging = true,
                lineHeight = 195,
            )
        val after = before.resetTypography()
        assertEquals(22, after.fontSize)
        assertEquals(ReaderFont.Serif, after.font)
        assertEquals(ReaderPalette.Black, after.palette)
        assertEquals(15, after.brightness)
        assertTrue(after.volumePaging)
    }

    @Test
    fun bothRenderersReceiveTheSameSemanticStyle() {
        val prefs =
            ReaderPreferences(
                fontSize = 28,
                font = ReaderFont.Sans,
                palette = ReaderPalette.Sepia,
                lineHeight = 180,
                horizontalMargin = 40,
                letterSpacing = 5,
                paragraphSpacing = 20,
                firstLineIndent = true,
                justify = true,
            )
        val style = prefs.readingStyle(true)
        assertEquals("sans-serif", style.fontFamily)
        assertEquals(1.8, style.lineHeight, 0.0)
        assertEquals(.05, style.letterSpacing, 0.0)
        assertEquals(.2, style.paragraphSpacing, 0.0)
        assertTrue(style.firstLineIndent)
        assertTrue(style.justify)
        assertFalse(style.darkTheme)
        assertEquals(ReaderPalette.Sepia.colors(false).background, style.backgroundColor)
    }

    @Test
    fun nightTogglePreservesTheApplicationThemeForExplicitPaper() {
        val p =
            ReaderPreferences(darkTheme = false, palette = ReaderPalette.Sepia)
                .toggleReaderNight(false)
        assertEquals(false, p.darkTheme)
        assertEquals(ReaderPalette.Night, p.palette)
        assertEquals(true, ReaderPreferences().toggleReaderNight(false).darkTheme)
    }

    @Test
    fun allReadingPalettesHaveStrongTextContrast() {
        fun l(color: Int): Double {
            fun c(shift: Int): Double {
                val x = ((color ushr shift) and 255) / 255.0
                return if (x <= .04045) x / 12.92 else ((x + .055) / 1.055).pow(2.4)
            }
            return .2126 * c(16) + .7152 * c(8) + .0722 * c(0)
        }
        for (p in ReaderPalette.entries) for (dark in listOf(false, true)) {
            val colors = p.colors(dark)
            val a = l(colors.background)
            val b = l(colors.ink)
            assertTrue("$p body contrast", (maxOf(a, b) + .05) / (minOf(a, b) + .05) >= 7)
        }
    }
}
