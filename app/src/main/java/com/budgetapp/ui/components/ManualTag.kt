package com.budgetapp.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Small "Manual" label beside a payment the user added by hand, not found in a bank alert. */
@Composable
fun ManualTag(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        "Manual",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
