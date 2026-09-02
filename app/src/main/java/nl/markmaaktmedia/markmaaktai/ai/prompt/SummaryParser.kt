package nl.markmaaktmedia.markmaaktai.ai.prompt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The structured shape the summariser is asked to produce. */
@Serializable
data class StructuredSummary(
    val summary: String = "",
    @SerialName("is_urgent") val isUrgent: Boolean = false,
    @SerialName("action_items") val actionItems: List<String> = emptyList(),
    val category: String = "other",
)

/**
 * Small models wrap their JSON in stray prose, a code fence, or a trailing comma
 * often enough that strict parsing is not worth it. This pulls the outermost brace
 * pair out of whatever came back, then falls back to treating the raw text as the
 * summary so a malformed answer still produces a usable card rather than nothing.
 */
object SummaryParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val allowedCategories = setOf(
        "message", "email", "calendar", "finance", "delivery", "social", "system", "other",
    )

    /** Below this it is a stray token, not a summary. A real one is far longer. */
    private const val MIN_SUMMARY_CHARS = 6
    private const val MAX_SUMMARY_CHARS = 300

    private val WHITESPACE = Regex("\\s+")

    /** Openings that mean the model repeated the prompt instead of answering it. */
    private val ECHOES = setOf(
        "one or two sentences", "short imperative", "je pakket komt morgen tussen 10:00",
        "summarise a burst", "answer with one json", "fields:", "example",
    )

    fun parse(raw: String): StructuredSummary {
        val candidate = extractObject(raw)
        if (candidate != null) {
            val parsed = runCatching { json.decodeFromString<StructuredSummary>(candidate) }.getOrNull()
            if (parsed != null && parsed.summary.isUsable()) return parsed.sanitised()
        }
        // No JSON at all, which happens when the model runs out of context mid object.
        // The first real sentence is still worth showing, and is_urgent stays false
        // because nothing said otherwise.
        val fallback = raw.trim().lines()
            .map { it.stripLabel() }
            .firstOrNull { it.isUsable() }
            .orEmpty()
        return StructuredSummary(
            summary = fallback.take(MAX_SUMMARY_CHARS),
            isUrgent = false,
            actionItems = emptyList(),
            category = "other",
        )
    }

    private fun StructuredSummary.sanitised(): StructuredSummary = copy(
        summary = summary.stripLabel().collapsed().take(MAX_SUMMARY_CHARS),
        actionItems = actionItems
            .map { it.stripLabel().collapsed() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3),
        category = category.trim().lowercase().takeIf { it in allowedCategories } ?: "other",
    )

    /**
     * Small models like to answer with the label they were given, or with the example
     * they were shown. Neither is a summary, and both look convincing on a card.
     */
    private fun String.isUsable(): Boolean {
        val trimmed = stripLabel().trim()
        if (trimmed.length < MIN_SUMMARY_CHARS) return false
        val lower = trimmed.lowercase()
        return ECHOES.none { lower.startsWith(it) }
    }

    private fun String.stripLabel(): String = trim()
        .removePrefix("JSON:")
        .removePrefix("Summary:")
        .removePrefix("summary:")
        .removePrefix("Samenvatting:")
        .trim()
        .trim('"', '\'', '*', '-', ' ')
        .trim()

    private fun String.collapsed(): String = replace(WHITESPACE, " ").trim()

    /** Finds the first balanced brace pair, ignoring braces inside string literals. */
    private fun extractObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until raw.length) {
            val char = raw[index]
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' -> depth++
                char == '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, index + 1)
                }
            }
        }
        return null
    }
}
