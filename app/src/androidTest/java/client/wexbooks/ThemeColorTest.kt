package client.wexbooks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import client.wexbooks.ui.components.WexIcons
import client.wexbooks.ui.theme.WexBooksTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemeColorTest {
    @get:Rule val rule = createComposeRule()

    @Test fun darkThemeProvidesLightTextAndIconsByDefault() = verify(true)

    @Test fun lightThemeProvidesDarkTextAndIconsByDefault() = verify(false)

    private fun verify(dark: Boolean) {
        var inherited = Color.Unspecified
        var expected = Color.Unspecified
        rule.setContent {
            WexBooksTheme(dark) {
                val actual = LocalContentColor.current
                val foreground = MaterialTheme.colorScheme.onBackground
                SideEffect {
                    inherited = actual
                    expected = foreground
                }
                Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    Text("История", Modifier.testTag("color-text"))
                    Icon(WexIcons.Book, null, Modifier.size(48.dp).testTag("color-icon"))
                }
            }
        }
        rule.waitForIdle()
        assertEquals(expected, inherited)
        for (tag in listOf("color-text", "color-icon")) {
            val pixels = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
            var matched = 0
            for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                val p = pixels[x, y]
                if (
                    kotlin.math.abs(p.red - expected.red) < 0.08f &&
                        kotlin.math.abs(p.green - expected.green) < 0.08f &&
                        kotlin.math.abs(p.blue - expected.blue) < 0.08f
                )
                    matched++
            }
            assertTrue("$tag must draw the theme foreground on actual device", matched > 8)
        }
    }
}
