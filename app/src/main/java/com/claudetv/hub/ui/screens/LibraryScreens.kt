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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.claudetv.hub.data.RowStyle
import com.claudetv.hub.data.StreamingService
import com.claudetv.hub.platform.AppLauncher
import com.claudetv.hub.ui.AppViewModel
import com.claudetv.hub.ui.components.PillButton
import com.claudetv.hub.ui.components.TitleCard
import com.claudetv.hub.ui.components.ToggleRow
import com.claudetv.hub.ui.components.rememberPivotSpec
import com.claudetv.hub.ui.components.safeRequest
import com.claudetv.hub.ui.theme.Dimens
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type
import kotlinx.coroutines.android.awaitFrame

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchlistScreen(vm: AppViewModel) {
    val list by vm.watchlist.collectAsState()
    val offers by vm.offers.collectAsState()
    Column(Modifier.fillMaxSize().padding(top = 76.dp)) {
        Column(Modifier.padding(horizontal = Dimens.ScreenH)) {
            Text("Watchlist", style = Type.ScreenTitle)
            Text(
                "Saved on this TV across every service. Service watchlists (like Netflix's My List) can't be read by other apps.",
                style = Type.RowSubtitle,
            )
        }
        Spacer(Modifier.height(20.dp))
        if (list.isEmpty()) {
            CenteredMessage("Nothing saved yet", "Press Watchlist on any title to keep it here.")
            return@Column
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivotSpec(0f, 24.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = Dimens.ScreenH, end = Dimens.ScreenH, top = 12.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(list, key = { it.key }) { t ->
                    val key = "watchlist/${t.key}"
                    val o = offers[t.key]
                    TitleCard(
                        title = t,
                        style = RowStyle.LANDSCAPE,
                        focusKey = key,
                        onClick = { vm.openDetails(t, key) },
                        onFocused = { vm.onCardFocused(t) },
                        service = t.demoService ?: o?.firstOrNull { vm.isIncluded(listOf(it)) }?.service,
                        included = vm.isIncluded(o),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServicesScreen(vm: AppViewModel) {
    val subs by vm.subscriptions.collectAsState()
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(top = 76.dp)) {
        Column(Modifier.padding(horizontal = Dimens.ScreenH)) {
            Text("Your Services", style = Type.ScreenTitle)
            Text(
                "Turn on the services you pay for. Home, search and Play buttons prioritize what's included.",
                style = Type.RowSubtitle,
            )
        }
        Spacer(Modifier.height(18.dp))
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivotSpec(0.25f, 0.dp)) {
            LazyColumn(
                contentPadding = PaddingValues(start = Dimens.ScreenH, end = Dimens.ScreenH, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(StreamingService.subscribable, key = { it.name }) { s ->
                    val installed = remember(s) { AppLauncher.installedPackage(context, s) != null }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToggleRow(
                            label = s.displayName,
                            detail = (if (installed) "Installed" else "App not installed") +
                                "  ·  Up to " + s.formats.sortedBy { it.ordinal }.joinToString(", ") { it.label },
                            checked = s in subs,
                            onClick = { vm.toggleSubscription(s) },
                            leading = {
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .background(Color(s.brandColor), RoundedCornerShape(9.dp)),
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .let { if (s == StreamingService.subscribable.first()) it.focusRequester(first) else it },
                        )
                        Spacer(Modifier.width(12.dp))
                        PillButton(
                            text = if (installed) "Open" else "Get App",
                            icon = Icons.Filled.PlayArrow,
                            onClick = {
                                if (installed) {
                                    val pkg = AppLauncher.installedPackage(context, s)
                                    val pm = context.packageManager
                                    val intent = pkg?.let { pm.getLeanbackLaunchIntentForPackage(it) ?: pm.getLaunchIntentForPackage(it) }
                                    if (intent != null) context.startActivity(intent) else vm.showToast("Couldn't open ${s.displayName}")
                                } else {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${s.packages.first()}")),
                                        )
                                    }.onFailure { vm.showToast("Google Play isn't available") }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
