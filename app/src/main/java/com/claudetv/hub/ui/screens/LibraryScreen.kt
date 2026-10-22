package com.claudetv.hub.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.claudetv.hub.data.plex.PlexItem
import com.claudetv.hub.data.plex.PlexSection
import com.claudetv.hub.data.plex.PlexState
import com.claudetv.hub.data.plex.PlexType
import com.claudetv.hub.ui.AppViewModel
import com.claudetv.hub.ui.Loadable
import com.claudetv.hub.ui.components.AmbientBackdrop
import com.claudetv.hub.ui.components.Chip
import com.claudetv.hub.ui.components.PillButton
import com.claudetv.hub.ui.components.PlexCard
import com.claudetv.hub.ui.components.PlexCardStyle
import com.claudetv.hub.ui.components.glass
import com.claudetv.hub.ui.components.px
import com.claudetv.hub.ui.components.rememberPivotSpec
import com.claudetv.hub.ui.theme.Dimens
import com.claudetv.hub.ui.theme.Type
import kotlinx.coroutines.flow.distinctUntilChanged

private enum class LibrarySort(val label: String, val plex: String) {
    RECENT("Recently Added", "addedAt:desc"),
    TITLE("A–Z", "titleSort:asc"),
    RELEASED("Release Date", "originallyAvailableAt:desc"),
    RATING("Top Rated", "audienceRating:desc"),
}

private const val PAGE = 60

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val state by vm.plexState.collectAsState()
    when (val s = state) {
        is PlexState.Connected -> ConnectedLibrary(vm)
        is PlexState.Connecting -> LibraryMessage("Connecting to ${s.serverName ?: "Plex"}…", null)
        is PlexState.Error -> LibraryMessage("Plex isn't reachable", s.message, "Try Again", vm::retryPlex)
        else -> LibraryMessage(
            "Your library, beautifully",
            "Connect your Plex Media Server to browse your movies and shows here and play them in original quality: 4K, HDR10, Dolby Vision and Atmos.",
            "Connect Plex",
            vm::startPlexLink,
        )
    }
}

@Composable
private fun LibraryMessage(title: String, body: String?, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(top = 90.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(560.dp)
                .glass(RoundedCornerShape(26.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = Type.ScreenTitle)
            body?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = Type.Body)
            }
            if (action != null) {
                Spacer(Modifier.height(22.dp))
                PillButton(action, onClick = onAction, primary = true, icon = if (action.startsWith("Try")) Icons.Filled.Refresh else Icons.Filled.PlayArrow)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConnectedLibrary(vm: AppViewModel) {
    val plexHome by vm.plexHome.collectAsState()
    val sections: List<PlexSection> = (plexHome as? Loadable.Ready)?.value?.sections.orEmpty()
    var section by remember { mutableStateOf<PlexSection?>(null) }
    var sort by remember { mutableStateOf(LibrarySort.RECENT) }
    var unwatched by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<PlexItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var ambient by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val ambientW = px(480.dp)
    val ambientH = px(270.dp)

    LaunchedEffect(sections) { if (section == null) section = sections.firstOrNull() }

    LaunchedEffect(section?.key, sort, unwatched) {
        val sec = section ?: return@LaunchedEffect
        loading = true
        val (page, count) = runCatching { vm.plexSection(sec, sort.plex, 0, PAGE, unwatched) }.getOrDefault(emptyList<PlexItem>() to 0)
        items = page
        total = count
        loading = false
        gridState.scrollToItem(0)
    }
    // Load more as you near the end.
    LaunchedEffect(gridState, section?.key, sort, unwatched) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                val sec = section ?: return@collect
                if (!loading && items.size < total && last >= items.size - 18) {
                    loading = true
                    val more = runCatching { vm.plexSection(sec, sort.plex, items.size, PAGE, unwatched).first }.getOrDefault(emptyList())
                    items = (items + more).distinctBy { it.ratingKey }
                    loading = false
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(ambient, blurred = true, alreadyBlurred = true, dim = 0.55f)
        Column(Modifier.fillMaxSize().padding(top = 76.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.ScreenH),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                lazyItems(sections, key = { "sec_${it.key}" }) { s -> Chip(s.title, s.key == section?.key, onClick = { section = s }) }
                item(key = "gap") { Spacer(Modifier.width(18.dp)) }
                lazyItems(LibrarySort.entries, key = { "sort_${it.name}" }) { o -> Chip(o.label, o == sort, onClick = { sort = o }) }
                item(key = "unwatched") { Chip("Unwatched", unwatched, onClick = { unwatched = !unwatched }) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    sections.isEmpty() && plexHome is Loadable.Loading -> "Loading your library…"
                    sections.isEmpty() -> "No movie or TV libraries found on this server"
                    loading && items.isEmpty() -> "Loading…"
                    else -> "$total ${if (section?.type == PlexType.SHOW) "shows" else "movies"}"
                },
                style = Type.RowSubtitle,
                modifier = Modifier.padding(horizontal = Dimens.ScreenH),
            )
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivotSpec(0.12f, 0.dp)) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(6),
                    contentPadding = PaddingValues(start = Dimens.ScreenH, end = Dimens.ScreenH, top = 18.dp, bottom = 60.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    items(items, key = { it.ratingKey }) { item ->
                        val key = "library/${item.ratingKey}"
                        PlexCard(
                            vm = vm,
                            item = item,
                            style = PlexCardStyle.POSTER,
                            focusKey = key,
                            width = 124.dp,
                            onClick = { vm.openPlex(item, key) },
                            onFocused = { ambient = vm.plexImage(item.art ?: item.thumb, ambientW, ambientH, blur = 60) },
                        )
                    }
                }
            }
        }
    }
}
