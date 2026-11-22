@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.budgetapp.ui.pay

import com.budgetapp.ui.theme.PhosphorLight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.budgetapp.ui.components.rememberCategoryRemover
import com.budgetapp.ui.nav.rememberEntrance
import com.budgetapp.ui.nav.riseIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetapp.data.Graph
import com.budgetapp.data.Payment
import com.budgetapp.data.PaymentImpact
import com.budgetapp.data.StandingKind
import com.budgetapp.data.categoryRoom
import com.budgetapp.data.manualPayment
import com.budgetapp.data.paiseToKeypadText
import com.budgetapp.data.paymentImpact
import androidx.compose.runtime.collectAsState
import java.io.File
import java.time.ZonedDateTime
import com.budgetapp.data.formatPaise
import com.budgetapp.ui.theme.LocalStatusColors
import com.budgetapp.ui.theme.cardOutline
import com.budgetapp.data.isManual
import com.budgetapp.data.title
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import com.budgetapp.data.NOTE_MAX_LENGTH
import com.budgetapp.data.cleanNote
import com.budgetapp.data.CategoryBudget
import com.budgetapp.ui.components.Pill
import com.budgetapp.ui.category.NewCategoryDialog
import com.budgetapp.data.toPaise
import com.budgetapp.data.afterKey

/**
 * Records a payment: enter the amount, pick a category, optionally add a
 * voice or written note. Opened from the + button, Add records a payment
 * already made outside the app.
 *
 * [editing] is a saved payment opened from the home screen's list. The page
 * starts filled in with it, and Save changes its amount, category and note.
 */
@Composable
fun PayScreen(
    categories: List<CategoryBudget>,
    onDone: () -> Unit,
    editing: Payment? = null,
) {
    val scope = rememberCoroutineScope()
    val repository = Graph.repository
    // Every month's payments, so the warning can measure an old payment against its own month.
    val data by repository.snapshot.collectAsState()

    var amountText by rememberSaveable { mutableStateOf(editing?.let { paiseToKeypadText(it.amountPaise) }.orEmpty()) }
    // A payment found in an alert sits under "Other" until the user picks; they start with nothing picked.
    var category by rememberSaveable { mutableStateOf(editing?.takeUnless { it.needsReview }?.category) }
    var voiceNote by rememberSaveable { mutableStateOf(editing?.voiceNotePath) }
    var note by rememberSaveable { mutableStateOf(editing?.note.orEmpty()) }
    // While true the phone's keyboard is up for the note and the number keypad steps aside.
    var writingNote by rememberSaveable { mutableStateOf(false) }
    var creatingCategory by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val amountPaise = amountText.toPaise()
    val selected = categories.firstOrNull { it.name == category }

    // The bottom section (voice note, Next, keypad) stays out of sight until
    // the sheet has risen, then rises into place as one block.
    val keypadIn = rememberEntrance(delayMs = KEYPAD_DELAY_MS, durationMs = KEYPAD_RISE_MS)

    // Removing the chosen category also clears the choice.
    val removeCategory = rememberCategoryRemover(onRemoved = { name -> if (category == name) category = null })

    fun saveEdit(saved: Payment) {
        val chosenCategory = category ?: return
        if (amountPaise <= 0 || saving) return
        saving = true
        scope.launch {
            repository.updatePayment(saved.id, amountPaise, chosenCategory, voiceNote, note)
            // The old note was kept until now in case the edit was dropped.
            val oldNote = saved.voiceNotePath
            if (oldNote != null && oldNote != voiceNote) File(oldNote).delete()
            onDone()
        }
    }

    fun deleteSaved(saved: Payment) {
        if (saving) return
        saving = true
        scope.launch {
            repository.deletePayment(saved.id)
            // Its saved note, and any new one recorded on this page, go with it.
            listOfNotNull(saved.voiceNotePath, voiceNote).distinct().forEach { File(it).delete() }
            onDone()
        }
    }

    // Records a payment made outside the app; nothing is sent to a UPI app.
    fun addManual() {
        val chosenCategory = category ?: return
        // A quick double tap must not save the same payment twice.
        if (amountPaise <= 0 || saving) return
        saving = true
        scope.launch {
            repository.addPayment(
                manualPayment(amountPaise, chosenCategory, voiceNote, System.currentTimeMillis())
                    .copy(note = cleanNote(note)),
            )
            onDone()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                PayeeHeader(editing, Modifier.align(Alignment.Center).padding(top = 8.dp))
                if (editing != null) {
                    IconButton(
                        onClick = { confirmingDelete = true },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(PhosphorLight.Trash, contentDescription = "Delete payment")
                    }
                }
            }

            AmountDisplay(
                text = amountText,
                // Tapping the amount while writing the note brings the number keypad back.
                onClick = { writingNote = false },
            )

            Text(
                "What is this for?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // Takes the leftover height and scrolls, so the list can grow long.
            CategoryChips(
                categories = categories,
                selected = category,
                onSelect = { category = it },
                onNew = { creatingCategory = true },
                onRemove = removeCategory,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            // Measured in the payment's own month: an edited October payment against October.
            val impact = selected?.let {
                paymentImpact(data.payments, data.categories, editing, amountPaise, it.name, ZonedDateTime.now())
            }
            if (impact != null) {
                BudgetWarning(impact)
                Spacer(Modifier.height(8.dp))
            }

            // Voice note, Next and the keypad arrive together as one section;
            // the keypad rising alone under a still button looked disjointed.
            Column(Modifier.riseIn(keypadIn, distance = 48.dp)) {
                if (writingNote) {
                    NoteField(
                        text = note,
                        onChange = { note = it.take(NOTE_MAX_LENGTH) },
                        onDone = { writingNote = false },
                    )
                } else {
                    // Wraps the note pill to its own line when a recorded voice note's two pills take the room.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VoiceNoteControl(
                            path = voiceNote,
                            onChange = { voiceNote = it },
                            keepOnRemove = editing?.voiceNotePath,
                        )
                        Pill(
                            icon = PhosphorLight.PencilSimple,
                            label = note.trim().ifEmpty { "Note" },
                            onClick = { writingNote = true },
                            modifier = Modifier.widthIn(max = 240.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (editing != null) saveEdit(editing) else addManual()
                    },
                    enabled = amountPaise > 0 && category != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    val label = if (editing != null && !editing.needsReview) "Save" else "Add"
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(10.dp))

                if (!writingNote) {
                    Keypad(onKey = { key -> amountText = amountText.afterKey(key) })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (creatingCategory) {
        NewCategoryDialog(
            existingNames = categories.map { it.name },
            // The same cap as the Budget screen: category budgets can't pass the monthly budget.
            monthlyBudget = data.overallBudget,
            freeBudget = categoryRoom(data.overallBudget, data.categories, null).takeIf { data.overallBudget > 0 },
            onCreate = { name, emoji, budget ->
                creatingCategory = false
                scope.launch {
                    repository.addCategory(name, emoji, budget)
                    category = name
                }
            },
            onDismiss = { creatingCategory = false },
        )
    }

    if (confirmingDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this payment?") },
            // The saved amount, not what is typed now: that is what stops counting.
            text = {
                val what = if (editing.isManual) "for ${editing.title}" else "to ${editing.payeeName}"
                Text("${formatPaise(editing.amountPaise)} $what will no longer count as spent. This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    deleteSaved(editing)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Keep") } },
        )
    }
}

/** Says what this payment does to the chosen category's budget; the figure and words come from [paymentImpact]. */
@Composable
private fun BudgetWarning(impact: PaymentImpact) {
    val status = LocalStatusColors.current
    val (background, content) = when (impact.standing.kind) {
        StandingKind.OVER -> status.danger.copy(alpha = 0.15f) to status.danger
        StandingKind.NEAR -> status.warningContainer to status.onWarningContainer
        StandingKind.LEFT -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        impact.words,
        color = content,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .cardOutline(RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
