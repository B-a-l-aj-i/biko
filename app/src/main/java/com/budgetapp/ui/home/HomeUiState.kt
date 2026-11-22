package com.budgetapp.ui.home

import com.budgetapp.data.BudgetLevel
import com.budgetapp.data.SpendingPace
import com.budgetapp.data.budgetLevel
import com.budgetapp.data.percentUsed
import com.budgetapp.data.rupeesToPaise


/**
 * Everything the home screen shows. Budgets are whole rupees; anything spent or
 * paid is exact paise, so no screen ever shows a rounded sum.
 */
data class HomeUiState(
    val monthLabel: String,
    val monthlyBudget: Int,
    val spentPaise: Long,
    /** Days left in the month, today included. */
    val daysLeft: Int,
    /** Whether the pay-time gate is active; null hides the chip until that feature exists. */
    val protectionOn: Boolean?,
    val pending: List<PendingPayment>,
    val recent: List<Transaction>,
    /** Every category with its spending in the shown month. */
    val allCategories: List<CategoryBudget>,
    /** Sum of the category budgets; the rest of [monthlyBudget] is free to give out. */
    val allocated: Int = monthlyBudget,
    /** How spending compares with an even pace through the month. */
    val pace: SpendingPace = SpendingPace.ON_TRACK,
    /** Today's line for [pace]; empty when there is no budget to pace against. */
    val paceMessage: String = "",
    /** Debits found in alerts that wait for the user's tick or cross, in the shown month only. */
    val toReview: Int = 0,
    /** What the month pill offers, newest first: this month, then earlier months with payments. */
    val months: List<java.time.YearMonth> = emptyList(),
    /** The month these figures are for; null only in previews. */
    val shownMonth: java.time.YearMonth? = null,
    /** True when [shownMonth] is a finished month picked from the month pill. */
    val pastMonth: Boolean = false,
) {
    /** Negative once over budget. */
    val leftPaise: Long
        get() = rupeesToPaise(monthlyBudget) - spentPaise

    val spentPercent: Int
        get() = percentUsed(spentPaise, monthlyBudget)

    /**
     * The rest of 100 after [spentPercent], so the two labels on the half donut
     * always add up to 100%. Spent rounds down, so this rounds up: it never says
     * 0% left while money is still left. Zero with no budget or once over.
     */
    val leftPercent: Int
        get() = if (monthlyBudget > 0) (100 - spentPercent).coerceAtLeast(0) else 0

    val level: BudgetLevel
        get() = budgetLevel(spentPaise, monthlyBudget)

    /**
     * Whether this month's spending is going well, for the page's colour: true
     * while on or under an even pace, false once spending runs ahead of it or
     * the budget is used up, null with no budget to judge against.
     */
    val spendingWell: Boolean?
        get() = when {
            monthlyBudget <= 0 -> null
            // Nothing happened that month: no good or bad to colour.
            isEmptyPastMonth -> null
            level == BudgetLevel.USED_UP -> false
            else -> pace != SpendingPace.OVER
        }

    /**
     * A finished month with nothing paid in it. Home shows an empty state for it
     * instead of the donut and its sum, which would only say ₹0 was spent.
     */
    val isEmptyPastMonth: Boolean
        get() = pastMonth && spentPaise == 0L

    /** For drawing the ring only; never compare or print this. */
    val spentFraction: Float
        get() = if (monthlyBudget > 0) spentPaise.toFloat() / rupeesToPaise(monthlyBudget) else 0f
}

/** A payment sent to a UPI app whose outcome isn't known yet. */
data class PendingPayment(val id: Long, val payee: String, val amountPaise: Long)

data class CategoryBudget(
    val name: String,
    val spentPaise: Long,
    /** Whole rupees. Zero means no budget set. */
    val budget: Int,
    val emoji: String = "",
) {
    /** Negative once over budget. */
    val leftPaise: Long
        get() = rupeesToPaise(budget) - spentPaise

}

data class Transaction(
    val payee: String,
    val category: String,
    val time: String,
    /** What counts against the budget. */
    val amountPaise: Long,
    /** Full bill when the user paid for others too; null when not split. */
    val totalPaidPaise: Long? = null,
    /** The saved payment's id, for opening it to edit. */
    val id: Long = 0,
    /** The day it was paid, as the list's heading shows it: "Today", "Yesterday", "12 Nov". */
    val day: String = "Today",
    /** What the user typed about it; null when there is none or it is already the [payee] line. */
    val note: String? = null,
    /** Added by hand: [payee] is the note or category, and the row shows a "Manual" tag. */
    val manual: Boolean = false,
)

/** Made-up data for the design preview. */
fun sampleHomeState() = HomeUiState(
    monthLabel = "Nov",
    monthlyBudget = 30_000,
    spentPaise = 12_400_00,
    daysLeft = 15,
    protectionOn = true,
    pending = listOf(
        PendingPayment(1, "Ravi", 200_00),
        PendingPayment(2, "Chai Point", 85_50),
    ),
    allCategories = listOf(
        CategoryBudget("Food", 4_300_00, 4_000),
        CategoryBudget("Travel", 2_550_00, 3_000),
        CategoryBudget("Shopping", 1_800_00, 6_000),
    ),
    recent = listOf(
        Transaction("Swiggy", "Food", "1:20 pm", 349_00),
        Transaction("Uber", "Travel", "9:05 am", 210_00, totalPaidPaise = 420_00),
    ),
    pace = SpendingPace.SAVING,
    paceMessage = paceMessage(SpendingPace.SAVING, 0),
)
