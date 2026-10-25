package client.wexbooks.ui

import client.wexbooks.ui.motion.TextMorph
import org.junit.Assert.*
import org.junit.Test

class TextMorphTest {
    @Test
    fun unchangedCharactersKeepIdentity() {
        assertEquals(
            listOf(TextMorph.Match(0, 0), TextMorph.Match(1, 1)),
            TextMorph.match(listOf("а", "б"), listOf("а", "б")),
        )
    }

    @Test
    fun changedCounterKeepsItsSuffix() {
        val matches = TextMorph.match(listOf("9", "9", "%"), listOf("1", "0", "0", "%"))
        assertTrue(matches.contains(TextMorph.Match(2, 3)))
        assertEquals(2, matches.count { it.new == null })
        assertEquals(3, matches.count { it.old == null })
    }

    @Test
    fun repeatedLettersRemainInOrder() {
        val matches = TextMorph.match(listOf("а", "а", "б"), listOf("а", "б", "а"))
        val pairs = matches.filter { it.old != null && it.new != null }
        assertEquals(pairs.map { it.old }.sortedBy { it }, pairs.map { it.old })
        assertEquals(pairs.map { it.new }.sortedBy { it }, pairs.map { it.new })
    }

    @Test
    fun graphemeClustersAreNotSplitIntoUtf16Characters() {
        val emoji = "👩🏽‍💻"
        assertEquals(
            TextMorph.Match(0, 1),
            TextMorph.match(listOf(emoji), listOf("А", emoji)).last(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun workIsBounded() {
        TextMorph.match(List(TextMorph.MaxGlyphs + 1) { "а" }, emptyList())
    }
}
