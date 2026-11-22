package com.budgetapp.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetapp.data.rupeesToPaise
import com.budgetapp.ui.theme.LocalStatusColors

/**
 * The month as a sum, like on paper, with no labels: the layout says it.
 *
 *     ₹5,000
 *   − ₹4,201
 *     ──────
 *      ₹799
 *
 * The two figures above the line are right-aligned on one edge, with
 * same-width digits, so the places line up. The minus sits close to the left
 * of the figure it takes away, and the line is only as wide as the widest
 * figure. The answer is the same size as the rest and centred under the line.
 */
@Composable
internal fun CentreSum(
    state: HomeUiState,
    money: (Long) -> String,
    focus: DonutPart?,
    /** Pulls to refresh so far; each new one writes the sum out again, line by line. */
    replays: Int,
    modifier: Modifier = Modifier,
) {
    if (state.monthlyBudget <= 0) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Spent", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(state.spentPaise), style = sumStyle(RESULT_SIZE), modifier = Modifier.amountBlur())
        }
        return
    }
    // A picked-out part keeps its own line clear and fades the others.
    val budgetAlpha by animateFloatAsState(if (focus == null) 1f else FADED, label = "budgetRow")
    val spentAlpha by animateFloatAsState(if (focus == DonutPart.LEFT) FADED else 1f, label = "spentRow")
    val leftAlpha by animateFloatAsState(if (focus == DonutPart.SPENT) FADED else 1f, label = "leftRow")
    // Over budget the result is negative and says so with its own minus, in red.
    val resultColor = if (state.leftPaise < 0) LocalStatusColors.current.danger else MaterialTheme.colorScheme.onSurface

    // After a pull to refresh the sum is written out again in order: the
    // budget, then what was spent, then the line and what is left. Only the
    // true figures are ever shown; nothing counts up through wrong ones.
    val written = remember { Animatable(1f) }
    LaunchedEffect(replays) {
        if (replays == 0) return@LaunchedEffect
        written.snapTo(0f)
        written.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))
    }
    // Line [index] of three: how far it has appeared, read while drawing only.
    fun Modifier.line(index: Int, alpha: () -> Float) = graphicsLayer {
        val shown = (written.value * 3f - index).coerceIn(0f, 1f)
        this.alpha = alpha() * shown
        translationY = (1f - shown) * 6.dp.toPx()
        // Fade each glyph in place rather than through an offscreen layer: a
        // layer is cut to the row's bounds, which would slice off the minus
        // that hangs outside them whenever the row is faded.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

    Column(modifier.width(IntrinsicSize.Max)) {
        SumRow("", money(rupeesToPaise(state.monthlyBudget)), sumStyle(LINE_SIZE), Modifier.line(0) { budgetAlpha })
        SumRow("−", money(state.spentPaise), sumStyle(LINE_SIZE), Modifier.line(1) { spentAlpha })
        Column(Modifier.line(2) { leftAlpha }) {
            Box(
                Modifier
                    .padding(top = 6.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
            // The answer is the same size as the figures above it and sits
            // centred under the line.
            // Just the figure: over budget it is negative, in red. A word after it
            // crowded the sum (the user's choice to keep it out).
            SumRow("", money(state.leftPaise), sumStyle(LINE_SIZE, resultColor), arrangement = Arrangement.Center)
        }
    }
}

/** Space between the minus and the figure it belongs to. */
private val SUM_SIGN_GAP = 6.dp

/** Every figure in the sum, the answer included. */
private val LINE_SIZE = 20.sp

/** The lone spent figure shown when there is no budget to subtract from. */
private val RESULT_SIZE = 32.sp

/** One weight for every figure in the sum. */
@Composable
private fun sumStyle(size: TextUnit, color: Color = MaterialTheme.colorScheme.onSurface) = TextStyle(
    fontSize = size,
    lineHeight = size * 1.25f,
    fontWeight = FontWeight.Normal,
    color = color,
    // Same-width digits, so units sit under units and thousands under thousands.
    fontFeatureSettings = "tnum",
)

@Composable
private fun SumRow(
    sign: String,
    amount: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    /** Where the figure sits across the sum: the right edge for the lines being subtracted. */
    arrangement: Arrangement.Horizontal = Arrangement.End,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = arrangement) {
        if (sign.isNotEmpty()) {
            // Hangs just left of its figure and takes no width, so the sum is
            // only as wide as its figures and the line under it matches them.
            Text(
                sign,
                style = style,
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(0, placeable.height) {
                        placeable.place(-placeable.width - SUM_SIGN_GAP.roundToPx(), 0)
                    }
                },
            )
        }
        Text(
            amount,
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.amountBlur(),
        )
    }
}
