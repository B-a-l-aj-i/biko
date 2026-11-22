@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.budgetapp.ui.pay

import com.budgetapp.ui.components.scrollFade
import com.budgetapp.ui.theme.PhosphorLight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.budgetapp.ui.components.Presence
import com.budgetapp.ui.components.RemoveBadge
import com.budgetapp.ui.components.RemoveMode
import com.budgetapp.ui.components.rememberPresences
import com.budgetapp.ui.components.rememberRemoveMode
import com.budgetapp.ui.components.shake
import com.budgetapp.data.CategoryBudget
import com.budgetapp.ui.components.glideToPlace
import com.budgetapp.data.categoryLabel

private val ChipShape = RoundedCornerShape(8.dp)
private val ChipHeight = 34.dp

/**
 * The category picker. Tapping a chip selects it. Pressing and holding one
 * switches to remove mode: every chip shakes and grows a small cross that
 * removes it. "Done", Back, or a tap on empty space ends remove mode.
 */
@Composable
fun CategoryChips(
    categories: List<CategoryBudget>,
    selected: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onRemove: (CategoryBudget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val removeMode = rememberRemoveMode(hasItems = categories.isNotEmpty())
    val removing = removeMode.active
    val chips = rememberPresences(categories) { it.name }
    val scroll = rememberScrollState()

    FlowRow(
        modifier = modifier
            .pointerInput(removing) {
                if (removing) detectTapGestures { removeMode.exit() }
            }
            .scrollFade(scroll)
            .verticalScroll(scroll)
            // Room for the crosses, which overhang the chips' corners.
            .padding(start = 6.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        chips.forEachIndexed { index, chip ->
            key(chip) {
                val item = chip.item
                CategoryChip(
                    label = categoryLabel(item.emoji, item.name),
                    selected = item.name == selected && !removing,
                    chip = chip,
                    removeMode = removeMode,
                    // Neighbours lean opposite ways, so the row doesn't sway as one.
                    lean = if (index % 2 == 0) 1f else -1f,
                    onClick = { if (!removing && !chip.leaving) onSelect(item.name) },
                    onRemove = { if (!chip.leaving) onRemove(item) },
                    // Once a removed chip has faded, the rest glide up to close the gap.
                    modifier = Modifier.glideToPlace(),
                )
            }
        }
        key("action") {
            if (removing) {
                ActionChip("Done", PhosphorLight.Check, Modifier.glideToPlace(), onClick = removeMode::exit)
            } else {
                ActionChip("New category", PhosphorLight.Plus, Modifier.glideToPlace(), onClick = onNew)
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    chip: Presence<CategoryBudget>,
    removeMode: RemoveMode,
    lean: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .shake(removeMode, lean)
            .graphicsLayer {
                // Arriving and leaving: fades while growing from, or shrinking to, a smaller size.
                val there = chip.amount.value.coerceIn(0f, 1.1f)
                alpha = there.coerceAtMost(1f)
                val scale = 0.6f + 0.4f * there
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(
            modifier = Modifier
                .height(ChipHeight)
                .clip(ChipShape)
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outline, ChipShape)
                .combinedClickable(onClick = onClick, onLongClick = removeMode::enter)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (removeMode.active && !chip.leaving) {
            RemoveBadge(
                label = label,
                onRemove = onRemove,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-11).dp, y = (-11).dp),
            )
        }
    }
}

/** The chip at the end of the list: "New category", or "Done" in remove mode. */
@Composable
private fun ActionChip(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(ChipHeight)
            .clip(ChipShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, ChipShape)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
