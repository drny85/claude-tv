package com.claudetv.hub.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Cinematic, near-monochrome palette: content artwork provides the colour. */
object Palette {
    val Background = Color(0xFF050507)
    val Surface = Color(0xFF15151B)
    val SurfaceRaised = Color(0xFF22222A)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA1A1A8)
    val TextTertiary = Color(0xFF6E6E76)
    val Focus = Color(0xFFF5F5F7)
    val OnFocus = Color(0xFF0A0A0C)
    val Included = Color(0xFF34C759)
    val Demo = Color(0xFFFFB340)
    val Scrim = Color(0xE6050507)
}

/**
 * Sizes are in dp/sp against Android TV's 960×540 dp canvas, which Google TV scales to the
 * 4K panel. Body text never goes below 14sp so it stays readable from the couch.
 */
object Type {
    val HeroTitle = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 48.sp, color = Palette.TextPrimary)
    val ScreenTitle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = Palette.TextPrimary)
    val RowTitle = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary)
    val RowSubtitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Palette.TextSecondary)
    val CardTitle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.TextPrimary)
    val CardMeta = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Palette.TextSecondary)
    val Body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp, color = Palette.TextSecondary)
    val Meta = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.TextSecondary)
    val Button = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val Badge = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    val Nav = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

object Dimens {
    /** Safe-area margins for TV overscan. */
    val ScreenH = 48.dp
    val ScreenV = 27.dp
    val RowGap = 30.dp
    val CardGap = 20.dp
    val LandscapeCardWidth = 236.dp
    val PosterCardWidth = 132.dp
    val CardRadius = 12.dp
}

object Motion {
    /** Apple-like ease-out used for focus, scroll and transitions. */
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val FocusMillis = 220
    const val ScrollMillis = 420
}

@Composable
fun ClaudeTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Focus,
            onPrimary = Palette.OnFocus,
            background = Palette.Background,
            onBackground = Palette.TextPrimary,
            surface = Palette.Surface,
            onSurface = Palette.TextPrimary,
            surfaceVariant = Palette.SurfaceRaised,
            onSurfaceVariant = Palette.TextSecondary,
        ),
        content = content,
    )
}
