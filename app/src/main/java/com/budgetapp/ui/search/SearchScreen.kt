package com.budgetapp.ui.search

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.rememberLazyListState
import com.budgetapp.ui.components.scrollFade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.budgetapp.data.Payment
import com.budgetapp.data.isManual
import com.budgetapp.data.noteLine
import com.budgetapp.data.title
import com.budgetapp.ui.components.ManualTag
import com.budgetapp.data.monthBounds
import com.budgetapp.data.searchPayments
import com.budgetapp.data.formatPaise
import com.budgetapp.ui.theme.PhosphorLight
import com.budgetapp.ui.theme.cardOutline
import java.time.Instant
import java.time.ZonedDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthYearFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/**
 * Finds a month's payments by payee, category, note, UPI ID or exact amount.
 * Opens with every payment of the month listed, newest first. [month] is the
 * one picked from Home's month pill; null is the current month.
 */
@Composable
fun SearchScreen(
    payments: List<Payment>,
    month: YearMonth? = null,
    /** Opens a payment to edit; takes its id. */
    onOpen: (Long) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // "Now" is read on each recomposition, so a month that turns while the
    // screen is open is picked up as soon as anything changes.
    val now = ZonedDateTime.now()
    val shown = month ?: YearMonth.from(now)
    val isCurrent = shown == YearMonth.from(now)
    // "this month", or "in October 2026" for one picked from the pill.
    val inMonth = if (isCurrent) "this month" else "in " + shown.format(monthYearFormat)
    val (from, until) = monthBounds(shown, now.zone)
    val results = remember(payments, query, from) { searchPayments(payments, query, from, until) }
    val total = results.sumOf { it.amountPaise }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                // Not the keyboard: padding the whole page by it would re-lay out
                // the page on every frame of the keyboard's slide, which the
                // system runs on this app's UI thread, so any slow frame makes
                // the keyboard itself stutter. The list makes room for it instead.
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
        ) {
            SearchField(query, onChange = { query = it }, onClose = onClose)
            Text(
                summary(results.size, total, if (query.isBlank()) inMonth else null),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (results.isEmpty()) {
                Text(
                    when {
                        query.isNotBlank() -> "No payments $inMonth match “${query.trim()}”"
                        isCurrent -> "No payments yet this month"
                        else -> "No payments $inMonth"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                )
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.scrollFade(listState),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    itemsIndexed(results, key = { _, p -> p.id }) { index, payment ->
                        ResultRow(
                            payment = payment,
                            first = index == 0,
                            last = index == results.lastIndex,
                            now = now,
                            onClick = { onOpen(payment.id) },
                        )
                    }
                    // Room to scroll the last rows above the keyboard. Only this
                    // spacer follows the keyboard's slide, and only while it is
                    // on screen, so each frame of the slide costs almost nothing.
                    item(key = "keyboard") {
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime.exclude(WindowInsets.navigationBars)))
                    }
                }
            }
        }
    }
}

/** e.g. "3 payments · ₹2,201.50": the count and the exact sum of what is listed. */
private fun summary(count: Int, totalPaise: Long, inMonth: String?): String {
    val payments = if (count == 1) "1 payment" else "$count payments"
    val scope = inMonth?.let { " $it" }.orEmpty()
    return "$payments$scope · ${formatPaise(totalPaise)} spent"
}

@Composable
// Takes no focus on its own: the sheet opens on the list, and the keyboard
// rises only when the user taps the search box.
private fun SearchField(query: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(PhosphorLight.ArrowLeft, contentDescription = "Back")
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .cardOutline(CircleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PhosphorLight.MagnifyingGlass, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Payee, category, note or amount", style = MaterialTheme.typography.bodyLarge, color = muted)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.merge(
                        TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    PhosphorLight.X,
                    contentDescription = "Clear search",
                    tint = muted,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onChange("") },
                )
            }
        }
    }
}

@Composable
private fun ResultRow(payment: Payment, first: Boolean, last: Boolean, now: ZonedDateTime, onClick: () -> Unit) {
    // Rows are drawn as one card: rounded only at its top and bottom ends.
    val shape = RoundedCornerShape(
        topStart = if (first) 20.dp else 0.dp,
        topEnd = if (first) 20.dp else 0.dp,
        bottomStart = if (last) 20.dp else 0.dp,
        bottomEnd = if (last) 20.dp else 0.dp,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .cardOutline(shape),
    ) {
        if (!first) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        payment.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (payment.isManual) {
                        Spacer(Modifier.width(8.dp))
                        ManualTag()
                    }
                }
                payment.noteLine?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${payment.category} · ${whenText(payment.createdAt, now)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("−${formatPaise(payment.amountPaise)}", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** "Today, 3:19 pm", or "12 Nov, 3:19 pm" for an earlier day. */
private fun whenText(millis: Long, now: ZonedDateTime): String {
    val at = Instant.ofEpochMilli(millis).atZone(now.zone)
    val time = timeFormat.format(at).lowercase()
    return if (at.toLocalDate() == now.toLocalDate()) "Today, $time" else "${dayFormat.format(at)}, $time"
}

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
