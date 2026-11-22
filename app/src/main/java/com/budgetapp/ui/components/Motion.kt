package com.budgetapp.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch

/** Scales the element's height by [amount] and fades it to match, for rows that arrive or leave. */
internal fun Modifier.collapsedBy(amount: () -> Float): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height * amount().coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, height) { placeable.place(0, 0) }
    }
    .graphicsLayer { alpha = amount().coerceIn(0f, 1f) }

/**
 * Makes an element slide to its new spot whenever the layout moves it, rather
 * than jump there. It is laid out at the new spot at once and drawn back at the
 * old one, then the difference is sprung down to nothing.
 */
internal fun Modifier.glideToPlace(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<IntOffset?>(null) }
    var position by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val goal = target ?: return@offset IntOffset.Zero
            // First placement: start where it is, with nothing to animate.
            val current = position ?: Animatable(goal, IntOffset.VectorConverter).also { position = it }
            if (current.targetValue != goal) {
                scope.launch {
                    current.animateTo(goal, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
                }
            }
            current.value - goal
        }
}
