package com.budgetapp.ui.components

import com.budgetapp.ui.theme.outlinedSurface
import com.budgetapp.ui.theme.PhosphorLight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp

/** The line between two rows inside a card, inset to line up with the rows' text. */
@Composable
fun CardDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** A tap target that shows no ripple, for controls that already show their own pressed state. */
fun Modifier.clickableWithoutRipple(onClickLabel: String? = null, onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClickLabel = onClickLabel,
        onClick = onClick,
    )
}

/**
 * One row of a list drawn as a single card: only the [first] row's top and the
 * [last] row's bottom are rounded, with a line between rows.
 */
@Composable
fun CardListRow(first: Boolean, last: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(
        topStart = if (first) 20.dp else 0.dp,
        topEnd = if (first) 20.dp else 0.dp,
        bottomStart = if (last) 20.dp else 0.dp,
        bottomEnd = if (last) 20.dp else 0.dp,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .outlinedSurface(shape),
    ) {
        if (!first) CardDivider()
        content()
    }
}

/** What a list says when it has nothing in it, centred a little way down. */
@Composable
fun EmptyListText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 24.dp, end = 24.dp),
    )
}

/** The top row of a full-screen sheet: a back arrow, then [content] (a title or a search box). */
@Composable
fun BackBar(onBack: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(PhosphorLight.ArrowLeft, contentDescription = "Back")
        }
        content()
    }
}
