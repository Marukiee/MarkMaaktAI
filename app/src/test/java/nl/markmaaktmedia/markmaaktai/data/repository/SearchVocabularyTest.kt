package nl.markmaaktmedia.markmaaktai.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchVocabularyTest {

    @Test
    fun `vliegtickets finds the words a boarding pass actually contains`() {
        val expanded = SearchVocabulary.expand(listOf("vliegtickets"))
        assertEquals("vliegtickets", expanded.first())
        assertTrue(expanded.containsAll(listOf("vlucht", "boarding", "gate")))
    }

    @Test
    fun `an unknown word is left exactly as it was typed`() {
        assertEquals(listOf("zwembroek"), SearchVocabulary.expand(listOf("zwembroek")))
    }

    @Test
    fun `a kind of thing points at the category it was filed under`() {
        assertEquals("travel", SearchVocabulary.categoryFor(listOf("vliegtickets")))
        assertEquals("finance", SearchVocabulary.categoryFor(listOf("facturen")))
        assertEquals(null, SearchVocabulary.categoryFor(listOf("zwembroek")))
    }
}
