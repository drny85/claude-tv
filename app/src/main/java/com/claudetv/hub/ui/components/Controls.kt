package com.claudetv.hub.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.claudetv.hub.data.VideoFormat
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type

/**
 * tvOS-style button: translucent at rest, solid white with dark content when focused.
 */
@Composable
fun PillButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 11.dp),
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
            contentColor = Palette.TextPrimary,
            focusedContainerColor = Palette.Focus,
            focusedContentColor = Palette.OnFocus,
            pressedContainerColor = Color(0xFFDADADF),
            pressedContentColor = Palette.OnFocus,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        val color = if (focused) Palette.OnFocus else Palette.TextPrimary
        Row(
            Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.let {
                Image(it, contentDescription = text, colorFilter = ColorFilter.tint(color), modifier = Modifier.size(20.dp))
            }
            text?.let { Text(it, style = Type.Button.copy(color = color), maxLines = 1) }
        }
    }
}

/** A selectable row used in Settings and Services: label, detail and an on/off state. */
@Composable
fun ToggleRow(
    label: String,
    detail: String?,
    checked: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface,
            contentColor = Palette.TextPrimary,
            focusedContainerColor = Palette.Focus,
            focusedContentColor = Palette.OnFocus,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        val fg = if (focused) Palette.OnFocus else Palette.TextPrimary
        val fg2 = if (focused) Palette.OnFocus.copy(alpha = 0.7f) else Palette.TextSecondary
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.let { it(); Spacer(Modifier.width(14.dp)) }
            Column(Modifier.weight(1f)) {
                Text(label, style = Type.RowTitle.copy(color = fg, fontSize = Type.Button.fontSize))
                detail?.let { Text(it, style = Type.CardMeta.copy(color = fg2)) }
            }
            trailingText?.let { Text(it, style = Type.Meta.copy(color = fg2)) }
            if (checked != null) {
                Spacer(Modifier.width(14.dp))
                Switch(checked, focused)
            }
        }
    }
}

@Composable
private fun Switch(checked: Boolean, focused: Boolean) {
    val track = when {
        checked -> Palette.Included
        focused -> Color.Black.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.18f)
    }
    Box(
        Modifier
            .width(46.dp)
            .height(26.dp)
            .background(track, RoundedCornerShape(50))
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).background(Color.White, RoundedCornerShape(50)))
    }
}

/** Outlined format badge, e.g. "4K" or "DOLBY VISION". Dimmed when the TV can't play it. */
@Composable
fun FormatBadge(format: VideoFormat, supportedByTv: Boolean = true) {
    val c = if (supportedByTv) Palette.TextPrimary else Palette.TextTertiary
    Text(
        format.label.uppercase(),
        style = Type.Badge.copy(color = c),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .border(BorderStroke(1.2.dp, c.copy(alpha = 0.8f)), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.24f) else Color.Transparent,
            contentColor = Palette.TextPrimary,
            focusedContainerColor = Palette.Focus,
            focusedContentColor = Palette.OnFocus,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0f else 0.25f)), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text,
            style = Type.Meta.copy(color = if (focused) Palette.OnFocus else Palette.TextPrimary),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
        )
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, gap: Dp = 12.dp) {
    Column(modifier.padding(bottom = gap)) {
        Text(title, style = Type.RowTitle)
        subtitle?.let { Text(it, style = Type.RowSubtitle) }
    }
}
