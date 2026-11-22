package com.budgetapp.ui.pay

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AmountDisplay(text: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Only the digits take up width, so it is the number itself that sits
        // in the centre of the screen. The rupee sign and the caret hang off its
        // edges without pushing it sideways.
        Box {
            val empty = text.isEmpty()
            val color = if (empty) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onBackground
            Text(if (empty) "0" else text, fontSize = 60.sp, fontWeight = FontWeight.Medium, color = color)
            Text(
                "₹",
                fontSize = 26.sp,
                color = color,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 8.dp)
                    .hangingLeft(),
            )
            // Caret sits before the "0" placeholder when empty, after the digits once typing.
            BlinkingCaret(
                Modifier
                    .align(if (empty) Alignment.CenterStart else Alignment.CenterEnd)
                    .straddlingEdge(),
            )
        }
    }
}

/** Text-field style caret so the keypad-driven amount reads as the active input. */
@Composable
private fun BlinkingCaret(modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
        ),
        label = "caretAlpha",
    )
    Box(
        modifier
            .padding(horizontal = 2.dp)
            .width(2.dp)
            .height(52.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)),
    )
}
