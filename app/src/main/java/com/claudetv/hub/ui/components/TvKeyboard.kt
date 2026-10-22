package com.claudetv.hub.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.claudetv.hub.ui.theme.Palette
import com.claudetv.hub.ui.theme.Type

/**
 * A D-pad friendly on-screen keyboard. Typing with the system IME on a TV remote is slow,
 * so search and key entry use this grid instead.
 */
@Composable
fun TvKeyboard(
    onChar: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    firstKeyRequester: FocusRequester? = null,
    includeSymbols: Boolean = false,
    columns: Int = 7,
    keySize: Dp = 38.dp,
) {
    val keys = buildList {
        addAll(('a'..'z').toList())
        addAll(('0'..'9').toList())
        if (includeSymbols) addAll(listOf('-', '_', '.'))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { i, c ->
                    val m = if (rowIndex == 0 && i == 0 && firstKeyRequester != null) Modifier.focusRequester(firstKeyRequester) else Modifier
                    Key(label = c.uppercase(), onClick = { onChar(c) }, width = keySize, height = keySize, modifier = m)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Key(label = "space", onClick = { onChar(' ') }, width = keySize * 3 + 12.dp, height = keySize)
            Key(icon = Icons.Filled.ArrowBack, onClick = onDelete, width = keySize * 2 + 6.dp, height = keySize)
            Key(icon = Icons.Filled.Clear, onClick = onClear, width = keySize * 2 + 6.dp, height = keySize)
        }
    }
}

@Composable
private fun Key(
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(height)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Palette.TextPrimary,
            focusedContainerColor = Palette.Focus,
            focusedContentColor = Palette.OnFocus,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
    ) {
        val color = if (focused) Palette.OnFocus else Palette.TextPrimary
        Box(Modifier.width(width).height(height), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(color), modifier = Modifier.size(18.dp))
            } else if (label != null) {
                Text(label, style = Type.Button.copy(color = color))
            }
        }
    }
}
