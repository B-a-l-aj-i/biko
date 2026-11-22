@file:OptIn(ExperimentalFoundationApi::class)

package com.budgetapp.ui.home

import com.budgetapp.data.formatPaise
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.budgetapp.ui.theme.BudgetAppTheme
import com.budgetapp.ui.theme.PhosphorLight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.runtime.mutableIntStateOf
import com.budgetapp.ui.components.Grabber
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

import java.time.YearMonth
import androidx.compose.foundation.ExperimentalFoundationApi

internal val CardShape = RoundedCornerShape(20.dp)

@Composable
fun HomeScreen(
    state: HomeUiState,
    onBudget: () -> Unit = {},
    /** Splitting a bill with friends; not built yet. */
    onSplit: () -> Unit = {},
    onPending: () -> Unit = {},
    onSearch: () -> Unit = {},
    onProfile: () -> Unit = {},
    onAdd: () -> Unit = {},
    /** Opens the page of debits found in alerts, waiting for a tick or a cross. */
    onReview: () -> Unit = {},
    /** Opens a payment from this month's list to edit it; takes the payment's id. */
    onTransaction: (Long) -> Unit = {},
    /** Pulled down from the top: rebuild the figures and show another pace line. */
    onRefresh: () -> Unit = {},
    /** A month picked from the month pill's list: opens its transactions. */
    onMonth: (YearMonth) -> Unit = {},
) {
    // Amounts start hidden every time the app opens. Leaving the app (Home,
    // Back, another app) hides them again, so coming back never shows them;
    // a fresh start after the app was closed begins hidden too.
    var amountsHidden by rememberSaveable { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { amountsHidden = true }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    // Counts the pulls that went far enough; each one makes the donut and the
    // sum work themselves out again in front of the user.
    var replays by remember { mutableIntStateOf(0) }
    val pull = remember {
        PagePull(
            thresholdPx = with(density) { PULL_THRESHOLD.toPx() },
            maxPx = with(density) { PULL_MAX.toPx() },
            scope = scope,
            // A light tick the moment the pull is far enough to count.
            onArmed = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            onRelease = {
                currentOnRefresh()
                replays++
            },
        )
    }
    // Takes exact paise; budgets, which are whole rupees, go through rupeesToPaise first.
    // Hidden amounts keep their text and are blurred out of sight by amountBlur;
    // only phones that can't blur get dots instead.
    val money: (Long) -> String = { if (amountsHidden && !CAN_BLUR) HIDDEN_AMOUNT else formatPaise(it) }

    CompositionLocalProvider(LocalAmountsHidden provides amountsHidden) {
    // Surface (not a plain background) so text and icons placed directly on
    // the page pick up onBackground, which matters in dark mode.
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
    // What the top header blurs: everything drawn into the hazed box below.
    val haze = remember { HazeState() }
    val list = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 }
    }
    // System bars and cutout only, not the keyboard: Home has no text field, and
    // reading the keyboard inset here would re-run all of Home on every frame of
    // the keyboard's slide while a sheet above it (Search) raises it, making it janky.
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val layout = LocalLayoutDirection.current
    Box(Modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .haze(state = haze)
            .nestedScroll(pull),
    ) {
        // Behind everything, status bar included; it stays put while the list scrolls.
        // Painted once into its own offscreen layer and reused: its full-screen
        // gradients are costly to paint, and without the layer the GPU repainted
        // them on every frame anything else moved (the donut's sweep, a scroll).
        ColourWash(
            state.spendingWell,
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        )
        LazyColumn(
            state = list,
            // The whole page follows a pull down from the top; the wash stays put.
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = pull.offset },
            // Edge to edge so the list scrolls under the frosted bar; the bottom
            // padding lets the last row scroll clear of it.
            contentPadding = PaddingValues(
                start = 16.dp + bars.calculateStartPadding(layout),
                end = 16.dp + bars.calculateEndPadding(layout),
                // Starts below the pinned header.
                top = 8.dp + HEADER_HEIGHT + bars.calculateTopPadding(),
                bottom = 16.dp + BAR_FADE + BOTTOM_BAR_HEIGHT + bars.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.protectionOn?.let { on -> item { ProtectionChip(on) } }
            item {
                if (state.isEmptyPastMonth) EmptyMonth(state.shownMonth) else BudgetSummary(state, money, replays)
            }
            if (state.pending.isNotEmpty()) {
                item { PendingCard(state.pending, money, onPending) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard(
                        "Budget",
                        "Set limits for each category",
                        PhosphorLight.Wallet,
                        onBudget,
                        Modifier.weight(1f),
                    )
                    ActionCard(
                        "Split",
                        "Share a bill with friends",
                        PhosphorLight.Users,
                        onSplit,
                        Modifier.weight(1f),
                    )
                }
            }
            // This month's payments, one card per day, newest day first.
            for ((day, payments) in state.recent.groupBy { it.day }) {
                item(key = "day-$day") {
                    Text(
                        day,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "card-$day") { RecentCard(payments, money, onTransaction) }
            }
        }
    }
        Header(
            monthLabel = state.monthLabel,
            months = state.months,
            shownMonth = state.shownMonth,
            onMonth = onMonth,
            paceMessage = state.paceMessage.ifEmpty { "No monthly budget set" },
            amountsHidden = amountsHidden,
            onToggleHidden = { amountsHidden = !amountsHidden },
            haze = haze,
            scrolled = scrolled,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { translationY = pull.offset },
        )
        // The very grabber the sheets have, riding on top of the page as it is
        // pulled. It appears as their flat bar, then bends into a down arrow
        // that is full once letting go counts.
        val grabberStartPx = with(density) { PULL_GRABBER_START.toPx() }
        val grabberShownPx = with(density) { PULL_GRABBER_SHOWN.toPx() }
        val pullThresholdPx = with(density) { PULL_THRESHOLD.toPx() }
        Grabber(
            pulled = { (pull.offset - grabberStartPx) / (pullThresholdPx - grabberStartPx) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = bars.calculateTopPadding())
                .graphicsLayer {
                    translationY = pull.offset - PULL_GRABBER_LIFT.toPx()
                    alpha = ((pull.offset - grabberStartPx) / (grabberShownPx - grabberStartPx)).coerceIn(0f, 1f)
                },
        )
        BottomBar(
            onProfile = onProfile,
            onSearch = onSearch,
            onReview = onReview,
            toReview = state.toReview,
            onAdd = onAdd,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 820)
@Composable
private fun HomeScreenPreview() {
    BudgetAppTheme {
        HomeScreen(sampleHomeState())
    }
}
