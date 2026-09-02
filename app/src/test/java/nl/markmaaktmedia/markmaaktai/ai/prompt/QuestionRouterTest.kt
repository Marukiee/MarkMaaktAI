package nl.markmaaktmedia.markmaaktai.ai.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionRouterTest {

    @Test
    fun `a question about the world does not drag the phone into it`() {
        val route = QuestionRouter.route("Hoe hard gaat de Python in de Efteling?", hasImage = false)
        assertFalse(route.usePhoneContext)
        assertTrue(route.useWeb)
    }

    @Test
    fun `a question about notifications reads the phone and not the web`() {
        val route = QuestionRouter.route("Heb ik nog een appje van Sanne gemist?", hasImage = false)
        assertTrue(route.usePhoneContext)
        assertFalse(route.useWeb)
    }

    @Test
    fun `summarise my day is a recap`() {
        listOf("Vat mijn dag samen", "Summarise my day", "Wat heb ik gemist?").forEach { question ->
            val route = QuestionRouter.route(question, hasImage = false)
            assertTrue(question, route.wantsRecap)
            assertTrue(question, route.usePhoneContext)
            assertFalse(question, route.useWeb)
        }
    }

    @Test
    fun `the search query loses the politeness and keeps the subject`() {
        assertEquals(
            "hoe hard gaat de python in de efteling",
            QuestionRouter.searchQuery("Kun je zoeken hoe hard gaat de Python in de Efteling?"),
        )
    }

    @Test
    fun `content terms drop the words that match everything`() {
        val terms = QuestionRouter.contentTerms("Hoe hard gaat de Python in de Efteling?")
        assertEquals(listOf("python", "efteling"), terms)
    }

    @Test
    fun `a question with nothing but filler yields no terms`() {
        assertTrue(QuestionRouter.contentTerms("Wat is dat dan?").isEmpty())
    }
}
