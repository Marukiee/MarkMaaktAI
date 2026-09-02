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
 *
 * The other half of the job is fitting. These models hold 1280 tokens for the prompt
 * and the answer together, and going over does not raise an error, it returns an
 * empty string. So the context is measured and cut to a [PromptBudget] before it is
 * sent, in order of what the question is likely to need.
 */
object PromptBuilder {

    private const val MAX_HISTORY_TURNS = 8
    private const val MAX_SNIPPET_CHARS = 320
    private const val MAX_SCREEN_CHARS = 1200

    /**
     * The house rules.
     *
     * Two of these are here because of a specific wrong answer. "Read the whole
     * question" is what stops a word being answered on its own: asked how fast the
     * Python in the Efteling goes, the model had latched onto `python` and explained
     * a programming language. "Context is reference material" is what stops an
     * unrelated notification being treated as the answer just because it was in the
     * prompt.
     */
    private val systemPrompt = """
        You are MarkMaaktAI, an assistant running entirely on the user's own phone.

        How to answer:
        - Reply in the language the user wrote in.
        - Answer the question that was asked, and only that. No preamble, no repeating
          the question back, no offer to help further.
        - Two or three sentences unless more was asked for.
        - Read the whole question before deciding what a word in it means. A word means
          whatever fits the rest of the sentence, not whatever it usually means on its
          own.
        - If you do not know, say so in one sentence. Never invent a number, a name, a
          price or a date. A wrong fact stated plainly is the worst thing you can do.

        The blocks below the line are reference material, not part of the question.
        Some of it may be about something else entirely. Use only what clearly answers
        the question and ignore the rest. Never join two unrelated things from it into
        one answer.
    """.trimIndent()

    /**
     * Builds the chat prompt, trimmed to fit.
     *
     * The order of the blocks is the order they are worth: what the photo itself
     * says, then the web, then what is on the phone, then the transcript so far. When
     * the room runs out it is the oldest turn that goes.
     */
    fun buildChat(
        history: List<PromptTurn>,
        question: String,
        context: PromptContext = PromptContext(),
        budget: PromptBudget = PromptBudget.Unknown,
    ): String {
        val head = buildString {
            appendLine(systemPrompt)
            appendLine()
            appendLine("Today is ${today()}.")
        }
        val tail = buildString {
            appendLine("User: $question")
            append("Assistant:")
        }

        val sections = buildList {
            if (context.photoPlace.isNotBlank()) {
                add(
                    PromptSection(
                        header = "Where the attached photo was taken:",
                        lines = listOf(context.photoPlace),
                        // Read out of the photo's own metadata, so it is the one thing
                        // here that is not a guess. It is never the block that gets cut.
                        weight = 1f,
                        minChars = 20,
                        footer = "This came from the photo itself, so it is reliable.",
                    )
                )
            }

            if (context.webResults.isNotEmpty()) {
                add(
                    PromptSection(
                        header = "Web results:",
                        lines = context.webResults.mapIndexed { index, source ->
                            val snippet = source.snippet.take(MAX_SNIPPET_CHARS).clean()
                            // The host, not the full URL. It is what tells a model
                            // whether a claim came from a newspaper or from a forum,
                            // and the path after it is thirty tokens of nothing.
                            "[${index + 1}] ${source.title.clean()} (${source.url.host()}) $snippet"
                        },
                        weight = 4f,
                        footer = "Cite a result you used as [1], [2]. Say so if none of " +
                            "them answer the question.",
                    )
                )
            }

            if (context.imageText.isNotBlank()) {
                add(
                    PromptSection(
                        header = "Text read from the attached image:",
                        lines = context.imageText.take(MAX_SCREEN_CHARS).lines(),
                        weight = 3f,
                    )
                )
            }

            if (context.notificationLines.isNotEmpty()) {
                add(
                    PromptSection(
                        header = "From this phone:",
                        lines = context.notificationLines,
                        weight = 3f,
                        footer = "Use a line only if it is about the question.",
                    )
                )
            }

            if (context.screenText.isNotBlank()) {
                add(
                    PromptSection(
                        header = "On the user's screen right now:",
                        lines = context.screenText.take(MAX_SCREEN_CHARS).lines(),
                        weight = 2f,
                    )
                )
            }

            val turns = history.takeLast(MAX_HISTORY_TURNS)
            if (turns.isNotEmpty()) {
                add(
                    PromptSection(
                        header = "Earlier in this conversation:",
                        // Newest last, but cut from the front, so what survives is what
                        // was said most recently.
                        lines = turns.reversed().map { turn ->
                            val label = if (turn.role == PromptTurn.Role.USER) "User" else "Assistant"
                            "$label: ${turn.text}"
                        },
                        weight = 2f,
                    )
                )
            }
        }

        // The one is the blank line written between the last block and the question.
        val room = budget.promptChars - head.length - tail.length - 1
        val fitted = fitSections(sections, room)

        return buildString {
            append(head)
            fitted.forEach { section ->
                appendLine()
                appendLine(section.header)
                val lines = if (section.header.startsWith("Earlier")) {
                    section.lines.reversed()
                } else {
                    section.lines
                }
                lines.forEach { appendLine(it) }
                if (section.footer.isNotBlank()) appendLine(section.footer)
            }
            appendLine()
            append(tail)
        }
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
     * Prompt for the background summariser.
     *
     * Written the long way round on purpose. Small models will answer the shape of a
     * prompt rather than its intent, so the shape is spelled out, the urgent test is
     * a list of cases instead of the word "urgent", and a worked example is given
     * with `is_urgent` false, because an example that says true teaches the model
     * that true is the expected answer and everything comes back urgent.
     */
    fun buildSummary(appLabel: String, messages: List<String>): String = buildString {
        appendLine("Summarise a burst of phone notifications.")
        appendLine("Answer with one JSON object and nothing before or after it.")
        appendLine()
        appendLine("Fields:")
        appendLine("""  "summary": what happened, in the language of the messages, at most two sentences.""")
        appendLine("""  "is_urgent": true or false, by the test below.""")
        appendLine("""  "action_items": up to three things the user has to do, each starting with a verb.""")
        appendLine("""  "category": one of message, email, calendar, finance, delivery, social, system, other.""")
        appendLine()
        appendLine("Write the summary so it can be read instead of the messages:")
        appendLine("- Name who it is from and what they want.")
        appendLine("- Keep every time, amount, date and name that appears. Those are the point.")
        appendLine("- Do not write \"the user received a message\". Write what the message says.")
        appendLine("- Do not repeat the app name, it is already on the card.")
        appendLine()
        appendLine("is_urgent is true ONLY when one of these is literally in the messages:")
        appendLine("- a deadline today or tomorrow, with a time or a date")
        appendLine("- money that has to be paid, or a payment that failed")
        appendLine("- something cancelled, delayed or gone wrong that the user must act on")
        appendLine("- a person waiting on an answer right now, asked in as many words")
        appendLine("- an alarm, a security warning, or a health or safety alert")
        appendLine()
        appendLine("is_urgent is false for everything else, including:")
        appendLine("- news, offers, discounts, sales and newsletters")
        appendLine("- social media, likes, follows, mentions, group chatter")
        appendLine("- app updates, backups, sync messages, tips from an app")
        appendLine("- an ordinary message with no deadline and no question in it")
        appendLine("- anything that merely sounds important because it uses capitals or \"now\"")
        appendLine("When in doubt, false. A card marked urgent that is not urgent makes")
        appendLine("every other urgent card worthless.")
        appendLine()
        appendLine("Example")
        appendLine("Notifications from Bol.com:")
        appendLine("- Bol.com: Je pakket is onderweg en komt morgen tussen 10:00 en 12:00.")
        appendLine("- Bol.com: Bekijk onze aanbiedingen van deze week.")
        appendLine("""JSON: {"summary": "Je pakket komt morgen tussen 10:00 en 12:00. Daarnaast een reclame voor de weekaanbiedingen.", "is_urgent": false, "action_items": [], "category": "delivery"}""")
        appendLine()
        appendLine("Now do the same for these.")
        appendLine("Notifications from $appLabel:")
        messages.forEach { appendLine("- ${it.clean()}") }
        appendLine()
        append("JSON:")
    }

    /** Prompt for the reply drafts offered on an urgent notification. */
    fun buildReplyDraft(appLabel: String, messages: List<String>): String = buildString {
        appendLine("Write one short reply the user could send back, in the language of the messages.")
        appendLine("Answer the last message. Plain sentence, no greeting, no signature, no")
        appendLine("quotes around it, at most 25 words.")
        appendLine()
        appendLine("Conversation in $appLabel:")
        messages.forEach { appendLine("- ${it.clean()}") }
        appendLine()
        append("Reply:")
    }

    /** Prompt used to name a conversation after the first exchange. */
    fun buildTitle(firstMessage: String): String = buildString {
        appendLine("Give this conversation a title of at most four words.")
        appendLine("Use the language of the message. Name the subject, not the fact that")
        appendLine("a question was asked.")
        appendLine("Reply with the title only, no quotes and no full stop.")
        appendLine()
        appendLine("First message: ${firstMessage.take(400).clean()}")
        append("Title:")
    }

    /** Just the domain, with the scheme and any www. taken off. */
    private fun String.host(): String = substringAfter("://")
        .substringBefore('/')
        .removePrefix("www.")
        .ifBlank { this }

    /** Collapses the whitespace a notification body or a scraped snippet arrives with. */
    private fun String.clean(): String = replace(WHITESPACE, " ").trim()

    private val WHITESPACE = Regex("\\s+")

    private fun today(): String =
        SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault()).format(Date())
}
