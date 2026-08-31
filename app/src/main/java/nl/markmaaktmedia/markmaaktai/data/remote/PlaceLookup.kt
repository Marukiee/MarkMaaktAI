package nl.markmaaktmedia.markmaaktai.data.remote

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the coordinates in a photo into a place a person would recognise.
 *
 * This is the honest way to answer "where is this?". A small vision model can see two
 * brick towers and a wide avenue, but it does not know Barcelona from Bologna, and a
 * model that guesses a place name sounds certain while being wrong. A photo taken on a
 * phone usually carries the exact spot it was taken in its own metadata, so the answer
 * is already in the file and only needs a name put to it.
 *
 * Two ways of doing that, in order:
 *
 * 1. Android's own geocoder. Nothing leaves the phone beyond what the platform does
 *    anyway, and on a phone with the service it is instant.
 * 2. OpenStreetMap's public reverse lookup. A de-Googled ROM has no geocoder at all,
 *    which is exactly the phone this app is built for, so without this the feature
 *    would be missing on the devices that need it most. It needs no account.
 *
 * Only the coordinates go out, never the photo, and only when the user has left the
 * switch in settings on.
 */
@Singleton
class PlaceLookup @Inject constructor(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** A short, readable description of where a coordinate is, or null. */
    suspend fun describe(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            fromPlatform(latitude, longitude) ?: fromOpenStreetMap(latitude, longitude)
        }

    private fun fromPlatform(latitude: Double, longitude: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        @Suppress("DEPRECATION")
        val results = Geocoder(context, Locale.getDefault())
            .getFromLocation(latitude, longitude, 1)
        val address = results?.firstOrNull() ?: return null

        // Feature name first, because that is the landmark: "Plaça d'Espanya" rather
        // than the house number opposite it.
        listOfNotNull(
            address.featureName?.takeIf { it.isNotBlank() && it != address.thoroughfare },
            address.thoroughfare,
            address.locality ?: address.subAdminArea,
            address.countryName,
        ).distinct().joinToString(", ").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun fromOpenStreetMap(latitude: Double, longitude: Double): String? = runCatching {
        val url = "https://nominatim.openstreetmap.org/reverse" +
            "?format=jsonv2&zoom=17&addressdetails=1" +
            "&lat=${"%.6f".format(Locale.US, latitude)}" +
            "&lon=${"%.6f".format(Locale.US, longitude)}" +
            "&accept-language=${Locale.getDefault().language}"

        val request = Request.Builder()
            .url(url)
            // Nominatim turns away anything that does not identify itself, and asking
            // politely is the condition of using it for free.
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null

            val root = json.parseToJsonElement(body).jsonObject
            val address = root["address"]?.jsonObject

            // The most specific name that is actually a place, then the town, then the
            // country. A full display name runs to a postcode and a house number, which
            // is not what anyone means by "where is this".
            val specific = listOf(
                "tourism", "attraction", "historic", "building", "amenity",
                "square", "pedestrian", "road",
            ).firstNotNullOfOrNull { key -> address?.get(key)?.jsonPrimitive?.contentOrNull }

            val town = listOf("city", "town", "village", "municipality", "county")
                .firstNotNullOfOrNull { key -> address?.get(key)?.jsonPrimitive?.contentOrNull }

            val country = address?.get("country")?.jsonPrimitive?.contentOrNull
            val named = root["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

            listOfNotNull(named ?: specific, town, country)
                .distinct()
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
                ?: root["display_name"]?.jsonPrimitive?.contentOrNull
        }
    }.getOrElse { error ->
        Log.w(TAG, "Reverse lookup failed", error)
        null
    }

    private companion object {
        const val TAG = "PlaceLookup"
        const val USER_AGENT = "MarkMaaktAI (github.com/Marukiee/MarkMaaktAI)"
    }
}
