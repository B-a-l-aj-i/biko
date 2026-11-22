package com.budgetapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetapp.ui.theme.LocalPalette
import com.budgetapp.ui.theme.PhosphorLight
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * The frosted glass of the top header: a heavy blur so text under
 * it melts into colour instead of showing through, a light wash of the page's
 * own colour so its colours still glow, and a touch of grain so it reads as glass.
 */
@Composable
internal fun frostStyle(): HazeStyle {
    val palette = LocalPalette.current
    return remember(palette) {
        HazeStyle(
            backgroundColor = palette.frostBackground,
            tint = HazeTint(palette.frostTint),
            blurRadius = 40.dp,
            noiseFactor = 0.12f,
        )
    }
}

/** Height of the bar's buttons, not counting the system navigation area below them. */
internal val BOTTOM_BAR_HEIGHT = 64.dp

/**
 * Strip above the bar's buttons where the page fades into the bar, so rows
 * scrolling down disappear softly instead of being cut off at a hard edge.
 */
internal val BAR_FADE = 40.dp

/**
 * Plain bar along the bottom, in the page's own colour. No blur: rows fade
 * out above it through [BAR_FADE] and are fully hidden behind the buttons.
 */
@Composable
internal fun BottomBar(
    onProfile: () -> Unit,
    onSearch: () -> Unit,
    onReview: () -> Unit,
    /** Shown as a count on the Review button; none when zero. */
    toReview: Int,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fadePx = with(LocalDensity.current) { BAR_FADE.toPx() }
    val page = MaterialTheme.colorScheme.background
    Column(
        modifier = modifier
            .fillMaxWidth()
            // See-through at the top of the fade strip, solid page colour from its bottom down.
            .background(
                Brush.verticalGradient(
                    colors = listOf(page.copy(alpha = 0f), page),
                    startY = 0f,
                    endY = fadePx,
                ),
            ),
    ) {
        // Takes no touches: taps here still reach the list.
        Spacer(Modifier.height(BAR_FADE))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(BOTTOM_BAR_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarItem(PhosphorLight.User, "Profile", onProfile, Modifier.weight(1f))
            BarItem(PhosphorLight.Receipt, "Transactions", onSearch, Modifier.weight(1f))
            BarItem(PhosphorLight.ListChecks, "Review", onReview, Modifier.weight(1f), count = toReview)
            BarItem(PhosphorLight.PlusCircle, "Add", onAdd, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BarItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A count on the icon's corner; none when zero. */
    count: Int = 0,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                if (count > 0) stateDescription = "$count waiting"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            if (count > 0) {
                // Ink on the page, like the month pill, not alarm red. The ring in
                // the page's colour cuts it cleanly out of the icon's corner.
                val ink = MaterialTheme.colorScheme.onBackground
                val page = MaterialTheme.colorScheme.background
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 9.dp, y = (-7).dp)
                        .border(2.dp, page, CircleShape)
                        .padding(2.dp)
                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(CircleShape)
                        .background(ink)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (count > 9) "9+" else count.toString(),
                        color = page,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
