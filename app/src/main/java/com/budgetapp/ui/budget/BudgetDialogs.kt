package com.budgetapp.ui.budget

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.budgetapp.data.categoryRoom
import com.budgetapp.data.percentText
import com.budgetapp.data.percentToRupees
import com.budgetapp.ui.components.AppDialog
import com.budgetapp.ui.components.BudgetAmountField
import com.budgetapp.ui.components.BudgetShareHint
import com.budgetapp.ui.components.SegmentedToggle
import com.budgetapp.data.cleanAmount
import com.budgetapp.data.cleanPercent
import com.budgetapp.data.CategoryBudget

@Composable
internal fun OverallBudgetDialog(initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (initial > 0) initial.toString() else "") }
    val amount = text.toIntOrNull() ?: 0

    AppDialog(
        title = "Monthly budget",
        confirmLabel = "Save",
        confirmEnabled = amount > 0,
        onConfirm = { onSave(amount) },
        onDismiss = onDismiss,
    ) {
        BudgetAmountField(text, onChange = { text = cleanAmount(it) }, percent = false, autoFocus = true)
        Text(
            "Each category keeps its percentage, so its amount changes with this.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Sets one category's budget as rupees or as a percentage of [monthlyBudget].
 * [room] is the most it may take (see [categoryRoom]): lowering is always allowed.
 */
@Composable
internal fun CategoryBudgetDialog(
    category: CategoryBudget,
    monthlyBudget: Int,
    room: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var byPercent by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(if (category.budget > 0) category.budget.toString() else "") }

    val amount = if (byPercent) {
        percentToRupees(text, monthlyBudget)
    } else {
        text.toIntOrNull() ?: 0
    }
    // Lowering is always allowed, even if the categories already overshoot.
    val tooMuch = amount > room

    fun switchTo(percent: Boolean) {
        if (percent == byPercent) return
        text = when {
            amount == 0 -> ""
            percent -> percentText(amount, monthlyBudget).removeSuffix("%")
            else -> amount.toString()
        }
        byPercent = percent
    }

    AppDialog(
        title = "${category.emoji} ${category.name} budget".trim(),
        confirmLabel = "Save",
        confirmEnabled = !tooMuch,
        onConfirm = { onSave(amount) },
        onDismiss = onDismiss,
    ) {
        SegmentedToggle(
            options = listOf("Amount", "Percentage"),
            selected = if (byPercent) 1 else 0,
            onSelect = { switchTo(it == 1) },
        )
        BudgetAmountField(
            text = text,
            onChange = { text = if (byPercent) cleanPercent(it) else cleanAmount(it) },
            percent = byPercent,
            autoFocus = true,
        )
        BudgetShareHint(amount, monthlyBudget, room, byPercent)
    }
}
