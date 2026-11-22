package com.budgetapp.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// Shared by the half donuts on Home (spent vs left) and Budget (spent by category).

/** One band of the half donut, with softly rounded corners. Angles are clockwise from 3 o'clock. */
fun DrawScope.donutSegment(
    center: Offset,
    outer: Float,
    inner: Float,
    startDeg: Float,
    sweepDeg: Float,
    corner: Float,
    color: Color,
) {
    if (sweepDeg <= 0f) return
    // A thin sliver can't take the full corner, or the rounding would make it wider.
    val c = minOf(corner, inner * Math.toRadians(sweepDeg.toDouble()).toFloat() * 0.45f, (outer - inner) / 2f)
    // Rounded corners the robust way: draw the band shrunk by c on every side,
    // then outline it with a 2c-wide round-joined stroke, which grows it back
    // out with a radius-c curve at each corner.
    val ro = outer - c
    val ri = inner + c
    fun inset(r: Float) = Math.toDegrees(asin((c / r).toDouble())).toFloat()
    val mid = startDeg + sweepDeg / 2
    val oStart = minOf(startDeg + inset(ro), mid)
    val oEnd = maxOf(startDeg + sweepDeg - inset(ro), mid)
    val iStart = minOf(startDeg + inset(ri), mid)
    val iEnd = maxOf(startDeg + sweepDeg - inset(ri), mid)

    fun point(r: Float, deg: Float): Offset {
        val a = Math.toRadians(deg.toDouble())
        return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
    }
    val path = Path()
    val outerSteps = max(2, ((oEnd - oStart) / 2f).toInt())
    for (i in 0..outerSteps) {
        val p = point(ro, oStart + (oEnd - oStart) * i / outerSteps)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    val innerSteps = max(2, ((iEnd - iStart) / 2f).toInt())
    for (i in innerSteps downTo 0) {
        val p = point(ri, iStart + (iEnd - iStart) * i / innerSteps)
        path.lineTo(p.x, p.y)
    }
    path.close()
    // The fill and its outline overlap by half the stroke. Drawn see-through
    // one after the other, that overlap would show as a darker band inside the
    // part. So a faded part is drawn solid into its own layer, and the layer
    // as a whole is made see-through.
    val solid = color.copy(alpha = 1f)
    fun shape() {
        drawPath(path, solid)
        if (c > 0f) drawPath(path, solid, style = Stroke(width = 2 * c, join = StrokeJoin.Round))
    }
    if (color.alpha >= 1f) {
        shape()
    } else {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = color.alpha })
            shape()
            canvas.restore()
        }
    }
}
