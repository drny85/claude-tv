package com.claudetv.hub.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.claudetv.hub.data.plex.PlexItem
import com.claudetv.hub.data.plex.PlexType
import com.claudetv.hub.ui.AppViewModel
import com.claudetv.hub.ui.Loadable
import com.claudetv.hub.ui.components.AmbientBackdrop
import com.claudetv.hub.ui.components.Chip
import com.claudetv.hub.ui.components.CinematicScrims
import com.claudetv.hub.ui.components.FormatRow
import com.claudetv.hub.ui.components.GlassChip
import com.claudetv.hub.ui.components.PillButton
import com.claudetv.hub.ui.components.PlexCard
import com.claudetv.hub.ui.components.PlexCardStyle
import com.claudetv.hub.ui.components.SectionHeader
import com.claudetv.hub.ui.components.glass
import com.claudetv.hub.ui.components.px
import com.claudetv.hub.ui.components.rememberPivotSpec
import com.claudetv.hub.ui.components.safeRequest
import com.claudetv.hub.ui.theme.Dimens
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type
import kotlinx.coroutines.android.awaitFrame

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlexDetailsScreen(vm: AppViewModel, start: PlexItem) {
    var item by remember(start.key) { mutableStateOf(start) }
    var version by remember(start.key) { mutableIntStateOf(0) }
    LaunchedEffect(start.key, version) { item = vm.plexMetadata(start) }

    val artPath = item.art ?: item.showArt ?: start.art
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
    ) {
        AmbientBackdrop(vm.plexImage(artPath, px(960.dp), px(540.dp)))
        CinematicScrims()
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivotSpec(0f, 60.dp)) {
            PlexDetailsContent(vm, item, onChanged = { version++ })
        }
    }
}

@Composable
private fun PlexDetailsContent(vm: AppViewModel, item: PlexItem, onChanged: () -> Unit) {
    val playRequester = remember { FocusRequester() }
    LaunchedEffect(item.ratingKey) {
        awaitFrame()
        playRequester.safeRequest()
    }
    val isShow = item.type == PlexType.SHOW
    val isEpisode = item.type == PlexType.EPISODE
    val resume = (item.viewOffsetMs ?: 0L) > 0

    // Seasons and episodes for shows; sibling episodes for an episode.
    var seasons by remember(item.ratingKey) { mutableStateOf<List<PlexItem>>(emptyList()) }
    var selectedSeason by remember(item.ratingKey) { mutableStateOf<PlexItem?>(null) }
    var episodes by remember(item.ratingKey) { mutableStateOf<Loadable<List<PlexItem>>>(Loadable.Loading) }
    LaunchedEffect(item.ratingKey) {
        if (isShow) {
            seasons = vm.plexChildren(item.ratingKey).filter { it.type == PlexType.SEASON }
            selectedSeason = seasons.firstOrNull { (it.viewedLeafCount ?: 0) < (it.leafCount ?: 0) } ?: seasons.firstOrNull()
        } else if (isEpisode && item.seasonKey != null) {
            episodes = Loadable.Ready(vm.plexChildren(item.seasonKey))
        }
    }
    LaunchedEffect(selectedSeason?.ratingKey) {
        selectedSeason?.let { s ->
            episodes = Loadable.Loading
            episodes = Loadable.Ready(vm.plexChildren(s.ratingKey))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.RowGap),
    ) {
        item(key = "header") {
            Column(
                Modifier
                    .padding(start = Dimens.ScreenH, end = Dimens.ScreenH, top = 120.dp)
                    .widthIn(max = 640.dp),
            ) {
                GlassChip { Text("YOUR LIBRARY", style = Type.Badge.copy(color = Palette.TextPrimary)) }
                Spacer(Modifier.height(12.dp))
                if (isEpisode) {
                    Text(item.showTitle.orEmpty(), style = Type.RowTitle.copy(color = Palette.TextSecondary))
                    Spacer(Modifier.height(2.dp))
                }
                val logo = if (!isEpisode) vm.plexImage(item.logo, px(400.dp), px(120.dp)) else null
                if (logo != null) {
                    AsyncImage(
                        model = logo,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier.heightIn(max = 110.dp).widthIn(max = 380.dp),
                    )
                } else {
                    Text(item.title, style = Type.HeroTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                val meta = listOfNotNull(
                    item.episodeLabel,
                    item.year?.toString(),
                    if (isShow) item.leafCount?.let { "$it episodes" } else item.durationMs?.let { formatRuntime(it) },
                    item.contentRating,
                    item.rating?.let { "★ %.1f".format(it) },
                ).joinToString("  ·  ")
                Text(meta, style = Type.Meta)
                if (item.genres.isNotEmpty()) {
                    Text(item.genres.take(3).joinToString(" · "), style = Type.Meta.copy(color = Palette.TextTertiary))
                }
                Spacer(Modifier.height(10.dp))
                FormatRow(item.quickFormats, vm.capabilities.formats)
                if (item.videoLabel != null || item.audioLabel != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(listOfNotNull(item.videoLabel, item.audioLabel).joinToString("  ·  "), style = Type.CardMeta)
                }
                Spacer(Modifier.height(12.dp))
                Text(item.summary, style = Type.Body, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton(
                        text = when {
                            isShow -> "Play"
                            resume -> "Resume"
                            else -> "Play"
                        },
                        icon = Icons.Filled.PlayArrow,
                        primary = true,
                        onClick = { vm.playPlex(item) },
                        modifier = Modifier.focusRequester(playRequester),
                    )
                    if (resume && !isShow) {
                        PillButton("From Beginning", onClick = { vm.playPlex(item, fromStart = true) }, icon = Icons.Filled.Refresh)
                    }
                    PillButton(
                        text = if (item.isWatched) "Watched" else "Mark Watched",
                        icon = Icons.Filled.Check,
                        onClick = { vm.setWatched(item, !item.isWatched, onChanged) },
                    )
                    item.remainingLabel?.takeIf { resume }?.let {
                        Text(it, style = Type.Meta)
                    }
                }
                if (item.progress != null && !isShow) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.width(320.dp)) { com.claudetv.hub.ui.components.ProgressPill(item.progress ?: 0f) }
                }
                if (item.cast.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text("Starring " + item.cast.joinToString(", "), style = Type.CardMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.directors.isNotEmpty()) {
                    Text("Directed by " + item.directors.joinToString(", "), style = Type.CardMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (isShow && seasons.size > 1) {
            item(key = "seasons") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dimens.ScreenH),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(seasons, key = { it.ratingKey }) { s ->
                        Chip(s.title, s.ratingKey == selectedSeason?.ratingKey, onClick = { selectedSeason = s })
                    }
                }
            }
        }

        if (isShow || isEpisode) {
            item(key = "episodes") {
                Column {
                    SectionHeader(
                        if (isEpisode) "More Episodes" else selectedSeason?.title ?: "Episodes",
                        null,
                        Modifier.padding(start = Dimens.ScreenH),
                    )
                    when (val e = episodes) {
                        is Loadable.Ready -> LazyRow(
                            contentPadding = PaddingValues(horizontal = Dimens.ScreenH),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                        ) {
                            items(e.value, key = { it.ratingKey }) { ep ->
                                Column(Modifier.width(Dimens.LandscapeCardWidth)) {
                                    PlexCard(
                                        vm = vm,
                                        item = ep,
                                        style = PlexCardStyle.LANDSCAPE,
                                        focusKey = "ep/${ep.ratingKey}",
                                        onClick = { vm.playPlex(ep) },
                                        showCaption = false,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        listOfNotNull(ep.episodeNumber?.let { "E$it" }, ep.title).joinToString(" · "),
                                        style = Type.CardTitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        listOfNotNull(ep.durationMs?.let { formatRuntime(it) }, ep.remainingLabel?.takeIf { (ep.viewOffsetMs ?: 0) > 0 }).joinToString(" · "),
                                        style = Type.CardMeta,
                                    )
                                    Text(ep.summary, style = Type.CardMeta.copy(color = Palette.TextTertiary), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        else -> Text("Loading episodes…", style = Type.Meta, modifier = Modifier.padding(start = Dimens.ScreenH))
                    }
                }
            }
        }

        item(key = "about") {
            if (item.studio != null || item.genres.isNotEmpty()) {
                Column(
                    Modifier
                        .padding(horizontal = Dimens.ScreenH)
                        .glass(RoundedCornerShape(18.dp))
                        .padding(20.dp),
                ) {
                    Text("About", style = Type.RowTitle)
                    Spacer(Modifier.height(6.dp))
                    item.studio?.let { Text("Studio: $it", style = Type.CardMeta) }
                    if (item.genres.isNotEmpty()) Text("Genres: " + item.genres.joinToString(", "), style = Type.CardMeta)
                    Text("Streams in original quality from your Plex server when this TV supports the file's formats.", style = Type.CardMeta)
                }
            }
        }
    }
}
