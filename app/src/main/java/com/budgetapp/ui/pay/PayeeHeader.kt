package com.budgetapp.ui.pay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetapp.data.Payment
import com.budgetapp.data.UNKNOWN_PAYEE
import com.budgetapp.data.title
import com.budgetapp.ui.theme.outlinedSurface

@Composable
internal fun PayeeHeader(
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
                .outlinedSurface(CircleShape),
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
internal fun Modifier.hangingLeft(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(0, placeable.height) { placeable.place(-placeable.width, 0) }
}

/** Centres the element on the edge it is aligned to, taking up no width itself. */
internal fun Modifier.straddlingEdge(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(0, placeable.height) { placeable.place(-placeable.width / 2, 0) }
}
