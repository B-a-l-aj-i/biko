package com.budgetapp.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetapp.data.Payment
import com.budgetapp.data.alertSourceLabel
import com.budgetapp.data.paymentsToReview
import com.budgetapp.ui.components.scrollFade
import com.budgetapp.data.formatPaise
import com.budgetapp.ui.theme.LocalStatusColors
import com.budgetapp.ui.theme.PhosphorLight
import com.budgetapp.ui.theme.cardOutline
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How long Undo stays on screen after a tick or a cross. */
private const val UNDO_MS = 5_000

/** What the last tick or cross did, and the payment as it was before, for Undo. */
private data class UndoAction(val message: String, val before: Payment, val stamp: Long)

/**
 * Payments found in UPI app notifications that the app didn't record. None
 * of them counts as spent until the user ticks it; a cross deletes it. Each
 * tick or cross can be undone for [UNDO_MS]. Tapping a row opens it to pick a
 * category before adding it.
 */
@Composable
fun ReviewScreen(
    payments: List<Payment>,
    /** Green tick: count it as spent, under "Other". */
    onConfirm: (Payment) -> Unit,
    /** Red cross: not a payment of mine; delete it. */
    onRemove: (Payment) -> Unit,
    /** Puts the payment back exactly as it was before the tick or cross. */
    onUndo: (Payment) -> Unit,
    /** Opens a payment to pick its category and add it; takes its id. */
    onOpen: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val toReview = remember(payments) { paymentsToReview(payments) }
    val now = ZonedDateTime.now()
    var undo by remember { mutableStateOf<UndoAction?>(null) }

    fun act(payment: Payment, confirm: Boolean) {
        val amount = formatPaise(payment.amountPaise)
        if (confirm) onConfirm(payment) else onRemove(payment)
        undo = UndoAction(
            message = if (confirm) "$amount added to spent" else "$amount removed",
            before = payment,
            stamp = System.nanoTime(),
        )
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(PhosphorLight.ArrowLeft, contentDescription = "Back")
                    }
                    Text("To review", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "Found in your UPI apps' notifications. None of these count as spent until you tick them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                if (toReview.isEmpty()) {
                    Text(
                        "Nothing to review",
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
                        // Room at the bottom so the last row can scroll clear of the Undo bar.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    ) {
                        itemsIndexed(toReview, key = { _, p -> p.id }) { index, payment ->
                            ReviewRow(
                                payment = payment,
                                first = index == 0,
                                last = index == toReview.lastIndex,
                                now = now,
                                onOpen = { onOpen(payment.id) },
                                onConfirm = { act(payment, confirm = true) },
                                onRemove = { act(payment, confirm = false) },
                            )
                        }
                    }
                }
            }

            UndoBar(
                action = undo,
                onUndo = { before ->
                    onUndo(before)
                    undo = null
                },
                onTimeout = { undo = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun ReviewRow(
    payment: Payment,
    first: Boolean,
    last: Boolean,
    now: ZonedDateTime,
    onOpen: () -> Unit,
    onConfirm: () -> Unit,
    onRemove: () -> Unit,
) {
    val status = LocalStatusColors.current
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
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    payment.payeeName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${alertSourceLabel(payment.alertSources.firstOrNull().orEmpty())} · ${whenText(payment.createdAt, now)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatPaise(payment.amountPaise), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(8.dp))
            RoundAction(PhosphorLight.X, "Not mine, remove", status.danger, onRemove)
            RoundAction(PhosphorLight.Check, "Yes, add to spent", status.success, onConfirm)
        }
    }
}

/** A tinted round button: the red cross or the green tick. */
@Composable
private fun RoundAction(icon: ImageVector, description: String, color: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(20.dp))
        }
    }
}

/** Says what the last tick or cross did, with Undo, and a line that runs out over [UNDO_MS]. */
@Composable
private fun UndoBar(
    action: UndoAction?,
    onUndo: (Payment) -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keeps the last message on screen while the bar slides away.
    var shown by remember { mutableStateOf(action) }
    if (action != null) shown = action
    val left = remember { Animatable(1f) }
    LaunchedEffect(action?.stamp) {
        if (action == null) return@LaunchedEffect
        left.snapTo(1f)
        left.animateTo(0f, tween(UNDO_MS, easing = LinearEasing))
        onTimeout()
    }
    AnimatedVisibility(
        visible = action != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val current = shown ?: return@AnimatedVisibility
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(current.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onUndo(current.before) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(PhosphorLight.ArrowCounterClockwise, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Undo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    }
                }
                // Runs out as the five seconds pass.
                Box(
                    Modifier
                        .fillMaxWidth(left.value)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f)),
                )
            }
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
