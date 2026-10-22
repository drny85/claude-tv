package com.claudetv.hub.ui.components

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.claudetv.hub.ui.theme.Motion
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type

/** Hairline edge that is brighter at the top, like light catching the rim of a glass pane. */
fun glassEdge(width: Dp = 1.dp, top: Float = 0.30f, bottom: Float = 0.04f) = BorderStroke(
    width,
    Brush.verticalGradient(listOf(Color.White.copy(alpha = top), Color.White.copy(alpha = bottom))),
)

/** Frosted panel: a faint white tint over whatever is behind it, with a glass edge. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp), tint: Float = 0.07f): Modifier =
    this
        .clip(shape)
        .background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = tint + 0.03f), Color.White.copy(alpha = tint * 0.6f))),
            shape,
        )
        .border(glassEdge(), shape)

/** Small frosted label used on top of artwork. */
@Composable
fun GlassChip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.45f), shape)
            .border(glassEdge(0.6.dp, 0.25f, 0.08f), shape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        content = content,
    )
}

/**
 * The media card used everywhere: sharp artwork behind a pane of glass. Resting cards carry a
 * faint sheen and hairline edge; focus lifts the card, brightens the sheen and edge, and casts a
 * soft shadow.
 */
@Composable
fun GlassCard(
    onClick: () -> Unit,
    imageUrl: String?,
    aspectRatio: Float,
    width: Dp,
    focusKey: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    progress: Float? = null,
    onFocused: () -> Unit = {},
    contentDescription: String? = title,
    fallback: @Composable BoxScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    RestoreFocusEffect(focusKey, requester)
    val sheen by animateFloatAsState(if (focused) 0.20f else 0.06f, tween(Motion.FocusMillis), label = "sheen")
    val captionColor by animateColorAsState(if (focused) Palette.TextPrimary else Palette.TextSecondary, tween(Motion.FocusMillis), label = "cap")
    val shape = RoundedCornerShape(16.dp)

    Column(modifier.width(width)) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .focusRequester(requester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Palette.Surface,
                focusedContainerColor = Palette.Surface,
                pressedContainerColor = Palette.Surface,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.09f, pressedScale = 1.03f),
            border = ClickableSurfaceDefaults.border(
                border = Border(glassEdge(1.dp, 0.22f, 0.03f), shape = shape),
                focusedBorder = Border(glassEdge(2.dp, 0.95f, 0.45f), shape = shape),
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.85f), elevation = 20.dp),
            ),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                fallback()
            }
            // Diagonal sheen: the "glass" over the artwork.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = sheen),
                        0.38f to Color.White.copy(alpha = sheen * 0.25f),
                        0.55f to Color.Transparent,
                    ),
                ),
            )
            overlay()
            if (progress != null && progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    ProgressPill(progress)
                }
            }
        }
        if (title != null) {
            Spacer(Modifier.height(if (focused) 14.dp else 10.dp))
            Text(title, style = Type.CardTitle.copy(color = captionColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = Type.CardMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ProgressPill(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.28f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(Color.White),
        )
    }
}

/**
 * Full-screen ambient backdrop. With [blurred] it frosts the art (pre-blurred by Plex, or with a
 * RenderEffect blur on Android 12+), so content in front reads like it sits on glass.
 */
@Composable
fun AmbientBackdrop(
    url: String?,
    modifier: Modifier = Modifier,
    blurred: Boolean = false,
    alreadyBlurred: Boolean = false,
    dim: Float = 0f,
) {
    Box(modifier.fillMaxSize().background(Palette.Background)) {
        Crossfade(targetState = url, animationSpec = tween(800), label = "ambient") { u ->
            if (u != null) {
                val blurMod = if (blurred && !alreadyBlurred && Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp) else Modifier
                AsyncImage(
                    model = u,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(blurMod),
                )
            }
        }
        val extraDim = if (blurred && !alreadyBlurred && Build.VERSION.SDK_INT < 31) 0.25f else 0f
        if (dim + extraDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (dim + extraDim).coerceAtMost(0.92f))))
    }
}

/** Left and bottom scrims that keep text legible over artwork. */
@Composable
fun CinematicScrims(strength: Float = 1f) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to Palette.Background.copy(alpha = 0.90f * strength),
                0.45f to Palette.Background.copy(alpha = 0.40f * strength),
                0.78f to Color.Transparent,
            ),
        ),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0.30f to Color.Transparent,
                0.80f to Palette.Background.copy(alpha = 0.88f * strength),
                1f to Palette.Background,
            ),
        ),
    )
}
