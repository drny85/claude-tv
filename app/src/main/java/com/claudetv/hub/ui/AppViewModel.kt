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
import com.claudetv.hub.platform.AppLauncher
import com.claudetv.hub.platform.DeviceCapabilities
import com.claudetv.hub.platform.DisplayCapabilities
import com.claudetv.hub.platform.LaunchResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

enum class Tab(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    WATCHLIST("Watchlist"),
    SERVICES("Services"),
    SETTINGS("Settings"),
}

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val message: String) : Loadable<Nothing>
}

@OptIn(FlowPreview::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val store = UserStore(app)
    private val repo = CatalogRepository(store)

    val subscriptions = store.subscriptions
    val watchlist = store.watchlist
    val recents = store.recents
    val apiKey = store.apiKey
    val region = store.region

    val capabilities: DeviceCapabilities = DisplayCapabilities.detect(app)

    private val _tab = MutableStateFlow(Tab.HOME)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _details = MutableStateFlow<List<Title>>(emptyList())
    val detailsStack: StateFlow<List<Title>> = _details.asStateFlow()

    private val _home = MutableStateFlow<Loadable<HomeData>>(Loadable.Loading)
    val home: StateFlow<Loadable<HomeData>> = _home.asStateFlow()

    private val _isLive = MutableStateFlow(repo.isLive)
    val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    private val _offers = MutableStateFlow<Map<String, List<Offer>>>(emptyMap())
    val offers: StateFlow<Map<String, List<Offer>>> = _offers.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _results = MutableStateFlow<Loadable<List<SearchResult>>>(Loadable.Ready(emptyList()))
    val results: StateFlow<Loadable<List<SearchResult>>> = _results.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** Card key that should take focus again after details closes. */
    private val _restoreFocus = MutableStateFlow<String?>(null)
    val restoreFocus: StateFlow<String?> = _restoreFocus.asStateFlow()

    private var toastJob: Job? = null
    private var offerJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadHome()
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

    fun openDetails(title: Title, focusKey: String?) {
        _restoreFocus.value = focusKey
        _details.value = _details.value + title
    }

    /** Returns true if a details page was closed. */
    fun closeDetails(): Boolean {
        if (_details.value.isEmpty()) return false
        _details.value = _details.value.dropLast(1)
        return true
    }

    fun consumeRestoreFocus() { _restoreFocus.value = null }

    private var initialFocusClaimed = false

    /** True exactly once per app session, for the first screen to take focus. */
    fun claimInitialFocus(): Boolean = !initialFocusClaimed.also { initialFocusClaimed = true }

    // ---------- data ----------

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
            return
        }
        searchJob = viewModelScope.launch {
            _results.value = Loadable.Loading
            _results.value = runCatching { repo.search(q, store.subscriptions.value) }
                .fold({ list ->
                    _offers.value = _offers.value + list.associate { it.title.key to it.offers }
                    Loadable.Ready(list)
                }, { Loadable.Failed(it.message ?: "Search failed") })
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

    fun removeRecent(title: Title) = store.removeRecent(title)

    // ---------- hand-off ----------

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

    fun showToast(msg: String) {
        toastJob?.cancel()
        _toast.value = msg
        toastJob = viewModelScope.launch {
            delay(3500)
            _toast.value = null
        }
    }
}
