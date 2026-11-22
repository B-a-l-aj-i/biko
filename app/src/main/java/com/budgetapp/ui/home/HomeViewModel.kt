package com.budgetapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.data.Category
import com.budgetapp.data.Payment
import com.budgetapp.data.PaymentStatus
import com.budgetapp.data.isManual
import com.budgetapp.data.noteLine
import com.budgetapp.data.title
import com.budgetapp.data.Repository
import com.budgetapp.data.monthsToOffer
import com.budgetapp.data.spendingPace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import com.budgetapp.data.rupeesToPaise
import com.budgetapp.data.monthBounds
import java.time.YearMonth
import com.budgetapp.data.CategoryBudget
import com.budgetapp.data.monthShortName
import com.budgetapp.data.timeText
import com.budgetapp.data.monthName
import com.budgetapp.data.dayText

class HomeViewModel(private val repository: Repository) : ViewModel() {

    // "Now" is read each time the data is rebuilt, which also happens whenever
    // the app comes back to the screen, so a new day or month is picked up.
    /** How many times the user has pulled to refresh; each pull shows another pace line. */
    private val refreshes = MutableStateFlow(0)

    /** The month Home shows; null is the current month. Budget and Pay always use [state]. */
    private val shownMonth = MutableStateFlow<YearMonth?>(null)

    /** Always the current month: Budget, Pay and the warnings read this. */
    val state: StateFlow<HomeUiState> =
        combine(
            repository.categories,
            repository.payments,
            repository.overallBudget,
            refreshes,
        ) { categories, payments, overall, refreshes ->
            buildHomeState(categories, payments, overall, ZonedDateTime.now(), refreshes)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // The store has already read the file by now, so the first frame shows
            // the real month. Starting from an empty state instead made every
            // launch redraw with the real figures moments later: the colour wash
            // faded from lavender and the donut's sweep restarted mid-launch.
            repository.snapshot.value.let { data ->
                buildHomeState(data.categories, data.payments, data.overallBudget, ZonedDateTime.now(), refreshes.value)
            },
        )

    /** Home's figures for the month picked from its month pill (the current one until then). */
    val homeState: StateFlow<HomeUiState> =
        combine(state, shownMonth, repository.snapshot) { current, month, data ->
            if (month == null || month == YearMonth.from(ZonedDateTime.now())) {
                current
            } else {
                buildHomeState(data.categories, data.payments, data.overallBudget, ZonedDateTime.now(), refreshes.value, month)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    /** Shows [month] on Home; null, or the current month, goes back to now. */
    fun showMonth(month: YearMonth?) {
        shownMonth.value = month
    }

    /** Rebuilds the figures for the current time and moves to the next pace line. */
    fun refresh() {
        refreshes.update { it + 1 }
    }

    /** Category budgets keep their percentage, so they change with it. */
    fun setOverallBudget(rupees: Int) {
        viewModelScope.launch { repository.setOverallBudget(rupees) }
    }

    fun setCategoryBudget(name: String, rupees: Int) {
        viewModelScope.launch { repository.setCategoryBudget(name, rupees) }
    }
}

/**
 * The home screen's figures for [month], or the month [now] falls in when it is
 * null. [payments] may hold any months. Only confirmed payments count as spent,
 * and every figure is an exact sum of paise: the categories always add up to
 * the total. A past month is measured against today's budgets (budgets keep no
 * history) and is judged as a finished month: no days left, and its pace is
 * the whole month's spending against the whole budget.
 */
internal fun buildHomeState(
    categories: List<Category>,
    payments: List<Payment>,
    overallBudget: Int,
    now: ZonedDateTime,
    /** Pulls to refresh so far; only picks which pace line is shown, never a figure. */
    refreshes: Int = 0,
    month: YearMonth? = null,
): HomeUiState {
    val zone = now.zone
    val today = now.toLocalDate()
    val current = YearMonth.from(now)
    val shown = month ?: current
    val past = shown < current
    val (monthStart, nextMonthStart) = monthBounds(shown, zone)

    val thisMonth = payments
        .filter { it.createdAt in monthStart until nextMonthStart }
        .sortedByDescending { it.createdAt }
    val paid = thisMonth.filter { it.status == PaymentStatus.PAID }
    val spentByCategory = paid.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountPaise } }

    val all = categories.map {
        CategoryBudget(it.name, spentByCategory[it.name] ?: 0L, it.monthlyBudget, it.emoji)
    }
    val allocated = categories.sumOf { it.monthlyBudget }
    val monthlyBudget = if (overallBudget > 0) overallBudget else allocated
    val spentPaise = paid.sumOf { it.amountPaise }
    val pace = if (past) {
        spendingPace(spentPaise, monthlyBudget, shown.lengthOfMonth(), shown.lengthOfMonth())
    } else {
        spendingPace(spentPaise, monthlyBudget, today.dayOfMonth, today.lengthOfMonth())
    }

    return HomeUiState(
        // Three letters (Jan, Feb, Nov), so the header's month button stays small.
        monthLabel = monthShortName(shown),
        monthlyBudget = monthlyBudget,
        spentPaise = spentPaise,
        daysLeft = if (past) 0 else today.lengthOfMonth() - today.dayOfMonth + 1,
        protectionOn = null,
        pending = thisMonth.filter { it.status == PaymentStatus.PENDING }
            .map { PendingPayment(it.id, it.payeeName, it.amountPaise) },
        // Every confirmed payment this month, newest first, each tagged with its day.
        recent = paid.map {
            val at = Instant.ofEpochMilli(it.createdAt).atZone(zone)
            Transaction(
                payee = it.title,
                category = it.category,
                time = timeText(at),
                amountPaise = it.amountPaise,
                id = it.id,
                day = dayLabel(at.toLocalDate(), today),
                note = it.noteLine,
                manual = it.isManual,
            )
        },
        allCategories = all,
        allocated = allocated,
        pace = pace,
        paceMessage = when {
            past && spentPaise == 0L -> pastMonthLine(shown, 0, monthlyBudget)
            monthlyBudget <= 0 -> ""
            past -> pastMonthLine(shown, spentPaise, monthlyBudget)
            else -> paceMessage(pace, today.toEpochDay(), refreshes)
        },
        // Only this month's: a debit from another month waits in that month.
        toReview = thisMonth.count { it.status == PaymentStatus.DETECTED },
        months = monthsToOffer(payments, now),
        shownMonth = shown,
        pastMonth = past,
    )
}

/**
 * How a finished month went, with no amount in it: "Nothing spent in July"
 * when nothing was paid, "October ended within budget" up to and including the
 * budget, "over budget" past it by even a paisa. Compared in paise, never rounded.
 */
internal fun pastMonthLine(month: YearMonth, spentPaise: Long, budgetRupees: Int): String {
    val name = monthName(month)
    return when {
        spentPaise == 0L -> "Nothing spent in $name"
        spentPaise <= rupeesToPaise(budgetRupees) -> "$name ended within budget"
        else -> "$name ended over budget"
    }
}

/** "Today", "Yesterday", or "12 Nov" for an earlier day. */
internal fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> dayText(day)
}
