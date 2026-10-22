package com.claudetv.hub.data

import com.claudetv.hub.BuildConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class HomeData(
    val hero: List<HeroItem>,
    val rows: List<ContentRow>,
)

data class SearchResult(val title: Title, val offers: List<Offer>)

/**
 * Live catalog from TMDB when a key is present, otherwise the bundled sample catalog.
 * Availability ("where to watch") comes from TMDB's watch-provider endpoints, which are
 * powered by JustWatch.
 */
class CatalogRepository(private val store: UserStore) {

    private val offerCache = ConcurrentHashMap<String, List<Offer>>()
    private val detailsCache = ConcurrentHashMap<String, TitleDetails>()
    private val limiter = Semaphore(8)

    private fun credential(): String = store.apiKey.value.ifBlank { BuildConfig.TMDB_API_KEY }
    val isLive: Boolean get() = credential().isNotBlank()
    private fun client() = TmdbClient(credential())
    private val region get() = store.region.value

    fun clearCaches() {
        offerCache.clear()
        detailsCache.clear()
    }

    /** Quick check used by Settings after a key is entered. */
    suspend fun validateKey(key: String): Boolean = runCatching {
        TmdbClient(key.trim()).get("/configuration")
        true
    }.getOrDefault(false)

    // ---------------- Home ----------------

    suspend fun loadHome(subs: Set<StreamingService>, seeds: List<Title>): HomeData =
        if (isLive) liveHome(subs, seeds) else demoHome(subs)

    private suspend fun liveHome(subs: Set<StreamingService>, seeds: List<Title>): HomeData = coroutineScope {
        val tmdb = client()
        val services = StreamingService.subscribable.filter { it in subs }
        val allProviders = services.flatMap { it.tmdbIds }.joinToString("|")

        val trending = async { safe { tmdb.get("/trending/all/week").results() } }
        val perService = services.map { s ->
            async { s to safe { discoverBoth(tmdb, s.tmdbIds.joinToString("|"), "popularity.desc") } }
        }
        val picks = async {
            if (allProviders.isEmpty()) emptyList() else safe { personalPicks(tmdb, allProviders, seeds) }
        }
        val topRated = async {
            if (allProviders.isEmpty()) emptyList() else safe { discoverBoth(tmdb, allProviders, "vote_average.desc", minVotes = true) }
        }

        val serviceRows = perService.awaitAll()
        val rows = buildList {
            val p = picks.await()
            if (p.isNotEmpty()) add(
                ContentRow("picks", if (seeds.isEmpty()) "Popular on Your Services" else "Top Picks for You",
                    if (seeds.isEmpty()) "Add titles to your watchlist to personalize this row" else "Based on what you watch and save, included in your subscriptions",
                    p, RowStyle.LANDSCAPE)
            )
            val tr = trending.await()
            if (tr.isNotEmpty()) add(ContentRow("trending", "Trending This Week", null, tr, RowStyle.POSTER))
            serviceRows.forEach { (s, items) ->
                if (items.isNotEmpty()) add(ContentRow("svc_${s.name}", "Popular on ${s.displayName}", null, items, RowStyle.LANDSCAPE, s))
            }
            val top = topRated.await()
            if (top.isNotEmpty()) add(ContentRow("top", "Critically Acclaimed", "Highest rated on your services", top, RowStyle.POSTER))
        }

        // Hero: the most popular title from each subscribed service, so everything up top is included.
        var heroTitles: List<Pair<Title, StreamingService?>> = serviceRows.mapNotNull { (s, items) ->
            items.firstOrNull { it.backdropPath != null }?.let { Pair<Title, StreamingService?>(it, s) }
        }
        if (heroTitles.isEmpty()) {
            heroTitles = trending.await().filter { it.backdropPath != null }.take(6).map { Pair<Title, StreamingService?>(it, null) }
        }
        heroTitles = heroTitles.distinctBy { it.first.key }.take(7)
        val hero = heroTitles.map { (t, s) ->
            async { HeroItem(t, safeOrNull { logoFor(tmdb, t) }, s) }
        }.awaitAll()

        HomeData(hero, rows)
    }

    private suspend fun discoverBoth(tmdb: TmdbClient, providers: String, sort: String, minVotes: Boolean = false, genres: Pair<String, String>? = null): List<Title> = coroutineScope {
        fun params(type: MediaType) = buildMap {
            put("watch_region", region)
            put("with_watch_providers", providers)
            put("with_watch_monetization_types", "flatrate")
            put("sort_by", sort)
            put("include_adult", "false")
            if (minVotes) put("vote_count.gte", if (type == MediaType.MOVIE) "1500" else "500")
            genres?.let { g -> (if (type == MediaType.MOVIE) g.first else g.second).takeIf { it.isNotEmpty() }?.let { put("with_genres", it) } }
        }
        val movies = async { safe { tmdb.get("/discover/movie", params(MediaType.MOVIE)).results(MediaType.MOVIE) } }
        val shows = async { safe { tmdb.get("/discover/tv", params(MediaType.TV)).results(MediaType.TV) } }
        interleave(shows.await(), movies.await()).take(20)
    }

    private suspend fun personalPicks(tmdb: TmdbClient, providers: String, seeds: List<Title>): List<Title> {
        if (seeds.isEmpty()) return discoverBoth(tmdb, providers, "popularity.desc").drop(3)
        val counts = seeds.flatMap { it.genreIds }.groupingBy { it }.eachCount()
        val top = counts.entries.sortedByDescending { it.value }.take(3).map { it.key }
        val movieGenres = top.map { MOVIE_FROM_TV[it] ?: it }.filter { it in MOVIE_GENRES }.distinct().joinToString("|")
        val tvGenres = top.map { TV_FROM_MOVIE[it] ?: it }.filter { it in TV_GENRES }.distinct().joinToString("|")
        val seen = seeds.map { it.key }.toSet()
        return discoverBoth(tmdb, providers, "popularity.desc", genres = movieGenres to tvGenres)
            .filterNot { it.key in seen }
    }

    private suspend fun logoFor(tmdb: TmdbClient, t: Title): String? {
        val json = tmdb.get("/${t.type.path}/${t.id}/images", mapOf("include_image_language" to "en,null"))
        val logos = json.optJSONArray("logos")?.objects().orEmpty()
        return (logos.firstOrNull { it.optString("iso_639_1") == "en" } ?: logos.firstOrNull())?.optStringOrNull("file_path")
    }

    private fun demoHome(subs: Set<StreamingService>): HomeData {
        val all = DemoCatalog.titles
        val mine = all.filter { it.demoService in subs }
        val hero = mine.groupBy { it.demoService }.values.mapNotNull { it.firstOrNull() }.take(7)
            .map { HeroItem(it, null, it.demoService) }
        val rows = buildList {
            add(ContentRow("picks", "Top Picks for You", "Sample data: add a TMDB key in Settings for the live catalog", mine.sortedByDescending { it.rating }, RowStyle.LANDSCAPE))
            add(ContentRow("trending", "Trending This Week", null, all.shuffled(java.util.Random(7)), RowStyle.POSTER))
            StreamingService.subscribable.filter { it in subs }.forEach { s ->
                val items = all.filter { it.demoService == s }
                if (items.isNotEmpty()) add(ContentRow("svc_${s.name}", "Popular on ${s.displayName}", null, items, RowStyle.LANDSCAPE, s))
            }
        }
        return HomeData(hero, rows)
    }

    // ---------------- Search ----------------

    suspend fun search(query: String, subs: Set<StreamingService>): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        if (!isLive) {
            val q = query.trim().lowercase()
            return DemoCatalog.titles.filter { it.name.lowercase().contains(q) }
                .map { SearchResult(it, DemoCatalog.offersFor(it)) }
                .sortedBy { if (it.title.demoService in subs) 0 else 1 }
        }
        val tmdb = client()
        val titles = tmdb.get("/search/multi", mapOf("query" to query, "include_adult" to "false")).results().take(24)
        val results = coroutineScope {
            titles.map { t -> async { SearchResult(t, safe { offers(t) }) } }.awaitAll()
        }
        // Titles included in your subscriptions first, then free, then everything else (keeping TMDB relevance order).
        return results.withIndex().sortedWith(compareBy({ rankFor(it.value.offers, subs) }, { it.index })).map { it.value }
    }

    private fun rankFor(offers: List<Offer>, subs: Set<StreamingService>): Int = when {
        offers.any { it.type == OfferType.SUBSCRIPTION && it.service in subs } -> 0
        offers.any { it.type == OfferType.FREE || it.type == OfferType.ADS } -> 1
        offers.isNotEmpty() -> 2
        else -> 3
    }

    // ---------------- Details & availability ----------------

    suspend fun offers(title: Title): List<Offer> {
        if (title.isDemo) return DemoCatalog.offersFor(title)
        offerCache[title.key]?.let { return it }
        val list = limiter.withPermit {
            client().get("/${title.type.path}/${title.id}/watch/providers").parseOffers(region)
        }
        offerCache[title.key] = list
        return list
    }

    suspend fun details(title: Title): TitleDetails {
        if (title.isDemo) {
            return TitleDetails(
                title = title,
                genres = title.genreIds.mapNotNull { GENRE_NAMES[it] },
                offers = DemoCatalog.offersFor(title),
                similar = DemoCatalog.titles.filter { it.key != title.key && it.genreIds.any { g -> g in title.genreIds } }.take(12),
                tagline = "Sample data",
            )
        }
        detailsCache[title.key]?.let { return it }
        val json = client().get(
            "/${title.type.path}/${title.id}",
            mapOf(
                "append_to_response" to "watch/providers,videos,credits,recommendations,images,${if (title.type == MediaType.MOVIE) "release_dates" else "content_ratings"}",
                "include_image_language" to "en,null",
            ),
        )
        val base = json.toTitle(title.type) ?: title
        val offers = json.optJSONObject("watch/providers")?.parseOffers(region).orEmpty()
        offerCache[title.key] = offers
        val trailer = json.optJSONObject("videos")?.optJSONArray("results")?.objects().orEmpty()
            .filter { it.optString("site") == "YouTube" }
            .sortedWith(compareBy({ if (it.optString("type") == "Trailer") 0 else 1 }, { if (it.optBoolean("official")) 0 else 1 }))
            .firstOrNull()?.optStringOrNull("key")
        val cert = if (title.type == MediaType.MOVIE) {
            json.optJSONObject("release_dates")?.optJSONArray("results")?.objects()
                ?.firstOrNull { it.optString("iso_3166_1") == region }
                ?.optJSONArray("release_dates")?.objects()?.map { it.optString("certification") }?.firstOrNull { it.isNotBlank() }
        } else {
            json.optJSONObject("content_ratings")?.optJSONArray("results")?.objects()
                ?.firstOrNull { it.optString("iso_3166_1") == region }?.optStringOrNull("rating")
        }
        val logos = json.optJSONObject("images")?.optJSONArray("logos")?.objects().orEmpty()
        val d = TitleDetails(
            title = base,
            tagline = json.optStringOrNull("tagline"),
            genres = json.optJSONArray("genres")?.objects()?.map { it.optString("name") }.orEmpty(),
            runtimeMinutes = json.optInt("runtime").takeIf { it > 0 }
                ?: json.optJSONArray("episode_run_time")?.let { if (it.length() > 0) it.optInt(0) else null }?.takeIf { it > 0 },
            seasons = json.optInt("number_of_seasons").takeIf { it > 0 },
            certification = cert,
            cast = json.optJSONObject("credits")?.optJSONArray("cast")?.objects()?.take(6)?.map { it.optString("name") }.orEmpty(),
            trailerYoutubeKey = trailer,
            offers = offers,
            similar = json.optJSONObject("recommendations")?.results(title.type)?.take(20).orEmpty(),
            logoPath = (logos.firstOrNull { it.optString("iso_639_1") == "en" } ?: logos.firstOrNull())?.optStringOrNull("file_path"),
        )
        detailsCache[title.key] = d
        return d
    }

    private fun JSONObject.results(type: MediaType? = null): List<Title> =
        optJSONArray("results")?.objects()?.mapNotNull { it.toTitle(type) }.orEmpty()

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> = runCatching { block() }.getOrDefault(emptyList())

    private suspend fun <T> safeOrNull(block: suspend () -> T?): T? = runCatching { block() }.getOrNull()

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> = buildList {
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            if (i < a.size) add(a[i])
            if (i < b.size) add(b[i])
        }
    }

    companion object {
        private val MOVIE_GENRES = setOf(28, 12, 16, 35, 80, 99, 18, 10751, 14, 36, 27, 10402, 9648, 10749, 878, 53, 10752, 37)
        private val TV_GENRES = setOf(10759, 16, 35, 80, 99, 18, 10751, 10762, 9648, 10763, 10764, 10765, 10766, 10767, 10768, 37)
        private val TV_FROM_MOVIE = mapOf(28 to 10759, 12 to 10759, 878 to 10765, 14 to 10765, 10752 to 10768)
        private val MOVIE_FROM_TV = mapOf(10759 to 28, 10765 to 878, 10768 to 10752)

        val GENRE_NAMES = mapOf(
            28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
            99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
            27 to "Horror", 9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi", 53 to "Thriller",
            37 to "Western", 10759 to "Action & Adventure", 10765 to "Sci-Fi & Fantasy",
        )
    }
}
