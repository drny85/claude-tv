package com.claudetv.hub.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.claudetv.hub.data.HeroItem
import com.claudetv.hub.data.HomeData
import com.claudetv.hub.data.RowStyle
import com.claudetv.hub.data.Title
import com.claudetv.hub.data.TmdbClient
import com.claudetv.hub.ui.AppViewModel
import com.claudetv.hub.ui.Loadable
import com.claudetv.hub.ui.components.Artwork
import com.claudetv.hub.ui.components.CardExtras
import com.claudetv.hub.ui.components.PillButton
import com.claudetv.hub.ui.components.ServiceBadge
import com.claudetv.hub.ui.components.TitleRow
import com.claudetv.hub.ui.components.rememberPivotSpec
import com.claudetv.hub.ui.components.safeRequest
import com.claudetv.hub.ui.theme.Dimens
import com.claudetv.hub.ui.theme.Motion
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type
import kotlinx.coroutines.android.awaitFrame

private val HeroHeight = 448.dp

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onChromeVisible: (Boolean) -> Unit,
    handlesBack: Boolean,
) {
    val state by vm.home.collectAsState()
    when (val s = state) {
        is Loadable.Loading -> HomeSkeleton()
        is Loadable.Failed -> CenteredMessage(
            "Couldn't load your catalog",
            s.message,
            action = "Try Again",
            onAction = vm::loadHome,
        )
        is Loadable.Ready -> HomeContent(vm, s.value, onChromeVisible, handlesBack)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    vm: AppViewModel,
    data: HomeData,
    onChromeVisible: (Boolean) -> Unit,
    handlesBack: Boolean,
) {
    val offers by vm.offers.collectAsState()
    val watchlist by vm.watchlist.collectAsState()
    val recents by vm.recents.collectAsState()
    val isLive by vm.isLive.collectAsState()

    var heroExpanded by remember { mutableStateOf(true) }
    var heroIndex by remember { mutableIntStateOf(0) }
    var ambient by remember { mutableStateOf<Title?>(null) }
    var focusedRow by remember { mutableIntStateOf(-1) }
    var pendingHeroFocus by remember { mutableStateOf(false) }
    val playRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val hero = data.hero
    val current: HeroItem? = hero.getOrNull(heroIndex % hero.size.coerceAtLeast(1))

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
    // Auto-advance the hero like the Apple TV app; resets whenever the person moves through it.
    LaunchedEffect(heroIndex, heroExpanded, hero.size) {
        if (heroExpanded && hero.size > 1) {
            kotlinx.coroutines.delay(9_000)
            heroIndex = (heroIndex + 1) % hero.size
        }
    }
    BackHandler(enabled = handlesBack && !heroExpanded) {
        heroExpanded = true
        pendingHeroFocus = true
    }

    val rows: List<ContentRow> = buildList {
        val cont = if (isLive) {
            recents.map { it.title }
        } else {
            DemoCatalog.sampleContinue.map { it.first }
        }
        if (cont.isNotEmpty()) add(ContentRow("continue", "Continue Watching",
            if (isLive) "Titles you opened from Claude TV" else "Sample progress: streaming services don't share watch progress",
            cont, RowStyle.LANDSCAPE))
        data.rows.firstOrNull()?.let { add(it) }
        if (watchlist.isNotEmpty()) add(ContentRow("watchlist", "Up Next from Your Watchlist", null, watchlist, RowStyle.LANDSCAPE))
        addAll(data.rows.drop(1))
    }
    val recentByKey = recents.associateBy { it.title.key }
    val demoProgress = DemoCatalog.sampleContinue.associate { it.first.key to it.second }

    Box(Modifier.fillMaxSize()) {
        // ---- Ambient backdrop: the hero, or the focused title once you're browsing rows ----
        val backdropTitle = if (heroExpanded) current?.title else (ambient ?: current?.title)
        val dim by animateFloatAsState(if (heroExpanded) 0f else 0.62f, tween(600, easing = Motion.Ease), label = "dim")
        Crossfade(targetState = backdropTitle, animationSpec = tween(700), label = "backdrop") { t ->
            if (t != null) Artwork(t, RowStyle.LANDSCAPE, Modifier.fillMaxSize(), hero = true)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Palette.Background.copy(alpha = 0.92f), 0.42f to Palette.Background.copy(alpha = 0.45f), 0.75f to Color.Transparent),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 0.82f to Palette.Background.copy(alpha = 0.9f), 1f to Palette.Background),
            ),
        )

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
                            vm = vm,
                            item = current,
                            index = heroIndex,
                            count = hero.size,
                            inWatchlist = watchlist.any { it.key == current.title.key },
                            focusable = heroExpanded,
                            playRequester = playRequester,
                            onPrev = { if (hero.isNotEmpty()) heroIndex = (heroIndex - 1 + hero.size) % hero.size },
                            onNext = { if (hero.isNotEmpty()) heroIndex = (heroIndex + 1) % hero.size },
                        )
                    }
                }
            }

            CompositionLocalProviderPivot {
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
                    if (!isLive) item(key = "demo") { DemoBanner() }
                    itemsIndexed(rows, key = { _, r -> r.id }) { i, row ->
                        TitleRow(
                            row = row,
                            offers = offers,
                            isIncluded = vm::isIncluded,
                            onOpen = { t, key -> vm.openDetails(t, key) },
                            onFocused = { t -> ambient = t; vm.onCardFocused(t) },
                            modifier = Modifier.onFocusChanged {
                                if (it.hasFocus) {
                                    focusedRow = i
                                    heroExpanded = false
                                }
                            },
                            extras = { t ->
                                when (row.id) {
                                    "continue" -> if (isLive) {
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
                                    else -> null
                                }
                            },
                        )
                    }
                    item(key = "footer") { Attribution(isLive) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompositionLocalProviderPivot(content: @Composable () -> Unit) {
    // Focused rows settle a fixed distance from the top so the row title always shows.
    androidx.compose.runtime.CompositionLocalProvider(
        LocalBringIntoViewSpec provides rememberPivotSpec(0f, 78.dp),
        content = content,
    )
}

@Composable
private fun HeroContent(
    vm: AppViewModel,
    item: HeroItem,
    index: Int,
    count: Int,
    inWatchlist: Boolean,
    focusable: Boolean,
    playRequester: FocusRequester,
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
            targetState = item,
            transitionSpec = {
                (fadeIn(tween(500, 150)) + slideInHorizontally(tween(600, easing = Motion.Ease)) { it / 14 })
                    .togetherWith(fadeOut(tween(250)))
            },
            label = "hero",
        ) { h ->
            Column(Modifier.width(560.dp)) {
                if (h.logoPath != null) {
                    AsyncImage(
                        model = TmdbClient.logo(h.logoPath),
                        contentDescription = h.title.name,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier.heightIn(max = 116.dp).widthIn(max = 400.dp),
                    )
                } else {
                    Text(h.title.name, style = Type.HeroTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    h.service?.let { ServiceBadge(it) }
                    val meta = listOfNotNull(
                        h.title.year,
                        h.title.type.label,
                        h.title.rating?.let { "★ %.1f".format(it) },
                    ).joinToString("  ·  ")
                    Text(meta, style = Type.Meta)
                    if (h.title.isDemo) com.claudetv.hub.ui.components.DemoBadge()
                }
                Spacer(Modifier.height(10.dp))
                Text(h.title.overview, style = Type.Body, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(20.dp))
        // Buttons stay outside AnimatedContent so focus survives slide changes.
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.DirectionLeft && focusedButton == 0 -> { onPrev(); true }
                    e.key == Key.DirectionRight && focusedButton == 2 -> { onNext(); true }
                    else -> false
                }
            },
        ) {
            val service = item.service
            PillButton(
                text = service?.let { "Play on ${it.displayName}" } ?: "View Details",
                icon = Icons.Filled.PlayArrow,
                primary = true,
                onClick = { if (service != null) vm.play(item.title, service) else vm.openDetails(item.title, null) },
                modifier = Modifier
                    .focusRequester(playRequester)
                    .focusProperties { canFocus = focusable }
                    .onFocusChanged { if (it.isFocused) focusedButton = 0 },
            )
            PillButton(
                text = if (inWatchlist) "In Watchlist" else "Watchlist",
                icon = if (inWatchlist) Icons.Filled.Check else Icons.Filled.Add,
                onClick = { vm.toggleWatchlist(item.title) },
                modifier = Modifier
                    .focusProperties { canFocus = focusable }
                    .onFocusChanged { if (it.isFocused) focusedButton = 1 },
            )
            PillButton(
                text = null,
                icon = Icons.Filled.Info,
                onClick = { vm.openDetails(item.title, null) },
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                    .focusProperties { canFocus = focusable }
                    .onFocusChanged { if (it.isFocused) focusedButton = 2 },
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
private fun DemoBanner() {
    Row(
        Modifier
            .padding(horizontal = Dimens.ScreenH)
            .background(Palette.Demo.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Demo mode: you're seeing sample data. Add a free TMDB API key in Settings to load the live catalog and real availability.",
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
        Box(Modifier.width(320.dp).height(36.dp).background(Palette.Surface, RoundedCornerShape(8.dp)))
        Box(Modifier.width(460.dp).height(14.dp).background(Palette.Surface, RoundedCornerShape(4.dp)))
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            repeat(5) {
                Box(Modifier.width(Dimens.LandscapeCardWidth).height(133.dp).background(Palette.Surface, RoundedCornerShape(Dimens.CardRadius)))
            }
        }
    }
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
