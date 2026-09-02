package nl.markmaaktmedia.markmaaktai.ai.prompt

import nl.markmaaktmedia.markmaaktai.data.db.WebSource
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBudgetTest {

    @Test
    fun `the answer never eats more than a third of the context`() {
        val budget = PromptBudget.forContext(contextTokens = 1280, requestedAnswerTokens = 1024)
        assertTrue(budget.answerTokens <= 1280 / 3)
        assertTrue(budget.promptChars > 0)
    }

    @Test
    fun `a prompt with every source still fits the smallest model`() {
        val budget = PromptBudget.forContext(contextTokens = 1280, requestedAnswerTokens = 640)
        val prompt = PromptBuilder.buildChat(
            history = (1..20).map { PromptTurn(PromptTurn.Role.USER, "Een eerdere vraag nummer $it") },
            question = "Wat moet ik vandaag nog doen?",
            context = PromptContext(
                webResults = (1..6).map {
                    WebSource("Resultaat $it", "https://example.org/$it", "x".repeat(600))
                },
                notificationLines = (1..20).map { "App $it, Iemand: " + "y".repeat(300) },
                screenText = "z".repeat(4000),
                imageText = "w".repeat(4000),
            ),
            budget = budget,
        )
        assertTrue(
            "prompt was ${prompt.length} chars, budget ${budget.promptChars}",
            prompt.length <= budget.promptChars,
        )
    }

    @Test
    fun `the question itself always survives`() {
        val budget = PromptBudget.forContext(contextTokens = 1280, requestedAnswerTokens = 640)
        val question = "Waar is dit?"
        val prompt = PromptBuilder.buildChat(
            history = emptyList(),
            question = question,
            context = PromptContext(notificationLines = (1..80).map { "Regel $it " + "y".repeat(200) }),
            budget = budget,
        )
        assertTrue(prompt.contains(question))
        assertTrue(prompt.trimEnd().endsWith("Assistant:"))
    }
}
