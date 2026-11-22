package com.budgetapp.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.budgetapp.ui.components.GRABBER_HEIGHT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How far the page must be pulled down before letting go shows another line. */
internal val PULL_THRESHOLD = 72.dp

/** The grabber's top sits this far above the page's top edge, so its point stays clear of the header. */
internal val PULL_GRABBER_LIFT = GRABBER_HEIGHT + 2.dp

/** The grabber is fully visible, still nearly flat, once the page is this far down. */
internal val PULL_GRABBER_SHOWN = 32.dp

/** The grabber stays unseen until the page is this far down, clear of the status bar. */
internal val PULL_GRABBER_START = 16.dp

/** The furthest the page follows a pull. */
internal val PULL_MAX = 180.dp

/** The page moves this much of the finger's distance, so the pull feels weighted. */
private const val PULL_RESISTANCE = 0.5f

/**
 * Pull down from the top and the whole page comes with the finger, then
 * springs back on release. There is nothing to load, since the data is on the
 * phone, so there is no spinner: a pull past the threshold calls [onRelease],
 * which shows another pace line.
 */
internal class PagePull(
    private val thresholdPx: Float,
    private val maxPx: Float,
    private val scope: CoroutineScope,
    private val onArmed: () -> Unit,
    private val onRelease: () -> Unit,
) : NestedScrollConnection {
    /** How far down the page is drawn, in pixels. */
    var offset by mutableFloatStateOf(0f)
        private set
    private var settle: Job? = null

    private fun moveTo(target: Float) {
        settle?.cancel()
        val to = target.coerceIn(0f, maxPx)
        if (offset < thresholdPx && to >= thresholdPx) onArmed()
        offset = to
    }

    // Dragging back up first returns the page, before the list scrolls.
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || offset <= 0f) return Offset.Zero
        val before = offset
        moveTo(offset + available.y)
        return Offset(0f, offset - before)
    }

    // What the list could not use, because it is already at its top, pulls the page.
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        moveTo(offset + available.y * PULL_RESISTANCE)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (offset <= 0f) return Velocity.Zero
        if (offset >= thresholdPx) onRelease()
        settle = scope.launch {
            animate(
                initialValue = offset,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> offset = value.coerceAtLeast(0f) }
        }
        // The page takes the fling, so the list doesn't also coast.
        return available
    }
}
