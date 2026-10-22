package com.claudetv.hub.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudetv.hub.data.CatalogRepository
import com.claudetv.hub.data.HomeData
import com.claudetv.hub.data.Offer
import com.claudetv.hub.data.OfferType
import com.claudetv.hub.data.SearchResult
import com.claudetv.hub.data.StreamingService
import com.claudetv.hub.data.Title
import com.claudetv.hub.data.TitleDetails
import com.claudetv.hub.data.UserStore
import com.claudetv.hub.data.plex.ConnectedServer
import com.claudetv.hub.data.plex.PlexItem
import com.claudetv.hub.data.plex.PlexPin
import com.claudetv.hub.data.plex.PlexRepository
import com.claudetv.hub.data.plex.PlexSection
import com.claudetv.hub.data.plex.PlexServer
import com.claudetv.hub.data.plex.PlexState
import com.claudetv.hub.data.plex.PlexType
import com.claudetv.hub.platform.AppLauncher
import com.claudetv.hub.platform.DeviceCapabilities
import com.claudetv.hub.platform.DisplayCapabilities
import com.claudetv.hub.platform.LaunchResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Tab(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    SEARCH("Search"),
    SETTINGS("Settings"),
}

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val message: String) : Loadable<Nothing>
}

/** A page stacked above the tabs. */
sealed interface Detail {
    val key: String

    data class Stream(val title: Title) : Detail {
        override val key get() = title.key
    }

    data class Plex(val item: PlexItem) : Detail {
        override val key get() = item.key
    }
}

data class PlayRequest(val item: PlexItem, val startMs: Long)

/** Full-screen layers above everything else. */
sealed interface Overlay {
    data object PlexLink : Overlay
    data class Player(val request: PlayRequest) : Overlay
}

data class PlexHome(
    val onDeck: List<PlexItem>,
    val recentMovies: List<PlexItem>,
    val recentShows: List<PlexItem>,
    val sections: List<PlexSection>,
) {
    /** Hero: recently added titles that have background art. */
    val hero: List<PlexItem>
        get() = (recentMovies.take(4) + recentShows.take(3)).filter { it.art != null }.take(6)
}

@OptIn(FlowPreview::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val store = UserStore(app)
    private val repo = CatalogRepository(store)
    val plex = PlexRepository(app)

    val subscriptions = store.subscriptions
    val watchlist = store.watchlist
    val recents = store.recents
    val apiKey = store.apiKey
    val region = store.region
    val plexState: StateFlow<PlexState> = plex.state

    val capabilities: DeviceCapabilities = DisplayCapabilities.detect(app)

    private val _tab = MutableStateFlow(Tab.HOME)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _details = MutableStateFlow<List<Detail>>(emptyList())
    val detailsStack: StateFlow<List<Detail>> = _details.asStateFlow()

    private val _overlay = MutableStateFlow<Overlay?>(null)
    val overlay: StateFlow<Overlay?> = _overlay.asStateFlow()

    private val _home = MutableStateFlow<Loadable<HomeData>>(Loadable.Loading)
    val home: StateFlow<Loadable<HomeData>> = _home.asStateFlow()

    private val _plexHome = MutableStateFlow<Loadable<PlexHome>?>(null)
    val plexHome: StateFlow<Loadable<PlexHome>?> = _plexHome.asStateFlow()

    private val _isLive = MutableStateFlow(repo.isLive)
    val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    private val _offers = MutableStateFlow<Map<String, List<Offer>>>(emptyMap())
    val offers: StateFlow<Map<String, List<Offer>>> = _offers.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _results = MutableStateFlow<Loadable<List<SearchResult>>>(Loadable.Ready(emptyList()))
    val results: StateFlow<Loadable<List<SearchResult>>> = _results.asStateFlow()
    private val _plexResults = MutableStateFlow<List<PlexItem>>(emptyList())
    val plexResults: StateFlow<List<PlexItem>> = _plexResults.asStateFlow()

    private val _pin = MutableStateFlow<PlexPin?>(null)
    val pin: StateFlow<PlexPin?> = _pin.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** Card key that should take focus again after a page above it closes. */
    private val _restoreFocus = MutableStateFlow<String?>(null)
    val restoreFocus: StateFlow<String?> = _restoreFocus.asStateFlow()

    private var toastJob: Job? = null
    private var offerJob: Job? = null
    private var searchJob: Job? = null
    private var pinJob: Job? = null

    init {
        loadHome()
        viewModelScope.launch { plex.restore() }
        viewModelScope.launch {
            plex.state.collect { s ->
                if (s is PlexState.Connected) {
                    loadPlexHome()
                    if (_overlay.value == Overlay.PlexLink) {
                        _overlay.value = null
                        showToast("Connected to ${s.server.name}")
                    }
                } else if (s is PlexState.SignedOut) {
                    _plexHome.value = null
                }
            }
        }
        viewModelScope.launch {
            combine(store.subscriptions, store.apiKey, store.region) { s, k, r -> Triple(s, k, r) }
                .distinctUntilChanged().drop(1).collect {
                    repo.clearCaches()
                    _offers.value = emptyMap()
                    _isLive.value = repo.isLive
                    loadHome()
                    if (_query.value.isNotBlank()) runSearch(_query.value)
                }
        }
        viewModelScope.launch {
            _query.drop(1).debounce(350).collect { runSearch(it) }
        }
    }

    // ---------- navigation ----------

    fun selectTab(t: Tab) { _tab.value = t }

    fun openDetails(title: Title, focusKey: String?) = open(Detail.Stream(title), focusKey)
    fun openPlex(item: PlexItem, focusKey: String?) = open(Detail.Plex(item), focusKey)

    private fun open(d: Detail, focusKey: String?) {
        if (focusKey != null) _restoreFocus.value = focusKey
        _details.value = _details.value + d
    }

    /** Returns true if something was closed. */
    fun closeTop(): Boolean {
        if (_overlay.value != null) {
            closeOverlay()
            return true
        }
        if (_details.value.isEmpty()) return false
        _details.value = _details.value.dropLast(1)
        return true
    }

    fun closeOverlay() {
        if (_overlay.value == Overlay.PlexLink) cancelPlexLink()
        val wasPlayer = _overlay.value is Overlay.Player
        _overlay.value = null
        if (wasPlayer) loadPlexHome(silent = true)
    }

    fun consumeRestoreFocus() { _restoreFocus.value = null }

    private var initialFocusClaimed = false

    /** True exactly once per app session, for the first screen to take focus. */
    fun claimInitialFocus(): Boolean = !initialFocusClaimed.also { initialFocusClaimed = true }

    // ---------- streaming catalog ----------

    fun loadHome() {
        viewModelScope.launch {
            if (_home.value !is Loadable.Ready) _home.value = Loadable.Loading
            val seeds = (store.watchlist.value + store.recents.value.map { it.title }).distinctBy { it.key }.take(12)
            _home.value = runCatching { repo.loadHome(store.subscriptions.value, seeds) }
                .fold({ Loadable.Ready(it) }, { Loadable.Failed(it.message ?: "Couldn't load") })
        }
    }

    suspend fun details(title: Title): TitleDetails {
        val d = repo.details(title)
        _offers.value = _offers.value + (title.key to d.offers)
        return d
    }

    /** Fetch availability for a focused card, after a short dwell so fast scrolling stays cheap. */
    fun onCardFocused(title: Title) {
        if (_offers.value.containsKey(title.key)) return
        offerJob?.cancel()
        offerJob = viewModelScope.launch {
            delay(250)
            val list = runCatching { repo.offers(title) }.getOrNull() ?: return@launch
            _offers.value = _offers.value + (title.key to list)
        }
    }

    fun setQuery(q: String) { _query.value = q }

    private fun runSearch(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) {
            _results.value = Loadable.Ready(emptyList())
            _plexResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            _results.value = Loadable.Loading
            coroutineScope {
                val plexHits = async { plex.server?.let { s -> runCatching { plex.api.search(s, q) }.getOrNull() }.orEmpty() }
                val stream = async { runCatching { repo.search(q, store.subscriptions.value) } }
                _plexResults.value = plexHits.await()
                _results.value = stream.await().fold({ list ->
                    _offers.value = _offers.value + list.associate { it.title.key to it.offers }
                    Loadable.Ready(list)
                }, { Loadable.Failed(it.message ?: "Search failed") })
            }
        }
    }

    fun toggleWatchlist(title: Title) {
        val adding = !store.isInWatchlist(title)
        store.toggleWatchlist(title)
        showToast(if (adding) "Added to Watchlist" else "Removed from Watchlist")
    }

    fun toggleSubscription(s: StreamingService) = store.toggleSubscription(s)

    fun setRegion(r: String) = store.setRegion(r)

    fun saveApiKey(key: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (key.isBlank()) {
                store.setApiKey("")
                onResult(true)
                return@launch
            }
            val ok = repo.validateKey(key)
            if (ok) store.setApiKey(key)
            onResult(ok)
        }
    }

    fun bestOffer(offers: List<Offer>): Offer? {
        val subs = store.subscriptions.value
        return offers.filter { it.service != null }.minByOrNull { o ->
            when {
                o.type == OfferType.SUBSCRIPTION && o.service in subs -> 0
                o.type == OfferType.FREE || o.type == OfferType.ADS -> 1
                o.type == OfferType.SUBSCRIPTION -> 2
                o.service in subs -> 3
                else -> 4 + o.type.rank
            }
        }
    }

    fun isIncluded(offers: List<Offer>?): Boolean {
        val subs = store.subscriptions.value
        return offers.orEmpty().any { it.type == OfferType.SUBSCRIPTION && it.service in subs }
    }

    fun play(title: Title, service: StreamingService) {
        val ctx = getApplication<Application>()
        val msg = when (val r = AppLauncher.launch(ctx, service, title)) {
            is LaunchResult.OpenedSearch -> { store.recordOpened(title, service); "Opening ${service.displayName}: searching for “${title.name}”" }
            is LaunchResult.OpenedApp -> { store.recordOpened(title, service); "Opening ${service.displayName}. Search for “${title.name}” there" }
            is LaunchResult.NotInstalled ->
                if (r.openedStore) "${service.displayName} isn't installed. Opening Google Play" else "${service.displayName} isn't installed on this TV"
            is LaunchResult.Failed -> r.message
        }
        showToast(msg)
    }

    fun playTrailer(key: String) {
        if (!AppLauncher.playTrailer(getApplication(), key)) showToast("YouTube isn't available on this TV")
    }

    // ---------- Plex ----------

    val plexServer: ConnectedServer? get() = plex.server

    fun loadPlexHome(silent: Boolean = false) {
        val s = plex.server ?: return
        viewModelScope.launch {
            if (!silent || _plexHome.value !is Loadable.Ready) _plexHome.value = Loadable.Loading
            _plexHome.value = runCatching {
                coroutineScope {
                    val sections = plex.api.sections(s)
                    val deck = async { runCatching { plex.api.onDeck(s) }.getOrDefault(emptyList()) }
                    val movies = sections.filter { it.type == PlexType.MOVIE }.map { sec ->
                        async { runCatching { plex.api.sectionItems(s, sec, "addedAt:desc", 0, 24).first }.getOrDefault(emptyList()) }
                    }
                    val shows = sections.filter { it.type == PlexType.SHOW }.map { sec ->
                        async { runCatching { plex.api.sectionItems(s, sec, "addedAt:desc", 0, 24).first }.getOrDefault(emptyList()) }
                    }
                    PlexHome(
                        onDeck = deck.await(),
                        recentMovies = movies.flatMap { it.await() },
                        recentShows = shows.flatMap { it.await() },
                        sections = sections,
                    )
                }
            }.fold({ Loadable.Ready(it) }, { Loadable.Failed(it.message ?: "Couldn't load your Plex library") })
        }
    }

    suspend fun plexMetadata(item: PlexItem): PlexItem =
        plex.server?.let { s -> runCatching { plex.api.metadata(s, item.ratingKey) }.getOrNull() } ?: item

    suspend fun plexChildren(ratingKey: String): List<PlexItem> =
        plex.server?.let { s -> runCatching { plex.api.children(s, ratingKey) }.getOrNull() }.orEmpty()

    suspend fun plexSection(section: PlexSection, sort: String, start: Int, size: Int, unwatched: Boolean): Pair<List<PlexItem>, Int> {
        val s = plex.server ?: return emptyList<PlexItem>() to 0
        return plex.api.sectionItems(s, section, sort, start, size, unwatched)
    }

    fun plexImage(path: String?, widthPx: Int, heightPx: Int, blur: Int = 0): String? =
        plex.server?.let { plex.api.image(it, path, widthPx, heightPx, blur) }

    /** Plays a movie or episode. For a show, plays the next unwatched episode. */
    fun playPlex(item: PlexItem, fromStart: Boolean = false) {
        viewModelScope.launch {
            val target = when (item.type) {
                PlexType.SHOW, PlexType.SEASON -> nextEpisode(item)
                else -> item
            }
            if (target == null) {
                showToast("No playable episodes found")
                return@launch
            }
            val full = if (target.media.isEmpty()) plexMetadata(target) else target
            if (full.media.isEmpty()) {
                showToast("This title has no playable file")
                return@launch
            }
            val start = if (fromStart) 0L else full.viewOffsetMs ?: 0L
            _overlay.value = Overlay.Player(PlayRequest(full, start))
        }
    }

    private suspend fun nextEpisode(item: PlexItem): PlexItem? {
        val seasons = if (item.type == PlexType.SEASON) listOf(item) else plexChildren(item.ratingKey).filter { it.type == PlexType.SEASON }
        val season = seasons.firstOrNull { (it.viewedLeafCount ?: 0) < (it.leafCount ?: 0) } ?: seasons.firstOrNull() ?: return null
        val episodes = plexChildren(season.ratingKey)
        return episodes.firstOrNull { (it.viewOffsetMs ?: 0L) > 0 } ?: episodes.firstOrNull { it.viewCount == 0 } ?: episodes.firstOrNull()
    }

    /** The next episode after this one in the same season, for autoplay. */
    suspend fun episodeAfter(item: PlexItem): PlexItem? {
        val seasonKey = item.seasonKey ?: return null
        val eps = plexChildren(seasonKey)
        val i = eps.indexOfFirst { it.ratingKey == item.ratingKey }
        return eps.getOrNull(i + 1)
    }

    fun playNext(item: PlexItem) {
        _overlay.value = Overlay.Player(PlayRequest(item, 0L))
    }

    fun reportPlayback(item: PlexItem, state: String, timeMs: Long, durationMs: Long) {
        val s = plex.server ?: return
        viewModelScope.launch { plex.api.timeline(s, item, state, timeMs, durationMs) }
    }

    fun setWatched(item: PlexItem, watched: Boolean, onDone: () -> Unit = {}) {
        val s = plex.server ?: return
        viewModelScope.launch {
            runCatching { plex.api.markWatched(s, item, watched) }
                .onSuccess { showToast(if (watched) "Marked as watched" else "Marked as unwatched"); loadPlexHome(silent = true); onDone() }
                .onFailure { showToast("Couldn't update Plex") }
        }
    }

    fun startPlexLink() {
        _overlay.value = Overlay.PlexLink
        if (plex.state.value is PlexState.Connected) return
        requestPin()
    }

    private fun requestPin() {
        pinJob?.cancel()
        pinJob = viewModelScope.launch {
            val p = runCatching { plex.api.createPin() }.getOrElse {
                _pin.value = null
                showToast("Couldn't reach plex.tv")
                return@launch
            }
            _pin.value = p
            while (isActive) {
                delay(2_000)
                if (System.currentTimeMillis() > p.expiresAt) {
                    requestPin()
                    return@launch
                }
                val token = runCatching { plex.api.checkPin(p.id) }.getOrNull()
                if (token != null) {
                    _pin.value = null
                    plex.onSignedIn(token)
                    return@launch
                }
            }
        }
    }

    fun cancelPlexLink() {
        pinJob?.cancel()
        _pin.value = null
    }

    fun choosePlexServer(server: PlexServer) {
        viewModelScope.launch { plex.choose(server) }
    }

    fun retryPlex() {
        viewModelScope.launch { plex.retry() }
    }

    fun switchPlexServer() {
        _overlay.value = Overlay.PlexLink
        viewModelScope.launch { plex.switchServer() }
    }

    fun signOutPlex() {
        plex.signOut()
        showToast("Signed out of Plex")
    }

    fun showToast(msg: String) {
        toastJob?.cancel()
        _toast.value = msg
        toastJob = viewModelScope.launch {
            delay(3500)
            _toast.value = null
        }
    }
}
