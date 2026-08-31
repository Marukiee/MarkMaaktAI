package nl.markmaaktmedia.markmaaktai.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.markmaaktmedia.markmaaktai.data.db.WebSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a lookup. Failures carry a reason so the UI can say what went wrong. */
sealed interface SearchOutcome {
    data class Results(val sources: List<WebSource>) : SearchOutcome
    data class Failed(val reason: String) : SearchOutcome
}

/**
 * The only outbound traffic the chat ever makes, and only when the user flips the
 * web search switch on.
 *
 * SearXNG is the default because it needs no account and no key, and a self hosted
 * instance can be pointed at from settings. Brave is there for people who would
 * rather use a key than trust a public instance. Neither is sent anything beyond
 * the query itself.
 */
@Singleton
class WebSearchClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(
        query: String,
        limit: Int,
        searxngUrl: String,
        braveApiKey: String,
    ): SearchOutcome = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchOutcome.Results(emptyList())
        if (braveApiKey.isNotBlank()) {
            when (val brave = searchBrave(query, limit, braveApiKey)) {
                is SearchOutcome.Results -> return@withContext brave
                is SearchOutcome.Failed -> Log.w(TAG, "Brave failed, falling back: ${brave.reason}")
            }
        }
        searchSearxng(query, limit, searxngUrl)
    }

    private fun searchSearxng(query: String, limit: Int, baseUrl: String): SearchOutcome {
        val base = baseUrl.trim().trimEnd('/').ifBlank { return SearchOutcome.Failed("No search instance set") }
        val url = "$base/search".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("format", "json")
            ?.addQueryParameter("safesearch", "0")
            ?.addQueryParameter("language", "auto")
            ?.build()
            ?: return SearchOutcome.Failed("That search address is not a valid URL")

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Public instances commonly disable the JSON endpoint. Say that
                    // plainly instead of showing a bare status code.
                    return@use SearchOutcome.Failed(
                        if (response.code == 403 || response.code == 429) {
                            "This instance does not allow the JSON search API. Try another one in settings."
                        } else {
                            "Search failed with status ${response.code}"
                        }
                    )
                }
                val body = response.body?.string().orEmpty()
                val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty()
                SearchOutcome.Results(
                    results.take(limit).mapNotNull { element ->
                        val obj = element.jsonObject
                        val title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val link = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val snippet = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (title.isBlank() || link.isBlank()) null
                        else WebSource(title = title, url = link, snippet = snippet)
                    }
                )
            }
        }.getOrElse { error ->
            Log.w(TAG, "SearXNG lookup failed", error)
            SearchOutcome.Failed(error.message ?: "Could not reach the search instance")
        }
    }

    private fun searchBrave(query: String, limit: Int, apiKey: String): SearchOutcome {
        val url = "https://api.search.brave.com/res/v1/web/search".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("count", limit.toString())
            ?.build()
            ?: return SearchOutcome.Failed("Could not build the Brave request")

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Subscription-Token", apiKey)
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use SearchOutcome.Failed("Brave replied with status ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val results = json.parseToJsonElement(body)
                    .jsonObject["web"]?.jsonObject?.get("results")?.jsonArray.orEmpty()
                SearchOutcome.Results(
                    results.take(limit).mapNotNull { element ->
                        val obj = element.jsonObject
                        val title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val link = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val snippet = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (title.isBlank() || link.isBlank()) null
                        else WebSource(title = title, url = link, snippet = snippet.stripHtml())
                    }
                )
            }
        }.getOrElse { error ->
            SearchOutcome.Failed(error.message ?: "Could not reach Brave")
        }
    }

    private fun String.stripHtml(): String = replace(HTML_TAG, "").trim()

    private companion object {
        const val TAG = "WebSearchClient"
        const val USER_AGENT = "MarkMaaktAI/1.0 (Android)"
        val HTML_TAG = Regex("<[^>]*>")
    }
}
