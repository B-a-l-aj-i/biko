package com.budgetapp.ui.category

import com.budgetapp.ui.theme.PhosphorLight
import com.budgetapp.ui.theme.cardBorder
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.budgetapp.data.percentText
import com.budgetapp.data.percentToRupees
import com.budgetapp.ui.components.BudgetAmountField
import com.budgetapp.ui.components.BudgetShareHint
import com.budgetapp.ui.components.ModalButtons
import com.budgetapp.ui.components.ModalFieldShape
import com.budgetapp.ui.components.SegmentedToggle
import androidx.compose.ui.focus.FocusManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.budgetapp.data.cleanAmount
import com.budgetapp.data.cleanPercent

private const val PANEL_SLIDE_MS = 280

/**
 * Asks for a new category's name, plus an optional emoji and monthly budget.
 *
 * The emoji field behaves like the other fields, except that the "keyboard"
 * it brings up is an emoji panel. Its search button swaps in the real keyboard
 * and shrinks the panel to a strip of matching emoji.
 */
@Composable
fun NewCategoryDialog(
    existingNames: List<String>,
    onCreate: (name: String, emoji: String, monthlyBudget: Int) -> Unit,
    onDismiss: () -> Unit,
    /** The overall monthly budget in rupees; zero hides the percentage option. */
    monthlyBudget: Int = 0,
    /** Rupees of [monthlyBudget] not yet given to a category; null means no limit. */
    freeBudget: Int? = null,
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }

    var panelOpen by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // The panel must never show while a field that uses the real keyboard has
    // focus. Tracked directly, as well as closing the panel on focus, because
    // near-simultaneous taps can deliver the focus events in either order.
    var nameFocused by remember { mutableStateOf(false) }
    var budgetFocused by remember { mutableStateOf(false) }
    val panelVisible = panelOpen && !nameFocused && !budgetFocused

    // Closing the panel also ends search, which would swap the strip for the
    // full grid halfway through the slide-out. Keep the size it had while open.
    val lastCompact = remember { mutableStateOf(false) }
    val shownCompact = if (panelVisible) searching else lastCompact.value
    SideEffect { if (panelVisible) lastCompact.value = searching }

    // How far the panel has risen, in pixels: 0 is hidden below the screen.
    // Read only inside graphicsLayer blocks, so each frame of the slide is a
    // cheap redraw with no recomposition or layout.
    val panelHeightPx = with(LocalDensity.current) {
        (if (shownCompact) EmojiPanelCompactHeight else EmojiPanelHeight).toPx()
    }
    val panelRise = animateFloatAsState(
        targetValue = if (panelVisible) panelHeightPx else 0f,
        animationSpec = tween(PANEL_SLIDE_MS, easing = FastOutSlowInEasing),
        label = "emojiPanelRise",
    )
    var cardTop by remember { mutableFloatStateOf(0f) }

    // The dialog is its own window with its own focus; the focus manager out
    // here belongs to the screen behind and can't clear a field in the dialog.
    // Filled in from inside the dialog's content.
    var focusManager by remember { mutableStateOf<FocusManager?>(null) }
    val allEmoji by produceState(emptyList<EmojiEntry>()) {
        value = withContext(Dispatchers.Default) { EmojiCatalog.all }
    }
    val shownEmoji = remember(allEmoji, query) { EmojiCatalog.search(allEmoji, query) }

    val trimmed = name.trim()
    val duplicate = existingNames.any { it.equals(trimmed, ignoreCase = true) }

    // With a monthly budget to take a share of, the budget can be typed as
    // rupees or as a percentage, and may not exceed what is still free.
    val sharesBudget = monthlyBudget > 0
    var byPercent by remember { mutableStateOf(false) }
    val amount = if (byPercent) percentToRupees(budget, monthlyBudget) else budget.toIntOrNull() ?: 0
    val room = freeBudget ?: Int.MAX_VALUE
    val tooMuch = sharesBudget && amount > room

    fun switchTo(percent: Boolean) {
        if (percent == byPercent) return
        budget = when {
            amount == 0 -> ""
            percent -> percentText(amount, monthlyBudget).removeSuffix("%")
            else -> amount.toString()
        }
        byPercent = percent
    }

    fun closePanel() {
        panelOpen = false
        searching = false
        query = ""
    }

    // Back and outside taps close the emoji panel first, then the dialog.
    val dismiss: () -> Unit = {
        if (panelVisible) {
            closePanel()
            focusManager?.clearFocus()
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = dismiss,
        // Full-screen window so the emoji panel can sit at the very bottom, and
        // so the layout can react to the keyboard.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogFocus = LocalFocusManager.current
        SideEffect { focusManager = dialogFocus }

        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = cardBorder(),
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .onPlaced { cardTop = it.positionInParent().y }
                        // Moves up by half the panel's height, which keeps the
                        // card centred in the space above the panel. Never past
                        // the top of the screen.
                        .graphicsLayer { translationY = -minOf(panelRise.value / 2, cardTop) }
                        // Swallows taps so they don't reach the dismiss area behind.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("New category", style = MaterialTheme.typography.titleLarge)

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(24) },
                            label = { Text("Name") },
                            singleLine = true,
                            shape = ModalFieldShape,
                            isError = duplicate,
                            supportingText = if (duplicate) {
                                { Text("That category already exists") }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged {
                                    nameFocused = it.isFocused
                                    if (it.isFocused) closePanel()
                                },
                        )

                        if (searching) {
                            EmojiSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                onClose = {
                                    searching = false
                                    query = ""
                                },
                            )
                        } else {
                            // Looks like a text field but never takes focus. A
                            // focused Material field animates its label and border,
                            // which re-laid-out the dialog on every frame of the
                            // panel's slide and made it stutter.
                            val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                            Box {
                                OutlinedTextField(
                                    value = emoji,
                                    onValueChange = {},
                                    enabled = false,
                                    label = { Text("Emoji (optional)") },
                                    singleLine = true,
                                    shape = ModalFieldShape,
                                    textStyle = TextStyle(fontSize = 22.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledLabelColor = onSurfaceVariant,
                                        // Highlighted while its "keyboard", the panel, is up.
                                        disabledBorderColor = if (panelVisible) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                // Tap target over the whole field.
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClickLabel = "Choose an emoji",
                                        ) {
                                            // Drops the real keyboard if another field had it.
                                            focusManager?.clearFocus()
                                            panelOpen = true
                                        },
                                )
                                IconButton(
                                    onClick = {
                                        panelOpen = true
                                        searching = true
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(top = 8.dp, end = 4.dp),
                                ) {
                                    Icon(
                                        PhosphorLight.MagnifyingGlass,
                                        contentDescription = "Search emoji",
                                        tint = onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (sharesBudget) {
                            SegmentedToggle(
                                options = listOf("Amount", "Percentage"),
                                selected = if (byPercent) 1 else 0,
                                onSelect = { switchTo(it == 1) },
                            )
                        }
                        BudgetAmountField(
                            text = budget,
                            onChange = { budget = if (byPercent) cleanPercent(it) else cleanAmount(it) },
                            percent = byPercent,
                            label = "Monthly budget (optional)",
                            modifier = Modifier.onFocusChanged {
                                budgetFocused = it.isFocused
                                if (it.isFocused) closePanel()
                            },
                        )
                        if (sharesBudget) {
                            BudgetShareHint(amount, monthlyBudget, room, byPercent)
                        }

                        ModalButtons(
                            confirmLabel = "Create",
                            confirmEnabled = trimmed.isNotEmpty() && !duplicate && !tooMuch,
                            onConfirm = { onCreate(trimmed, emoji, amount) },
                            onCancel = onDismiss,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // Rises from the bottom edge and sinks back into it, like the keyboard
            // does. The panel is always laid out in its open position and only
            // slid by a layer offset, so the animation never re-measures anything.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY = panelHeightPx - panelRise.value
                        // Fully lowered, its top edge would still peek out
                        // behind the system navigation bar.
                        alpha = if (panelRise.value < 1f) 0f else 1f
                    },
            ) {
                EmojiPanel(
                    entries = shownEmoji,
                    compact = shownCompact,
                    onPick = {
                        emoji = it
                        closePanel()
                        focusManager?.clearFocus()
                    },
                    onClear = if (emoji.isNotEmpty() && !searching) {
                        {
                            emoji = ""
                            closePanel()
                            focusManager?.clearFocus()
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** The emoji field while searching: typing filters the panel. Takes focus as it appears. */
@Composable
private fun EmojiSearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        // Focus given by code doesn't reliably raise the keyboard inside a
        // dialog; ask for it once the field has settled.
        delay(150)
        keyboard?.show()
    }
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChange(it.take(20)) },
        label = { Text("Search emoji") },
        singleLine = true,
        shape = ModalFieldShape,
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(PhosphorLight.X, contentDescription = "Stop searching")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
}
