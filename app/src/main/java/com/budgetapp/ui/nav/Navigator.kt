package com.budgetapp.ui.nav

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Springs rather than fixed durations: they carry the finger's speed into the
// animation and can be interrupted mid-flight without a jump, which is most of
// what makes iOS motion feel smooth.
internal val SheetSpring = spring<Float>(dampingRatio = 0.92f, stiffness = 330f)

/**
 * One screen above the root. Every screen is a sheet: it rises from the
 * bottom as a card while the screen beneath shrinks back, and a pull down
 * closes it.
 */
@Stable
class NavEntry<T> internal constructor(
    val key: T,
) {
    /** 0 at the sheet's normal height, 1 filling the screen; a swipe up grows it. */
    val expansion = mutableFloatStateOf(0f)

    /**
     * 0 is fully away, 1 is fully shown. Springs may overshoot slightly past 1.
     * A plain value, written directly by drags, so that every finger movement
     * lands at once and in order; animations write to it frame by frame.
     */
    val progress = mutableFloatStateOf(0f)

    /** The animation currently moving [progress], if any. */
    internal var motion: Job? = null

    /** Set once it has started leaving, so it can't be closed twice. */
    var leaving by mutableStateOf(false)
        internal set
}

@Stable
class Navigator<T> internal constructor(private val scope: CoroutineScope) {

    /** Screens above the root, bottom first. Includes any still animating out. */
    val entries = mutableStateListOf<NavEntry<T>>()

    val top: NavEntry<T>?
        get() = entries.lastOrNull { !it.leaving }

    /** Opens [key] as a sheet. */
    fun push(key: T) {
        val entry = NavEntry(key)
        entries += entry
        // Let the new screen compose and draw off-screen first; starting the
        // animation on the same frame as that work makes the first frames stutter.
        move(entry, target = 1f, velocity = 0f, framesToWait = 2) {}
    }

    /** Animates [entry] away and removes it. [velocity] is in progress units per second. */
    fun pop(entry: NavEntry<T>? = top, velocity: Float = 0f) {
        if (entry == null || entry.leaving) return
        entry.leaving = true
        move(entry, target = 0f, velocity = velocity) { entries.remove(entry) }
    }

    /** Brings [entry] back to fully shown, after a drag or back swipe was abandoned. */
    fun restore(entry: NavEntry<T>, velocity: Float = 0f) {
        if (entry.leaving) return
        move(entry, target = 1f, velocity = velocity) {}
    }

    /** Puts [entry] exactly at [progress], for following a finger. Ignored once it is leaving. */
    internal fun dragTo(entry: NavEntry<T>, progress: Float) {
        if (entry.leaving) return
        entry.motion?.cancel()
        entry.progress.floatValue = progress.coerceIn(0f, 1f)
    }

    /** Springs [entry] to [target], replacing any animation already running on it. */
    private fun move(
        entry: NavEntry<T>,
        target: Float,
        velocity: Float,
        framesToWait: Int = 0,
        onArrived: () -> Unit,
    ) {
        entry.motion?.cancel()
        entry.motion = scope.launch {
            repeat(framesToWait) { withFrameNanos { } }
            animate(
                initialValue = entry.progress.floatValue,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = SheetSpring,
            ) { value, _ -> entry.progress.floatValue = value }
            onArrived()
        }
    }
}

@Composable
fun <T> rememberNavigator(): Navigator<T> {
    val scope = rememberCoroutineScope()
    return remember { Navigator(scope) }
}
