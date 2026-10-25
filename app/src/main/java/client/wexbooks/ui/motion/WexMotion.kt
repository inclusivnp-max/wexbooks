package client.wexbooks.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.staticCompositionLocalOf

val LocalReducedMotion = staticCompositionLocalOf { false }

/** One motion vocabulary: response first, short settling, no idle animation loop. */
object WexMotion {
    fun <T> response(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(160, easing = FastOutSlowInEasing)

    fun <T> springResponse(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.82f, stiffness = 500f)

    fun pageTransition(reduced: Boolean, forward: Boolean): ContentTransform {
        if (reduced) return fadeIn(snap()) togetherWith fadeOut(snap())
        val direction = if (forward) 1 else -1
        val enter =
            fadeIn(tween(200)) +
                slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) {
                    direction * it / 18
                }
        val exit =
            fadeOut(tween(110)) +
                slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) {
                    -direction * it / 24
                }
        return enter togetherWith exit
    }
}
