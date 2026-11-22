package com.budgetapp.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetapp.ui.components.donutSegment
import com.budgetapp.ui.theme.LocalPalette
import com.budgetapp.ui.theme.PhosphorLight
import com.budgetapp.ui.theme.cardOutline
import com.budgetapp.ui.theme.labelOn
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** A part of the half donut the user can pick out by tapping it or its legend. */
internal enum class DonutPart { SPENT, LEFT }

/** Stands in for the donut and its sum for a past month with no payments in it. */
@Composable
internal fun EmptyMonth(month: YearMonth?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(PhosphorLight.Receipt, contentDescription = null, tint = muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            month?.let { "No payments in " + it.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)) }
                ?: "No payments",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Nothing was added or spent this month.",
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun BudgetSummary(state: HomeUiState, money: (Long) -> String, replays: Int) {
    var focus by rememberSaveable { mutableStateOf<DonutPart?>(null) }
    val toggle: (DonutPart?) -> Unit = { focus = if (it == focus) null else it }
    val colors = donutColors(state)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        HalfDonut(state, money, colors, focus, onTap = toggle, replays = replays, modifier = Modifier.fillMaxWidth())
        if (state.monthlyBudget > 0) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendItem("Spent", colors.spent, DonutPart.SPENT, focus, toggle)
                // Once over budget nothing is left, so the arc is all spent.
                if (state.leftPaise > 0) LegendItem("Left", colors.left, DonutPart.LEFT, focus, toggle)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private data class DonutColors(val spent: Color, val left: Color)

/** Neither is green nor red: those belong to the background, which says how the month is going. */
@Composable
private fun donutColors(state: HomeUiState): DonutColors {
    val palette = LocalPalette.current
    return DonutColors(
        spent = palette.donutSpent,
        left = when {
            state.monthlyBudget > 0 -> palette.donutLeft
            // Black and white has no faint outline colour to borrow: its own grey instead.
            palette.outlined -> palette.emptyArc
            else -> MaterialTheme.colorScheme.outlineVariant
        },
    )
}

@Composable
private fun LegendItem(
    label: String,
    color: Color,
    part: DonutPart,
    focus: DonutPart?,
    onTap: (DonutPart?) -> Unit,
) {
    val alpha by animateFloatAsState(if (focus == null || focus == part) 1f else FADED, label = "legend")
    Row(
        modifier = Modifier
            .alpha(alpha)
            .clip(CircleShape)
            .background(if (focus == part) MaterialTheme.colorScheme.surface else Color.Transparent)
            .then(if (focus == part) Modifier.cardOutline(CircleShape) else Modifier)
            .selectable(selected = focus == part, role = Role.Tab) { onTap(part) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** How see-through the parts not picked out become. */
internal const val FADED = 0.35f

/**
 * Half donut split into spent and left, which grows from the left when the
 * screen opens, with the month's sum (budget minus spent equals left) inside.
 * Tapping a part lifts it and fades the rest; tapping inside clears that.
 */
@Composable
private fun HalfDonut(
    state: HomeUiState,
    money: (Long) -> String,
    colors: DonutColors,
    focus: DonutPart?,
    onTap: (DonutPart?) -> Unit,
    /** Pulls to refresh so far; each new one redraws the arc and the sum from nothing. */
    replays: Int,
    modifier: Modifier = Modifier,
) {
    val hasBudget = state.monthlyBudget > 0
    // Floats are for drawing only; every printed figure comes from paise.
    val target = if (hasBudget) state.spentFraction.coerceIn(0f, 1f) else 0f
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(target) {
        // On launch, let the first frames (the whole page composing and laying
        // out for the first time) finish before the sweep starts, so the sweep
        // doesn't share its opening frames with that work and stutter.
        if (sweep.value == 0f) repeat(SWEEP_FRAMES_TO_WAIT) { withFrameNanos { } }
        sweep.animateTo(target, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    // A pull to refresh empties the arc and fills it again from the figures as
    // they are now, so the user sees it worked out afresh.
    val currentTarget by rememberUpdatedState(target)
    LaunchedEffect(replays) {
        if (replays == 0) return@LaunchedEffect
        sweep.snapTo(0f)
        sweep.animateTo(currentTarget, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    val spentLift by animateFloatAsState(if (focus == DonutPart.SPENT) 1f else 0f, label = "spentLift")
    val leftLift by animateFloatAsState(if (focus == DonutPart.LEFT) 1f else 0f, label = "leftLift")
    val spentAlpha by animateFloatAsState(if (focus == DonutPart.LEFT) FADED else 1f, label = "spentAlpha")
    val leftAlpha by animateFloatAsState(if (focus == DonutPart.SPENT) FADED else 1f, label = "leftAlpha")

    // Percent labels only while there is a budget and it isn't overspent; past
    // it the arc is all spent and the centre says how much over.
    val showLabels = hasBudget && state.leftPaise >= 0
    val spentLabel = "${state.spentPercent}%"
    val leftLabel = "${state.leftPercent}%"
    // No colour here: a label inside the band takes one that reads on its part,
    // a label moved inside the arc takes the page's text colour.
    val labelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val pageInk = LocalPalette.current.labelOnPage
    val measurer = rememberTextMeasurer()
    // sweep.value is read only when a tap lands and while drawing, never here:
    // reading it during composition would recompose the donut on every frame of
    // its sweep.
    val currentOnTap by rememberUpdatedState(onTap)

    BoxWithConstraints(modifier) {
        val radius = maxWidth / 2
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(radius)
                .pointerInput(hasBudget) {
                    detectTapGestures { tap ->
                        if (!hasBudget) return@detectTapGestures
                        val g = DonutGeometry(size.width.toFloat(), LIFT.toPx())
                        val dx = tap.x - g.center.x
                        val dy = tap.y - g.center.y
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                        val onBand = distance >= g.inner - 8.dp.toPx() && distance <= g.outer + LIFT.toPx() + 8.dp.toPx()
                        if (!onBand) {
                            currentOnTap(null) // a tap inside the arc clears the highlight
                            return@detectTapGestures
                        }
                        // 180 is the left end, 270 the top, 360 the right end.
                        var angle = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
                        if (angle < 0f) angle += 360f
                        if (angle < 90f) angle = 360f // just below the right end
                        if (angle in 90f..180f) angle = 180f // just below the left end
                        val drawnSweep = sweep.value
                        val spentEnd = 180f + 180f * drawnSweep
                        // A thin sliver of spending still gets a fair-sized target.
                        val part = when {
                            drawnSweep >= 1f -> DonutPart.SPENT
                            angle <= maxOf(spentEnd, 180f + MIN_TAP_SWEEP) -> DonutPart.SPENT
                            else -> DonutPart.LEFT
                        }
                        currentOnTap(part)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val g = DonutGeometry(size.width, LIFT.toPx())
                val thickness = g.outer - g.inner
                val corner = 10.dp.toPx()
                val gap = Math.toDegrees((6.dp.toPx() / (g.inner + thickness / 2)).toDouble()).toFloat()
                val lift = LIFT.toPx()
                val spentOuter = g.outer + lift * spentLift
                val leftOuter = g.outer + lift * leftLift
                val spentFill = colors.spent.copy(alpha = spentAlpha)
                val leftFill = colors.left.copy(alpha = leftAlpha)

                val spentSweep = 180f * sweep.value
                when {
                    spentSweep <= 0f -> donutSegment(g.center, leftOuter, g.inner, 180f, 180f, corner, leftFill)
                    spentSweep >= 180f -> donutSegment(g.center, spentOuter, g.inner, 180f, 180f, corner, spentFill)
                    else -> {
                        // A sliver of spending stays visible, however small.
                        val spentDrawn = (spentSweep - gap / 2).coerceAtLeast(MIN_SWEEP)
                        val leftStart = 180f + spentDrawn + gap
                        donutSegment(g.center, spentOuter, g.inner, 180f, spentDrawn, corner, spentFill)
                        donutSegment(g.center, leftOuter, g.inner, leftStart, 360f - leftStart, corner, leftFill)
                    }
                }

                if (showLabels) {
                    val mid = g.inner + thickness / 2
                    val roomPx = LABEL_ROOM.toPx()
                    // Every part with any share gets its percent: inside the band
                    // when the text fits along it, otherwise just inside the arc,
                    // next to its own part.
                    fun label(text: String, start: Float, sweepDeg: Float, alpha: Float, fill: Color) {
                        if (sweepDeg <= 0f) return
                        val angle = Math.toRadians((start + sweepDeg / 2).toDouble())
                        val layout = measurer.measure(text, labelStyle)
                        val half = Offset(layout.size.width / 2f, layout.size.height / 2f)
                        val alongBand = mid * Math.toRadians(sweepDeg.toDouble()).toFloat()
                        val fits = alongBand >= layout.size.width + roomPx
                        val r = if (fits) mid else g.inner - roomPx - half.getDistance()
                        val at = Offset(
                            g.center.x + r * cos(angle).toFloat() - half.x,
                            g.center.y + r * sin(angle).toFloat() - half.y,
                        )
                        drawText(layout, color = if (fits) labelOn(fill) else pageInk, topLeft = at, alpha = alpha)
                    }
                    // The labels carry the final percentages, so they stay hidden
                    // while the arc is still filling (a part-filled arc would not
                    // match them) and fade in over the last stretch of the sweep.
                    val settled = if (target > 0f) {
                        ((sweep.value / target - LABELS_APPEAR_FROM) / (1f - LABELS_APPEAR_FROM)).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    label(spentLabel, 180f, spentSweep, spentAlpha * settled, colors.spent)
                    label(leftLabel, 180f + spentSweep, 180f - spentSweep, leftAlpha * settled, colors.left)
                }
            }
            CentreSum(
                state = state,
                money = money,
                focus = focus,
                replays = replays,
                // The minus hangs outside the block, so this centres the figures and their line.
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Where the donut sits in a box of [width]: room is kept at the edge for a lifted part. */
private class DonutGeometry(width: Float, lift: Float) {
    val outer = width / 2 - lift
    val inner = outer * (1 - DONUT_THICKNESS)
    val center = Offset(width / 2, width / 2)
}

/** How far a tapped part rises out of the arc. */
private val LIFT = 6.dp

/** Share of the sweep that must be done before the percent labels start to fade in. */
private const val LABELS_APPEAR_FROM = 0.85f

/** Frames the donut waits on launch before sweeping, so it starts after the first heavy frames. */
private const val SWEEP_FRAMES_TO_WAIT = 3

/** Smallest arc, in degrees, that a tap on the spent part lands in. */
private const val MIN_TAP_SWEEP = 12f

/** Share of the radius the donut's band takes up. */
private const val DONUT_THICKNESS = 0.27f

/** Smallest arc, in degrees, drawn for a part that isn't zero. */
private const val MIN_SWEEP = 2.5f

/** Clear space a percent label keeps around it, along the band or from the arc's inner edge. */
private val LABEL_ROOM = 10.dp
