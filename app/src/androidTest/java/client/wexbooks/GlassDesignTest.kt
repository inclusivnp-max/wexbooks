@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package client.wexbooks

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.espresso.Espresso.pressBack
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GlassDesignTest {
    @get:Rule(order = 0) val reset = ReaderTestSetup()

    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun awaitLoadedPreferences() {
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10_000)
    }

    private fun waitChecked(tag: String) {
        rule.waitUntil(5_000) {
            rule
                .onNodeWithTag(tag)
                .fetchSemanticsNode()
                .config[SemanticsProperties.ToggleableState] == ToggleableState.On
        }
    }

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
        rule.waitUntil(5_000) {
            WindowInsetsControllerCompat(rule.activity.window, rule.activity.window.decorView)
                .isAppearanceLightStatusBars != initiallyLight
        }
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
    fun actualEpubOpensAndFontControlsWork() {
        rule.onNodeWithTag("open-reading-demo").performScrollTo().performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("reader-ready"), 30_000)
        rule.onNodeWithTag("epub-reader").assertIsDisplayed()
        rule.onNodeWithText("Aa").performClick()
        rule.onNodeWithTag("font-size").assertTextEquals("22")
        rule.onNodeWithTag("font-increase").performClick()
        rule.waitUntil(5_000) {
            rule
                .onNodeWithTag("font-size")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .first()
                .text == "24"
        }
        pressBack()
        rule.onNodeWithContentDescription("Назад в библиотеку").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("library-screen"), 10_000)
    }

    @Test
    fun reducedTransparencySurvivesActivityRecreation() {
        rule.onNodeWithTag("tab-2").performClick()
        rule.onNodeWithTag("reduce-transparency-switch").performClick()
        waitChecked("reduce-transparency-switch")
        rule.onNodeWithTag("reduce-motion-switch").performScrollTo().performClick()
        waitChecked("reduce-motion-switch")
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("settings-screen").assertIsDisplayed()
        waitChecked("reduce-transparency-switch")
        waitChecked("reduce-motion-switch")
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
