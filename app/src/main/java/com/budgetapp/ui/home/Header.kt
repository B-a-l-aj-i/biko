@file:OptIn(ExperimentalFoundationApi::class)

package com.budgetapp.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetapp.ui.theme.LocalPalette
import com.budgetapp.ui.theme.LocalStatusColors
import com.budgetapp.ui.theme.PhosphorLight
import com.budgetapp.ui.theme.cardOutline
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import java.time.YearMonth
import com.budgetapp.ui.theme.outlinedSurface

/**
 * Soft washes of colour behind the page that say how the month is going:
 * green while spending is on or under pace, red once it runs ahead or the
 * budget is used up, lavender with no budget. Green and red are kept for this
 * alone, so the donut never uses them. Black-and-white mode has no wash; the
 * pace line in the header says the same thing in words.
 */
@Composable
internal fun ColourWash(spendingWell: Boolean?, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val target = when (spendingWell) {
        true -> palette.washGood
        false -> palette.washBad
        null -> palette.washNone
    }
    val main by animateColorAsState(target, tween(durationMillis = 700), label = "wash")
    Canvas(modifier) {
        val w = size.width
        fun wash(color: Color, center: Offset, radius: Float) = drawCircle(
            brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius),
            radius = radius,
            center = center,
        )
        wash(main, Offset(w * 0.9f, w * 0.05f), w * 0.9f)
        wash(main.copy(alpha = 0.7f), Offset(w * 1.05f, w * 0.85f), w * 0.7f)
        wash(main.copy(alpha = 0.45f), Offset(-w * 0.1f, w * 0.35f), w * 0.6f)
    }
}

/**
 * Pinned to the top, over the status bar too, so the list scrolls under it
 * instead of sliding up into the clock and battery icons.
 */
@Composable
internal fun Header(
    monthLabel: String,
    /** Offered by the month pill, newest first; the first is the current month. */
    months: List<YearMonth>,
    /** The month Home is showing, ticked in the list. */
    shownMonth: YearMonth?,
    onMonth: (YearMonth) -> Unit,
    paceMessage: String,
    amountsHidden: Boolean,
    onToggleHidden: () -> Unit,
    haze: HazeState,
    /** Whether the list has moved off its top, so there is something under the bar. */
    scrolled: Boolean,
    modifier: Modifier = Modifier,
) {
    // Clear glass while the page is at the top, frosted once the list is under it.
    val frost by animateFloatAsState(if (scrolled) 1f else 0f, tween(durationMillis = 200), label = "headerFrost")
    // Full frost behind the status bar and the row, fading out over the strip below.
    val density = LocalDensity.current
    val fadePx = with(density) { HEADER_FADE.toPx() }
    val rowBottomPx = WindowInsets.statusBars.getTop(density) + with(density) { HEADER_HEIGHT.toPx() }
    val frostMask = remember(rowBottomPx, fadePx) {
        Brush.verticalGradient(
            colors = listOf(Color.Black, Color.Transparent),
            startY = rowBottomPx,
            endY = rowBottomPx + fadePx,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hazeChild(state = haze, style = frostStyle()) {
                mask = frostMask
                // Fades the whole effect, blur included, so at rest it is fully clear.
                alpha = frost
                // Fully clear means nothing to blur: skip the blur pass, which
                // otherwise runs on the GPU every frame the page changes (the
                // donut's launch sweep) even though none of it shows.
                blurEnabled = frost > 0f
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = HEADER_TOP_GAP, bottom = HEADER_BOTTOM_GAP)
                .height(HEADER_ROW_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The pill and the eye are the same height, so they sit on one line.
            // A tap or a long press lists the months to look back at.
            var picking by remember { mutableStateOf(false) }
            val pillHaptics = LocalHapticFeedback.current
            Box(Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(
                        onClickLabel = "Pick a month",
                        onLongClick = {
                            pillHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            picking = true
                        },
                        onClick = { picking = true },
                    )
                    .padding(start = 12.dp, end = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    monthLabel.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    PhosphorLight.CaretDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
            MonthMenu(
                expanded = picking,
                months = months,
                shown = shownMonth ?: months.firstOrNull(),
                onPick = { month ->
                    picking = false
                    onMonth(month)
                },
                onDismiss = { picking = false },
            )
            }
            // A line about the pace of spending, with no amount in it. It changes
            // on each pull to refresh.
            // The new line rises into place as the old one leaves upward.
            AnimatedContent(
                paceMessage,
                transitionSpec = {
                    (slideInVertically(tween(320)) { it } + fadeIn(tween(320))) togetherWith
                        (slideOutVertically(tween(220)) { -it } + fadeOut(tween(160)))
                },
                contentAlignment = Alignment.Center,
                label = "paceMessage",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) { line ->
                // Two lines at most: the bar keeps one height, so the page below never jumps.
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier
                    .size(HEADER_ROW_HEIGHT)
                    .outlinedSurface(CircleShape)
                    .clickable(onClick = onToggleHidden),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (amountsHidden) PhosphorLight.EyeSlash else PhosphorLight.Eye,
                    contentDescription = if (amountsHidden) "Show amounts" else "Hide amounts",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        // The strip the frost fades out over, below the header's own row.
        Spacer(Modifier.height(HEADER_FADE))
    }
}

/**
 * Strip below the header's row where its frost fades to nothing, so the list
 * melts into the header with no hard edge. It adds no room above the list:
 * the list still starts right under the row and scrolls through the fade.
 */
private val HEADER_FADE = 28.dp

/** The header's pill and eye button are this tall. */
private val HEADER_ROW_HEIGHT = 32.dp

private val HEADER_TOP_GAP = 12.dp

private val HEADER_BOTTOM_GAP = 8.dp

/** Height of the pinned header, not counting the status bar above it. */
internal val HEADER_HEIGHT = HEADER_TOP_GAP + HEADER_ROW_HEIGHT + HEADER_BOTTOM_GAP

/**
 * Shows whether the pay-time gate can still run; it fails silently if the user
 * revokes its permissions.
 */
@Composable
internal fun ProtectionChip(protectionOn: Boolean) {
    val status = LocalStatusColors.current
    Row {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (protectionOn) status.successContainer else status.warningContainer)
                .cardOutline(CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (protectionOn) status.success else status.onWarningContainer
            Icon(
                if (protectionOn) PhosphorLight.ShieldCheck else PhosphorLight.ShieldWarning,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (protectionOn) "Protection on" else "Protection off",
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }
}
