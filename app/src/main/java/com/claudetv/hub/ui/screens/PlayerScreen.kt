package com.claudetv.hub.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.claudetv.hub.data.plex.PlexItem
import com.claudetv.hub.data.plex.PlexType
import com.claudetv.hub.ui.AppViewModel
import com.claudetv.hub.ui.PlayRequest
import com.claudetv.hub.ui.components.FormatRow
import com.claudetv.hub.ui.components.GlassChip
import com.claudetv.hub.ui.components.ProgressPill
import com.claudetv.hub.ui.components.glass
import com.claudetv.hub.ui.components.safeRequest
import com.claudetv.hub.ui.theme.Dimens
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Full-screen Plex playback. Plays the original file (direct play) so 4K, HDR10, Dolby Vision
 * and Dolby/DTS passthrough survive; if this TV can't decode the file, retries through Plex's
 * transcoder. Reports position to Plex so Continue Watching stays in sync everywhere.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun PlayerScreen(vm: AppViewModel, request: PlayRequest) {
    val context = LocalContext.current
    val server = vm.plexServer
    if (server == null) {
        LaunchedEffect(Unit) { vm.showToast("Plex isn't connected"); vm.closeOverlay() }
        return
    }
    val scope = rememberCoroutineScope()
    var item by remember { mutableStateOf(request.item) }
    var startMs by remember { mutableLongStateOf(request.startMs) }
    var transcoding by remember(item.ratingKey) { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(item.durationMs ?: 0L) }
    var interaction by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val session = remember { UUID.randomUUID().toString() }
    val focus = remember { FocusRequester() }

    val player = remember {
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    fun report(state: String) {
        val d = player.duration.takeIf { it > 0 } ?: item.durationMs ?: 0L
        vm.reportPlayback(item, state, player.currentPosition, d)
    }

    // Load (or reload) the current item.
    LaunchedEffect(item.ratingKey, transcoding) {
        error = null
        val url = if (transcoding) vm.plex.api.transcodeUrl(server, item, session, 0L) else vm.plex.api.directPlayUrl(server, item)
        if (url == null) {
            error = "This title has no playable file."
            return@LaunchedEffect
        }
        val media = MediaItem.Builder()
            .setUri(url)
            .apply { if (transcoding) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        val resumeAt = if (transcoding) player.currentPosition.takeIf { it > 0 } ?: startMs else startMs
        player.setMediaItem(media, resumeAt)
        player.prepare()
        player.playWhenReady = true
        awaitFrame()
        focus.safeRequest()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                report(if (playing) "playing" else "paused")
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) duration = player.duration.takeIf { it > 0 } ?: duration
                if (state == Player.STATE_ENDED) {
                    report("stopped")
                    scope.launch {
                        val next = if (item.type == PlexType.EPISODE) vm.episodeAfter(item) else null
                        if (next != null) {
                            vm.showToast("Up next: ${next.episodeLabel ?: ""} ${next.title}".trim())
                            startMs = 0L
                            item = vm.plexMetadata(next)
                        } else {
                            vm.closeOverlay()
                        }
                    }
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                if (!transcoding) {
                    vm.showToast("This TV can't play the original file. Switching to Plex transcoding")
                    startMs = player.currentPosition
                    transcoding = true
                } else {
                    error = "Playback failed: ${e.errorCodeName}"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            report("stopped")
            player.removeListener(listener)
            player.release()
        }
    }

    // Keep Plex up to date every 10 seconds while watching.
    LaunchedEffect(item.ratingKey) {
        while (true) {
            delay(10_000)
            if (player.isPlaying) report("playing")
        }
    }
    // Position for the overlay.
    LaunchedEffect(overlayVisible) {
        while (overlayVisible) {
            position = player.currentPosition
            if (player.duration > 0) duration = player.duration
            delay(500)
        }
    }
    // Auto-hide the overlay while playing.
    LaunchedEffect(interaction, isPlaying) {
        if (isPlaying) {
            delay(4_500)
            overlayVisible = false
        } else {
            overlayVisible = true
        }
    }

    BackHandler {
        if (overlayVisible && isPlaying) overlayVisible = false else vm.closeOverlay()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                interaction++
                val wasHidden = !overlayVisible
                overlayVisible = true
                when (e.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_SPACE -> {
                        if (player.isPlaying) player.pause() else player.play()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); true }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (!wasHidden || !player.isPlaying) player.seekBack()
                        position = player.currentPosition
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (!wasHidden || !player.isPlaying) player.seekForward()
                        position = player.currentPosition
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) {
            GlassChip(Modifier.align(Alignment.Center)) {
                Text("Loading…", style = Type.Meta.copy(color = Palette.TextPrimary), modifier = Modifier.padding(6.dp))
            }
        }
        if (!isPlaying && !buffering && error == null) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(88.dp)
                    .glass(CircleShape, tint = 0.10f),
                contentAlignment = Alignment.Center,
            ) {
                Image(Icons.Filled.PlayArrow, null, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.size(44.dp))
            }
        }
        error?.let {
            Column(
                Modifier.align(Alignment.Center).glass(RoundedCornerShape(20.dp)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Can't play this title", style = Type.RowTitle)
                Text(it, style = Type.Meta)
                Text("Press Back to return.", style = Type.CardMeta)
            }
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerOverlay(item, position, duration, transcoding, vm.capabilities.formats)
        }
    }
}

@Composable
private fun PlayerOverlay(item: PlexItem, position: Long, duration: Long, transcoding: Boolean, tvFormats: Set<com.claudetv.hub.data.VideoFormat>) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .padding(horizontal = Dimens.ScreenH, vertical = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(22.dp))
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            if (item.type == PlexType.EPISODE) {
                Text(listOfNotNull(item.showTitle, item.episodeLabel).joinToString("  ·  "), style = Type.Meta)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.title, style = Type.ScreenTitle.copy(fontSize = Type.RowTitle.fontSize * 1.3f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                FormatRow(item.quickFormats, tvFormats)
                GlassChip {
                    Text(if (transcoding) "TRANSCODING" else "ORIGINAL QUALITY", style = Type.Badge.copy(color = if (transcoding) Palette.Demo else Palette.Included))
                }
            }
            Spacer(Modifier.height(14.dp))
            ProgressPill(if (duration > 0) position.toFloat() / duration else 0f, height = 5.dp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(time(position), style = Type.CardMeta)
                Spacer(Modifier.weight(1f))
                Text("◀ 10s   OK play/pause   10s ▶", style = Type.CardMeta.copy(color = Palette.TextTertiary))
                Spacer(Modifier.weight(1f))
                Text("-" + time((duration - position).coerceAtLeast(0)), style = Type.CardMeta)
            }
        }
    }
}

private fun time(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
