package com.claudetv.hub.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.claudetv.hub.data.ContentRow
import com.claudetv.hub.data.DemoCatalog
import com.claudetv.hub.data.HomeData
import com.claudetv.hub.data.RowStyle
import com.claudetv.hub.data.StreamingService
import com.claudetv.hub.data.Title
import com.claudetv.hub.data.TmdbClient
import com.claudetv.hub.data.VideoFormat
import com.claudetv.hub.data.plex.PlexItem
import com.claudetv.hub.data.plex.PlexType
import com.claudetv.hub.ui.AppViewModel
import com.claudetv.hub.ui.Loadable
import com.claudetv.hub.ui.PlexHome
import com.claudetv.hub.ui.components.AmbientBackdrop
import com.claudetv.hub.ui.components.CardExtras
import com.claudetv.hub.ui.components.CinematicScrims
import com.claudetv.hub.ui.components.DemoBadge
import com.claudetv.hub.ui.components.FormatRow
import com.claudetv.hub.ui.components.GlassChip
import com.claudetv.hub.ui.components.PillButton
import com.claudetv.hub.ui.components.PlexCardStyle
import com.claudetv.hub.ui.components.PlexRow
import com.claudetv.hub.ui.components.ServiceBadge
import com.claudetv.hub.ui.components.TitleRow
import com.claudetv.hub.ui.components.glass
import com.claudetv.hub.ui.components.px
import com.claudetv.hub.ui.components.rememberPivotSpec
import com.claudetv.hub.ui.components.safeRequest
import com.claudetv.hub.ui.theme.Dimens
import com.claudetv.hub.ui.theme.Motion
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type
import kotlinx.coroutines.android.awaitFrame

private val HeroHeight = 448.dp

/** One page of the hero carousel, from either your Plex library or the streaming catalog. */
private data class HeroSlide(
    val key: String,
    val title: String,
    val artUrl: String?,
    val logoUrl: String?,
    val meta: String,
    val overview: String,
    val service: StreamingService?,
    val fromLibrary: Boolean,
    val isDemo: Boolean,
    val formats: Set<VideoFormat>,
    val primaryLabel: String,
    val onPrimary: () -> Unit,
    val secondaryLabel: String?,
    val secondaryIcon: ImageVector?,
    val onSecondary: (() -> Unit)?,
    val onInfo: () -> Unit,
    val streamTitle: Title?,
)

/** Rows on Home, in order. */
private sealed interface HomeRow {
    val id: String
    data class Plex(override val id: String, val title: String, val subtitle: String?, val items: List<PlexItem>, val style: PlexCardStyle) : HomeRow
    data class Stream(override val id: String, val row: ContentRow) : HomeRow
    data object PlexPromo : HomeRow { override val id = "plex_promo" }
    data object DemoNote : HomeRow { override val id = "demo_note" }
}

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onChromeVisible: (Boolean) -> Unit,
    handlesBack: Boolean,
) {
    val state by vm.home.collectAsState()
    val plexHome by vm.plexHome.collectAsState()
    val plexData = (plexHome as? Loadable.Ready)?.value
    when (val s = state) {
        is Loadable.Loading -> if (plexData != null) HomeContent(vm, HomeData(emptyList(), emptyList()), plexData, onChromeVisible, handlesBack) else HomeSkeleton()
        is Loadable.Failed -> if (plexData != null) {
            HomeContent(vm, HomeData(emptyList(), emptyList()), plexData, onChromeVisible, handlesBack)
        } else {
            CenteredMessage("Couldn't load your catalog", s.message, action = "Try Again", onAction = vm::loadHome)
        }
        is Loadable.Ready -> HomeContent(vm, s.value, plexData, onChromeVisible, handlesBack)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    vm: AppViewModel,
    data: HomeData,
    plex: PlexHome?,
    onChromeVisible: (Boolean) -> Unit,
    handlesBack: Boolean,
) {
    val offers by vm.offers.collectAsState()
    val watchlist by vm.watchlist.collectAsState()
    val recents by vm.recents.collectAsState()
    val isLive by vm.isLive.collectAsState()
    val plexState by vm.plexState.collectAsState()

    var heroExpanded by remember { mutableStateOf(true) }
    var heroIndex by remember { mutableIntStateOf(0) }
    var ambientUrl by remember { mutableStateOf<String?>(null) }
    var ambientPreBlurred by remember { mutableStateOf(false) }
    var focusedRow by remember { mutableIntStateOf(-1) }
    var pendingHeroFocus by remember { mutableStateOf(false) }
    val playRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val screenW = px(960.dp)
    val screenH = px(540.dp)

    // ---------- hero slides ----------
    val slides: List<HeroSlide> = if (plex != null && plex.hero.isNotEmpty()) {
        plex.hero.map { item ->
            val resume = (item.viewOffsetMs ?: 0L) > 0
            HeroSlide(
                key = item.key,
                title = item.title,
                artUrl = vm.plexImage(item.art, screenW, screenH),
                logoUrl = vm.plexImage(item.logo, px(400.dp), px(120.dp)),
                meta = listOfNotNull(
                    item.year?.toString(),
                    if (item.type == PlexType.SHOW) item.leafCount?.let { "$it episodes" } else item.durationMs?.let { formatRuntime(it) },
                    item.contentRating,
                    item.rating?.let { "★ %.1f".format(it) },
                ).joinToString("  ·  "),
                overview = item.summary,
                service = null,
                fromLibrary = true,
                isDemo = false,
                formats = item.quickFormats,
                primaryLabel = when {
                    item.type == PlexType.SHOW -> "Play"
                    resume -> "Resume"
                    else -> "Play"
                },
                onPrimary = { vm.playPlex(item) },
                secondaryLabel = null,
                secondaryIcon = null,
                onSecondary = null,
                onInfo = { vm.openPlex(item, null) },
                streamTitle = null,
            )
        }
    } else {
        data.hero.map { h ->
            val inList = watchlist.any { it.key == h.title.key }
            HeroSlide(
                key = h.title.key,
                title = h.title.name,
                artUrl = TmdbClient.heroBackdrop(h.title.backdropPath),
                logoUrl = TmdbClient.logo(h.logoPath),
                meta = listOfNotNull(h.title.year, h.title.type.label, h.title.rating?.let { "★ %.1f".format(it) }).joinToString("  ·  "),
                overview = h.title.overview,
                service = h.service,
                fromLibrary = false,
                isDemo = h.title.isDemo,
                formats = emptySet(),
                primaryLabel = h.service?.let { "Play on ${it.displayName}" } ?: "View Details",
                onPrimary = { h.service?.let { s -> vm.play(h.title, s) } ?: vm.openDetails(h.title, null) },
                secondaryLabel = if (inList) "In Watchlist" else "Watchlist",
                secondaryIcon = if (inList) Icons.Filled.Check else Icons.Filled.Add,
                onSecondary = { vm.toggleWatchlist(h.title) },
                onInfo = { vm.openDetails(h.title, null) },
                streamTitle = h.title,
            )
        }
    }
    val current: HeroSlide? = slides.getOrNull(heroIndex % slides.size.coerceAtLeast(1))

    LaunchedEffect(heroExpanded) { onChromeVisible(heroExpanded) }
    // Only on app launch; coming back via the tab bar leaves focus on the tab bar.
    LaunchedEffect(Unit) {
        if (vm.claimInitialFocus()) {
            awaitFrame()
            playRequester.safeRequest()
        }
    }
    LaunchedEffect(pendingHeroFocus) {
        if (pendingHeroFocus) {
            listState.animateScrollToItem(0)
            awaitFrame()
            playRequester.safeRequest()
            pendingHeroFocus = false
        }
    }
    LaunchedEffect(heroIndex, heroExpanded, slides.size) {
        if (heroExpanded && slides.size > 1) {
            kotlinx.coroutines.delay(9_000)
            heroIndex = (heroIndex + 1) % slides.size
        }
    }
    BackHandler(enabled = handlesBack && !heroExpanded) {
        heroExpanded = true
        pendingHeroFocus = true
    }

    // ---------- rows ----------
    val rows: List<HomeRow> = buildList {
        if (plex != null) {
            if (plex.onDeck.isNotEmpty()) add(HomeRow.Plex("deck", "Continue Watching", null, plex.onDeck, PlexCardStyle.LANDSCAPE))
            if (plex.recentMovies.isNotEmpty()) add(HomeRow.Plex("recent_movies", "Recently Added Movies", null, plex.recentMovies, PlexCardStyle.POSTER))
            if (plex.recentShows.isNotEmpty()) add(HomeRow.Plex("recent_shows", "Recently Added TV", null, plex.recentShows, PlexCardStyle.POSTER))
        } else if (plexState !is com.claudetv.hub.data.plex.PlexState.Connecting) {
            add(HomeRow.PlexPromo)
        }
        if (!isLive) add(HomeRow.DemoNote)
        if (plex == null) {
            val cont = if (isLive) recents.map { it.title } else DemoCatalog.sampleContinue.map { it.first }
            if (cont.isNotEmpty()) add(HomeRow.Stream("continue", ContentRow("continue", "Continue Watching",
                if (isLive) "Titles you opened from Claude TV" else "Sample progress: streaming apps don't share watch progress",
                cont, RowStyle.LANDSCAPE)))
        }
        data.rows.firstOrNull()?.let { add(HomeRow.Stream(it.id, if (plex != null) it.copy(title = "Discover on Your Services") else it)) }
        if (watchlist.isNotEmpty()) add(HomeRow.Stream("watchlist", ContentRow("watchlist", "Your Watchlist", null, watchlist, RowStyle.LANDSCAPE)))
        data.rows.drop(1).forEach { add(HomeRow.Stream(it.id, it)) }
        if (plex != null && isLive && recents.isNotEmpty()) {
            add(HomeRow.Stream("continue", ContentRow("continue", "Recently Opened in Apps", null, recents.map { it.title }, RowStyle.LANDSCAPE)))
        }
    }
    val recentByKey = recents.associateBy { it.title.key }
    val demoProgress = DemoCatalog.sampleContinue.associate { it.first.key to it.second }
    val ambientW = px(480.dp)
    val ambientH = px(270.dp)

    Box(Modifier.fillMaxSize()) {
        // ---- Backdrop: the sharp hero art, or the focused title frosted once you're browsing ----
        val dim by animateFloatAsState(if (heroExpanded) 0f else 0.45f, tween(600, easing = Motion.Ease), label = "dim")
        if (heroExpanded || ambientUrl == null) {
            AmbientBackdrop(current?.artUrl, dim = dim)
        } else {
            AmbientBackdrop(ambientUrl, blurred = true, alreadyBlurred = ambientPreBlurred, dim = dim)
        }
        CinematicScrims()

        Column(Modifier.fillMaxSize()) {
            val heroH by animateDpAsState(if (heroExpanded) HeroHeight else 0.dp, tween(520, easing = Motion.Ease), label = "heroH")
            val heroAlpha by animateFloatAsState(if (heroExpanded) 1f else 0f, tween(360), label = "heroA")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroH)
                    .clipToBounds()
                    .graphicsLayer { alpha = heroAlpha }
                    .onFocusChanged { if (it.hasFocus) heroExpanded = true },
            ) {
                if (current != null) {
                    Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Bottom, unbounded = true).height(HeroHeight).align(Alignment.BottomStart)) {
                        HeroContent(
                            slide = current,
                            index = heroIndex,
                            count = slides.size,
                            focusable = heroExpanded,
                            playRequester = playRequester,
                            tvFormats = vm.capabilities.formats,
                            onPrev = { if (slides.isNotEmpty()) heroIndex = (heroIndex - 1 + slides.size) % slides.size },
                            onNext = { if (slides.isNotEmpty()) heroIndex = (heroIndex + 1) % slides.size },
                        )
                    }
                }
            }

            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivotSpec(0f, 78.dp)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp && focusedRow == 0 && !heroExpanded && current != null) {
                                heroExpanded = true
                                pendingHeroFocus = true
                                true
                            } else false
                        },
                    contentPadding = PaddingValues(top = 6.dp, bottom = 60.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.RowGap),
                ) {
                    itemsIndexed(rows, key = { _, r -> r.id }) { i, row ->
                        val rowMod = Modifier.onFocusChanged {
                            if (it.hasFocus) {
                                focusedRow = i
                                heroExpanded = false
                            }
                        }
                        when (row) {
                            is HomeRow.Plex -> PlexRow(
                                vm = vm,
                                rowId = row.id,
                                title = row.title,
                                subtitle = row.subtitle,
                                items = row.items,
                                style = row.style,
                                onOpen = { item, key -> vm.openPlex(item, key) },
                                onFocused = { item ->
                                    ambientUrl = vm.plexImage(item.art ?: item.showArt ?: item.thumb, ambientW, ambientH, blur = 60)
                                    ambientPreBlurred = true
                                },
                                modifier = rowMod,
                            )
                            is HomeRow.Stream -> TitleRow(
                                row = row.row,
                                offers = offers,
                                isIncluded = vm::isIncluded,
                                onOpen = { t, key -> vm.openDetails(t, key) },
                                onFocused = { t ->
                                    ambientUrl = TmdbClient.image(t.backdropPath ?: t.posterPath, "w780")
                                    ambientPreBlurred = false
                                    vm.onCardFocused(t)
                                },
                                modifier = rowMod,
                                extras = { t ->
                                    if (row.row.id != "continue") null
                                    else if (isLive) {
                                        val r = recentByKey[t.key]
                                        CardExtras(
                                            service = r?.service,
                                            caption = r?.let {
                                                listOfNotNull(
                                                    it.service?.let { s -> "Opened in ${s.displayName}" },
                                                    DateUtils.getRelativeTimeSpanString(it.openedAt).toString(),
                                                ).joinToString(" · ")
                                            },
                                        )
                                    } else {
                                        CardExtras(service = t.demoService, progress = demoProgress[t.key], caption = "Sample progress")
                                    }
                                },
                            )
                            HomeRow.PlexPromo -> PlexPromo(onConnect = vm::startPlexLink, modifier = rowMod)
                            HomeRow.DemoNote -> DemoBanner()
                        }
                    }
                    item(key = "footer") { Attribution(isLive) }
                }
            }
        }
    }
}

@Composable
private fun HeroContent(
    slide: HeroSlide,
    index: Int,
    count: Int,
    focusable: Boolean,
    playRequester: FocusRequester,
    tvFormats: Set<VideoFormat>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    var focusedButton by remember { mutableIntStateOf(-1) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Dimens.ScreenH, end = Dimens.ScreenH, bottom = 20.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        AnimatedContent(
            targetState = slide,
            contentKey = { it.key },
            transitionSpec = {
                (fadeIn(tween(500, 150)) + slideInHorizontally(tween(600, easing = Motion.Ease)) { it / 14 })
                    .togetherWith(fadeOut(tween(250)))
            },
            label = "hero",
        ) { h ->
            Column(Modifier.width(560.dp)) {
                if (h.fromLibrary) {
                    GlassChip { Text("YOUR LIBRARY", style = Type.Badge.copy(color = Palette.TextPrimary)) }
                    Spacer(Modifier.height(12.dp))
                }
                if (h.logoUrl != null) {
                    AsyncImage(
                        model = h.logoUrl,
                        contentDescription = h.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier.heightIn(max = 116.dp).widthIn(max = 400.dp),
                    )
                } else {
                    Text(h.title, style = Type.HeroTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    h.service?.let { ServiceBadge(it) }
                    Text(h.meta, style = Type.Meta)
                    FormatRow(h.formats, tvFormats)
                    if (h.isDemo) DemoBadge()
                }
                Spacer(Modifier.height(10.dp))
                Text(h.overview, style = Type.Body, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(20.dp))
        // Buttons stay outside AnimatedContent so focus survives slide changes.
        val lastIndex = if (slide.onSecondary != null) 2 else 1
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.DirectionLeft && focusedButton == 0 -> { onPrev(); true }
                    e.key == Key.DirectionRight && focusedButton == lastIndex -> { onNext(); true }
                    else -> false
                }
            },
        ) {
            PillButton(
                text = slide.primaryLabel,
                icon = Icons.Filled.PlayArrow,
                primary = true,
                onClick = slide.onPrimary,
                modifier = Modifier
                    .focusRequester(playRequester)
                    .focusProperties { canFocus = focusable }
                    .onFocusChanged { if (it.isFocused) focusedButton = 0 },
            )
            if (slide.onSecondary != null) {
                PillButton(
                    text = slide.secondaryLabel,
                    icon = slide.secondaryIcon,
                    onClick = slide.onSecondary,
                    modifier = Modifier
                        .focusProperties { canFocus = focusable }
                        .onFocusChanged { if (it.isFocused) focusedButton = 1 },
                )
            }
            PillButton(
                text = null,
                icon = Icons.Filled.Info,
                onClick = slide.onInfo,
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                    .focusProperties { canFocus = focusable }
                    .onFocusChanged { if (it.isFocused) focusedButton = lastIndex },
            )
        }
        Spacer(Modifier.height(18.dp))
        if (count > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(count) { i ->
                    val active = i == index % count
                    val w by animateDpAsState(if (active) 22.dp else 7.dp, tween(400, easing = Motion.Ease), label = "dot")
                    Box(
                        Modifier
                            .width(w)
                            .height(7.dp)
                            .background(if (active) Palette.TextPrimary else Palette.TextPrimary.copy(alpha = 0.3f), RoundedCornerShape(50)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlexPromo(onConnect: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = Dimens.ScreenH)
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Bring your Plex library home", style = Type.RowTitle)
            Text("Sign in once and your movies and shows play right here in full 4K, HDR and Dolby Vision.", style = Type.RowSubtitle)
        }
        Spacer(Modifier.width(20.dp))
        PillButton("Connect Plex", onClick = onConnect, primary = true, icon = Icons.Filled.PlayArrow)
    }
}

@Composable
private fun DemoBanner() {
    Row(
        Modifier
            .padding(horizontal = Dimens.ScreenH)
            .glass(RoundedCornerShape(14.dp), tint = 0.04f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Streaming discovery is in demo mode. Add a free TMDB API key in Settings for the live catalog.",
            style = Type.Meta.copy(color = Palette.Demo),
        )
    }
}

@Composable
private fun Attribution(isLive: Boolean) {
    Text(
        if (isLive) "Catalog and artwork from TMDB. Streaming availability data by JustWatch. This product uses the TMDB API but is not endorsed or certified by TMDB."
        else "Sample catalog. Titles are real, but availability, formats and progress shown here are illustrative.",
        style = Type.CardMeta.copy(color = Palette.TextTertiary),
        modifier = Modifier.padding(horizontal = Dimens.ScreenH),
    )
}

@Composable
private fun HomeSkeleton() {
    Column(
        Modifier.fillMaxSize().padding(start = Dimens.ScreenH, top = 300.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(320.dp).height(36.dp).glass(RoundedCornerShape(8.dp)))
        Box(Modifier.width(460.dp).height(14.dp).glass(RoundedCornerShape(4.dp)))
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            repeat(5) {
                Box(Modifier.width(Dimens.LandscapeCardWidth).height(133.dp).glass(RoundedCornerShape(16.dp)))
            }
        }
    }
}

fun formatRuntime(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

@Composable
fun CenteredMessage(title: String, body: String?, action: String? = null, onAction: () -> Unit = {}) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { awaitFrame(); requester.safeRequest() }
    Column(
        Modifier.fillMaxSize().padding(80.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = Type.ScreenTitle)
        body?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = Type.Body, maxLines = 4)
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            PillButton(action, onAction, icon = Icons.Filled.Refresh, modifier = Modifier.focusRequester(requester))
        }
    }
}
