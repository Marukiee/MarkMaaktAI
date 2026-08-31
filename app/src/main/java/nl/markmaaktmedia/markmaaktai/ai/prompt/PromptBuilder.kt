package nl.markmaaktmedia.markmaaktai.ai.prompt

import nl.markmaaktmedia.markmaaktai.data.db.WebSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One turn as the model sees it. */
data class PromptTurn(val role: Role, val text: String) {
    enum class Role { USER, ASSISTANT }
}

/** Extra material folded into a prompt before the question itself. */
data class PromptContext(
    val webResults: List<WebSource> = emptyList(),
    val notificationLines: List<String> = emptyList(),
    val screenText: String = "",
    val imageText: String = "",
    /** Where the attached photo was taken, read from its own metadata. */
    val photoPlace: String = "",
)

/**
 * Turns a conversation plus its context into the single string the runtime gets.
 *
 * Deliberately a plain readable transcript rather than one model's chat template.
 * The catalogue mixes Gemma, Llama and Qwen builds and every one of them ships its
 * own tokens, so a neutral format is the one thing that behaves the same across all
 * of them. Everything the model is allowed to lean on is stated in the prompt, so a
 * 1B model has no room to guess.
 */
object PromptBuilder {

    private const val MAX_HISTORY_TURNS = 12
    private const val MAX_SNIPPET_CHARS = 480
    private const val MAX_SCREEN_CHARS = 2400

    private val systemPrompt = """
        You are MarkMaaktAI, an assistant running entirely on the user's own phone.
        Answer in the language the user writes in. Be direct and concrete, skip the
        preamble, and keep answers short unless detail was asked for. When context is
        supplied below, use it and say so plainly. If the context does not cover the
        question, say what is missing instead of inventing an answer.

        With a photo, describe what is actually in it. Do not name a place, a building,
        a person or a date unless the picture shows it in writing. A guess stated as a
        fact is worse than saying the picture does not show it.
    """.trimIndent()

    fun buildChat(
        history: List<PromptTurn>,
        question: String,
        context: PromptContext = PromptContext(),
    ): String = buildString {
        appendLine(systemPrompt)
        appendLine()
        appendLine("Today is ${today()}.")
        appendLine()

        if (context.webResults.isNotEmpty()) {
            appendLine("Web results:")
            context.webResults.forEachIndexed { index, source ->
                appendLine("[${index + 1}] ${source.title} (${source.url})")
                appendLine(source.snippet.take(MAX_SNIPPET_CHARS))
            }
            appendLine("Cite the results you used as [1], [2] and so on.")
            appendLine()
        }

        if (context.notificationLines.isNotEmpty()) {
            appendLine("Notifications on this phone that match the question:")
            context.notificationLines.forEach { appendLine("- $it") }
            appendLine("Only answer from these lines. Say so if the answer is not among them.")
            appendLine()
        }

        if (context.screenText.isNotBlank()) {
            appendLine("Text currently on the user's screen:")
            appendLine(context.screenText.take(MAX_SCREEN_CHARS))
            appendLine()
        }

        if (context.photoPlace.isNotBlank()) {
            appendLine("The attached photo was taken at: ${context.photoPlace}.")
            appendLine("This came from the photo's own metadata, so it is reliable.")
            appendLine()
        }

        if (context.imageText.isNotBlank()) {
            appendLine("Text found in the attached image:")
            appendLine(context.imageText.take(MAX_SCREEN_CHARS))
            appendLine()
        }

        history.takeLast(MAX_HISTORY_TURNS).forEach { turn ->
            val label = if (turn.role == PromptTurn.Role.USER) "User" else "Assistant"
            appendLine("$label: ${turn.text}")
        }

        appendLine("User: $question")
        append("Assistant:")
    }

    /**
     * Asks for what is visible, and nothing else.
     *
     * No question, no history and no room to speculate. The answer is fed to a search
     * engine, so a guessed place name in it would poison the query it is meant to
     * build.
     */
    fun buildImageDescription(): String = """
        Describe only what is visible in this image, in one sentence.
        Name the objects, materials, colours, and any words written in the picture.
        Do not name a place, a building, a person or a date.
        Assistant:
    """.trimIndent()

    /**
     * Prompt for the background summariser. Asks for one JSON object and nothing
     * else, which small models manage as long as the shape is spelled out and an
     * example is given.
     */
    fun buildSummary(appLabel: String, messages: List<String>): String = buildString {
        appendLine("You turn a burst of phone notifications into one short summary.")
        appendLine("Reply with a single JSON object and no other text.")
        appendLine()
        appendLine("Shape:")
        appendLine("""{"summary": "one or two sentences", "is_urgent": true or false, "action_items": ["short imperative", "..."], "category": "message|email|calendar|finance|delivery|social|system|other"}""")
        appendLine()
        appendLine("Rules:")
        appendLine("- Write the summary in the language of the messages.")
        appendLine("- is_urgent is true only for a deadline today, a payment, a cancellation or someone waiting on a reply now.")
        appendLine("- action_items holds at most three items and is empty when nothing is asked of the user.")
        appendLine()
        appendLine("Notifications from $appLabel:")
        messages.forEach { appendLine("- $it") }
        appendLine()
        append("JSON:")
    }

    /** Prompt for the reply drafts offered on an urgent notification. */
    fun buildReplyDraft(appLabel: String, messages: List<String>): String = buildString {
        appendLine("Write one short reply the user could send back, in the language of the messages.")
        appendLine("Plain sentence, no greeting, no signature, no quotes around it, at most 25 words.")
        appendLine()
        appendLine("Conversation in $appLabel:")
        messages.forEach { appendLine("- $it") }
        appendLine()
        append("Reply:")
    }

    /** Prompt used to name a conversation after the first exchange. */
    fun buildTitle(firstMessage: String): String = buildString {
        appendLine("Give this conversation a title of at most four words.")
        appendLine("Reply with the title only, no quotes and no full stop.")
        appendLine()
        appendLine("First message: ${firstMessage.take(400)}")
        append("Title:")
    }

    private fun today(): String =
        SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault()).format(Date())
}
