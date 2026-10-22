package com.claudetv.hub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal client for The Movie Database (TMDB) v3 API.
 * Accepts either a v3 API key or a v4 read access token (which starts with "eyJ").
 */
class TmdbClient(private val credential: String) {

    private val isBearer = credential.startsWith("eyJ")

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val query = buildMap {
                putAll(params)
                if (!isBearer) put("api_key", credential)
            }.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
            val url = URL("$BASE$path${if (query.isEmpty()) "" else "?$query"}")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/json")
                if (isBearer) conn.setRequestProperty("Authorization", "Bearer $credential")
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw TmdbException(code, err)
                }
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }

    companion object {
        private const val BASE = "https://api.themoviedb.org/3"
        private const val IMG = "https://image.tmdb.org/t/p/"

        fun image(path: String?, size: String): String? = path?.let { "$IMG$size$it" }
        fun backdrop(path: String?) = image(path, "w1280")
        fun heroBackdrop(path: String?) = image(path, "original")
        fun poster(path: String?) = image(path, "w500")
        fun logo(path: String?) = image(path, "w500")
        fun providerLogo(path: String?) = image(path, "w154")
    }
}

class TmdbException(val code: Int, body: String) : Exception("TMDB $code: ${body.take(200)}")

// ---------- JSON parsing helpers ----------

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

internal fun JSONObject.toTitle(fallbackType: MediaType? = null): Title? {
    val type = MediaType.from(optStringOrNull("media_type")) ?: fallbackType ?: return null
    val name = optStringOrNull("title") ?: optStringOrNull("name") ?: return null
    val date = optStringOrNull("release_date") ?: optStringOrNull("first_air_date")
    val genres = optJSONArray("genre_ids")?.let { arr -> (0 until arr.length()).map { arr.optInt(it) } }
        ?: optJSONArray("genres")?.objects()?.map { it.optInt("id") }
        ?: emptyList()
    return Title(
        id = optInt("id"),
        type = type,
        name = name,
        overview = optString("overview"),
        posterPath = optStringOrNull("poster_path"),
        backdropPath = optStringOrNull("backdrop_path"),
        year = date?.take(4),
        rating = optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
        genreIds = genres,
    )
}

internal fun JSONObject.parseOffers(region: String): List<Offer> {
    val r = optJSONObject("results")?.optJSONObject(region) ?: return emptyList()
    val out = mutableListOf<Offer>()
    fun add(key: String, type: OfferType) {
        r.optJSONArray(key)?.objects()?.forEach { p ->
            val id = p.optInt("provider_id")
            out += Offer(
                providerId = id,
                providerName = p.optString("provider_name"),
                logoPath = p.optStringOrNull("logo_path"),
                type = type,
                service = StreamingService.forTmdbId(id),
            )
        }
    }
    add("flatrate", OfferType.SUBSCRIPTION)
    add("free", OfferType.FREE)
    add("ads", OfferType.ADS)
    add("rent", OfferType.RENT)
    add("buy", OfferType.BUY)
    return out
}
