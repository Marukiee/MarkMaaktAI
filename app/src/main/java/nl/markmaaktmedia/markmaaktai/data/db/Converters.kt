package nl.markmaaktmedia.markmaaktai.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Small lists are stored as JSON. There are never enough of them to justify a table. */
class Converters {

    @TypeConverter
    fun stringListToJson(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun sourcesToJson(value: List<WebSource>): String =
        json.encodeToString(value.map { StoredSource(it.title, it.url, it.snippet) })

    @TypeConverter
    fun jsonToSources(value: String): List<WebSource> =
        runCatching {
            json.decodeFromString<List<StoredSource>>(value)
                .map { WebSource(it.title, it.url, it.snippet) }
        }.getOrDefault(emptyList())
}

@kotlinx.serialization.Serializable
internal data class StoredSource(
    val title: String = "",
    val url: String = "",
    val snippet: String = "",
)
