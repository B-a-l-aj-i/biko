@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.budgetapp.ui.pay

import com.budgetapp.ui.theme.PhosphorLight
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.layout.layout
import com.budgetapp.ui.components.rememberCategoryRemover
import com.budgetapp.ui.nav.rememberEntrance
import com.budgetapp.ui.nav.riseIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetapp.data.Graph
import com.budgetapp.data.Payment
import com.budgetapp.ui.home.CategoryBudget
import com.budgetapp.data.PaymentImpact
import com.budgetapp.data.StandingKind
import com.budgetapp.data.categoryRoom
import com.budgetapp.data.manualPayment
import com.budgetapp.data.paiseToKeypadText
import com.budgetapp.data.parsePaise
import com.budgetapp.data.paymentImpact
import androidx.compose.runtime.collectAsState
import java.io.File
import java.time.ZonedDateTime
import com.budgetapp.data.formatPaise
import com.budgetapp.ui.theme.LocalStatusColors
import com.budgetapp.ui.theme.cardBorder
import com.budgetapp.ui.theme.cardOutline
import com.budgetapp.data.UNKNOWN_PAYEE
import com.budgetapp.data.isManual
import com.budgetapp.data.title
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.budgetapp.data.NOTE_MAX_LENGTH
import com.budgetapp.data.cleanNote

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

@Composable
private fun PayeeHeader(
    editing: Payment?,
    modifier: Modifier = Modifier,
) {
    val name = editing?.title
    val line = when {
        editing?.needsReview == true -> "Found in your UPI app's alert. What was it for?"
        editing != null -> "Change the amount, category or note"
        else -> "One you paid outside this app"
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .cardOutline(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name?.firstOrNull()?.uppercase() ?: "₹",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                name == null -> "Add a payment"
                editing.needsReview && editing.payeeName != UNKNOWN_PAYEE -> "Paid to $name"
                editing.needsReview -> "Paid from your account"
                else -> name
            },
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 56.dp),
        )
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Draws the element just left of where it is placed, taking up no width itself. */
private fun Modifier.hangingLeft(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(0, placeable.height) { placeable.place(-placeable.width, 0) }
}

/** Centres the element on the edge it is aligned to, taking up no width itself. */
private fun Modifier.straddlingEdge(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(0, placeable.height) { placeable.place(-placeable.width / 2, 0) }
}

/**
 * One line of text for the payment's note, with the phone's keyboard. Done on
 * the keyboard, the tick, or closing the keyboard puts the number keypad back.
 */
@Composable
private fun NoteField(text: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun finish() {
        keyboard?.hide()
        onDone()
    }

    // Closing the keyboard (Back) ends the note too, so the page is never left
    // with neither keyboard nor keypad. Only once it has been seen open, since
    // it takes a moment to rise.
    val imeVisible = WindowInsets.isImeVisible
    var imeOpened by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) imeOpened = true else if (imeOpened) onDone()
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .cardOutline(CircleShape)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorLight.PencilSimple, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text("What was it? e.g. lunch with team", style = MaterialTheme.typography.bodyLarge, color = muted)
            }
            BasicTextField(
                value = text,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.merge(
                    TextStyle(color = MaterialTheme.colorScheme.onSurface),
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { finish() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        IconButton(onClick = ::finish) {
            Icon(PhosphorLight.Check, contentDescription = "Done with note")
        }
    }
}

@Composable
private fun AmountDisplay(text: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Only the digits take up width, so it is the number itself that sits
        // in the centre of the screen. The rupee sign and the caret hang off its
        // edges without pushing it sideways.
        Box {
            val empty = text.isEmpty()
            val color = if (empty) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onBackground
            Text(if (empty) "0" else text, fontSize = 60.sp, fontWeight = FontWeight.Medium, color = color)
            Text(
                "₹",
                fontSize = 26.sp,
                color = color,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 8.dp)
                    .hangingLeft(),
            )
            // Caret sits before the "0" placeholder when empty, after the digits once typing.
            BlinkingCaret(
                Modifier
                    .align(if (empty) Alignment.CenterStart else Alignment.CenterEnd)
                    .straddlingEdge(),
            )
        }
    }
}

/** Text-field style caret so the keypad-driven amount reads as the active input. */
@Composable
private fun BlinkingCaret(modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
        ),
        label = "caretAlpha",
    )
    Box(
        modifier
            .padding(horizontal = 2.dp)
            .width(2.dp)
            .height(52.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)),
    )
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

private const val BACKSPACE = "⌫"

/** How long the bottom section waits for the sheet to rise before it starts to arrive. */
private const val KEYPAD_DELAY_MS = 280L
private const val KEYPAD_RISE_MS = 420

@Composable
private fun Keypad(onKey: (String) -> Unit) {
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

/** Asks how a payment left pending (from an older version of the app) ended. */
@Composable
fun ConfirmPaymentDialog(payee: String?, amount: String, onAnswer: (paid: Boolean) -> Unit) {
    AlertDialog(
        // Dismissing without answering would leave the payment unresolved.
        onDismissRequest = {},
        title = { Text("Did the payment go through?") },
        text = { Text(if (payee != null) "$amount to $payee" else amount) },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Yes, paid") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Not paid") } },
    )
}

private fun String.toPaise(): Long = parsePaise(this) ?: 0L

/** Applies one keypad press, allowing at most 7 rupee digits and 2 decimals. */
private fun String.afterKey(key: String): String = when (key) {
    BACKSPACE -> dropLast(1)
    "." -> if (contains('.')) this else if (isEmpty()) "0." else "$this."
    else -> {
        val decimals = substringAfter('.', missingDelimiterValue = "")
        when {
            contains('.') -> if (decimals.length < 2) this + key else this
            this == "0" -> key
            length >= 7 -> this
            else -> this + key
        }
    }
}
