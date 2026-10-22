package com.claudetv.hub.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.claudetv.hub.data.VideoFormat
import com.claudetv.hub.data.plex.PlexItem
import com.claudetv.hub.data.plex.PlexType
import com.claudetv.hub.ui.AppViewModel
import com.claudetv.hub.ui.theme.Dimens
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type

/** Converts a dp size to device pixels so Plex can resize artwork exactly for the card. */
@Composable
fun px(dp: Dp): Int = with(LocalDensity.current) { dp.roundToPx() }

enum class PlexCardStyle { POSTER, LANDSCAPE }

@Composable
fun PlexCard(
    vm: AppViewModel,
    item: PlexItem,
    style: PlexCardStyle,
    focusKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = if (style == PlexCardStyle.POSTER) Dimens.PosterCardWidth else Dimens.LandscapeCardWidth,
    onFocused: () -> Unit = {},
    showCaption: Boolean = true,
) {
    val poster = style == PlexCardStyle.POSTER
    val ratio = if (poster) 2f / 3f else 16f / 9f
    // Ask for the image at the card's focused size so it stays crisp at 1.09x on a 4K panel.
    val wPx = (px(width) * 1.15f).toInt()
    val hPx = (wPx / ratio).toInt()
    val url = vm.plexImage(if (poster) item.posterPath else item.landscapePath, wPx, hPx)
    GlassCard(
        onClick = onClick,
        imageUrl = url,
        aspectRatio = ratio,
        width = width,
        focusKey = focusKey,
        modifier = modifier,
        title = if (showCaption) item.displayTitle else null,
        subtitle = if (showCaption) item.displaySubtitle else null,
        progress = item.progress,
        onFocused = onFocused,
        overlay = {
            val fourK = VideoFormat.UHD_4K in item.quickFormats
            if (fourK && !poster) {
                GlassChip(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Text("4K", style = Type.Badge.copy(color = Palette.TextPrimary))
                }
            }
            when {
                item.isWatched -> GlassChip(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Image(Icons.Filled.Check, null, colorFilter = ColorFilter.tint(Palette.TextPrimary), modifier = Modifier.size(12.dp))
                }
                item.type == PlexType.SHOW && (item.unwatchedCount ?: 0) > 0 ->
                    GlassChip(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Text("${item.unwatchedCount}", style = Type.Badge.copy(color = Palette.TextPrimary))
                    }
            }
        },
    )
}

/** A titled row of Plex cards. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlexRow(
    vm: AppViewModel,
    rowId: String,
    title: String,
    items: List<PlexItem>,
    style: PlexCardStyle,
    onOpen: (PlexItem, String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onFocused: (PlexItem) -> Unit = {},
) {
    Column(modifier) {
        SectionHeader(title, subtitle, Modifier.padding(start = Dimens.ScreenH))
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivotSpec(0f, Dimens.ScreenH)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.ScreenH),
                horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            ) {
                items(items.distinctBy { it.ratingKey }, key = { it.ratingKey }) { item ->
                    val key = "$rowId/${item.ratingKey}"
                    PlexCard(
                        vm = vm,
                        item = item,
                        style = style,
                        focusKey = key,
                        onClick = { onOpen(item, key) },
                        onFocused = { onFocused(item) },
                    )
                }
            }
        }
    }
}

/** Real per-title formats from the file's streams, e.g. 4K · DOLBY VISION · ATMOS. */
@Composable
fun FormatRow(formats: Set<VideoFormat>, tvFormats: Set<VideoFormat>, modifier: Modifier = Modifier) {
    if (formats.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        formats.sortedBy { it.ordinal }.forEach { FormatBadge(it, supportedByTv = tvFormats.isEmpty() || it in tvFormats) }
    }
}
