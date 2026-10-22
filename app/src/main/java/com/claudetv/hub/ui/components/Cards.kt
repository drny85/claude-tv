package com.claudetv.hub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.claudetv.hub.data.RowStyle
import com.claudetv.hub.data.StreamingService
import com.claudetv.hub.data.Title
import com.claudetv.hub.data.TmdbClient
import com.claudetv.hub.ui.theme.Dimens
import com.claudetv.hub.ui.theme.Motion
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type

/** Artwork from TMDB, or generated art for sample titles and titles without images. */
@Composable
fun Artwork(title: Title, style: RowStyle, modifier: Modifier = Modifier, hero: Boolean = false) {
    val url = when {
        hero -> TmdbClient.heroBackdrop(title.backdropPath)
        style == RowStyle.POSTER -> TmdbClient.poster(title.posterPath)
        else -> TmdbClient.backdrop(title.backdropPath) ?: TmdbClient.poster(title.posterPath)
    }
    Box(modifier.background(Palette.Surface)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = title.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GeneratedArt(title, style, hero)
        }
    }
}

@Composable
private fun GeneratedArt(title: Title, style: RowStyle, hero: Boolean) {
    val base = Color(title.demoService?.brandColor ?: 0xFF3A3A48)
    val h = (title.name.hashCode() and 0xFFFFFF)
    val tint = Color(0xFF000000 or h.toLong()).copy(alpha = 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        lerpColor(base, Color.Black, 0.35f),
                        lerpColor(tint, Color.Black, 0.72f),
                        Color(0xFF07070A),
                    ),
                ),
            ),
    ) {
        if (!hero) {
            Text(
                text = title.name,
                style = Type.CardTitle.copy(
                    fontSize = if (style == RowStyle.POSTER) 17.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = if (style == RowStyle.POSTER) 20.sp else 23.sp,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
            )
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/**
 * A focusable title card. Focus lifts the card, adds a bright edge and brightens the caption,
 * like tvOS.
 */
@Composable
fun TitleCard(
    title: Title,
    style: RowStyle,
    focusKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    service: StreamingService? = null,
    included: Boolean = false,
    caption: String? = null,
    progress: Float? = null,
    width: androidx.compose.ui.unit.Dp = if (style == RowStyle.POSTER) Dimens.PosterCardWidth else Dimens.LandscapeCardWidth,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    RestoreFocusEffect(focusKey, requester)
    val captionColor by animateColorAsState(
        if (focused) Palette.TextPrimary else Palette.TextSecondary,
        tween(Motion.FocusMillis), label = "caption",
    )
    val shape = RoundedCornerShape(Dimens.CardRadius)

    Column(modifier.width(width)) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (style == RowStyle.POSTER) 2f / 3f else 16f / 9f)
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
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f, pressedScale = 1.04f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.5.dp, Palette.Focus.copy(alpha = 0.9f)), shape = shape),
            ),
        ) {
            Artwork(title, style, Modifier.fillMaxSize().clip(shape))
            if (style != RowStyle.POSTER) {
                // Bottom scrim keeps badges legible on bright artwork.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f))),
                )
            }
            if (service != null || title.isDemo) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    service?.let { ServiceBadge(it, compact = true) }
                    if (title.isDemo && style != RowStyle.POSTER) DemoBadge()
                }
            }
            if (included) {
                Text(
                    "INCLUDED",
                    style = Type.Badge.copy(color = Palette.Included),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            if (progress != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                            .height(4.dp)
                            .background(Color.White),
                    )
                }
            }
        }
        Spacer(Modifier.height(if (focused) 14.dp else 9.dp))
        Text(
            title.name,
            style = Type.CardTitle.copy(color = captionColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = caption ?: listOfNotNull(title.year, title.type.label).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(meta, style = Type.CardMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Small pill naming the service, tinted with its brand colour. */
@Composable
fun ServiceBadge(service: StreamingService, compact: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
            .padding(horizontal = if (compact) 7.dp else 10.dp, vertical = if (compact) 3.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .width(if (compact) 6.dp else 8.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(50))
                .background(Color(service.brandColor)),
        )
        Text(
            service.displayName,
            style = if (compact) Type.Badge.copy(color = Palette.TextPrimary) else Type.Meta.copy(color = Palette.TextPrimary, fontSize = 13.sp),
        )
    }
}

@Composable
fun DemoBadge(modifier: Modifier = Modifier) {
    Text(
        "SAMPLE",
        style = Type.Badge.copy(color = Palette.Demo),
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
