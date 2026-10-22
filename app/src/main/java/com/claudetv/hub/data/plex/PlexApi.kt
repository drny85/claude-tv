package com.claudetv.hub.data.plex

import com.claudetv.hub.BuildConfig
import com.claudetv.hub.data.VideoFormat
import com.claudetv.hub.data.objects
import com.claudetv.hub.data.optStringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Plex APIs: plex.tv for sign-in and server discovery, and your Plex Media Server for
 * the library, images, playback URLs and progress reporting.
 */
class PlexApi(private val clientId: String) {

    private fun headers(token: String?): Map<String, String> = buildMap {
        put("Accept", "application/json")
        put("X-Plex-Client-Identifier", clientId)
        put("X-Plex-Product", PRODUCT)
        put("X-Plex-Version", BuildConfig.VERSION_NAME)
        put("X-Plex-Platform", "Android")
        put("X-Plex-Device", "Android TV")
        put("X-Plex-Device-Name", PRODUCT)
        if (token != null) put("X-Plex-Token", token)
    }

    private suspend fun request(
        url: String,
        token: String?,
        method: String = "GET",
        timeoutMs: Int = 15_000,
    ): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            headers(token).forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (method == "POST") {
                conn.doOutput = true
                conn.outputStream.use { }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw PlexException(code, "Plex returned $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---------------- plex.tv: sign-in ----------------

    suspend fun createPin(): PlexPin {
        val o = JSONObject(request("https://plex.tv/api/v2/pins?strong=false", null, "POST"))
        val expiresIn = o.optLong("expiresIn", 900)
        return PlexPin(o.getLong("id"), o.getString("code"), System.currentTimeMillis() + expiresIn * 1000)
    }

    /** Returns the auth token once the person has entered the code at plex.tv/link. */
    suspend fun checkPin(id: Long): String? {
        val o = JSONObject(request("https://plex.tv/api/v2/pins/$id", null))
        return o.optStringOrNull("authToken")
    }

    suspend fun servers(userToken: String): List<PlexServer> {
        val arr = JSONArray(request("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1&includeIPv6=0", userToken))
        return arr.objects()
            .filter { it.optString("provides").split(",").contains("server") }
            .map { r ->
                PlexServer(
                    name = r.optString("name"),
                    clientId = r.optString("clientIdentifier"),
                    accessToken = r.optStringOrNull("accessToken") ?: userToken,
                    owned = r.optBoolean("owned"),
                    connections = r.optJSONArray("connections")?.objects().orEmpty().map { c ->
                        PlexConnection(c.optString("uri"), c.optBoolean("local"), c.optBoolean("relay"))
                    },
                )
            }
    }

    /** Tries every address in parallel and keeps the best one that answers (local, then remote, then relay). */
    suspend fun connect(server: PlexServer): ConnectedServer? = coroutineScope {
        val ordered = server.connections.sortedBy { c -> if (c.relay) 2 else if (c.local) 0 else 1 }
        val results = ordered.map { c ->
            async {
                withTimeoutOrNull(4_000) {
                    runCatching { request("${c.uri}/identity", server.accessToken, timeoutMs = 4_000); c }.getOrNull()
                }
            }
        }.awaitAll()
        val ok = results.filterNotNull().firstOrNull() ?: return@coroutineScope null
        ConnectedServer(server.name, ok.uri.trimEnd('/'), server.accessToken, server.clientId)
    }

    // ---------------- Plex Media Server: library ----------------

    private suspend fun pms(s: ConnectedServer, path: String): JSONObject =
        JSONObject(request(s.baseUrl + path, s.token)).optJSONObject("MediaContainer") ?: JSONObject()

    suspend fun sections(s: ConnectedServer): List<PlexSection> =
        pms(s, "/library/sections").optJSONArray("Directory")?.objects().orEmpty()
            .map { PlexSection(it.optString("key"), it.optString("title"), PlexType.from(it.optString("type"))) }
            .filter { it.type == PlexType.MOVIE || it.type == PlexType.SHOW }

    suspend fun onDeck(s: ConnectedServer): List<PlexItem> =
        pms(s, "/library/onDeck?X-Plex-Container-Start=0&X-Plex-Container-Size=30").items()

    suspend fun sectionItems(s: ConnectedServer, section: PlexSection, sort: String, start: Int, size: Int, unwatchedOnly: Boolean = false): Pair<List<PlexItem>, Int> {
        val type = if (section.type == PlexType.SHOW) 2 else 1
        val extra = if (unwatchedOnly) "&unwatched=1" else ""
        val c = pms(s, "/library/sections/${section.key}/all?type=$type&sort=${enc(sort)}$extra&X-Plex-Container-Start=$start&X-Plex-Container-Size=$size")
        return c.items() to c.optInt("totalSize", c.optInt("size"))
    }

    suspend fun children(s: ConnectedServer, ratingKey: String): List<PlexItem> =
        pms(s, "/library/metadata/$ratingKey/children").items()

    suspend fun metadata(s: ConnectedServer, ratingKey: String): PlexItem? =
        pms(s, "/library/metadata/$ratingKey?includeExtras=0").items(full = true).firstOrNull()

    suspend fun search(s: ConnectedServer, query: String): List<PlexItem> {
        val hubs = pms(s, "/hubs/search?query=${enc(query)}&limit=12").optJSONArray("Hub")?.objects().orEmpty()
        return hubs.flatMap { h -> h.optJSONArray("Metadata")?.objects().orEmpty().mapNotNull { it.toPlexItem(false) } }
            .filter { it.type == PlexType.MOVIE || it.type == PlexType.SHOW || it.type == PlexType.EPISODE }
    }

    // ---------------- images & playback ----------------

    /** Server-side resized (and optionally blurred) image, sized for the card so 4K stays sharp but light. */
    fun image(s: ConnectedServer, path: String?, width: Int, height: Int, blur: Int = 0): String? {
        if (path == null) return null
        if (path.startsWith("http")) return path
        val b = if (blur > 0) "&blur=$blur" else ""
        return "${s.baseUrl}/photo/:/transcode?width=$width&height=$height&minSize=1&upscale=1$b&url=${enc(path)}&X-Plex-Token=${s.token}"
    }

    /** Original file, streamed as-is: keeps 4K, HDR10, Dolby Vision and lossless/Atmos audio. */
    fun directPlayUrl(s: ConnectedServer, item: PlexItem): String? {
        val part = item.media.firstOrNull()?.parts?.firstOrNull() ?: return null
        return "${s.baseUrl}${part.key}?X-Plex-Token=${s.token}"
    }

    /** HLS from Plex's transcoder, used only when this TV can't play the original file. */
    fun transcodeUrl(s: ConnectedServer, item: PlexItem, session: String, offsetMs: Long): String {
        val params = listOf(
            "path" to "/library/metadata/${item.ratingKey}",
            "mediaIndex" to "0",
            "partIndex" to "0",
            "protocol" to "hls",
            "fastSeek" to "1",
            "directPlay" to "0",
            "directStream" to "1",
            "directStreamAudio" to "0",
            "videoQuality" to "100",
            "videoResolution" to "3840x2160",
            "maxVideoBitrate" to "40000",
            "location" to "lan",
            "session" to session,
            "offset" to (offsetMs / 1000).toString(),
            "X-Plex-Session-Identifier" to session,
            "X-Plex-Client-Identifier" to clientId,
            "X-Plex-Product" to PRODUCT,
            "X-Plex-Platform" to "Android",
            "X-Plex-Token" to s.token,
        ).joinToString("&") { "${it.first}=${enc(it.second)}" }
        return "${s.baseUrl}/video/:/transcode/universal/start.m3u8?$params"
    }

    /** Reports playback position so Plex's On Deck and every other Plex app stay in sync. */
    suspend fun timeline(s: ConnectedServer, item: PlexItem, state: String, timeMs: Long, durationMs: Long) {
        runCatching {
            request(
                "${s.baseUrl}/:/timeline?ratingKey=${item.ratingKey}&key=${enc("/library/metadata/${item.ratingKey}")}" +
                    "&state=$state&time=$timeMs&duration=$durationMs",
                s.token,
                timeoutMs = 6_000,
            )
        }
    }

    suspend fun markWatched(s: ConnectedServer, item: PlexItem, watched: Boolean) {
        val path = if (watched) "scrobble" else "unscrobble"
        request("${s.baseUrl}/:/$path?identifier=com.plexapp.plugins.library&key=${item.ratingKey}", s.token)
    }

    private fun JSONObject.items(full: Boolean = false): List<PlexItem> =
        optJSONArray("Metadata")?.objects().orEmpty().mapNotNull { it.toPlexItem(full) }

    companion object {
        const val PRODUCT = "Claude TV"
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}

class PlexException(val code: Int, message: String) : Exception(message)

private fun JSONObject.tags(key: String): List<String> =
    optJSONArray(key)?.objects().orEmpty().map { it.optString("tag") }.filter { it.isNotBlank() }

private fun JSONObject.toPlexItem(full: Boolean): PlexItem? {
    val ratingKey = optStringOrNull("ratingKey") ?: return null
    val type = PlexType.from(optString("type"))
    val media = optJSONArray("Media")?.objects().orEmpty()
    var formats: Set<VideoFormat> = emptySet()
    var audioLabel: String? = null
    var videoLabel: String? = null
    if (full) {
        val streams = media.firstOrNull()?.optJSONArray("Part")?.objects()?.firstOrNull()
            ?.optJSONArray("Stream")?.objects().orEmpty()
        val video = streams.firstOrNull { it.optInt("streamType") == 1 }
        val audio = streams.firstOrNull { it.optInt("streamType") == 2 && it.optBoolean("selected", false) }
            ?: streams.firstOrNull { it.optInt("streamType") == 2 }
        formats = buildSet {
            if (media.any { it.optString("videoResolution").equals("4k", true) }) add(VideoFormat.UHD_4K)
            if (video != null) {
                if (video.optBoolean("DOVIPresent")) add(VideoFormat.DOLBY_VISION)
                val trc = video.optString("colorTrc")
                val vt = video.optString("displayTitle") + " " + video.optString("extendedDisplayTitle")
                if (trc == "smpte2084" || vt.contains("HDR10", true)) add(if (vt.contains("HDR10+", true)) VideoFormat.HDR10_PLUS else VideoFormat.HDR10)
            }
            if (audio != null) {
                val at = audio.optString("displayTitle") + " " + audio.optString("extendedDisplayTitle") + " " + audio.optString("title")
                if (at.contains("atmos", true)) add(VideoFormat.DOLBY_ATMOS)
            }
        }
        audioLabel = audio?.optStringOrNull("displayTitle")
        videoLabel = video?.optStringOrNull("displayTitle")
    }
    val logo = optJSONArray("Image")?.objects()?.firstOrNull { it.optString("type") == "clearLogo" }?.optStringOrNull("url")
    return PlexItem(
        ratingKey = ratingKey,
        type = type,
        title = optString("title"),
        summary = optString("summary"),
        year = optInt("year").takeIf { it > 0 },
        thumb = optStringOrNull("thumb"),
        art = optStringOrNull("art"),
        logo = logo,
        showTitle = optStringOrNull("grandparentTitle") ?: if (type == PlexType.SEASON) optStringOrNull("parentTitle") else null,
        showKey = optStringOrNull("grandparentRatingKey") ?: if (type == PlexType.SEASON) optStringOrNull("parentRatingKey") else null,
        seasonKey = if (type == PlexType.EPISODE) optStringOrNull("parentRatingKey") else null,
        seasonNumber = when (type) {
            PlexType.EPISODE -> optInt("parentIndex", -1).takeIf { it >= 0 }
            PlexType.SEASON -> optInt("index", -1).takeIf { it >= 0 }
            else -> null
        },
        episodeNumber = if (type == PlexType.EPISODE) optInt("index", -1).takeIf { it >= 0 } else null,
        showThumb = optStringOrNull("grandparentThumb") ?: if (type == PlexType.SEASON) optStringOrNull("parentThumb") else null,
        showArt = optStringOrNull("grandparentArt"),
        durationMs = optLong("duration").takeIf { it > 0 },
        viewOffsetMs = optLong("viewOffset").takeIf { it > 0 },
        viewCount = optInt("viewCount"),
        leafCount = if (has("leafCount")) optInt("leafCount") else null,
        viewedLeafCount = if (has("viewedLeafCount")) optInt("viewedLeafCount") else null,
        contentRating = optStringOrNull("contentRating"),
        rating = (optDouble("audienceRating").takeIf { !it.isNaN() } ?: optDouble("rating").takeIf { !it.isNaN() })?.takeIf { it > 0 },
        genres = tags("Genre"),
        cast = tags("Role").take(6),
        directors = tags("Director"),
        studio = optStringOrNull("studio"),
        media = media.map { m ->
            PlexMedia(
                videoResolution = m.optStringOrNull("videoResolution"),
                videoCodec = m.optStringOrNull("videoCodec"),
                audioCodec = m.optStringOrNull("audioCodec"),
                audioChannels = m.optInt("audioChannels").takeIf { it > 0 },
                parts = m.optJSONArray("Part")?.objects().orEmpty().map { p -> PlexPart(p.optString("key"), p.optStringOrNull("container")) },
            )
        },
        formats = formats,
        audioLabel = audioLabel,
        videoLabel = videoLabel,
    )
}
