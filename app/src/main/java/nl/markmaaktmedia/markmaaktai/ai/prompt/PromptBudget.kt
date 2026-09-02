package nl.markmaaktmedia.markmaaktai.ai.prompt

/**
 * How much text is allowed to reach the model.
 *
 * A `.task` export has one fixed KV cache for the prompt and the answer together,
 * and the ones this app ships are small: `ekv1280` is the recommended model. Hand
 * over more than fits and the runtime does not complain, it finishes immediately
 * with an empty string. That was the whole reason a question with notifications or
 * web results behind it came back blank while a bare question worked.
 *
 * So the prompt is measured before it is sent and trimmed until it fits. Everything
 * here counts in characters, because that is what the builder can actually cut, and
 * converts with a deliberately pessimistic ratio: Dutch tokenises worse than English
 * on every one of these models, and a prompt that fits with room to spare only costs
 * a slightly shorter answer.
 */
data class PromptBudget(
    /** Characters the finished prompt may take up. */
    val promptChars: Int,
    /** Tokens left over for the answer. */
    val answerTokens: Int,
) {

    companion object {

        /** Pessimistic on purpose. Real Qwen output sits nearer 3.5 on English prose. */
        private const val CHARS_PER_TOKEN = 3

        /** Left unclaimed for the template tokens the runtime adds around a prompt. */
        private const val RESERVE_TOKENS = 96

        /** Below this an answer is not worth generating, so the prompt gives way. */
        private const val MIN_ANSWER_TOKENS = 160

        /** Enough for the system prompt, the question and a short answer. */
        private const val MIN_PROMPT_CHARS = 600

        /** Used before a model is loaded, when its cache size is not known yet. */
        val Unknown = PromptBudget(promptChars = 2_000, answerTokens = 384)

        fun estimateTokens(text: String): Int = (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

        /**
         * Splits a model's context between the prompt and the answer.
         *
         * The answer gets at most a third, because an assistant that thinks out loud
         * for 600 tokens and then has no room left for the question it was asked is
         * worse than a short one that read everything.
         */
        fun forContext(contextTokens: Int, requestedAnswerTokens: Int): PromptBudget {
            if (contextTokens <= 0) return Unknown

            val usable = (contextTokens - RESERVE_TOKENS).coerceAtLeast(MIN_ANSWER_TOKENS)
            val answer = requestedAnswerTokens
                .coerceAtMost(usable / 3)
                .coerceAtLeast(MIN_ANSWER_TOKENS)
                .coerceAtMost(usable)
            val promptTokens = (usable - answer).coerceAtLeast(0)

            return PromptBudget(
                promptChars = (promptTokens * CHARS_PER_TOKEN).coerceAtLeast(MIN_PROMPT_CHARS),
                answerTokens = answer,
            )
        }
    }
}

/**
 * One block of context, and what it is worth.
 *
 * Blocks are cut to fit in order of [weight], so when the room runs out it is the
 * twelfth notification that goes and not the web result the question was about.
 */
internal data class PromptSection(
    val header: String,
    val lines: List<String>,
    /** Share of the free space this block gets when everything is present. */
    val weight: Float,
    /** A block cut below this is dropped, since half a line helps nobody. */
    val minChars: Int = 120,
    /** Printed under the block, once, when anything of it survived. */
    val footer: String = "",
) {
    val isEmpty: Boolean get() = lines.none { it.isNotBlank() }
}

/**
 * Fits the blocks into [available] characters.
 *
 * Every block gets a share of the room in proportion to its weight, and whatever a
 * small block leaves unused is handed to the others rather than wasted. Lines are
 * dropped whole from the end, which keeps each surviving line readable; only the
 * last line that still partly fits is cut mid-sentence.
 */
internal fun fitSections(sections: List<PromptSection>, available: Int): List<PromptSection> {
    val present = sections.filterNot { it.isEmpty }
    if (present.isEmpty() || available <= 0) return emptyList()

    var room = available
    val kept = mutableListOf<PromptSection>()

    // Cheapest blocks first, so the leftovers of a short one grow the long ones.
    val ordered = present.sortedBy { it.charCount() }
    var weightLeft = ordered.sumOf { it.weight.toDouble() }.toFloat()

    ordered.forEach { section ->
        val share = if (weightLeft <= 0f) room else (room * (section.weight / weightLeft)).toInt()
        val allowance = minOf(share, section.charCount())
        val trimmed = section.trimmedTo(allowance)
        weightLeft -= section.weight
        if (trimmed != null) {
            kept += trimmed
            room -= trimmed.charCount()
        }
    }

    // Back into the order they were declared in, which is the order they read best.
    return sections.mapNotNull { original -> kept.firstOrNull { it.header == original.header } }
}

/**
 * What this block costs once written out.
 *
 * The three is the blank line before the block plus the newline after the header and
 * after the footer. Leaving it out is how a prompt that was measured as fitting came
 * out sixteen characters over, which on a full context is a truncated answer.
 */
private fun PromptSection.charCount(): Int =
    header.length + footer.length + lines.sumOf { it.length + 1 } + SECTION_OVERHEAD

private fun PromptSection.trimmedTo(allowance: Int): PromptSection? {
    if (allowance < minChars) return null

    var room = allowance - header.length - footer.length - SECTION_OVERHEAD
    if (room <= 0) return null

    val kept = mutableListOf<String>()
    for (line in lines) {
        if (line.isBlank()) continue
        if (room >= line.length + 1) {
            kept += line
            room -= line.length + 1
        } else if (kept.isEmpty() && room > MIN_LINE_CHARS) {
            // A single very long line, an email body say, is worth a fragment of.
            kept += line.take(room - 1).trimEnd() + "..."
            room = 0
        } else {
            break
        }
    }
    return if (kept.isEmpty()) null else copy(lines = kept)
}

private const val MIN_LINE_CHARS = 80

/** Blank line before the block, newline after the header, newline after the footer. */
private const val SECTION_OVERHEAD = 3
