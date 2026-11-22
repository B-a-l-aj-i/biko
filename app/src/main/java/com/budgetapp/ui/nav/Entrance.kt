package com.budgetapp.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A timeline for content that arrives after its screen has opened. Returns a
 * reader giving 0 until [delayMs] has passed, then climbing steadily to 1 over
 * [durationMs]. Pass it to [riseIn].
 */
@Composable
fun rememberEntrance(delayMs: Long = 240, durationMs: Int = 480): () -> Float {
    val timeline = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        timeline.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    return { timeline.value }
}

/**
 * Fades the element in while it rises [distance] into its own place, driven by
 * [progress] from [rememberEntrance]. With several sections, give each its
 * [order] out of [of]: later ones start a little later, so the page settles
 * from the top down. Only layer properties change, so nothing is re-measured.
 */
fun Modifier.riseIn(
    progress: () -> Float,
    order: Int = 0,
    of: Int = 1,
    distance: Dp = 28.dp,
): Modifier = graphicsLayer {
    // The starts are spread over the first half of the timeline.
    val gap = if (of > 1) 0.5f / (of - 1) else 0f
    val t = ((progress() - order * gap) / (1f - gap * (of - 1))).coerceIn(0f, 1f)
    // Fast at first, easing to a stop.
    val eased = 1f - (1f - t) * (1f - t) * (1f - t)
    alpha = eased
    translationY = (1f - eased) * distance.toPx()
}
