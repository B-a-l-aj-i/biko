package com.budgetapp.ui.home

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

internal const val HIDDEN_AMOUNT = "₹ ••••"

/** Whether the eye in the header has hidden the amounts. */
internal val LocalAmountsHidden = compositionLocalOf { false }

/** Blurring needs Android 12; older phones swap hidden amounts for dots instead. */
internal val CAN_BLUR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Enough to melt the largest figure on the page into a smudge. */
private val AMOUNT_BLUR = 14.dp

/**
 * For any text that shows an amount: when the eye hides amounts the text blurs
 * out of sight, and blurs back into focus when they are shown again. A screen
 * reader is told the amount is hidden rather than reading it out.
 */
@Composable
internal fun Modifier.amountBlur(): Modifier {
    val hidden = LocalAmountsHidden.current
    val radius by animateDpAsState(
        if (hidden && CAN_BLUR) AMOUNT_BLUR else 0.dp,
        tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "amountBlur",
    )
    val blurred = if (radius > 0.dp) blur(radius, BlurredEdgeTreatment.Unbounded) else this
    return if (hidden) blurred.clearAndSetSemantics { contentDescription = "Amount hidden" } else blurred
}
