package com.claudetv.hub.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.claudetv.hub.data.ContentRow
import com.claudetv.hub.data.Offer
import com.claudetv.hub.data.OfferType
import com.claudetv.hub.data.RowStyle
import com.claudetv.hub.data.Title
import com.claudetv.hub.data.TitleDetails
import com.claudetv.hub.data.TmdbClient
import com.claudetv.hub.data.VideoFormat
import com.claudetv.hub.ui.AppViewModel
import com.claudetv.hub.ui.Loadable
import com.claudetv.hub.ui.components.Artwork
import com.claudetv.hub.ui.components.DemoBadge
import com.claudetv.hub.ui.components.FormatBadge
import com.claudetv.hub.ui.components.PillButton
import com.claudetv.hub.ui.components.SectionHeader
import com.claudetv.hub.ui.components.TitleRow
import com.claudetv.hub.ui.components.rememberPivotSpec
import com.claudetv.hub.ui.components.safeRequest
import com.claudetv.hub.ui.theme.Dimens
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type
import kotlinx.coroutines.android.awaitFrame

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailsScreen(vm: AppViewModel, title: Title) {
    var state by remember(title.key) { mutableStateOf<Loadable<TitleDetails>>(Loadable.Loading) }
    LaunchedEffect(title.key) {
        state = runCatching { vm.details(title) }
            .fold({ Loadable.Ready(it) }, { Loadable.Failed(it.message ?: "Couldn't load details") })
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            // Keep D-pad focus inside this page; the screen underneath stays composed for focus restore.
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
    ) {
        if (title.backdropPath != null) {
            com.claudetv.hub.ui.components.AmbientBackdrop(TmdbClient.heroBackdrop(title.backdropPath))
        } else {
            Artwork(title, RowStyle.LANDSCAPE, Modifier.fillMaxSize(), hero = true)
        }
        com.claudetv.hub.ui.components.CinematicScrims()
        when (val s = state) {
            is Loadable.Loading -> Column(Modifier.padding(start = Dimens.ScreenH, top = 300.dp)) {
                Text(title.name, style = Type.HeroTitle)
            }
            is Loadable.Failed -> CenteredMessage("Couldn't load this title", s.message)
            is Loadable.Ready -> CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivotSpec(0f, 60.dp)) {
                DetailsContent(vm, s.value)
            }
        }
    }
}

@Composable
private fun DetailsContent(vm: AppViewModel, d: TitleDetails) {
    val t = d.title
    val watchlist by vm.watchlist.collectAsState()
    val subs by vm.subscriptions.collectAsState()
    val offers by vm.offers.collectAsState()
    val inList = watchlist.any { it.key == t.key }
    val best = vm.bestOffer(d.offers)
    val playRequester = remember { FocusRequester() }
    LaunchedEffect(t.key) {
        awaitFrame()
        playRequester.safeRequest()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.RowGap),
    ) {
        item(key = "header") {
            Column(
                Modifier
                    .padding(start = Dimens.ScreenH, end = Dimens.ScreenH, top = 150.dp)
                    .widthIn(max = 620.dp),
            ) {
                if (d.logoPath != null) {
                    AsyncImage(
                        model = TmdbClient.logo(d.logoPath),
                        contentDescription = t.name,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier.heightIn(max = 110.dp).widthIn(max = 380.dp),
                    )
                } else {
                    Text(t.name, style = Type.HeroTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val meta = listOfNotNull(
                        t.year,
                        t.type.label,
                        d.runtimeMinutes?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" },
                        d.seasons?.let { if (it == 1) "1 Season" else "$it Seasons" },
                        t.rating?.let { "★ %.1f".format(it) },
                    ).joinToString("  ·  ")
                    Text(meta, style = Type.Meta)
                    d.certification?.let {
                        Text(
                            it,
                            style = Type.Badge.copy(color = Palette.TextSecondary),
                            modifier = Modifier
                                .border(BorderStroke(1.dp, Palette.TextSecondary), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (t.isDemo) DemoBadge()
                }
                if (d.genres.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(d.genres.joinToString(" · "), style = Type.Meta.copy(color = Palette.TextTertiary))
                }
                d.tagline?.takeIf { it.isNotBlank() && !t.isDemo }?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = Type.Body.copy(color = Palette.TextPrimary))
                }
                Spacer(Modifier.height(10.dp))
                Text(t.overview, style = Type.Body, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val service = best?.service
                    val bestType = best?.type
                    PillButton(
                        text = when {
                            service == null -> "Not streaming in your region"
                            bestType == OfferType.RENT -> "Rent on ${service.displayName}"
                            bestType == OfferType.BUY -> "Buy on ${service.displayName}"
                            else -> "Play on ${service.displayName}"
                        },
                        icon = Icons.Filled.PlayArrow,
                        primary = true,
                        onClick = { if (service != null) vm.play(t, service) else vm.showToast("No supported service has this title in your region") },
                        modifier = Modifier.focusRequester(playRequester),
                    )
                    d.trailerYoutubeKey?.let { key ->
                        PillButton("Trailer", onClick = { vm.playTrailer(key) })
                    }
                    PillButton(
                        text = if (inList) "In Watchlist" else "Watchlist",
                        icon = if (inList) Icons.Filled.Check else Icons.Filled.Add,
                        onClick = { vm.toggleWatchlist(t) },
                    )
                }
                if (d.cast.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text("Starring " + d.cast.joinToString(", "), style = Type.CardMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item(key = "where") {
            WhereToWatch(vm, t, d.offers, subs)
        }

        item(key = "formats") {
            FormatsPanel(vm, t, best)
        }

        if (d.similar.isNotEmpty()) {
            item(key = "similar") {
                TitleRow(
                    row = ContentRow("similar_${t.key}", "More Like This", null, d.similar, RowStyle.POSTER),
                    offers = offers,
                    isIncluded = vm::isIncluded,
                    onOpen = { s, key -> vm.openDetails(s, key) },
                    onFocused = { s -> vm.onCardFocused(s) },
                )
            }
        }
    }
}

@Composable
private fun WhereToWatch(vm: AppViewModel, t: Title, offers: List<Offer>, subs: Set<com.claudetv.hub.data.StreamingService>) {
    val sorted = offers
        .distinctBy { it.providerId to it.type }
        .sortedWith(compareBy({ if (it.type == OfferType.SUBSCRIPTION && it.service in subs) 0 else 1 }, { it.type.rank }, { if (it.service != null) 0 else 1 }))
    Column {
        SectionHeader(
            "Where to Watch",
            if (offers.isEmpty()) "No streaming, rental or purchase options found for your region"
            else if (t.isDemo) "Sample availability" else "Availability by JustWatch, via TMDB",
            Modifier.padding(start = Dimens.ScreenH),
        )
        if (sorted.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.ScreenH),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(sorted, key = { "${it.providerId}_${it.type}" }) { o ->
                    OfferTile(o, included = o.type == OfferType.SUBSCRIPTION && o.service in subs) {
                        val s = o.service
                        if (s != null) vm.play(t, s) else vm.showToast("${o.providerName} isn't one of the apps Claude TV can open yet")
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferTile(o: Offer, included: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.width(200.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Palette.Focus,
            contentColor = Palette.TextPrimary,
            focusedContentColor = Palette.OnFocus,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(if (included) BorderStroke(1.dp, Palette.Included.copy(alpha = 0.7f)) else com.claudetv.hub.ui.components.glassEdge(), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val logo = TmdbClient.providerLogo(o.logoPath)
            if (logo != null) {
                AsyncImage(model = logo, contentDescription = o.providerName, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)))
            } else {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(9.dp))
                        .background(Color(o.service?.brandColor ?: 0xFF444450)),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                val fg = if (focused) Palette.OnFocus else Palette.TextPrimary
                Text(o.providerName, style = Type.CardTitle.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (included) "Included" else o.type.label,
                    style = Type.CardMeta.copy(color = if (included) Palette.Included else if (focused) Palette.OnFocus.copy(alpha = 0.7f) else Palette.TextSecondary),
                )
            }
        }
    }
}

@Composable
private fun FormatsPanel(vm: AppViewModel, t: Title, best: Offer?) {
    val tv = vm.capabilities
    val bestService = best?.service
    val formats: Set<VideoFormat> = if (t.isDemo) t.demoFormats else bestService?.formats.orEmpty()
    Column(Modifier.padding(horizontal = Dimens.ScreenH)) {
        SectionHeader(
            "Picture & Sound",
            when {
                t.isDemo -> "Sample formats"
                bestService != null -> "Up to these formats on ${bestService.displayName}'s top plan. Public APIs don't list per-title formats."
                else -> "Format information appears when a supported service carries this title"
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            formats.sortedBy { it.ordinal }.forEach { FormatBadge(it, supportedByTv = it in tv.formats || tv.formats.isEmpty()) }
            if (formats.isNotEmpty()) Spacer(Modifier.width(10.dp))
            Text(
                "Your TV: " + (listOf(tv.resolutionLabel) + tv.formats.filter { it != VideoFormat.UHD_4K }.map { it.label }).joinToString(" · "),
                style = Type.CardMeta,
            )
        }
    }
}
