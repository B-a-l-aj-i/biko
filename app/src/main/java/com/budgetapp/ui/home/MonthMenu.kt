package com.budgetapp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.budgetapp.ui.theme.PhosphorLight
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

private val monthShortWithYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

private val MONTH_MENU_WIDTH = 128.dp

/**
 * The month pill's list: a small "Month" heading, then one short row per month
 * ("Nov", or "Dec 2025" outside the current year), oldest at the top and the
 * current month at the bottom, with a tick on the month Home is showing.
 * Narrow on purpose: Material's menu rows are at least 112dp wide and pad
 * generously, so these rows are drawn by hand. Its background is the card
 * colour (white in the light look): Material tints a menu with its surface
 * tint by elevation, which is what made it grey, so that tint is turned off.
 */
@Composable
internal fun MonthMenu(
    expanded: Boolean,
    months: List<YearMonth>,
    shown: YearMonth?,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = months.firstOrNull() ?: return
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surfaceContainer = MaterialTheme.colorScheme.surface,
            // The tint is laid over the surface at the menu's elevation; tinting white
            // with white keeps it white (a transparent tint still darkens it).
            surfaceTint = MaterialTheme.colorScheme.surface,
        ),
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(14.dp)),
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            offset = DpOffset(0.dp, 6.dp),
            modifier = Modifier.width(MONTH_MENU_WIDTH),
        ) {
            Text(
                "Month",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp),
            )
            months.asReversed().forEach { month ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(month) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        month.format(if (month.year == current.year) monthShort else monthShortWithYear),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (month == shown) {
                        Icon(PhosphorLight.Check, contentDescription = "Shown now", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
