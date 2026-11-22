package com.budgetapp.ui.components

import com.budgetapp.ui.theme.PhosphorLight
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.budgetapp.data.Graph
import com.budgetapp.data.formatPaise
import kotlinx.coroutines.launch
import com.budgetapp.data.CategoryBudget

// Everything the "press and hold to remove" behaviour needs, shared by the
// category chips on the payment sheet and the category rows on the budget page.

private const val SHAKE_HALF_PERIOD_MS = 170

/**
 * Whether a list is in remove mode, as on an iPhone home screen: its items
 * shake and each shows a small cross.
 */
@Stable
class RemoveMode internal constructor(private val haptics: HapticFeedback) {
    var active by mutableStateOf(false)
        private set

    /** The shared swing between -1 and 1 that items tilt with; null when not in remove mode. */
    internal var swing by mutableStateOf<State<Float>?>(null)

    /** Call from an item's long press. */
    fun enter() {
        if (active) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        active = true
    }

    fun exit() {
        active = false
    }
}

/** Back leaves remove mode, and it ends by itself once [hasItems] is false. */
@Composable
fun rememberRemoveMode(hasItems: Boolean): RemoveMode {
    val haptics = LocalHapticFeedback.current
    val mode = remember { RemoveMode(haptics) }

    BackHandler(enabled = mode.active) { mode.exit() }
    LaunchedEffect(hasItems) { if (!hasItems) mode.exit() }

    // Runs only in remove mode. Items read it while drawing, so the shake
    // costs no recomposition.
    mode.swing = if (mode.active) {
        rememberInfiniteTransition(label = "removeShake").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(SHAKE_HALF_PERIOD_MS, easing = EaseInOutSine),
                RepeatMode.Reverse,
            ),
            label = "removeSwing",
        )
    } else {
        null
    }
    return mode
}

/**
 * Tilts the element gently while [mode] is active. [lean] is 1 or -1, so that
 * neighbours can sway opposite ways. Wide elements need fewer [degrees], since
 * the same angle moves their ends further.
 */
fun Modifier.shake(mode: RemoveMode, lean: Float, degrees: Float = 0.8f): Modifier = graphicsLayer {
    rotationZ = (mode.swing?.value ?: 0f) * degrees * lean
}

/** The small cross shown on an item in remove mode. Pops in with a slight bounce. */
@Composable
fun RemoveBadge(label: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
    // The tap target is larger than the dot that is drawn.
    Box(
        modifier = modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(CircleShape)
            .clickable(onClickLabel = "Remove $label", onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .background(MaterialTheme.colorScheme.onSurface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PhosphorLight.X,
                contentDescription = "Remove $label",
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/**
 * An item on screen. Outlives its data briefly, so that a removed item can
 * fade away where it stood instead of vanishing.
 */
@Stable
class Presence<T> internal constructor(item: T, shown: Boolean) {
    var item by mutableStateOf(item)
        internal set

    /** 0 is absent, 1 is fully there. */
    val amount = Animatable(if (shown) 1f else 0f)

    var leaving by mutableStateOf(false)
        internal set
}

/**
 * What to draw for [items]: the same list, except that a removed item stays
 * until it has faded out and a new one starts absent and grows in. [key]
 * tells items apart.
 */
@Composable
fun <T> rememberPresences(items: List<T>, key: (T) -> Any): List<Presence<T>> {
    val scope = rememberCoroutineScope()
    val shown = remember { mutableStateListOf<Presence<T>>().apply { addAll(items.map { Presence(it, shown = true) }) } }
    LaunchedEffect(items) {
        val keys = items.map(key).toSet()
        items.forEach { item ->
            val existing = shown.firstOrNull { !it.leaving && key(it.item) == key(item) }
            if (existing != null) {
                existing.item = item
            } else {
                val added = Presence(item, shown = false)
                shown += added
                // On its own scope: this effect restarts whenever the list
                // changes, which would otherwise cut the animation short.
                scope.launch { added.amount.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 380f)) }
            }
        }
        shown.filter { !it.leaving && key(it.item) !in keys }.forEach { gone ->
            gone.leaving = true
            scope.launch {
                gone.amount.animateTo(0f, tween(190, easing = FastOutLinearInEasing))
                shown.remove(gone)
            }
        }
    }
    return shown
}

/**
 * Returns the action that removes a category. One with spending this month
 * asks first; others go at once. Payments already tagged with it keep the
 * name. [onRemoved] hears the name as soon as removal is decided.
 */
@Composable
fun rememberCategoryRemover(onRemoved: (name: String) -> Unit = {}): (CategoryBudget) -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnRemoved by rememberUpdatedState(onRemoved)
    var asking by remember { mutableStateOf<CategoryBudget?>(null) }

    val remove = remember {
        { item: CategoryBudget ->
            currentOnRemoved(item.name)
            scope.launch { Graph.repository.removeCategory(item.name) }
            Unit
        }
    }

    asking?.let { item ->
        AppDialog(
            title = "Remove ${item.name}?",
            confirmLabel = "Remove",
            confirmEnabled = true,
            onConfirm = {
                asking = null
                remove(item)
            },
            onDismiss = { asking = null },
        ) {
            Text(
                "You've spent ${formatPaise(item.spentPaise)} in it this month. Those payments " +
                    "stay in your history, but its budget is removed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    return remember { { item: CategoryBudget -> if (item.spentPaise > 0) asking = item else remove(item) } }
}
