package com.budgetapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetapp.ui.components.ManualTag
import com.budgetapp.ui.theme.LocalStatusColors
import com.budgetapp.ui.theme.PhosphorLight
import com.budgetapp.ui.theme.cardBorder
import com.budgetapp.ui.theme.cardOutline

@Composable
internal fun PendingCard(
    pending: List<PendingPayment>,
    money: (Long) -> String,
    onClick: () -> Unit,
) {
    val status = LocalStatusColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(status.warningContainer)
            .cardOutline(CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorLight.Tag, contentDescription = null, tint = status.onWarningContainer)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (pending.size == 1) "1 payment to confirm" else "${pending.size} payments to confirm",
                fontWeight = FontWeight.Normal,
                color = status.onWarningContainer,
            )
            Text(
                pending.take(2).joinToString(" · ") { "${money(it.amountPaise)} to ${it.payee}" },
                style = MaterialTheme.typography.bodySmall,
                color = status.onWarningContainer,
                // Amounts and names share this line, so the whole line blurs.
                modifier = Modifier.amountBlur(),
            )
        }
        Icon(PhosphorLight.CaretRight, contentDescription = null, tint = status.onWarningContainer)
    }
}

@Composable
internal fun ActionCard(
    label: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(onClick = onClick, shape = CardShape, border = cardBorder(), modifier = modifier) {
        // The icon on top, then the label and a one-line description under it.
        Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(20.dp))
            // Kept on one line each so the cards in the row stay the same height.
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Normal, maxLines = 1, softWrap = false)
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun RecentCard(recent: List<Transaction>, money: (Long) -> String, onOpen: (Long) -> Unit) {
    Surface(shape = CardShape, border = cardBorder(), modifier = Modifier.fillMaxWidth()) {
        // No side padding here: each row pads itself, so its press highlight
        // spans the full card and the card's shape rounds it at the ends.
        Column {
            recent.forEachIndexed { index, txn ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(txn.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                txn.payee,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (txn.manual) {
                                Spacer(Modifier.width(8.dp))
                                ManualTag()
                            }
                        }
                        txn.note?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row {
                            val small = MaterialTheme.typography.bodySmall
                            val tint = MaterialTheme.colorScheme.onSurfaceVariant
                            val share = txn.totalPaidPaise
                            Text(
                                "${txn.category} · ${txn.time}" + if (share != null) " · my share of " else "",
                                style = small,
                                color = tint,
                            )
                            // Its own text, so only the figure blurs when amounts are hidden.
                            if (share != null) {
                                Text(money(share), style = small, color = tint, modifier = Modifier.amountBlur())
                            }
                        }
                    }
                    Text(
                        "−${money(txn.amountPaise)}",
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.amountBlur(),
                    )
                }
            }
        }
    }
}
