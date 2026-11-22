package com.budgetapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Fades scrolling content out at the top and bottom edges of its viewport, so
 * rows slide softly under the edge instead of being cut off. Each edge fades
 * only while there is more to scroll that way, so nothing is dimmed at rest:
 * the first row is never faded while the list is at its top.
 *
 * Put it before the scroll modifier (or on the lazy list itself), so it draws
 * over the viewport rather than scrolling with the content. It fades to the
 * page background, which is what lies behind every list that uses it.
 */
@Composable
fun Modifier.scrollFade(state: ScrollableState): Modifier {
    val color = MaterialTheme.colorScheme.background
    val top by animateFloatAsState(if (state.canScrollBackward) 1f else 0f, tween(200), label = "fadeTop")
    val bottom by animateFloatAsState(if (state.canScrollForward) 1f else 0f, tween(200), label = "fadeBottom")
    return drawWithContent {
        drawContent()
        val h = FADE_HEIGHT.toPx().coerceAtMost(size.height / 2)
        if (top > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(color.copy(alpha = top), color.copy(alpha = 0f)), startY = 0f, endY = h),
                size = Size(size.width, h),
            )
        }
        if (bottom > 0f) {
            drawRect(
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0f), color.copy(alpha = bottom)),
                    startY = size.height - h,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - h),
                size = Size(size.width, h),
            )
        }
    }
}

private val FADE_HEIGHT = 28.dp
