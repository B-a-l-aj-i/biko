package com.budgetapp.ui.pay

import com.budgetapp.ui.theme.PhosphorLight
import com.budgetapp.ui.theme.cardBorder
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val COLUMNS = 8
private val RowHeight = 48.dp

/** Tall enough to cover the pay screen's Voice note button behind the dialog. */
val EmojiPanelHeight = 376.dp

/** The strip shown above the real keyboard while searching. */
val EmojiPanelCompactHeight = 112.dp

private val EmojiCategory.icon: ImageVector
    get() = when (this) {
        EmojiCategory.FOOD -> PhosphorLight.Hamburger
        EmojiCategory.TRAVEL -> PhosphorLight.Car
        EmojiCategory.OBJECTS -> PhosphorLight.Lightbulb
        EmojiCategory.ACTIVITIES -> PhosphorLight.Trophy
        EmojiCategory.NATURE -> PhosphorLight.Leaf
        EmojiCategory.PEOPLE -> PhosphorLight.Person
        EmojiCategory.SMILEYS -> PhosphorLight.Smiley
        EmojiCategory.SYMBOLS -> PhosphorLight.Heart
    }

/** The grid's rows, plus the row at which each category begins. */
private class EmojiRows(val rows: List<List<EmojiEntry>>, val starts: List<Pair<EmojiCategory, Int>>)

/** Every category starts on a fresh row, so a tab can jump straight to it. */
private fun rowsByCategory(entries: List<EmojiEntry>): EmojiRows {
    val rows = ArrayList<List<EmojiEntry>>()
    val starts = ArrayList<Pair<EmojiCategory, Int>>()
    EmojiCategory.entries.forEach { category ->
        val inCategory = entries.filter { it.category == category }
        if (inCategory.isNotEmpty()) {
            starts += category to rows.size
            rows += inCategory.chunked(COLUMNS)
        }
    }
    return EmojiRows(rows, starts)
}

/**
 * Grid of emoji that sits at the bottom of the screen like a keyboard, with a
 * row of category tabs on top.
 * [compact] shortens it to a tab-less strip, for when the real keyboard is also
 * open and the grid is showing search results.
 * [onClear] is offered above the grid when there is an emoji to remove.
 */
@Composable
fun EmojiPanel(
    entries: List<EmojiEntry>,
    compact: Boolean,
    onPick: (String) -> Unit,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val grid = remember(entries, compact) {
        if (compact) EmojiRows(entries.chunked(COLUMNS), emptyList()) else rowsByCategory(entries)
    }
    val textSize = with(LocalDensity.current) { 26.sp.toPx() }
    val paint = remember(textSize) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            this.textSize = textSize
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The "Remove emoji" button, when shown, is list item 0 and shifts every row down one.
    val rowOffset = if (onClear != null) 1 else 0

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = cardBorder(),
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) EmojiPanelCompactHeight else EmojiPanelHeight),
    ) {
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No emoji found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Surface
        }
        Column {
            if (grid.starts.isNotEmpty()) {
                // The tab of whichever category is at the top of the grid.
                val current by remember(grid, rowOffset) {
                    derivedStateOf {
                        val topRow = listState.firstVisibleItemIndex - rowOffset
                        grid.starts.lastOrNull { it.second <= topRow }?.first ?: grid.starts.first().first
                    }
                }
                CategoryTabs(
                    categories = grid.starts.map { it.first },
                    selected = current,
                    onSelect = { category ->
                        val row = grid.starts.first { it.first == category }.second
                        // A jump, not an animation: animating across hundreds of rows stutters.
                        scope.launch { listState.scrollToItem(row + rowOffset) }
                    },
                )
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                if (onClear != null) {
                    item(key = "clear", contentType = "clear") {
                        TextButton(onClick = onClear) { Text("Remove emoji") }
                    }
                }
                items(grid.rows, key = { it.first().emoji }, contentType = { "row" }) { row ->
                    EmojiRow(row, paint, onPick)
                }
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    categories: List<EmojiCategory>,
    selected: EmojiCategory,
    onSelect: (EmojiCategory) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        categories.forEach { category ->
            val isSelected = category == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clickable { onSelect(category) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    category.icon,
                    contentDescription = category.label,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
                if (isSelected) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .width(28.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
        }
    }
}

/**
 * One row of the grid, drawn straight onto the canvas as a single element.
 * A composable per emoji made scrolling stutter: each one built its own text
 * layout, eight times per row.
 */
@Composable
private fun EmojiRow(row: List<EmojiEntry>, paint: Paint, onPick: (String) -> Unit) {
    val currentOnPick by rememberUpdatedState(onPick)
    Box(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .pointerInput(row) {
                detectTapGestures { tap ->
                    val column = (tap.x / (size.width / COLUMNS.toFloat())).toInt()
                    row.getOrNull(column)?.let { currentOnPick(it.emoji) }
                }
            }
            .drawBehind {
                val cellWidth = size.width / COLUMNS
                // Centres the glyphs vertically around the row's middle.
                val baseline = size.height / 2 - (paint.ascent() + paint.descent()) / 2
                drawIntoCanvas { canvas ->
                    row.forEachIndexed { index, entry ->
                        canvas.nativeCanvas.drawText(
                            entry.emoji,
                            cellWidth * index + cellWidth / 2,
                            baseline,
                            paint,
                        )
                    }
                }
            },
    )
}
