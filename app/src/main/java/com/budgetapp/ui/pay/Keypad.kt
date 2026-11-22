package com.budgetapp.ui.pay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetapp.ui.theme.PhosphorLight
import com.budgetapp.ui.theme.cardBorder
import com.budgetapp.data.BACKSPACE

/** How long the bottom section waits for the sheet to rise before it starts to arrive. */
internal const val KEYPAD_DELAY_MS = 280L

internal const val KEYPAD_RISE_MS = 420

@Composable
internal fun Keypad(onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", BACKSPACE),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Surface(
                        onClick = { onKey(key) },
                        shape = RoundedCornerShape(14.dp),
                        border = cardBorder(),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == BACKSPACE) {
                                Icon(PhosphorLight.Backspace, contentDescription = "Delete")
                            } else {
                                Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
