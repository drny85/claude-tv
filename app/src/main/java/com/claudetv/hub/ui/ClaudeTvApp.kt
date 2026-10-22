package com.claudetv.hub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.claudetv.hub.ui.components.FocusRestore
import com.claudetv.hub.ui.components.LocalFocusRestore
import com.claudetv.hub.ui.screens.DetailsScreen
import com.claudetv.hub.ui.screens.HomeScreen
import com.claudetv.hub.ui.screens.SearchScreen
import com.claudetv.hub.ui.screens.ServicesScreen
import com.claudetv.hub.ui.screens.SettingsScreen
import com.claudetv.hub.ui.screens.WatchlistScreen
import com.claudetv.hub.ui.theme.Motion
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type

@Composable
fun ClaudeTvApp(vm: AppViewModel) {
    val tab by vm.tab.collectAsState()
    val details by vm.detailsStack.collectAsState()
    val toast by vm.toast.collectAsState()
    val restore by vm.restoreFocus.collectAsState()
    var homeChrome by remember { mutableStateOf(true) }

    // Back: close details, else return to Home, else let the system leave the app.
    BackHandler(enabled = details.isNotEmpty() || tab != Tab.HOME) {
        if (!vm.closeDetails()) vm.selectTab(Tab.HOME)
    }

    val navVisible = details.isEmpty() && (tab != Tab.HOME || homeChrome)

    CompositionLocalProvider(
        LocalFocusRestore provides FocusRestore(if (details.isEmpty()) restore else null) { vm.consumeRestoreFocus() },
    ) {
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(350)).togetherWith(fadeOut(tween(200))) },
                label = "tab",
            ) { t ->
                when (t) {
                    Tab.HOME -> HomeScreen(vm, onChromeVisible = { homeChrome = it }, handlesBack = details.isEmpty())
                    Tab.SEARCH -> SearchScreen(vm)
                    Tab.WATCHLIST -> WatchlistScreen(vm)
                    Tab.SERVICES -> ServicesScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }

            AnimatedVisibility(
                visible = navVisible,
                enter = fadeIn(tween(300)) + slideInVertically(tween(400, easing = Motion.Ease)) { -it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(300, easing = Motion.Ease)) { -it },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopNav(selected = tab, onSelect = vm::selectTab)
            }

            // Details pages stack above the tab, which stays composed so focus can return to the card.
            AnimatedContent(
                targetState = details.lastOrNull(),
                transitionSpec = {
                    (fadeIn(tween(400)) + scaleIn(tween(450, easing = Motion.Ease), initialScale = 1.04f))
                        .togetherWith(fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 1.02f))
                },
                contentKey = { it?.key },
                label = "details",
            ) { title ->
                if (title != null) DetailsScreen(vm, title)
            }

            AnimatedVisibility(
                visible = toast != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
            ) {
                Text(
                    toast.orEmpty(),
                    style = Type.Meta.copy(color = Palette.TextPrimary),
                    modifier = Modifier
                        .background(Palette.SurfaceRaised.copy(alpha = 0.96f), RoundedCornerShape(50))
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** Centered, minimal tab bar in the tvOS style. Moving focus across it switches tabs. */
@Composable
private fun TopNav(selected: Tab, onSelect: (Tab) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 22.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { t ->
                var focused by remember { mutableStateOf(false) }
                val isSel = t == selected
                Surface(
                    onClick = { onSelect(t) },
                    modifier = Modifier.onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused && !isSel) onSelect(t)
                    },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSel) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                        contentColor = Palette.TextPrimary,
                        focusedContainerColor = Palette.Focus,
                        focusedContentColor = Palette.OnFocus,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                ) {
                    Text(
                        t.label,
                        style = Type.Nav.copy(
                            color = when {
                                focused -> Palette.OnFocus
                                isSel -> Palette.TextPrimary
                                else -> Palette.TextSecondary
                            },
                        ),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
