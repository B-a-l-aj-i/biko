package com.budgetapp.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Stable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetapp.data.formatRupees
import com.budgetapp.data.percentText
import com.budgetapp.ui.theme.LocalStatusColors
import com.budgetapp.data.budgetInputRupees
import com.budgetapp.data.cleanPercent
import com.budgetapp.data.cleanAmount
import com.budgetapp.data.budgetInputText

/** A large figure field for rupees, or for a percentage when [percent] is set. */
@Composable
fun BudgetAmountField(
    text: String,
    onChange: (String) -> Unit,
    percent: Boolean,
    modifier: Modifier = Modifier,
    /** Takes the keyboard as soon as it appears. */
    autoFocus: Boolean = false,
    label: String? = null,
) {
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }

    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = label?.let { { Text(it) } },
        prefix = if (percent) null else ({ Text("₹") }),
        suffix = if (percent) ({ Text("%") }) else null,
        singleLine = true,
        shape = ModalFieldShape,
        textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (percent) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
}

/**
 * Two lines under a budget field: what share of [monthlyBudget] the [amount]
 * is, and how much of the monthly budget is still not split into category
 * budgets (the same words the Budget screen uses). [room] is the most this figure may be; going
 * past it turns the second line into a warning.
 */
@Composable
fun BudgetShareHint(amount: Int, monthlyBudget: Int, room: Int, byPercent: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tooMuch = amount > room
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (byPercent) {
                "${formatRupees(amount)} of the ${formatRupees(monthlyBudget)} monthly budget"
            } else {
                "${percentText(amount, monthlyBudget)} of the ${formatRupees(monthlyBudget)} monthly budget"
            },
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        Text(
            if (tooMuch) {
                "Only ${formatRupees(room)} (${percentText(room, monthlyBudget)}) is not split yet. " +
                    "Lower another category or raise the monthly budget."
            } else {
                "After this, ${formatRupees(room - amount)} " +
                    "(${percentText(room - amount, monthlyBudget)}) is still not split"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (tooMuch) LocalStatusColors.current.danger else muted,
        )
    }
}

/**
 * What a budget field holds: whole rupees, or a percentage of [monthlyBudget],
 * as the user typed it. Switching between the two keeps the same amount.
 */
@Stable
class BudgetInputState internal constructor(initial: String, private val monthlyBudget: Int) {
    var text by mutableStateOf(initial)
        private set
    var byPercent by mutableStateOf(false)
        private set

    /** What the field means, in whole rupees. */
    val rupees: Int get() = budgetInputRupees(text, byPercent, monthlyBudget)

    /** Takes what was typed, keeping only what fits a rupee amount or a percentage. */
    fun type(typed: String) {
        text = if (byPercent) cleanPercent(typed) else cleanAmount(typed)
    }

    fun switchTo(percent: Boolean) {
        if (percent == byPercent) return
        text = budgetInputText(rupees, percent, monthlyBudget)
        byPercent = percent
    }
}

@Composable
fun rememberBudgetInput(initial: String, monthlyBudget: Int): BudgetInputState =
    remember { BudgetInputState(initial, monthlyBudget) }
