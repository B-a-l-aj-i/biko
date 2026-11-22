package com.budgetapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Width of the grabber, and its thickness: a 40 by 5 bar when at rest. */
private val GRABBER_WIDTH = 40.dp
private val GRABBER_THICKNESS = 5.dp

/** How far the middle of the bar sinks below its ends when fully pulled. */
private val GRABBER_DIP = 9.dp

/** Height the grabber takes up: its thickness plus room for the arrow's point. */
val GRABBER_HEIGHT = GRABBER_THICKNESS + GRABBER_DIP

/**
 * The drag handle shared by the sheets and the home screen: a short rounded
 * bar that says "this can be pulled down". While it is being pulled it bends
 * into an arrow pointing down, the way it is going.
 *
 * [pulled] runs from 0 (at rest, a flat bar) to 1 (pulled far enough that
 * letting go does something, a full arrow). It is read while drawing, so a
 * drag redraws the grabber without recomposing anything.
 */
@Composable
fun Grabber(pulled: () -> Float, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outline
    Canvas(modifier.size(width = GRABBER_WIDTH, height = GRABBER_HEIGHT)) {
        val thickness = GRABBER_THICKNESS.toPx()
        val dip = GRABBER_DIP.toPx() * pulled().coerceIn(0f, 1f)
        // Bends about its own middle, so a flat bar sits centred in its box.
        val ends = size.height / 2 - dip / 2
        val point = size.height / 2 + dip / 2
        val arrow = Path().apply {
            moveTo(thickness / 2, ends)
            lineTo(size.width / 2, point)
            lineTo(size.width - thickness / 2, ends)
        }
        drawPath(arrow, color, style = Stroke(width = thickness, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
