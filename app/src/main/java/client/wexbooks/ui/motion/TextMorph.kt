package client.wexbooks.ui.motion

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Independent bounded sequence matcher; matching characters keep their identity during a change.
 */
object TextMorph {
    const val DurationMillis = 280
    const val MaxGlyphs = 1200
    val Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    data class Match(val old: Int?, val new: Int?)

    fun match(old: List<String>, new: List<String>): List<Match> {
        require(old.size <= MaxGlyphs && new.size <= MaxGlyphs)
        // Readers call this per line; short labels use the same deterministic matcher.
        val width = new.size + 1
        val lengths = IntArray((old.size + 1) * width)
        for (i in old.lastIndex downTo 0) for (j in new.lastIndex downTo 0) {
            lengths[i * width + j] =
                if (old[i] == new[j]) 1 + lengths[(i + 1) * width + j + 1]
                else maxOf(lengths[(i + 1) * width + j], lengths[i * width + j + 1])
        }
        val result = ArrayList<Match>()
        var i = 0
        var j = 0
        while (i < old.size || j < new.size) {
            when {
                i < old.size && j < new.size && old[i] == new[j] -> result += Match(i++, j++)
                j >= new.size ||
                    i < old.size && lengths[(i + 1) * width + j] >= lengths[i * width + j + 1] ->
                    result += Match(i++, null)
                else -> result += Match(null, j++)
            }
        }
        return result
    }
}
