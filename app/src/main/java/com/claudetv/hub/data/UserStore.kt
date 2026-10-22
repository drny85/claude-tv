package com.claudetv.hub.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Everything the hub remembers on the TV itself. Nothing leaves the device. */
class UserStore(context: Context) {
    private val prefs = context.getSharedPreferences("claude_tv", Context.MODE_PRIVATE)

    private val _subscriptions = MutableStateFlow(loadSubscriptions())
    val subscriptions: StateFlow<Set<StreamingService>> = _subscriptions.asStateFlow()

    private val _watchlist = MutableStateFlow(loadTitles(KEY_WATCHLIST))
    val watchlist: StateFlow<List<Title>> = _watchlist.asStateFlow()

    private val _recents = MutableStateFlow(loadRecents())
    val recents: StateFlow<List<RecentItem>> = _recents.asStateFlow()

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API, "").orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _region = MutableStateFlow(prefs.getString(KEY_REGION, "US") ?: "US")
    val region: StateFlow<String> = _region.asStateFlow()

    fun toggleSubscription(service: StreamingService) {
        val next = _subscriptions.value.toMutableSet().apply { if (!add(service)) remove(service) }
        _subscriptions.value = next
        prefs.edit().putStringSet(KEY_SUBS, next.map { it.name }.toSet()).apply()
    }

    fun isInWatchlist(title: Title) = _watchlist.value.any { it.key == title.key }

    fun toggleWatchlist(title: Title) {
        val list = _watchlist.value
        val next = if (list.any { it.key == title.key }) list.filterNot { it.key == title.key } else listOf(title) + list
        _watchlist.value = next
        prefs.edit().putString(KEY_WATCHLIST, JSONArray(next.map { it.toJson() }).toString()).apply()
    }

    fun recordOpened(title: Title, service: StreamingService?) {
        val next = (listOf(RecentItem(title, service, System.currentTimeMillis())) +
            _recents.value.filterNot { it.title.key == title.key }).take(20)
        _recents.value = next
        prefs.edit().putString(KEY_RECENTS, JSONArray(next.map { r ->
            JSONObject().put("title", r.title.toJson()).put("service", r.service?.name).put("at", r.openedAt)
        }).toString()).apply()
    }

    fun removeRecent(title: Title) {
        val next = _recents.value.filterNot { it.title.key == title.key }
        _recents.value = next
        prefs.edit().putString(KEY_RECENTS, JSONArray(next.map { r ->
            JSONObject().put("title", r.title.toJson()).put("service", r.service?.name).put("at", r.openedAt)
        }).toString()).apply()
    }

    fun setApiKey(key: String) {
        _apiKey.value = key.trim()
        prefs.edit().putString(KEY_API, key.trim()).apply()
    }

    fun setRegion(region: String) {
        _region.value = region
        prefs.edit().putString(KEY_REGION, region).apply()
    }

    private fun loadSubscriptions(): Set<StreamingService> {
        val saved = prefs.getStringSet(KEY_SUBS, null)
            ?: return setOf(
                StreamingService.NETFLIX, StreamingService.DISNEY_PLUS, StreamingService.PRIME_VIDEO,
                StreamingService.APPLE_TV_PLUS, StreamingService.MAX, StreamingService.HULU,
            )
        return saved.mapNotNull { n -> StreamingService.entries.firstOrNull { it.name == n } }.toSet()
    }

    private fun loadTitles(key: String): List<Title> = runCatching {
        JSONArray(prefs.getString(key, "[]")).objects().mapNotNull { it.toStoredTitle() }
    }.getOrDefault(emptyList())

    private fun loadRecents(): List<RecentItem> = runCatching {
        JSONArray(prefs.getString(KEY_RECENTS, "[]")).objects().mapNotNull { o ->
            val t = o.optJSONObject("title")?.toStoredTitle() ?: return@mapNotNull null
            val s = o.optStringOrNull("service")?.let { n -> StreamingService.entries.firstOrNull { it.name == n } }
            RecentItem(t, s, o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY_SUBS = "subscriptions"
        const val KEY_WATCHLIST = "watchlist"
        const val KEY_RECENTS = "recents"
        const val KEY_API = "tmdb_key"
        const val KEY_REGION = "region"
    }
}

private fun Title.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("type", type.path)
    .put("name", name)
    .put("overview", overview)
    .put("poster", posterPath)
    .put("backdrop", backdropPath)
    .put("year", year)
    .put("rating", rating)
    .put("genres", JSONArray(genreIds))
    .put("demoService", demoService?.name)
    .put("demoFormats", JSONArray(demoFormats.map { it.name }))

private fun JSONObject.toStoredTitle(): Title? {
    val type = MediaType.from(optStringOrNull("type")) ?: return null
    val genres = optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty()
    val formats = optJSONArray("demoFormats")?.let { a ->
        (0 until a.length()).mapNotNull { i -> VideoFormat.entries.firstOrNull { it.name == a.optString(i) } }
    }.orEmpty().toSet()
    return Title(
        id = optInt("id"),
        type = type,
        name = optString("name"),
        overview = optString("overview"),
        posterPath = optStringOrNull("poster"),
        backdropPath = optStringOrNull("backdrop"),
        year = optStringOrNull("year"),
        rating = optDouble("rating").takeIf { !it.isNaN() },
        genreIds = genres,
        demoService = optStringOrNull("demoService")?.let { n -> StreamingService.entries.firstOrNull { it.name == n } },
        demoFormats = formats,
    )
}
