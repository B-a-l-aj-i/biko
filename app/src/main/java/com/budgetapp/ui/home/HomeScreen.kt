@file:OptIn(ExperimentalFoundationApi::class)

package com.budgetapp.ui.home

import com.budgetapp.data.formatPaise
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import com.budgetapp.ui.theme.BudgetAppTheme
import com.budgetapp.data.rupeesToPaise
import com.budgetapp.ui.theme.LocalStatusColors
import com.budgetapp.ui.theme.LocalPalette
import com.budgetapp.ui.theme.cardBorder
import com.budgetapp.ui.theme.cardOutline
import com.budgetapp.ui.theme.labelOn
import com.budgetapp.ui.components.ManualTag
import com.budgetapp.ui.components.donutSegment
import com.budgetapp.ui.theme.PhosphorLight
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import android.os.Build
import androidx.compose.foundation.layout.statusBars
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.mutableIntStateOf
import com.budgetapp.ui.components.GRABBER_HEIGHT
import com.budgetapp.ui.components.Grabber
import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.cos
import kotlin.math.sin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.ExperimentalFoundationApi

private val CardShape = RoundedCornerShape(20.dp)
private const val HIDDEN_AMOUNT = "₹ ••••"
private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val monthShortWithYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
private val MONTH_MENU_WIDTH = 128.dp

/**
 * The month pill's list: a small "Month" heading, then one short row per month
 * ("Nov", or "Dec 2025" outside the current year), oldest at the top and the
 * current month at the bottom, with a tick on the month Home is showing.
 * Narrow on purpose: Material's menu rows are at least 112dp wide and pad
 * generously, so these rows are drawn by hand. Its background is the card
 * colour (white in the light look): Material tints a menu with its surface
 * tint by elevation, which is what made it grey, so that tint is turned off.
 */
@Composable
private fun MonthMenu(
    expanded: Boolean,
    months: List<YearMonth>,
    shown: YearMonth?,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = months.firstOrNull() ?: return
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surfaceContainer = MaterialTheme.colorScheme.surface,
            // The tint is laid over the surface at the menu's elevation; tinting white
            // with white keeps it white (a transparent tint still darkens it).
            surfaceTint = MaterialTheme.colorScheme.surface,
        ),
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(14.dp)),
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            offset = DpOffset(0.dp, 6.dp),
            modifier = Modifier.width(MONTH_MENU_WIDTH),
        ) {
            Text(
                "Month",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp),
            )
            months.asReversed().forEach { month ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(month) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        month.format(if (month.year == current.year) monthShort else monthShortWithYear),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (month == shown) {
                        Icon(PhosphorLight.Check, contentDescription = "Shown now", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

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

/** Whether the eye in the header has hidden the amounts. */
private val LocalAmountsHidden = compositionLocalOf { false }

/** Blurring needs Android 12; older phones swap hidden amounts for dots instead. */
private val CAN_BLUR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Enough to melt the largest figure on the page into a smudge. */
private val AMOUNT_BLUR = 14.dp

/**
 * For any text that shows an amount: when the eye hides amounts the text blurs
 * out of sight, and blurs back into focus when they are shown again. A screen
 * reader is told the amount is hidden rather than reading it out.
 */
@Composable
private fun Modifier.amountBlur(): Modifier {
    val hidden = LocalAmountsHidden.current
    val radius by animateDpAsState(
        if (hidden && CAN_BLUR) AMOUNT_BLUR else 0.dp,
        tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "amountBlur",
    )
    val blurred = if (radius > 0.dp) blur(radius, BlurredEdgeTreatment.Unbounded) else this
    return if (hidden) blurred.clearAndSetSemantics { contentDescription = "Amount hidden" } else blurred
}

/** How far the page must be pulled down before letting go shows another line. */
private val PULL_THRESHOLD = 72.dp

/** The grabber's top sits this far above the page's top edge, so its point stays clear of the header. */
private val PULL_GRABBER_LIFT = GRABBER_HEIGHT + 2.dp

/** The grabber is fully visible, still nearly flat, once the page is this far down. */
private val PULL_GRABBER_SHOWN = 32.dp

/** The grabber stays unseen until the page is this far down, clear of the status bar. */
private val PULL_GRABBER_START = 16.dp

/** The furthest the page follows a pull. */
private val PULL_MAX = 180.dp

/** The page moves this much of the finger's distance, so the pull feels weighted. */
private const val PULL_RESISTANCE = 0.5f

/**
 * Pull down from the top and the whole page comes with the finger, then
 * springs back on release. There is nothing to load, since the data is on the
 * phone, so there is no spinner: a pull past the threshold calls [onRelease],
 * which shows another pace line.
 */
private class PagePull(
    private val thresholdPx: Float,
    private val maxPx: Float,
    private val scope: CoroutineScope,
    private val onArmed: () -> Unit,
    private val onRelease: () -> Unit,
) : NestedScrollConnection {
    /** How far down the page is drawn, in pixels. */
    var offset by mutableFloatStateOf(0f)
        private set
    private var settle: Job? = null

    private fun moveTo(target: Float) {
        settle?.cancel()
        val to = target.coerceIn(0f, maxPx)
        if (offset < thresholdPx && to >= thresholdPx) onArmed()
        offset = to
    }

    // Dragging back up first returns the page, before the list scrolls.
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || offset <= 0f) return Offset.Zero
        val before = offset
        moveTo(offset + available.y)
        return Offset(0f, offset - before)
    }

    // What the list could not use, because it is already at its top, pulls the page.
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        moveTo(offset + available.y * PULL_RESISTANCE)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (offset <= 0f) return Velocity.Zero
        if (offset >= thresholdPx) onRelease()
        settle = scope.launch {
            animate(
                initialValue = offset,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> offset = value.coerceAtLeast(0f) }
        }
        // The page takes the fling, so the list doesn't also coast.
        return available
    }
}

/**
 * The frosted glass of the top header: a heavy blur so text under
 * it melts into colour instead of showing through, a light wash of the page's
 * own colour so its colours still glow, and a touch of grain so it reads as glass.
 */
@Composable
private fun frostStyle(): HazeStyle {
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
private val BOTTOM_BAR_HEIGHT = 64.dp

/**
 * Strip above the bar's buttons where the page fades into the bar, so rows
 * scrolling down disappear softly instead of being cut off at a hard edge.
 */
private val BAR_FADE = 40.dp

/**
 * Plain bar along the bottom, in the page's own colour. No blur: rows fade
 * out above it through [BAR_FADE] and are fully hidden behind the buttons.
 */
@Composable
private fun BottomBar(
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

/**
 * Soft washes of colour behind the page that say how the month is going:
 * green while spending is on or under pace, red once it runs ahead or the
 * budget is used up, lavender with no budget. Green and red are kept for this
 * alone, so the donut never uses them. Black-and-white mode has no wash; the
 * pace line in the header says the same thing in words.
 */
@Composable
private fun ColourWash(spendingWell: Boolean?, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val target = when (spendingWell) {
        true -> palette.washGood
        false -> palette.washBad
        null -> palette.washNone
    }
    val main by animateColorAsState(target, tween(durationMillis = 700), label = "wash")
    Canvas(modifier) {
        val w = size.width
        fun wash(color: Color, center: Offset, radius: Float) = drawCircle(
            brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius),
            radius = radius,
            center = center,
        )
        wash(main, Offset(w * 0.9f, w * 0.05f), w * 0.9f)
        wash(main.copy(alpha = 0.7f), Offset(w * 1.05f, w * 0.85f), w * 0.7f)
        wash(main.copy(alpha = 0.45f), Offset(-w * 0.1f, w * 0.35f), w * 0.6f)
    }
}

/**
 * Pinned to the top, over the status bar too, so the list scrolls under it
 * instead of sliding up into the clock and battery icons.
 */
@Composable
private fun Header(
    monthLabel: String,
    /** Offered by the month pill, newest first; the first is the current month. */
    months: List<YearMonth>,
    /** The month Home is showing, ticked in the list. */
    shownMonth: YearMonth?,
    onMonth: (YearMonth) -> Unit,
    paceMessage: String,
    amountsHidden: Boolean,
    onToggleHidden: () -> Unit,
    haze: HazeState,
    /** Whether the list has moved off its top, so there is something under the bar. */
    scrolled: Boolean,
    modifier: Modifier = Modifier,
) {
    // Clear glass while the page is at the top, frosted once the list is under it.
    val frost by animateFloatAsState(if (scrolled) 1f else 0f, tween(durationMillis = 200), label = "headerFrost")
    // Full frost behind the status bar and the row, fading out over the strip below.
    val density = LocalDensity.current
    val fadePx = with(density) { HEADER_FADE.toPx() }
    val rowBottomPx = WindowInsets.statusBars.getTop(density) + with(density) { HEADER_HEIGHT.toPx() }
    val frostMask = remember(rowBottomPx, fadePx) {
        Brush.verticalGradient(
            colors = listOf(Color.Black, Color.Transparent),
            startY = rowBottomPx,
            endY = rowBottomPx + fadePx,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hazeChild(state = haze, style = frostStyle()) {
                mask = frostMask
                // Fades the whole effect, blur included, so at rest it is fully clear.
                alpha = frost
                // Fully clear means nothing to blur: skip the blur pass, which
                // otherwise runs on the GPU every frame the page changes (the
                // donut's launch sweep) even though none of it shows.
                blurEnabled = frost > 0f
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = HEADER_TOP_GAP, bottom = HEADER_BOTTOM_GAP)
                .height(HEADER_ROW_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The pill and the eye are the same height, so they sit on one line.
            // A tap or a long press lists the months to look back at.
            var picking by remember { mutableStateOf(false) }
            val pillHaptics = LocalHapticFeedback.current
            Box(Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(
                        onClickLabel = "Pick a month",
                        onLongClick = {
                            pillHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            picking = true
                        },
                        onClick = { picking = true },
                    )
                    .padding(start = 12.dp, end = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    monthLabel.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    PhosphorLight.CaretDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
            MonthMenu(
                expanded = picking,
                months = months,
                shown = shownMonth ?: months.firstOrNull(),
                onPick = { month ->
                    picking = false
                    onMonth(month)
                },
                onDismiss = { picking = false },
            )
            }
            // A line about the pace of spending, with no amount in it. It changes
            // on each pull to refresh.
            // The new line rises into place as the old one leaves upward.
            AnimatedContent(
                paceMessage,
                transitionSpec = {
                    (slideInVertically(tween(320)) { it } + fadeIn(tween(320))) togetherWith
                        (slideOutVertically(tween(220)) { -it } + fadeOut(tween(160)))
                },
                contentAlignment = Alignment.Center,
                label = "paceMessage",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) { line ->
                // Two lines at most: the bar keeps one height, so the page below never jumps.
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier
                    .size(HEADER_ROW_HEIGHT)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .cardOutline(CircleShape)
                    .clickable(onClick = onToggleHidden),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (amountsHidden) PhosphorLight.EyeSlash else PhosphorLight.Eye,
                    contentDescription = if (amountsHidden) "Show amounts" else "Hide amounts",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        // The strip the frost fades out over, below the header's own row.
        Spacer(Modifier.height(HEADER_FADE))
    }
}

/**
 * Strip below the header's row where its frost fades to nothing, so the list
 * melts into the header with no hard edge. It adds no room above the list:
 * the list still starts right under the row and scrolls through the fade.
 */
private val HEADER_FADE = 28.dp

/** The header's pill and eye button are this tall. */
private val HEADER_ROW_HEIGHT = 32.dp
private val HEADER_TOP_GAP = 12.dp
private val HEADER_BOTTOM_GAP = 8.dp

/** Height of the pinned header, not counting the status bar above it. */
private val HEADER_HEIGHT = HEADER_TOP_GAP + HEADER_ROW_HEIGHT + HEADER_BOTTOM_GAP

/**
 * Shows whether the pay-time gate can still run; it fails silently if the user
 * revokes its permissions.
 */
@Composable
private fun ProtectionChip(protectionOn: Boolean) {
    val status = LocalStatusColors.current
    Row {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (protectionOn) status.successContainer else status.warningContainer)
                .cardOutline(CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (protectionOn) status.success else status.onWarningContainer
            Icon(
                if (protectionOn) PhosphorLight.ShieldCheck else PhosphorLight.ShieldWarning,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (protectionOn) "Protection on" else "Protection off",
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }
}

/** A part of the half donut the user can pick out by tapping it or its legend. */
private enum class DonutPart { SPENT, LEFT }

/** Stands in for the donut and its sum for a past month with no payments in it. */
@Composable
private fun EmptyMonth(month: YearMonth?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(PhosphorLight.Receipt, contentDescription = null, tint = muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            month?.let { "No payments in " + it.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)) }
                ?: "No payments",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Nothing was added or spent this month.",
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BudgetSummary(state: HomeUiState, money: (Long) -> String, replays: Int) {
    var focus by rememberSaveable { mutableStateOf<DonutPart?>(null) }
    val toggle: (DonutPart?) -> Unit = { focus = if (it == focus) null else it }
    val colors = donutColors(state)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        HalfDonut(state, money, colors, focus, onTap = toggle, replays = replays, modifier = Modifier.fillMaxWidth())
        if (state.monthlyBudget > 0) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendItem("Spent", colors.spent, DonutPart.SPENT, focus, toggle)
                // Once over budget nothing is left, so the arc is all spent.
                if (state.leftPaise > 0) LegendItem("Left", colors.left, DonutPart.LEFT, focus, toggle)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private data class DonutColors(val spent: Color, val left: Color)

/** Neither is green nor red: those belong to the background, which says how the month is going. */
@Composable
private fun donutColors(state: HomeUiState): DonutColors {
    val palette = LocalPalette.current
    return DonutColors(
        spent = palette.donutSpent,
        left = when {
            state.monthlyBudget > 0 -> palette.donutLeft
            // Black and white has no faint outline colour to borrow: its own grey instead.
            palette.outlined -> palette.emptyArc
            else -> MaterialTheme.colorScheme.outlineVariant
        },
    )
}

@Composable
private fun LegendItem(
    label: String,
    color: Color,
    part: DonutPart,
    focus: DonutPart?,
    onTap: (DonutPart?) -> Unit,
) {
    val alpha by animateFloatAsState(if (focus == null || focus == part) 1f else FADED, label = "legend")
    Row(
        modifier = Modifier
            .alpha(alpha)
            .clip(CircleShape)
            .background(if (focus == part) MaterialTheme.colorScheme.surface else Color.Transparent)
            .then(if (focus == part) Modifier.cardOutline(CircleShape) else Modifier)
            .selectable(selected = focus == part, role = Role.Tab) { onTap(part) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** How see-through the parts not picked out become. */
private const val FADED = 0.35f

/**
 * Half donut split into spent and left, which grows from the left when the
 * screen opens, with the month's sum (budget minus spent equals left) inside.
 * Tapping a part lifts it and fades the rest; tapping inside clears that.
 */
@Composable
private fun HalfDonut(
    state: HomeUiState,
    money: (Long) -> String,
    colors: DonutColors,
    focus: DonutPart?,
    onTap: (DonutPart?) -> Unit,
    /** Pulls to refresh so far; each new one redraws the arc and the sum from nothing. */
    replays: Int,
    modifier: Modifier = Modifier,
) {
    val hasBudget = state.monthlyBudget > 0
    // Floats are for drawing only; every printed figure comes from paise.
    val target = if (hasBudget) state.spentFraction.coerceIn(0f, 1f) else 0f
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(target) {
        // On launch, let the first frames (the whole page composing and laying
        // out for the first time) finish before the sweep starts, so the sweep
        // doesn't share its opening frames with that work and stutter.
        if (sweep.value == 0f) repeat(SWEEP_FRAMES_TO_WAIT) { withFrameNanos { } }
        sweep.animateTo(target, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    // A pull to refresh empties the arc and fills it again from the figures as
    // they are now, so the user sees it worked out afresh.
    val currentTarget by rememberUpdatedState(target)
    LaunchedEffect(replays) {
        if (replays == 0) return@LaunchedEffect
        sweep.snapTo(0f)
        sweep.animateTo(currentTarget, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    val spentLift by animateFloatAsState(if (focus == DonutPart.SPENT) 1f else 0f, label = "spentLift")
    val leftLift by animateFloatAsState(if (focus == DonutPart.LEFT) 1f else 0f, label = "leftLift")
    val spentAlpha by animateFloatAsState(if (focus == DonutPart.LEFT) FADED else 1f, label = "spentAlpha")
    val leftAlpha by animateFloatAsState(if (focus == DonutPart.SPENT) FADED else 1f, label = "leftAlpha")

    // Percent labels only while there is a budget and it isn't overspent; past
    // it the arc is all spent and the centre says how much over.
    val showLabels = hasBudget && state.leftPaise >= 0
    val spentLabel = "${state.spentPercent}%"
    val leftLabel = "${state.leftPercent}%"
    // No colour here: a label inside the band takes one that reads on its part,
    // a label moved inside the arc takes the page's text colour.
    val labelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val pageInk = LocalPalette.current.labelOnPage
    val measurer = rememberTextMeasurer()
    // sweep.value is read only when a tap lands and while drawing, never here:
    // reading it during composition would recompose the donut on every frame of
    // its sweep.
    val currentOnTap by rememberUpdatedState(onTap)

    BoxWithConstraints(modifier) {
        val radius = maxWidth / 2
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(radius)
                .pointerInput(hasBudget) {
                    detectTapGestures { tap ->
                        if (!hasBudget) return@detectTapGestures
                        val g = DonutGeometry(size.width.toFloat(), LIFT.toPx())
                        val dx = tap.x - g.center.x
                        val dy = tap.y - g.center.y
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                        val onBand = distance >= g.inner - 8.dp.toPx() && distance <= g.outer + LIFT.toPx() + 8.dp.toPx()
                        if (!onBand) {
                            currentOnTap(null) // a tap inside the arc clears the highlight
                            return@detectTapGestures
                        }
                        // 180 is the left end, 270 the top, 360 the right end.
                        var angle = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
                        if (angle < 0f) angle += 360f
                        if (angle < 90f) angle = 360f // just below the right end
                        if (angle in 90f..180f) angle = 180f // just below the left end
                        val drawnSweep = sweep.value
                        val spentEnd = 180f + 180f * drawnSweep
                        // A thin sliver of spending still gets a fair-sized target.
                        val part = when {
                            drawnSweep >= 1f -> DonutPart.SPENT
                            angle <= maxOf(spentEnd, 180f + MIN_TAP_SWEEP) -> DonutPart.SPENT
                            else -> DonutPart.LEFT
                        }
                        currentOnTap(part)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val g = DonutGeometry(size.width, LIFT.toPx())
                val thickness = g.outer - g.inner
                val corner = 10.dp.toPx()
                val gap = Math.toDegrees((6.dp.toPx() / (g.inner + thickness / 2)).toDouble()).toFloat()
                val lift = LIFT.toPx()
                val spentOuter = g.outer + lift * spentLift
                val leftOuter = g.outer + lift * leftLift
                val spentFill = colors.spent.copy(alpha = spentAlpha)
                val leftFill = colors.left.copy(alpha = leftAlpha)

                val spentSweep = 180f * sweep.value
                when {
                    spentSweep <= 0f -> donutSegment(g.center, leftOuter, g.inner, 180f, 180f, corner, leftFill)
                    spentSweep >= 180f -> donutSegment(g.center, spentOuter, g.inner, 180f, 180f, corner, spentFill)
                    else -> {
                        // A sliver of spending stays visible, however small.
                        val spentDrawn = (spentSweep - gap / 2).coerceAtLeast(MIN_SWEEP)
                        val leftStart = 180f + spentDrawn + gap
                        donutSegment(g.center, spentOuter, g.inner, 180f, spentDrawn, corner, spentFill)
                        donutSegment(g.center, leftOuter, g.inner, leftStart, 360f - leftStart, corner, leftFill)
                    }
                }

                if (showLabels) {
                    val mid = g.inner + thickness / 2
                    val roomPx = LABEL_ROOM.toPx()
                    // Every part with any share gets its percent: inside the band
                    // when the text fits along it, otherwise just inside the arc,
                    // next to its own part.
                    fun label(text: String, start: Float, sweepDeg: Float, alpha: Float, fill: Color) {
                        if (sweepDeg <= 0f) return
                        val angle = Math.toRadians((start + sweepDeg / 2).toDouble())
                        val layout = measurer.measure(text, labelStyle)
                        val half = Offset(layout.size.width / 2f, layout.size.height / 2f)
                        val alongBand = mid * Math.toRadians(sweepDeg.toDouble()).toFloat()
                        val fits = alongBand >= layout.size.width + roomPx
                        val r = if (fits) mid else g.inner - roomPx - half.getDistance()
                        val at = Offset(
                            g.center.x + r * cos(angle).toFloat() - half.x,
                            g.center.y + r * sin(angle).toFloat() - half.y,
                        )
                        drawText(layout, color = if (fits) labelOn(fill) else pageInk, topLeft = at, alpha = alpha)
                    }
                    // The labels carry the final percentages, so they stay hidden
                    // while the arc is still filling (a part-filled arc would not
                    // match them) and fade in over the last stretch of the sweep.
                    val settled = if (target > 0f) {
                        ((sweep.value / target - LABELS_APPEAR_FROM) / (1f - LABELS_APPEAR_FROM)).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    label(spentLabel, 180f, spentSweep, spentAlpha * settled, colors.spent)
                    label(leftLabel, 180f + spentSweep, 180f - spentSweep, leftAlpha * settled, colors.left)
                }
            }
            CentreSum(
                state = state,
                money = money,
                focus = focus,
                replays = replays,
                // The minus hangs outside the block, so this centres the figures and their line.
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Where the donut sits in a box of [width]: room is kept at the edge for a lifted part. */
private class DonutGeometry(width: Float, lift: Float) {
    val outer = width / 2 - lift
    val inner = outer * (1 - DONUT_THICKNESS)
    val center = Offset(width / 2, width / 2)
}

/** How far a tapped part rises out of the arc. */
private val LIFT = 6.dp

/** Share of the sweep that must be done before the percent labels start to fade in. */
private const val LABELS_APPEAR_FROM = 0.85f

/** Frames the donut waits on launch before sweeping, so it starts after the first heavy frames. */
private const val SWEEP_FRAMES_TO_WAIT = 3

/** Smallest arc, in degrees, that a tap on the spent part lands in. */
private const val MIN_TAP_SWEEP = 12f

/** Share of the radius the donut's band takes up. */
private const val DONUT_THICKNESS = 0.27f

/** Smallest arc, in degrees, drawn for a part that isn't zero. */
private const val MIN_SWEEP = 2.5f

/** Clear space a percent label keeps around it, along the band or from the arc's inner edge. */
private val LABEL_ROOM = 10.dp

/**
 * The month as a sum, like on paper, with no labels: the layout says it.
 *
 *     ₹5,000
 *   − ₹4,201
 *     ──────
 *      ₹799
 *
 * The two figures above the line are right-aligned on one edge, with
 * same-width digits, so the places line up. The minus sits close to the left
 * of the figure it takes away, and the line is only as wide as the widest
 * figure. The answer is the same size as the rest and centred under the line.
 */
@Composable
private fun CentreSum(
    state: HomeUiState,
    money: (Long) -> String,
    focus: DonutPart?,
    /** Pulls to refresh so far; each new one writes the sum out again, line by line. */
    replays: Int,
    modifier: Modifier = Modifier,
) {
    if (state.monthlyBudget <= 0) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Spent", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(state.spentPaise), style = sumStyle(RESULT_SIZE), modifier = Modifier.amountBlur())
        }
        return
    }
    // A picked-out part keeps its own line clear and fades the others.
    val budgetAlpha by animateFloatAsState(if (focus == null) 1f else FADED, label = "budgetRow")
    val spentAlpha by animateFloatAsState(if (focus == DonutPart.LEFT) FADED else 1f, label = "spentRow")
    val leftAlpha by animateFloatAsState(if (focus == DonutPart.SPENT) FADED else 1f, label = "leftRow")
    // Over budget the result is negative and says so with its own minus, in red.
    val resultColor = if (state.leftPaise < 0) LocalStatusColors.current.danger else MaterialTheme.colorScheme.onSurface

    // After a pull to refresh the sum is written out again in order: the
    // budget, then what was spent, then the line and what is left. Only the
    // true figures are ever shown; nothing counts up through wrong ones.
    val written = remember { Animatable(1f) }
    LaunchedEffect(replays) {
        if (replays == 0) return@LaunchedEffect
        written.snapTo(0f)
        written.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))
    }
    // Line [index] of three: how far it has appeared, read while drawing only.
    fun Modifier.line(index: Int, alpha: () -> Float) = graphicsLayer {
        val shown = (written.value * 3f - index).coerceIn(0f, 1f)
        this.alpha = alpha() * shown
        translationY = (1f - shown) * 6.dp.toPx()
        // Fade each glyph in place rather than through an offscreen layer: a
        // layer is cut to the row's bounds, which would slice off the minus
        // that hangs outside them whenever the row is faded.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

    Column(modifier.width(IntrinsicSize.Max)) {
        SumRow("", money(rupeesToPaise(state.monthlyBudget)), sumStyle(LINE_SIZE), Modifier.line(0) { budgetAlpha })
        SumRow("−", money(state.spentPaise), sumStyle(LINE_SIZE), Modifier.line(1) { spentAlpha })
        Column(Modifier.line(2) { leftAlpha }) {
            Box(
                Modifier
                    .padding(top = 6.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
            // The answer is the same size as the figures above it and sits
            // centred under the line.
            // Just the figure: over budget it is negative, in red. A word after it
            // crowded the sum (the user's choice to keep it out).
            SumRow("", money(state.leftPaise), sumStyle(LINE_SIZE, resultColor), arrangement = Arrangement.Center)
        }
    }
}

/** Space between the minus and the figure it belongs to. */
private val SUM_SIGN_GAP = 6.dp
/** Every figure in the sum, the answer included. */
private val LINE_SIZE = 20.sp

/** The lone spent figure shown when there is no budget to subtract from. */
private val RESULT_SIZE = 32.sp

/** One weight for every figure in the sum. */
@Composable
private fun sumStyle(size: TextUnit, color: Color = MaterialTheme.colorScheme.onSurface) = TextStyle(
    fontSize = size,
    lineHeight = size * 1.25f,
    fontWeight = FontWeight.Normal,
    color = color,
    // Same-width digits, so units sit under units and thousands under thousands.
    fontFeatureSettings = "tnum",
)

@Composable
private fun SumRow(
    sign: String,
    amount: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    /** Where the figure sits across the sum: the right edge for the lines being subtracted. */
    arrangement: Arrangement.Horizontal = Arrangement.End,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = arrangement) {
        if (sign.isNotEmpty()) {
            // Hangs just left of its figure and takes no width, so the sum is
            // only as wide as its figures and the line under it matches them.
            Text(
                sign,
                style = style,
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(0, placeable.height) {
                        placeable.place(-placeable.width - SUM_SIGN_GAP.roundToPx(), 0)
                    }
                },
            )
        }
        Text(
            amount,
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.amountBlur(),
        )
    }
}

@Composable
private fun PendingCard(
    pending: List<PendingPayment>,
    money: (Long) -> String,
    onClick: () -> Unit,
) {
    val status = LocalStatusColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(status.warningContainer)
            .cardOutline(CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorLight.Tag, contentDescription = null, tint = status.onWarningContainer)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (pending.size == 1) "1 payment to confirm" else "${pending.size} payments to confirm",
                fontWeight = FontWeight.Normal,
                color = status.onWarningContainer,
            )
            Text(
                pending.take(2).joinToString(" · ") { "${money(it.amountPaise)} to ${it.payee}" },
                style = MaterialTheme.typography.bodySmall,
                color = status.onWarningContainer,
                // Amounts and names share this line, so the whole line blurs.
                modifier = Modifier.amountBlur(),
            )
        }
        Icon(PhosphorLight.CaretRight, contentDescription = null, tint = status.onWarningContainer)
    }
}

@Composable
private fun ActionCard(
    label: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(onClick = onClick, shape = CardShape, border = cardBorder(), modifier = modifier) {
        // The icon on top, then the label and a one-line description under it.
        Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(20.dp))
            // Kept on one line each so the cards in the row stay the same height.
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Normal, maxLines = 1, softWrap = false)
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentCard(recent: List<Transaction>, money: (Long) -> String, onOpen: (Long) -> Unit) {
    Surface(shape = CardShape, border = cardBorder(), modifier = Modifier.fillMaxWidth()) {
        // No side padding here: each row pads itself, so its press highlight
        // spans the full card and the card's shape rounds it at the ends.
        Column {
            recent.forEachIndexed { index, txn ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(txn.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                txn.payee,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (txn.manual) {
                                Spacer(Modifier.width(8.dp))
                                ManualTag()
                            }
                        }
                        txn.note?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row {
                            val small = MaterialTheme.typography.bodySmall
                            val tint = MaterialTheme.colorScheme.onSurfaceVariant
                            val share = txn.totalPaidPaise
                            Text(
                                "${txn.category} · ${txn.time}" + if (share != null) " · my share of " else "",
                                style = small,
                                color = tint,
                            )
                            // Its own text, so only the figure blurs when amounts are hidden.
                            if (share != null) {
                                Text(money(share), style = small, color = tint, modifier = Modifier.amountBlur())
                            }
                        }
                    }
                    Text(
                        "−${money(txn.amountPaise)}",
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.amountBlur(),
                    )
                }
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
