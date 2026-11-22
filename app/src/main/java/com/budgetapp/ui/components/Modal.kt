package com.budgetapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.budgetapp.data.percentText
import com.budgetapp.data.formatRupees
import com.budgetapp.ui.theme.LocalPalette
import com.budgetapp.ui.theme.LocalStatusColors
import com.budgetapp.ui.theme.cardBorder
import com.budgetapp.ui.theme.cardOutline

/** Corner radius and inner spacing shared by every pop-up card in the app. */
val ModalShape = RoundedCornerShape(28.dp)
val ModalFieldShape = RoundedCornerShape(14.dp)

/**
 * The app's pop-up: a title, the [content], and Cancel beside one main button.
 * Tighter than the stock Material dialog, which leaves wide empty margins
 * around short forms.
 */
@Composable
fun AppDialog(
    title: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // Lets the card choose its own width instead of the platform's narrow default.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = ModalShape,
            color = MaterialTheme.colorScheme.surface,
            border = cardBorder(),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
                ModalButtons(
                    confirmLabel = confirmLabel,
                    confirmEnabled = confirmEnabled,
                    onConfirm = onConfirm,
                    onCancel = onDismiss,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Cancel and the main action, side by side at equal width. */
@Composable
fun ModalButtons(
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = cardBorder(),
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
        ) { Text("Cancel") }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
        ) { Text(confirmLabel) }
    }
}

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

/** Digits only; eight of them keeps the figure inside an Int. */
fun cleanAmount(raw: String): String = raw.filter(Char::isDigit).take(8)

/** Up to three digits and one decimal place, e.g. "12.5". */
fun cleanPercent(raw: String): String {
    val kept = raw.filter { it.isDigit() || it == '.' }
    val whole = kept.substringBefore('.').take(3)
    return if ('.' in kept) whole + "." + kept.substringAfter('.').filter(Char::isDigit).take(1) else whole
}
