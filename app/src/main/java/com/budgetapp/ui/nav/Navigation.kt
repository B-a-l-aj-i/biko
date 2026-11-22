package com.budgetapp.ui.nav

import androidx.activity.compose.PredictiveBackHandler
import com.budgetapp.ui.components.Grabber
import androidx.compose.animation.core.animate
import kotlinx.coroutines.Job
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import com.budgetapp.ui.theme.LocalPalette
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Spacer
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * How many times the gap above a sheet the finger travels to expand it to
 * full screen: more than the gap itself, so the growth feels weighted.
 */
private const val EXPAND_DRAG = 2.5f

/**
 * A sheet let go below this much of its way up closes; above it, it springs
 * back. The grabber's arrow is full at the same point.
 */
private const val SHEET_CLOSE_BELOW = 0.66f

/** How far a back swipe moves the sheet before it is released. */
private const val BACK_TRAVEL = 0.3f

/**
 * Draws [root] with the navigator's screens stacked above it, and runs every
 * transition. Each animation only changes layer properties (offset, scale,
 * clip, alpha), so a frame of animation re-measures and recomposes nothing.
 */
@Composable
fun <T> StackHost(
    navigator: Navigator<T>,
    root: @Composable () -> Unit,
    content: @Composable (NavEntry<T>) -> Unit,
) {
    val entries = navigator.entries

    // The system back gesture drags the top screen along with the finger
    // (Android 14+); on older versions it simply closes it.
    PredictiveBackHandler(enabled = navigator.top != null) { swipe ->
        val entry = navigator.top ?: return@PredictiveBackHandler
        try {
            swipe.collect { event ->
                navigator.dragTo(entry, 1f - event.progress * BACK_TRAVEL)
            }
            navigator.pop(entry)
        } catch (cancelled: CancellationException) {
            navigator.restore(entry)
            throw cancelled
        }
    }

    // Black shows around a screen that has shrunk back behind a sheet.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Covered(by = entries.firstOrNull()) { root() }
        entries.forEachIndexed { index, entry ->
            key(entry) {
                Covered(by = entries.getOrNull(index + 1)) {
                    // Swallows touches so they can't reach the screen underneath.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {},
                    ) {
                        SheetLayer(entry, navigator) { content(entry) }
                    }
                }
            }
        }
    }
}

/** Applies what a screen does while [by], the sheet above it, arrives: shrink, round and dim. */
@Composable
private fun Covered(by: NavEntry<*>?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val above = by ?: return@graphicsLayer
                    val p = above.progress.floatValue.coerceIn(0f, 1f)
                    val scale = 1f - 0.08f * p
                    scaleX = scale
                    scaleY = scale
                    if (p > 0f) {
                        shape = RoundedCornerShape((28f * p).dp)
                        clip = true
                    }
                },
        ) {
            content()
        }
        if (by != null) {
            // A separate layer, so dimming never redraws the screen beneath it.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.45f * by.progress.floatValue.coerceIn(0f, 1f)
                    }
                    .background(Color.Black),
            )
        }
    }
}

@Composable
private fun <T> SheetLayer(entry: NavEntry<T>, navigator: Navigator<T>, content: @Composable () -> Unit) {
    var heightPx by remember { mutableFloatStateOf(1f) }
    // Leaves a strip of the shrunken screen beneath visible above the sheet.
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topGap = statusBar + 10.dp
    val density = LocalDensity.current
    val gapPx = with(density) { topGap.toPx() }
    val statusBarPx = with(density) { statusBar.toPx() }
    val scope = rememberCoroutineScope()
    val expansion = entry.expansion
    var expanding by remember { mutableStateOf<Job?>(null) }

    // A drag first grows or shrinks the sheet between its normal
    // height and full screen; only below normal height does it pull the sheet away.
    val drag = rememberDraggableState { delta ->
        val e = expansion.floatValue
        val atFullHeight = entry.progress.floatValue >= 1f
        if (atFullHeight && ((delta < 0f && e < 1f) || (delta > 0f && e > 0f))) {
            expanding?.cancel()
            expansion.floatValue = (e - delta / (gapPx * EXPAND_DRAG)).coerceIn(0f, 1f)
        } else {
            navigator.dragTo(entry, entry.progress.floatValue - delta / heightPx)
        }
    }

    /** Snaps the sheet to normal height or full screen after a drag ending at [velocity] px/s down. */
    fun settleExpansion(velocity: Float) {
        val target = when {
            velocity < -800f -> 1f
            velocity > 800f -> 0f
            expansion.floatValue > 0.5f -> 1f
            else -> 0f
        }
        expanding?.cancel()
        expanding = scope.launch {
            animate(expansion.floatValue, target, animationSpec = SheetSpring) { value, _ ->
                expansion.floatValue = value.coerceIn(0f, 1f)
            }
        }
    }

    /** Closes the sheet or springs it back, after a drag ending at [velocity] px/s downward. */
    fun settle(velocity: Float) {
        if (entry.leaving) return
        if (entry.progress.floatValue >= 1f) {
            settleExpansion(velocity)
            return
        }
        // A flick down closes it from anywhere; otherwise it needs to have
        // been pulled a third of the way.
        val flungDown = velocity > 1_400f
        val flungUp = velocity < -1_400f
        val progressVelocity = -velocity / heightPx
        if (flungDown || (entry.progress.floatValue < SHEET_CLOSE_BELOW && !flungUp)) {
            navigator.pop(entry, progressVelocity)
        } else {
            navigator.restore(entry, progressVelocity)
        }
    }

    // Scrolling lists inside the sheet take drags for themselves, so a pull
    // that starts on one would never reach the sheet. This passes it on: once
    // the list is at its top, the rest of a downward drag moves the sheet.
    val pullThrough = remember(entry) {
        object : NestedScrollConnection {
            private val pulled get() = entry.progress.floatValue < 1f
            private val canExpand get() = entry.expansion.floatValue < 1f
            private val midExpansion get() = entry.expansion.floatValue in 0.001f..0.999f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // While the sheet is part-way down, dragging back up raises it
                // before the list is allowed to scroll; the sheet
                // then grows to full screen before the list scrolls.
                if (source != NestedScrollSource.UserInput || available.y >= 0f || !(pulled || canExpand)) return Offset.Zero
                drag.dispatchRawDelta(available.y)
                return Offset(0f, available.y)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                drag.dispatchRawDelta(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!pulled && !midExpansion) return Velocity.Zero
                settle(available.y)
                // Taken whole, so the list doesn't also fling.
                return available
            }
        }
    }

    // On a light page the dimmed screen behind sets the sheet apart. On a dark
    // page the sheet and the dimmed screen are both near black, so the sheet
    // gets a thin line round its top edge: a quiet one in dark mode, a full
    // one in black-and-white mode.
    val scheme = MaterialTheme.colorScheme
    val edge = when {
        LocalPalette.current.outlined -> scheme.onSurface
        scheme.background.luminance() < 0.5f -> scheme.outline
        else -> Color.Unspecified
    }
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            // The gap above the sheet shrinks to nothing as it expands. Read
            // while laying out, so expanding re-lays out but never recomposes.
            .layout { measurable, constraints ->
                val gap = (gapPx * (1f - expansion.floatValue)).roundToInt()
                val height = (constraints.maxHeight - gap).coerceAtLeast(0)
                val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, gap) }
            }
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationY = size.height * (1f - entry.progress.floatValue)
                // Square corners once it fills the screen, as a full page has.
                val corner = (28f * (1f - expansion.floatValue)).dp
                shape = RoundedCornerShape(topStart = corner, topEnd = corner)
                clip = true
            }
            .drawWithContent {
                drawContent()
                if (edge != Color.Unspecified) {
                    val corner = 28.dp.toPx() * (1f - expansion.floatValue)
                    val line = 1.dp.toPx()
                    // Taller than the sheet, so its bottom edge and lower corners fall off screen.
                    drawRoundRect(
                        color = edge,
                        topLeft = Offset(line / 2, line / 2),
                        size = Size(size.width - line, size.height + corner + line),
                        cornerRadius = CornerRadius(corner),
                        style = Stroke(line),
                    )
                }
            }
            .nestedScroll(pullThrough)
            .draggable(
                state = drag,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> settle(velocity) },
            ),
    ) {
        Column {
            // Expanded, the sheet's top is under the status bar: push the
            // grabber and content down below it.
            Spacer(
                Modifier.layout { measurable, constraints ->
                    val h = (statusBarPx * expansion.floatValue).roundToInt()
                    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                    layout(placeable.width, h) { placeable.place(0, 0) }
                },
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                // The grabber: says the sheet can be pulled down. As the sheet
                // is pulled it bends into a down arrow, which is full once
                // letting go would close the sheet.
                Grabber(pulled = { (1f - entry.progress.floatValue) / (1f - SHEET_CLOSE_BELOW) })
            }
            // The gap above already clears the status bar; stop the screen
            // inside from adding that space again.
            Box(
                Modifier
                    .weight(1f)
                    .consumeWindowInsets(WindowInsets.statusBars),
            ) {
                content()
            }
        }
    }
}
