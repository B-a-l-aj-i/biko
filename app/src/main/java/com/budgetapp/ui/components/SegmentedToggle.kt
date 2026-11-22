package com.budgetapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.budgetapp.ui.theme.LocalPalette
import com.budgetapp.ui.theme.cardOutline

/** A two-or-more way switch whose highlight slides to the chosen option. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "segment",
    )
    val gap = 3.dp
    // Black and white has no grey to tint the chosen option with: it is filled
    // solid instead, with its label in the page's colour.
    val solid = LocalPalette.current.outlined
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .cardOutline(RoundedCornerShape(12.dp)),
    ) {
        // The highlight: one option wide, moved by a layer offset only.
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(1f / options.size)
                .padding(gap)
                .graphicsLayer { translationX = position.value * (size.width + 2 * gap.toPx()) }
                .background(if (solid) ink else ink.copy(alpha = 0.14f), RoundedCornerShape(9.dp)),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (index == selected) {
                            if (solid) MaterialTheme.colorScheme.surface else ink
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
