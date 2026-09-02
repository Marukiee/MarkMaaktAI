package nl.markmaaktmedia.markmaaktai.ai.prompt

import nl.markmaaktmedia.markmaaktai.data.db.WebSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun `puts the question last so the model answers it`() {
        val prompt = PromptBuilder.buildChat(
            history = listOf(PromptTurn(PromptTurn.Role.USER, "Earlier question")),
            question = "What time is the meeting?",
        )
        assertTrue(prompt.trimEnd().endsWith("Assistant:"))
        assertTrue(prompt.contains("User: What time is the meeting?"))
    }

    @Test
    fun `only mentions web results when there are some`() {
        val without = PromptBuilder.buildChat(emptyList(), "Hello")
        assertFalse(without.contains("Web results:"))

        val with = PromptBuilder.buildChat(
            history = emptyList(),
            question = "Hello",
            context = PromptContext(
                webResults = listOf(WebSource("Title", "https://example.org", "Snippet")),
            ),
        )
        assertTrue(with.contains("Web results:"))
        // The host is what reaches the model; the full URL is only used by the UI.
        assertTrue(with.contains("example.org"))
    }

    @Test
    fun `keeps the history short enough to fit a small context window`() {
        val history = (1..40).map { PromptTurn(PromptTurn.Role.USER, "Message number $it") }
        val prompt = PromptBuilder.buildChat(history, "And now?")
        assertFalse(prompt.contains("Message number 1\n"))
        assertTrue(prompt.contains("Message number 40"))
    }

    @Test
    fun `the summary prompt spells out the shape and the rules`() {
        val prompt = PromptBuilder.buildSummary("Signal", listOf("Tom: are we still on?"))
        assertTrue(prompt.contains("is_urgent"))
        assertTrue(prompt.contains("action_items"))
        assertTrue(prompt.contains("Tom: are we still on?"))
        assertTrue(prompt.trimEnd().endsWith("JSON:"))
    }
}
