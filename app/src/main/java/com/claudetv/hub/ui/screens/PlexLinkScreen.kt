package com.claudetv.hub.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.claudetv.hub.data.plex.PlexState
import com.claudetv.hub.ui.AppViewModel
import com.claudetv.hub.ui.components.PillButton
import com.claudetv.hub.ui.components.ToggleRow
import com.claudetv.hub.ui.components.glass
import com.claudetv.hub.ui.components.safeRequest
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type
import kotlinx.coroutines.android.awaitFrame

/** Sign in with a code at plex.tv/link (no typing passwords with a remote), then pick a server. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlexLinkScreen(vm: AppViewModel) {
    val state by vm.plexState.collectAsState()
    val pin by vm.pin.collectAsState()
    val cancel = remember { FocusRequester() }
    LaunchedEffect(state) { awaitFrame(); cancel.safeRequest() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF1C1A24), Palette.Background)))
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(600.dp)
                .glass(RoundedCornerShape(28.dp), tint = 0.08f)
                .padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                is PlexState.SignedOut -> {
                    Text("Sign in to Plex", style = Type.ScreenTitle)
                    Spacer(Modifier.height(10.dp))
                    Text("On your phone or computer, go to", style = Type.Body)
                    Text("plex.tv/link", style = Type.RowTitle.copy(fontSize = 24.sp))
                    Spacer(Modifier.height(18.dp))
                    Text("and enter this code", style = Type.Body)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        (pin?.code ?: "····").forEach { c ->
                            Box(
                                Modifier
                                    .width(64.dp)
                                    .height(80.dp)
                                    .glass(RoundedCornerShape(16.dp), tint = 0.10f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(c.toString(), style = Type.HeroTitle.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(if (pin == null) "Getting a code…" else "Waiting for you to sign in. This screen updates on its own.", style = Type.CardMeta)
                }
                is PlexState.Connecting -> {
                    Text("Connecting…", style = Type.ScreenTitle)
                    Spacer(Modifier.height(8.dp))
                    Text(s.serverName?.let { "Reaching “$it” on your network" } ?: "Finding your Plex servers", style = Type.Body)
                }
                is PlexState.ChooseServer -> {
                    Text("Choose a server", style = Type.ScreenTitle)
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        s.servers.forEach { server ->
                            ToggleRow(
                                label = server.name,
                                detail = if (server.owned) "Your server" else "Shared with you",
                                checked = null,
                                trailingText = "Connect",
                                onClick = { vm.choosePlexServer(server) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                is PlexState.Error -> {
                    Text("Couldn't connect", style = Type.ScreenTitle)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, style = Type.Body)
                    if (s.canRetry) {
                        Spacer(Modifier.height(18.dp))
                        PillButton("Try Again", onClick = vm::retryPlex, primary = true)
                    }
                }
                is PlexState.Connected -> {
                    Text("Connected to ${s.server.name}", style = Type.ScreenTitle)
                }
            }
            Spacer(Modifier.height(24.dp))
            PillButton("Close", onClick = vm::closeOverlay, modifier = Modifier.focusRequester(cancel))
        }
        Text(
            "Claude TV talks only to plex.tv and your own server. Your Plex password is never entered here.",
            style = Type.CardMeta.copy(color = Palette.TextTertiary),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
        )
    }
}
