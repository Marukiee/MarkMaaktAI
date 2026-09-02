package nl.markmaaktmedia.markmaaktai.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * Tries each backend in turn and returns the first that produces results.
     *
     * The order is deliberate. Brave is first because a key buys a real search API
     * that answers reliably. A self hosted SearXNG is second, since someone who
     * entered an address meant it. Wikipedia is last and needs nothing, so the switch
     * still does something on a fresh install.
     *
     * There is no public SearXNG default any more. Every instance worth naming either
     * disables the JSON endpoint or rate limits it into uselessness, so shipping one
     * as the default meant the feature failed for everyone who never opened settings.
     */
    suspend fun search(
        query: String,
        limit: Int,
        searxngUrl: String,
        braveApiKey: String,
    ): SearchOutcome = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchOutcome.Results(emptyList())

        // The HTTP client is shared with the model downloader, so its read timeout is
        // measured in minutes. A search that takes minutes is a search that failed,
        // and the chat sitting on a spinner behind it reads as the app being broken.
        withTimeoutOrNull(TIMEOUT_MILLIS) { searchAll(query, limit, searxngUrl, braveApiKey) }
            ?: SearchOutcome.Failed("The search took too long and was given up on")
    }

    private fun searchAll(
        query: String,
        limit: Int,
        searxngUrl: String,
        braveApiKey: String,
    ): SearchOutcome {
        val reasons = mutableListOf<String>()

        if (braveApiKey.isNotBlank()) {
            when (val brave = searchBrave(query, limit, braveApiKey)) {
                is SearchOutcome.Results -> if (brave.sources.isNotEmpty()) return brave
                is SearchOutcome.Failed -> reasons += "Brave: ${brave.reason}"
            }
        }

        if (searxngUrl.isNotBlank()) {
            when (val searx = searchSearxng(query, limit, searxngUrl)) {
                is SearchOutcome.Results -> if (searx.sources.isNotEmpty()) return searx
                is SearchOutcome.Failed -> reasons += "SearXNG: ${searx.reason}"
            }
        }

        when (val ddg = searchDuckDuckGo(query, limit)) {
            is SearchOutcome.Results -> if (ddg.sources.isNotEmpty()) return ddg
            is SearchOutcome.Failed -> reasons += "DuckDuckGo: ${ddg.reason}"
        }

        when (val wiki = searchWikipedia(query, limit)) {
            is SearchOutcome.Results -> if (wiki.sources.isNotEmpty()) return wiki
            is SearchOutcome.Failed -> reasons += "Wikipedia: ${wiki.reason}"
        }

        return SearchOutcome.Failed(
            reasons.joinToString("\n").ifBlank { "No search backend returned anything" }
        )
    }

    /**
     * The keyless general search.
     *
     * DuckDuckGo's plain HTML endpoint, read with a normal browser user agent. There
     * is no free general search API without a key, and this is the closest thing: no
     * account, and it answers questions about the world rather than about an
     * encyclopedia. It is scraping, so it is best effort. A network DuckDuckGo does
     * not like answers 403, and the next backend takes over instead of the whole
     * feature failing.
     */
    private fun searchDuckDuckGo(query: String, limit: Int): SearchOutcome {
        val locale = java.util.Locale.getDefault()
        val form = okhttp3.FormBody.Builder()
            .add("q", query)
            .add("kl", locale.language + "-" + locale.country.lowercase())
            .build()

        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/")
            .post(form)
            .header("User-Agent", BROWSER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", locale.toLanguageTag())
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use SearchOutcome.Failed("Refused with status " + response.code)
                }
                val html = response.body?.string().orEmpty()

                val links = resultBlocks(html)
                    .mapNotNull { match ->
                        val href = decodeRedirect(match.groupValues[1])
                        val title = match.groupValues[2].stripHtml().unescape()
                        if (href.isBlank() || title.isBlank()) null
                        else WebSource(title = title, url = href, snippet = "")
                    }
                    .toMutableList()

                // Snippets sit in their own blocks, in the same order as the links.
                val snippets = SNIPPET_BLOCK.findAll(html)
                    .map { it.groupValues[1].stripHtml().unescape() }
                    .toList()
                links.indices.forEach { index ->
                    snippets.getOrNull(index)?.let { links[index] = links[index].copy(snippet = it) }
                }

                SearchOutcome.Results(links.take(limit))
            }
        }.getOrElse { error ->
            SearchOutcome.Failed(error.message ?: "Could not reach DuckDuckGo")
        }
    }

    /**
     * Finds the result links, whichever markup came back.
     *
     * This is scraping, so the page is allowed to change under it and periodically
     * does. Two shapes are tried before giving up, which is the difference between the
     * feature quietly dying on a DuckDuckGo tweak and it falling through to the next
     * backend the way it was meant to.
     */
    private fun resultBlocks(html: String): Sequence<MatchResult> {
        val primary = RESULT_BLOCK.findAll(html)
        return if (primary.any()) primary else RESULT_BLOCK_FALLBACK.findAll(html)
    }

    /** Result links are wrapped in a redirect, with the real target as a parameter. */
    private fun decodeRedirect(href: String): String {
        val raw = href.unescape()
        if (!raw.contains("uddg=")) return if (raw.startsWith("http")) raw else ""
        val encoded = raw.substringAfter("uddg=").substringBefore("&")
        return runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault("")
    }

    private fun String.unescape(): String = this
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .trim()

    /**
     * The last resort.
     *
     * Not a web search, and it does not pretend to be one: it is an encyclopedia, so
     * it answers questions about things and not about today. It is here because it
     * needs no account, has a stable API, and never hands back an HTML page when JSON
     * was asked for.
     */
    private fun searchWikipedia(query: String, limit: Int): SearchOutcome {
        val language = java.util.Locale.getDefault().language.takeIf { it.isNotBlank() } ?: "en"
        val url = "https://$language.wikipedia.org/w/api.php".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("action", "query")
            ?.addQueryParameter("list", "search")
            ?.addQueryParameter("srsearch", query)
            ?.addQueryParameter("srlimit", limit.toString())
            ?.addQueryParameter("format", "json")
            ?.build()
            ?: return SearchOutcome.Failed("Could not build the Wikipedia request")

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use SearchOutcome.Failed("Wikipedia replied with status ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val results = json.parseToJsonElement(body)
                    .jsonObject["query"]?.jsonObject?.get("search")?.jsonArray.orEmpty()

                SearchOutcome.Results(
                    results.take(limit).mapNotNull { element ->
                        val obj = element.jsonObject
                        val title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (title.isBlank()) return@mapNotNull null
                        WebSource(
                            title = title,
                            url = "https://$language.wikipedia.org/wiki/" + title.replace(' ', '_'),
                            snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull.orEmpty().stripHtml(),
                        )
                    }
                )
            }
        }.getOrElse { error ->
            SearchOutcome.Failed(error.message ?: "Could not reach Wikipedia")
        }
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
                val contentType = response.header("Content-Type").orEmpty()
                val body = response.body?.string().orEmpty()
                if (!contentType.contains("json", ignoreCase = true) || body.trimStart().startsWith("<")) {
                    return@use SearchOutcome.Failed(
                        "This instance answered with a web page instead of JSON, which " +
                            "means its JSON API is switched off. Use your own instance, " +
                            "or add a Brave key in settings."
                    )
                }
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

        /** Long enough for a slow instance, short enough that nobody waits on it. */
        const val TIMEOUT_MILLIS = 15_000L
        const val USER_AGENT = "MarkMaaktAI/1.0 (Android)"

        /** DuckDuckGo serves a different page to anything that looks like a bot. */
        const val BROWSER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"

        val HTML_TAG = Regex("<[^>]*>")
        val RESULT_BLOCK = Regex(
            "<a rel=\"nofollow\" class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            RegexOption.DOT_MATCHES_ALL,
        )
        /** Any anchor pointing at DuckDuckGo's redirector is a result, whatever its class. */
        val RESULT_BLOCK_FALLBACK = Regex(
            "<a[^>]+href=\"(/l/\\?[^\"]*uddg=[^\"]+)\"[^>]*>(.*?)</a>",
            RegexOption.DOT_MATCHES_ALL,
        )
        val SNIPPET_BLOCK = Regex(
            "<a class=\"result__snippet\"[^>]*>(.*?)</a>",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
