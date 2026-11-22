package com.budgetapp.ui.home

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.budgetapp.ui.theme.PhosphorLight
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

private val monthShortWithYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

private val MONTH_MENU_WIDTH = 128.dp

/** Space between the pill and the top of the list. */
private val MENU_GAP = 6.dp

/** Padding above the heading and below the last row, inside the card. */
private val MENU_PADDING = 8.dp

/** Every height is fixed, so a finger's position says exactly which row it is on. */
private val HEADING_HEIGHT = 26.dp
private val ROW_HEIGHT = 40.dp

/**
 * The month under a finger that pressed the pill and is sliding over the open
 * list: [position] is in the pill's own coordinates, and the list hangs
 * [MENU_GAP] below the pill's [pillHeight], from its left edge. Null over the
 * heading, the padding, or anywhere off the list.
 */
internal fun monthUnderFinger(months: List<YearMonth>, position: Offset, pillHeight: Int, density: Density): YearMonth? =
    with(density) {
        if (position.x < 0f || position.x > MONTH_MENU_WIDTH.toPx()) return null
        val rowsTop = pillHeight + (MENU_GAP + MENU_PADDING + HEADING_HEIGHT).toPx()
        val index = ((position.y - rowsTop) / ROW_HEIGHT.toPx()).let { if (it < 0f) return null else it.toInt() }
        // The list runs oldest at the top to the current month at the bottom.
        months.asReversed().getOrNull(index)
    }

/** Hangs the list from the pill's bottom-left corner, never flipped above it. */
private class BelowPill(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(anchorBounds.left, anchorBounds.bottom + gapPx)
}

/**
 * The month pill's list: a small "Month" heading, then one short row per month
 * ("Nov", or "Dec 2025" outside the current year), oldest at the top and the
 * current month at the bottom, with a tick on the month Home is showing.
 *
 * It can be scrubbed: the pill opens it on touch down, and while the finger
 * stays down and slides over it, [highlighted] (the row under the finger) is
 * tinted; lifting picks it (see [monthUnderFinger]). A plain tap leaves it open
 * to tap a row. Drawn by hand at fixed heights, rather than as Material's menu,
 * so the rows are narrow and the finger maths is exact. Its background is the
 * card colour (white in the light look).
 */
@Composable
internal fun MonthMenu(
    expanded: Boolean,
    months: List<YearMonth>,
    shown: YearMonth?,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
    highlighted: YearMonth? = null,
) {
    val current = months.firstOrNull() ?: return
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    if (!visible.currentState && !visible.targetState) return

    val transition = rememberTransition(visible, label = "monthMenu")
    val alpha = transition.animateFloat({ tween(durationMillis = 120) }, label = "menuAlpha") { if (it) 1f else 0f }
    val scale = transition.animateFloat({ tween(durationMillis = 120) }, label = "menuScale") { if (it) 1f else 0.92f }
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { MENU_GAP.roundToPx() }

    Popup(
        popupPositionProvider = remember(gapPx) { BelowPill(gapPx) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .width(MONTH_MENU_WIDTH)
                .graphicsLayer {
                    this.alpha = alpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            Column(Modifier.padding(vertical = MENU_PADDING)) {
                Text(
                    "Month",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .height(HEADING_HEIGHT)
                        .padding(horizontal = 14.dp),
                )
                months.asReversed().forEach { month ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT)
                            .background(
                                if (month == highlighted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                else MaterialTheme.colorScheme.surface,
                            )
                            .clickable { onPick(month) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            month.format(if (month.year == current.year) monthShort else monthShortWithYear),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (month == shown) {
                            Icon(PhosphorLight.Check, contentDescription = "Shown now", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
