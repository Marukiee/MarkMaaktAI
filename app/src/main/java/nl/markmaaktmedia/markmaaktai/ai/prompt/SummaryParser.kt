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

    fun parse(raw: String): StructuredSummary {
        val candidate = extractObject(raw)
        if (candidate != null) {
            val parsed = runCatching { json.decodeFromString<StructuredSummary>(candidate) }.getOrNull()
            if (parsed != null && parsed.summary.isNotBlank()) return parsed.sanitised()
        }
        return StructuredSummary(
            summary = raw.trim().lines().firstOrNull { it.isNotBlank() }.orEmpty().take(300),
            isUrgent = false,
            actionItems = emptyList(),
            category = "other",
        )
    }

    private fun StructuredSummary.sanitised(): StructuredSummary = copy(
        summary = summary.trim(),
        actionItems = actionItems.map { it.trim() }.filter { it.isNotBlank() }.take(3),
        category = category.trim().lowercase().takeIf { it in allowedCategories } ?: "other",
    )

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
