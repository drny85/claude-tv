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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.claudetv.hub.BuildConfig
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import com.claudetv.hub.data.StreamingService
import com.claudetv.hub.data.plex.PlexState
import com.claudetv.hub.platform.AppLauncher
import com.claudetv.hub.ui.AppViewModel
import com.claudetv.hub.ui.components.Chip
import com.claudetv.hub.ui.components.FormatBadge
import com.claudetv.hub.ui.components.PillButton
import com.claudetv.hub.ui.components.SectionHeader
import com.claudetv.hub.ui.components.ToggleRow
import com.claudetv.hub.ui.components.TvKeyboard
import com.claudetv.hub.ui.components.rememberPivotSpec
import com.claudetv.hub.ui.components.safeRequest
import com.claudetv.hub.ui.theme.Dimens
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type
import kotlinx.coroutines.android.awaitFrame

private val Regions = listOf("US", "CA", "GB", "IE", "AU", "DE", "FR", "ES", "IT", "MX", "BR", "DO")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val apiKey by vm.apiKey.collectAsState()
    val region by vm.region.collectAsState()
    val isLive by vm.isLive.collectAsState()
    val plexState by vm.plexState.collectAsState()
    val subs by vm.subscriptions.collectAsState()
    var editingKey by remember { mutableStateOf(false) }

    if (editingKey) {
        KeyEntry(vm, initial = apiKey, onDone = { editingKey = false })
        return
    }

    val first = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val keySource = when {
        apiKey.isNotBlank() -> "Live catalog · using the key you entered"
        BuildConfig.TMDB_API_KEY.isNotBlank() -> "Live catalog · using the key built into this app"
        else -> "Demo mode · sample data"
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivotSpec(0.2f, 0.dp)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Dimens.ScreenH, end = Dimens.ScreenH, top = 76.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text("Settings", style = Type.ScreenTitle) }

            item { SectionHeader("Plex", null, gap = 4.dp) }
            item {
                val ps = plexState
                ToggleRow(
                    label = when (ps) {
                        is PlexState.Connected -> ps.server.name
                        is PlexState.Connecting -> "Connecting…"
                        else -> "Connect Plex"
                    },
                    detail = when (ps) {
                        is PlexState.Connected -> "Connected · ${if (ps.server.baseUrl.contains(".plex.direct") || ps.server.baseUrl.startsWith("http://")) "streaming from your server" else "via relay"}"
                        is PlexState.Error -> ps.message
                        is PlexState.ChooseServer -> "Choose which server to use"
                        is PlexState.Connecting -> null
                        else -> "Sign in with a code at plex.tv/link"
                    },
                    checked = null,
                    trailingText = if (ps is PlexState.Connected) "Switch server" else "Set up",
                    onClick = { if (ps is PlexState.Connected) vm.switchPlexServer() else vm.startPlexLink() },
                    modifier = Modifier.fillMaxWidth().focusRequester(first),
                )
            }
            if (plexState !is PlexState.SignedOut) {
                item {
                    ToggleRow(
                        label = "Sign out of Plex",
                        detail = null,
                        checked = null,
                        onClick = vm::signOutPlex,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { SectionHeader("Streaming services you pay for", "Discovery and Play buttons prioritize what's included", Modifier.padding(top = 10.dp), gap = 4.dp) }
            items(StreamingService.subscribable, key = { it.name }) { svc ->
                ToggleRow(
                    label = svc.displayName,
                    detail = (if (AppLauncher.installedPackage(context, svc) != null) "App installed" else "App not installed") +
                        "  ·  up to " + svc.formats.sortedBy { it.ordinal }.joinToString(", ") { it.label },
                    checked = svc in subs,
                    onClick = { vm.toggleSubscription(svc) },
                    leading = {
                        Box(Modifier.size(28.dp).background(Color(svc.brandColor), RoundedCornerShape(8.dp)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { SectionHeader("Discovery", null, Modifier.padding(top = 10.dp), gap = 4.dp) }

            item {
                ToggleRow(
                    label = "Catalog data (TMDB)",
                    detail = keySource,
                    checked = null,
                    trailingText = if (isLive) "Change key" else "Add key",
                    onClick = { editingKey = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Column {
                    SectionHeader("Region", "Availability differs by country", gap = 8.dp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(Regions) { r -> Chip(r, r == region, onClick = { vm.setRegion(r) }) }
                    }
                }
            }

            item {
                val caps = vm.capabilities
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Palette.Surface, RoundedCornerShape(14.dp))
                        .padding(20.dp),
                ) {
                    Text("This TV", style = Type.RowTitle)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${caps.resolutionLabel} · ${caps.maxWidth}×${caps.maxHeight} · up to ${"%.0f".format(caps.maxRefreshRate)} Hz",
                        style = Type.Meta,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (caps.formats.isEmpty()) Text("No HDR or Atmos support reported", style = Type.CardMeta)
                        caps.formats.sortedBy { it.ordinal }.forEach { FormatBadge(it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Read live from Android's display and audio APIs. Atmos shows when the TV or a connected soundbar reports Dolby Digital Plus JOC or TrueHD passthrough.",
                        style = Type.CardMeta,
                    )
                }
            }

            item { SectionHeader("What's real and what's simulated", null, Modifier.padding(top = 10.dp), gap = 4.dp) }
            items(Limitations) { (head, body) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Palette.Surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(head, style = Type.CardTitle)
                    Text(body, style = Type.CardMeta)
                }
            }
            item {
                // A focusable end stop so the list can scroll to the bottom with the D-pad.
                PillButton("Back to top", onClick = { first.safeRequest() }, icon = Icons.Filled.Check)
            }
            item {
                Text(
                    "Claude TV ${BuildConfig.VERSION_NAME} · This product uses the TMDB API but is not endorsed or certified by TMDB. Streaming availability by JustWatch.",
                    style = Type.CardMeta.copy(color = Palette.TextTertiary),
                )
            }
        }
    }
}

private val Limitations = listOf(
    "Real · Your Plex library and playback" to
        "Browsing, artwork, Continue Watching and progress come straight from your Plex server. Files play in original quality (4K, HDR10, Dolby Vision, lossless audio passthrough) when this TV supports them, otherwise through Plex's transcoder.",
    "Real · Per-title formats for your files" to
        "4K, HDR10, Dolby Vision and Atmos badges on Plex titles are read from the file's own streams.",
    "Real · Catalog, artwork, trending, search" to
        "Live from TMDB once a key is set. Without one the app runs on a labeled sample catalog.",
    "Real · Which service has each title" to
        "TMDB watch-provider data (from JustWatch), per region: included, free, rent or buy.",
    "Real · Opening the official app" to
        "Play hands off to the installed Netflix, Disney+, Prime Video, Apple TV, Max, Hulu, Paramount+ or Peacock app. Where the app accepts its own web links it opens on a search for the title; otherwise it opens the app's home. Exact episode deep links need each service's private content ids, which aren't public.",
    "Real · Your TV's 4K, HDR, Dolby Vision and Atmos support" to
        "Read from the device. For streaming services, formats are the service's top tier (\"up to\"); per-title format data isn't in any public API.",
    "Local · Watchlist and recommendations" to
        "Stored on this TV. Recommendations are built from your watchlist and recently opened titles, filtered to your services.",
    "Limited · Streaming services' watch progress" to
        "Netflix, Disney+ and the rest don't share progress with other apps, so their titles can't appear in Continue Watching with progress. Plex titles do.",
    "Not possible · Playing Netflix and other services inside Claude TV" to
        "Their video is DRM-protected and licensed to play only in their own apps, so Play hands off to them.",
)

@Composable
private fun KeyEntry(vm: AppViewModel, initial: String, onDone: () -> Unit) {
    var key by remember { mutableStateOf(initial) }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { awaitFrame(); first.safeRequest() }
    androidx.activity.compose.BackHandler(onBack = onDone)

    Row(Modifier.fillMaxSize().padding(start = Dimens.ScreenH, end = Dimens.ScreenH, top = 60.dp)) {
        Column(Modifier.width(330.dp)) {
            TvKeyboard(
                onChar = { if (it != ' ') key += it },
                onDelete = { key = key.dropLast(1) },
                onClear = { key = "" },
                firstKeyRequester = first,
                includeSymbols = true,
                columns = 8,
                keySize = 34.dp,
            )
        }
        Spacer(Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text("TMDB API Key", style = Type.ScreenTitle)
            Spacer(Modifier.height(8.dp))
            Text(
                "Get a free key at themoviedb.org → Settings → API (on your phone or computer), then type the 32-character \"API Key\" here. " +
                    "Or set it once as the TMDB_API_KEY secret in GitHub so every build has it.",
                style = Type.Body,
            )
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    if (key.isEmpty()) "No key" else key.chunked(4).joinToString(" ") + "▏",
                    style = Type.RowTitle.copy(color = if (key.isEmpty()) Palette.TextTertiary else Palette.TextPrimary),
                )
            }
            Text("${key.length} characters", style = Type.CardMeta, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    text = if (checking) "Checking…" else "Save",
                    primary = true,
                    icon = Icons.Filled.Check,
                    onClick = {
                        if (checking) return@PillButton
                        checking = true
                        status = null
                        vm.saveApiKey(key) { ok ->
                            checking = false
                            if (ok) {
                                vm.showToast(if (key.isBlank()) "Key removed" else "Key saved. Loading the live catalog")
                                onDone()
                            } else {
                                status = "TMDB didn't accept that key. Check it and try again."
                            }
                        }
                    },
                )
                PillButton("Cancel", onClick = onDone)
            }
            status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = Type.Meta.copy(color = Palette.Demo))
            }
        }
    }
}
