package com.budgetapp.ui.budget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetapp.ui.components.donutSegment
import com.budgetapp.data.formatPaise
import com.budgetapp.ui.theme.LocalPalette
import com.budgetapp.ui.theme.cardBorder
import com.budgetapp.ui.theme.labelOn
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import com.budgetapp.data.categoryLabel

/** One category's slice of the month's spending, as the donut draws it. */
data class DonutPiece(
    /** Category name, which also identifies the piece. */
    val name: String,
    val emoji: String,
    val color: Color,
    val spentPaise: Long,
    /** Share of all spending, rounded down; printed as is. */
    val percent: Int,
)

/**
 * Half donut of where the month's money went, one slice per category, largest
 * first from the left, with the total spent inside. Tapping a slice lifts it,
 * fades the rest and floats a small legend card beside it; [onSelect] gets the
 * slice's name, or null when a tap clears it.
 */
@Composable
fun CategoryDonut(
    pieces: List<DonutPiece>,
    totalPaise: Long,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Grows from the left when the screen opens, after its first frames are drawn.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        repeat(3) { withFrameNanos { } }
        reveal.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    // The last slice picked, kept while its highlight fades away after a clear.
    var shown by remember { mutableStateOf(selected) }
    if (selected != null) shown = selected
    val focus by animateFloatAsState(if (selected != null) 1f else 0f, tween(250), label = "donutFocus")
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)

    val measurer = rememberTextMeasurer()
    // No colour here: each label takes one that reads on its own slice.
    val labelStyle = TextStyle(fontSize = 12.sp)
    val emptyArc = LocalPalette.current.emptyArc
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Spending by category: " +
                    pieces.joinToString { "${it.name} ${formatPaise(it.spentPaise)}, ${it.percent}%" }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val geometry = with(density) { DonutLayout(widthPx, LIFT.toPx(), LEGEND_ROOM.toPx(), GAP.toPx()) }
        val arcs = geometry.arcs(pieces)
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { geometry.height.toDp() })
                .pointerInput(pieces, geometry) {
                    detectTapGestures { tap ->
                        val hit = geometry.pieceAt(tap, arcs, pieces)
                        currentOnSelect(if (hit == null || hit == currentSelected) null else hit)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val corner = 10.dp.toPx()
                val lift = LIFT.toPx()
                val revealEnd = 180f + 180f * reveal.value
                if (pieces.isEmpty()) {
                    // Nothing spent yet: an empty grey arc, so the screen keeps its shape.
                    donutSegment(geometry.center, geometry.outer, geometry.inner, 180f, 180f * reveal.value, corner, emptyArc)
                    return@Canvas
                }
                arcs.forEachIndexed { i, arc ->
                    val piece = pieces[i]
                    val picked = piece.name == shown
                    val visible = (minOf(arc.start + arc.sweep, revealEnd) - arc.start).coerceAtLeast(0f)
                    val outer = geometry.outer + if (picked) lift * focus else 0f
                    val alpha = if (picked) 1f else 1f - (1f - FADED) * focus
                    donutSegment(geometry.center, outer, geometry.inner, arc.start, visible, corner, piece.color.copy(alpha = alpha))
                }
                // Percents where they fit inside their slice; slivers go without.
                if (reveal.value >= 1f) {
                    val mid = (geometry.inner + geometry.outer) / 2
                    arcs.forEachIndexed { i, arc ->
                        val piece = pieces[i]
                        val layout = measurer.measure("${piece.percent}%", labelStyle)
                        val along = mid * Math.toRadians(arc.sweep.toDouble()).toFloat()
                        if (along < layout.size.width + 10.dp.toPx() || piece.percent == 0) return@forEachIndexed
                        val a = Math.toRadians((arc.start + arc.sweep / 2).toDouble())
                        val alpha = if (piece.name == shown) 1f else 1f - (1f - FADED) * focus
                        drawText(
                            layout,
                            color = labelOn(piece.color),
                            topLeft = Offset(
                                geometry.center.x + mid * cos(a).toFloat() - layout.size.width / 2f,
                                geometry.center.y + mid * sin(a).toFloat() - layout.size.height / 2f,
                            ),
                            alpha = alpha,
                        )
                    }
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Spent", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatPaise(totalPaise), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal))
            }

            val legendIndex = pieces.indexOfFirst { it.name == shown }
            if (legendIndex >= 0 && focus > 0f) {
                FloatingLegend(
                    piece = pieces[legendIndex],
                    totalPaise = totalPaise,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = focus
                            val s = 0.9f + 0.1f * focus
                            scaleX = s
                            scaleY = s
                        }
                        .layout { measurable, constraints ->
                            val card = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                            val at = geometry.legendPosition(arcs[legendIndex], card.width, card.height)
                            layout(constraints.maxWidth, constraints.maxHeight) { card.place(at.first, at.second) }
                        },
                )
            }
        }
    }
}

/** Small card naming the picked slice, floated just outside it. */
@Composable
private fun FloatingLegend(piece: DonutPiece, totalPaise: Long, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = cardBorder(),
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(piece.color, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(categoryLabel(piece.emoji, piece.name), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatPaise(piece.spentPaise)} · ${piece.percent}% of ${formatPaise(totalPaise)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Where one slice sits on the arc, in degrees clockwise from 3 o'clock. */
private data class Arc(val start: Float, val sweep: Float)

/** The donut's geometry in a box [width] pixels wide, with room kept above it for the legend. */
private data class DonutLayout(val width: Float, val lift: Float, val legendRoom: Float, val gapPx: Float) {
    val outer = width / 2 - lift
    val inner = outer * (1 - THICKNESS)
    val center = Offset(width / 2, legendRoom + width / 2)
    val height = center.y

    /**
     * One arc per piece, laid left to right with a small gap between. A piece
     * too small to see still gets a sliver; the others give up a little room
     * for it. Drawing only: every printed figure comes from paise.
     */
    fun arcs(pieces: List<DonutPiece>): List<Arc> {
        if (pieces.isEmpty()) return emptyList()
        val gap = if (pieces.size > 1) Math.toDegrees((gapPx / ((inner + outer) / 2)).toDouble()).toFloat() else 0f
        val room = 180f - gap * (pieces.size - 1)
        val total = pieces.sumOf { it.spentPaise }.toFloat()
        val raw = pieces.map { room * it.spentPaise / total }
        val small = raw.count { it < MIN_SWEEP }
        val bigTotal = raw.filter { it >= MIN_SWEEP }.sum()
        val scale = if (bigTotal > 0f) (room - small * MIN_SWEEP) / bigTotal else 1f
        var start = 180f
        return raw.map { r ->
            val sweep = if (r < MIN_SWEEP) MIN_SWEEP else r * scale
            Arc(start, sweep).also { start += sweep + gap }
        }
    }

    /**
     * The name of the piece under [tap], or null when the tap is off the band.
     * Gaps count toward the nearer neighbour, and taps just past either end of
     * the arc toward the end piece, so a thin sliver is still easy to hit.
     */
    fun pieceAt(tap: Offset, arcs: List<Arc>, pieces: List<DonutPiece>): String? {
        if (arcs.isEmpty()) return null
        val dx = tap.x - center.x
        val dy = tap.y - center.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < inner - TAP_SLACK_PX || distance > outer + lift + TAP_SLACK_PX) return null
        // 180 is the left end, 270 the top, 360 the right end.
        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (angle < 0f) angle += 360f
        if (angle < 90f) angle = 360f // just below the right end
        if (angle in 90f..180f) angle = 180f // just below the left end
        val nearest = arcs.indices.minBy { i ->
            val arc = arcs[i]
            when {
                angle < arc.start -> arc.start - angle
                angle > arc.start + arc.sweep -> angle - (arc.start + arc.sweep)
                else -> 0f
            }
        }
        return pieces[nearest].name
    }

    /** Top-left corner for a [w] by [h] legend card beside [arc], kept inside the box. */
    fun legendPosition(arc: Arc, w: Int, h: Int): Pair<Int, Int> {
        val a = Math.toRadians((arc.start + arc.sweep / 2).toDouble())
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val r = outer + lift + 8f
        val ax = center.x + r * dx
        val ay = center.y + r * dy
        // Above the slice near the top of the arc, beside it lower down.
        val x = if (dy < -0.6f) ax - w / 2f else if (dx < 0f) ax - w else ax
        val y = if (dy < -0.6f) ay - h else ay - h / 2f
        return x.coerceIn(0f, (width - w).coerceAtLeast(0f)).roundToInt() to
            y.coerceIn(0f, (height - h).coerceAtLeast(0f)).roundToInt()
    }
}

/** How far off the band, in pixels, a tap still counts as on it. */
private const val TAP_SLACK_PX = 24f

/** Share of the radius the band takes up, as on Home. */
private const val THICKNESS = 0.27f

/** Smallest slice drawn, in degrees. */
private const val MIN_SWEEP = 2.5f

/** How see-through the slices not picked become. */
private const val FADED = 0.35f

/** How far a picked slice rises out of the arc. */
private val LIFT = 6.dp

/** Space between neighbouring slices, measured along the middle of the band. */
private val GAP = 5.dp

/** Room above the arc for the legend card of a slice near the top. */
private val LEGEND_ROOM = 56.dp
