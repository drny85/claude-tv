package com.claudetv.hub.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.claudetv.hub.ui.theme.Motion

/**
 * Keeps the focused item at a fixed "pivot" instead of scrolling the minimum amount, which is
 * what gives tvOS-style rows their calm, predictable motion.
 *
 * @param fraction where in the viewport the focused item's leading edge should sit (0 = start).
 * @param offset extra fixed offset from that point (e.g. the row's start padding).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberPivotSpec(fraction: Float, offset: Dp): BringIntoViewSpec {
    val offsetPx = with(LocalDensity.current) { offset.toPx() }
    return remember(fraction, offsetPx) {
        object : BringIntoViewSpec {
            @Deprecated("Kept for Compose 1.7 compatibility")
            override val scrollAnimationSpec: AnimationSpec<Float> =
                tween(durationMillis = Motion.ScrollMillis, easing = Motion.Ease)

            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val target = containerSize * fraction + offsetPx
                // Items bigger than what fits after the pivot are aligned to the start instead.
                return if (size > containerSize - target) offset else offset - target
            }
        }
    }
}

/** Lets a card take focus back when the details page above it closes. */
class FocusRestore(val key: String?, val consume: () -> Unit)

val LocalFocusRestore = compositionLocalOf { FocusRestore(null) {} }

@Composable
fun RestoreFocusEffect(myKey: String, requester: FocusRequester) {
    val restore = LocalFocusRestore.current
    LaunchedEffect(restore.key) {
        if (restore.key == myKey) {
            runCatching { requester.requestFocus() }
            restore.consume()
        }
    }
}

/** requestFocus throws if the node isn't attached yet; this never does. */
fun FocusRequester.safeRequest() {
    runCatching { requestFocus() }
}
