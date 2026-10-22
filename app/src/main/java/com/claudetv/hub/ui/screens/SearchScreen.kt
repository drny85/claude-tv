package com.claudetv.hub.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.claudetv.hub.data.MediaType
import com.claudetv.hub.data.RowStyle
import com.claudetv.hub.data.Title
import com.claudetv.hub.ui.AppViewModel
import com.claudetv.hub.ui.Loadable
import com.claudetv.hub.ui.components.Chip
import com.claudetv.hub.ui.components.TitleCard
import com.claudetv.hub.ui.components.TvKeyboard
import com.claudetv.hub.ui.components.rememberPivotSpec
import com.claudetv.hub.ui.components.safeRequest
import com.claudetv.hub.ui.theme.Dimens
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type
import kotlinx.coroutines.android.awaitFrame

private enum class SearchFilter(val label: String) { ALL("All"), INCLUDED("In My Subscriptions"), MOVIES("Movies"), SHOWS("Series") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(vm: AppViewModel) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val offers by vm.offers.collectAsState()
    val home by vm.home.collectAsState()
    var filter by remember { mutableStateOf(SearchFilter.ALL) }
    val keyRequester = remember { FocusRequester() }

    Row(Modifier.fillMaxSize().padding(start = Dimens.ScreenH, end = Dimens.ScreenH, top = 76.dp)) {
        // ---- Left: query + keyboard ----
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    if (query.isEmpty()) "Movies, shows…" else query + "▏",
                    style = Type.RowTitle.copy(color = if (query.isEmpty()) Palette.TextTertiary else Palette.TextPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            TvKeyboard(
                onChar = { vm.setQuery(query + it) },
                onDelete = { vm.setQuery(query.dropLast(1)) },
                onClear = { vm.setQuery("") },
                firstKeyRequester = keyRequester,
            )
            Spacer(Modifier.height(14.dp))
            Text("One search across all your services. Titles in your subscriptions are listed first.", style = Type.CardMeta)
        }

        Spacer(Modifier.width(32.dp))

        // ---- Right: filters + results ----
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchFilter.entries.forEach { f -> Chip(f.label, f == filter, onClick = { filter = f }) }
            }
            Spacer(Modifier.height(18.dp))

            val suggestions: List<Title> = (home as? Loadable.Ready)?.value?.rows
                ?.firstOrNull { it.id == "trending" }?.items.orEmpty()

            val items: List<Title>? = when {
                query.isBlank() -> suggestions
                results is Loadable.Ready -> (results as Loadable.Ready).value.map { it.title }
                else -> null
            }
            val filtered = items?.filter { t ->
                when (filter) {
                    SearchFilter.ALL -> true
                    SearchFilter.INCLUDED -> vm.isIncluded(offers[t.key]) || (t.isDemo && t.demoService in vm.subscriptions.value)
                    SearchFilter.MOVIES -> t.type == MediaType.MOVIE
                    SearchFilter.SHOWS -> t.type == MediaType.TV
                }
            }

            Text(
                when {
                    query.isBlank() -> "Trending Searches"
                    results is Loadable.Loading -> "Searching…"
                    results is Loadable.Failed -> "Search failed: ${(results as Loadable.Failed).message}"
                    filtered.isNullOrEmpty() -> "No results for “$query”"
                    else -> "Results for “$query”"
                },
                style = Type.RowTitle,
            )
            Spacer(Modifier.height(12.dp))

            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivotSpec(0f, 16.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
                ) {
                    items(filtered.orEmpty(), key = { it.key }) { t ->
                        val o = offers[t.key]
                        val included = vm.isIncluded(o) || (t.isDemo && t.demoService in vm.subscriptions.value)
                        val service = t.demoService
                            ?: o?.firstOrNull { it.service != null && vm.isIncluded(listOf(it)) }?.service
                            ?: o?.firstOrNull { it.service != null }?.service
                        val key = "search/${t.key}"
                        TitleCard(
                            title = t,
                            style = RowStyle.POSTER,
                            focusKey = key,
                            onClick = { vm.openDetails(t, key) },
                            onFocused = { vm.onCardFocused(t) },
                            service = service,
                            included = false,
                            caption = when {
                                included -> "Included"
                                o.isNullOrEmpty() && !t.isDemo && query.isNotBlank() -> "Not streaming"
                                else -> null
                            },
                            width = 124.dp,
                        )
                    }
                }
            }
        }
    }
}
