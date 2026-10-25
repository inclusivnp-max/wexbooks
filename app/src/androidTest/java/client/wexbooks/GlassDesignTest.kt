package client.wexbooks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.WindowInsetsControllerCompat
import org.junit.Rule
import org.junit.Test

class GlassDesignTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun themeUpdatesSystemBarContrast() {
        var initiallyLight = false
        rule.runOnIdle {
            initiallyLight =
                WindowInsetsControllerCompat(
                        rule.activity.window,
                        rule.activity.window.decorView,
                    )
                    .isAppearanceLightStatusBars
        }
        rule.onNodeWithContentDescription("Сменить тему").performClick()
        rule.runOnIdle {
            val controller =
                WindowInsetsControllerCompat(
                    rule.activity.window,
                    rule.activity.window.decorView,
                )
            org.junit.Assert.assertEquals(!initiallyLight, controller.isAppearanceLightStatusBars)
            org.junit.Assert.assertEquals(
                !initiallyLight,
                controller.isAppearanceLightNavigationBars,
            )
        }
    }

    @Test
    fun readingDemoOpensAndFontControlsWork() {
        rule.onNodeWithTag("library-screen").assertIsDisplayed()
        rule.onNodeWithTag("open-reading-demo").performClick()
        rule.onNodeWithTag("reading-demo-screen").assertIsDisplayed()
        rule.onNodeWithTag("font-size").assertTextEquals("22")
        rule.onNodeWithContentDescription("Увеличить шрифт").performClick()
        rule.onNodeWithTag("font-size").assertTextEquals("24")
        rule.onNodeWithContentDescription("Назад в библиотеку").performClick()
        rule.onNodeWithTag("library-screen").assertIsDisplayed()
    }

    @Test
    fun reducedTransparencySurvivesActivityRecreation() {
        rule.onNodeWithTag("tab-2").performClick()
        rule.onNodeWithTag("reduce-transparency-switch").performClick()
        rule.onNodeWithTag("reduce-transparency-switch").assertIsOn()
        rule.onNodeWithTag("reduce-motion-switch").performScrollTo().performClick()
        rule.onNodeWithTag("reduce-motion-switch").assertIsOn()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("settings-screen").assertIsDisplayed()
        rule.onNodeWithTag("reduce-transparency-switch").assertIsOn()
        rule.onNodeWithTag("reduce-motion-switch").assertIsOn()
        rule.onNodeWithTag("tab-1").performClick()
        rule.onNodeWithTag("notes-screen").assertIsDisplayed()
    }

    @Test
    fun notesTabShowsHonestEmptyState() {
        rule.onNodeWithTag("tab-1").performClick()
        rule.onNodeWithTag("notes-screen").assertIsDisplayed()
    }

    @Test
    fun interruptedTabAnimationSettlesOnTheLastSelection() {
        rule.mainClock.autoAdvance = false
        try {
            val start = rule.onNodeWithTag("tab-indicator").fetchSemanticsNode().positionInRoot.x
            rule.onNodeWithTag("tab-1").performClick()
            rule.mainClock.advanceTimeBy(64)
            val intermediate =
                rule.onNodeWithTag("tab-indicator").fetchSemanticsNode().positionInRoot.x
            org.junit.Assert.assertTrue(
                "Indicator actually moves, rather than jumping",
                intermediate > start,
            )
            rule.onNodeWithTag("tab-2").performClick()
            rule.mainClock.advanceTimeBy(1000)
            rule.onNodeWithTag("settings-screen").assertIsDisplayed()
            rule.onNodeWithTag("tab-2").assertIsSelected()
            val end = rule.onNodeWithTag("tab-indicator").fetchSemanticsNode().positionInRoot.x
            org.junit.Assert.assertTrue("Latest interaction wins", end > intermediate)
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }
}
