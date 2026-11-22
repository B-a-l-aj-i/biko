package com.budgetapp.ui.budget

import androidx.compose.foundation.lazy.rememberLazyListState
import com.budgetapp.ui.components.scrollFade
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.saveable.rememberSaveable
import com.budgetapp.data.spendShares
import com.budgetapp.data.Category
import com.budgetapp.data.categoryRoom
import com.budgetapp.data.budgetStanding
import com.budgetapp.data.StandingKind
import com.budgetapp.ui.theme.LocalPalette
import com.budgetapp.ui.theme.cardBorder
import com.budgetapp.ui.theme.PhosphorLight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Button
import androidx.compose.runtime.key
import com.budgetapp.ui.components.RemoveBadge
import com.budgetapp.ui.components.RemoveMode
import com.budgetapp.ui.components.rememberCategoryRemover
import com.budgetapp.ui.components.rememberPresences
import com.budgetapp.ui.components.rememberRemoveMode
import com.budgetapp.ui.components.shake
import com.budgetapp.data.percentText
import com.budgetapp.ui.home.HomeUiState
import com.budgetapp.data.formatPaise
import com.budgetapp.data.formatRupees
import com.budgetapp.ui.home.sampleHomeState
import com.budgetapp.ui.theme.BudgetAppTheme
import com.budgetapp.ui.theme.LocalStatusColors
import com.budgetapp.ui.category.NewCategoryDialog
import com.budgetapp.data.CategoryBudget
import com.budgetapp.ui.components.collapsedBy
import com.budgetapp.ui.theme.CardShape
import com.budgetapp.ui.components.CardDivider

/** What the budget dialog is editing; a null [category] means the overall budget. */
private data class Editing(val category: CategoryBudget?)

/** Shows the month's budget split by category and lets the user set each figure. */
@Composable
fun BudgetScreen(
    state: HomeUiState,
    onSetOverall: (Int) -> Unit,
    onSetCategory: (name: String, rupees: Int) -> Unit,
    onAddCategory: (name: String, emoji: String, rupees: Int) -> Unit = { _, _, _ -> },
) {
    var editing by remember { mutableStateOf<Editing?>(null) }
    var adding by remember { mutableStateOf(false) }

    // A category keeps its colour by position in the full list, so it never
    // changes with how much was spent. The same colour marks it in the donut,
    // the bar and its row.
    val palette = LocalPalette.current
    val neutral = palette.categoryNeutral
    val colors = state.allCategories.indices.map { palette.categories.getOrNull(it) }
    val ringOf = state.allCategories.mapIndexed { index, category -> category.name to colors[index] }.toMap()

    // The donut: this month's spending by category, largest first.
    val shares = spendShares(state.allCategories.map { it.name to it.spentPaise }, state.spentPaise)
    val emojiOf = state.allCategories.associate { it.name to it.emoji }
    val pieces = shares.map { share ->
        DonutPiece(
            name = share.name,
            emoji = emojiOf[share.name].orEmpty(),
            color = ringOf[share.name] ?: neutral,
            spentPaise = share.spentPaise,
            percent = share.percent,
        )
    }
    // The slice picked in the donut; its row in the list below lights up too.
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    if (picked != null && pieces.none { it.name == picked }) picked = null

    // Press and hold a row to remove categories, exactly as on the payment sheet.
    val removeMode = rememberRemoveMode(hasItems = state.allCategories.isNotEmpty())
    val removeCategory = rememberCategoryRemover()
    val rows = rememberPresences(state.allCategories) { it.name }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .scrollFade(listState),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // No back arrow: it's a sheet, closed by swiping it down or by the back gesture.
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Budget", style = MaterialTheme.typography.titleLarge)
                }
            }
            item {
                CategoryDonut(
                    pieces = pieces,
                    totalPaise = state.spentPaise,
                    selected = picked,
                    onSelect = { picked = it },
                )
            }
            item {
                OverallCard(
                    state = state,
                    onEdit = { editing = Editing(null) },
                    removing = removeMode.active,
                    onAddCategory = { adding = true },
                    onDone = removeMode::exit,
                )
            }
            item {
                Surface(shape = CardShape, border = cardBorder(), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        rows.forEachIndexed { index, row ->
                            key(row) {
                                val category = row.item
                                // A removed row fades and closes up; a new one opens out.
                                Column(Modifier.collapsedBy { row.amount.value }) {
                                    if (index > 0) {
                                        CardDivider()
                                    }
                                    CategoryRow(
                                        category = category,
                                        monthlyBudget = state.monthlyBudget,
                                        // A row on its way out is no longer in the list, so has no colour.
                                        ring = ringOf[category.name] ?: neutral,
                                        highlighted = category.name == picked,
                                        removeMode = removeMode,
                                        lean = if (index % 2 == 0) 1f else -1f,
                                        onClick = {
                                            if (!removeMode.active && !row.leaving) editing = Editing(category)
                                        },
                                        onRemove = { if (!row.leaving) removeCategory(category) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        NewCategoryDialog(
            existingNames = state.allCategories.map { it.name },
            monthlyBudget = state.monthlyBudget,
            freeBudget = categoryRoom(state.monthlyBudget, state.budgetCategories(), null).takeIf { state.monthlyBudget > 0 },
            onCreate = { name, emoji, rupees ->
                onAddCategory(name, emoji, rupees)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }

    editing?.let { target ->
        val category = target.category
        if (category == null) {
            OverallBudgetDialog(
                initial = state.monthlyBudget,
                onSave = { rupees ->
                    onSetOverall(rupees)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        } else {
            CategoryBudgetDialog(
                category = category,
                monthlyBudget = state.monthlyBudget,
                room = categoryRoom(state.monthlyBudget, state.budgetCategories(), category.name),
                onSave = { rupees ->
                    onSetCategory(category.name, rupees)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun OverallCard(
    state: HomeUiState,
    onEdit: () -> Unit,
    /** In remove mode the button below ends it instead of adding a category. */
    removing: Boolean,
    onAddCategory: () -> Unit,
    onDone: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val status = LocalStatusColors.current
    // Same words as every screen: "₹1,200 left" or "₹500 over", never a minus.
    val standing = budgetStanding(state.spentPaise, state.monthlyBudget)

    Surface(shape = CardShape, border = cardBorder(), modifier = Modifier.fillMaxWidth()) {
        // 8dp side padding here plus 8dp inside each child keeps the text 16dp
        // in, while the tappable row's press highlight gets an 8dp margin round
        // the text instead of cutting into the first letter.
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Change monthly budget", onClick = onEdit)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Monthly budget", style = MaterialTheme.typography.bodySmall, color = muted)
                    Text(formatRupees(state.monthlyBudget), fontSize = 30.sp, fontWeight = FontWeight.Medium)
                }
                Icon(PhosphorLight.CaretRight, contentDescription = null, tint = muted)
            }
            // What was spent is in the donut's centre above, so only what is left.
            Text(
                standing.words,
                style = MaterialTheme.typography.bodyMedium,
                color = if (standing.kind == StandingKind.OVER) status.danger else muted,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            // How the monthly budget is split into category budgets: the plan,
            // not what was spent.
            Spacer(Modifier.height(12.dp))
            Text(
                allocationText(state.monthlyBudget, state.allocated),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.allocated > state.monthlyBudget) status.danger else muted,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = if (removing) onDone else onAddCategory,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .height(46.dp),
            ) {
                Icon(
                    if (removing) PhosphorLight.Check else PhosphorLight.Plus,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (removing) "Done" else "Add category")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    category: CategoryBudget,
    monthlyBudget: Int,
    ring: Color,
    /** Picked in the donut above: tinted in its colour and scrolled into view. */
    highlighted: Boolean,
    removeMode: RemoveMode,
    lean: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val standing = budgetStanding(category.spentPaise, category.budget)
    val over = category.budget > 0 && standing.kind == StandingKind.OVER
    val tint by animateColorAsState(
        if (highlighted) ring.copy(alpha = 0.45f) else Color.Transparent,
        tween(durationMillis = 250),
        label = "rowHighlight",
    )
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(highlighted) { if (highlighted) bringIntoView.bringIntoView() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .drawBehind { drawRect(tint) }
            // A full-width row needs far less tilt than a chip to move as much.
            .shake(removeMode, lean, degrees = 0.3f)
            .combinedClickable(
                onClickLabel = "Change budget",
                onLongClickLabel = "Remove categories",
                onLongClick = removeMode::enter,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            // The dot is the key to the donut and bar above: same colour, same category.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(category.emoji, fontSize = 18.sp)
            }
            if (removeMode.active) {
                RemoveBadge(
                    label = category.name,
                    onRemove = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-9).dp, y = (-9).dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (over) {
                    "${formatPaise(category.spentPaise)} spent · ${standing.words}"
                } else {
                    "${formatPaise(category.spentPaise)} spent"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (over) LocalStatusColors.current.danger else muted,
            )
        }
        if (category.budget > 0) {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatRupees(category.budget), fontWeight = FontWeight.Medium)
                Text(
                    percentText(category.budget, monthlyBudget),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        } else {
            Text("Set budget", color = muted)
        }
        Spacer(Modifier.width(4.dp))
        Icon(PhosphorLight.CaretRight, contentDescription = null, tint = muted)
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 820)
@Composable
private fun BudgetScreenPreview() {
    BudgetAppTheme {
        BudgetScreen(sampleHomeState(), onSetOverall = {}, onSetCategory = { _, _ -> })
    }
}

/** The categories as the budget rules take them: name, emoji and budget. */
private fun HomeUiState.budgetCategories(): List<Category> = allCategories.map { Category(it.name, it.emoji, it.budget) }
