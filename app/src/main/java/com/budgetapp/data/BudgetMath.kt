package com.budgetapp.data

import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.time.ZonedDateTime

/**
 * Category budgets for a monthly budget that changed from [oldTotal] to [newTotal],
 * with every category keeping the percentage it had.
 *
 * Whole rupees can't always hold an exact percentage, so the leftover rupees go to
 * the categories that lost the most to rounding. That keeps the sum exact: budgets
 * that filled the old total fill the new one to the rupee.
 *
 * Budgets that added up to more than [oldTotal] are fitted into [newTotal] instead,
 * since percentages of a budget can't pass 100.
 */
fun rescaleBudgets(budgets: List<Int>, oldTotal: Int, newTotal: Int): List<Int> {
    val allocated = budgets.sumOf { it.toLong() }
    val base = maxOf(oldTotal.toLong(), allocated)
    if (base <= 0L || newTotal <= 0) return budgets

    val target = (allocated * newTotal + base / 2) / base
    val floors = budgets.map { it.toLong() * newTotal / base }
    val remainders = budgets.map { it.toLong() * newTotal % base }
    val extra = (target - floors.sum()).toInt()
    val roundedUp = budgets.indices.sortedByDescending { remainders[it] }.take(extra).toSet()
    return floors.mapIndexed { index, floor -> (floor + if (index in roundedUp) 1 else 0).toInt() }
}

/** Share of [total] that [part] is, to one decimal place: "20%", "12.5%". */
fun percentText(part: Int, total: Int): String {
    if (total <= 0) return "0%"
    val tenths = (part.toLong() * 1000 + total / 2) / total
    return if (tenths % 10 == 0L) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"
}

/**
 * Rupees that [percent] of [total] comes to, rounded to the nearest rupee.
 * [percent] is text such as "12.5"; anything that isn't a number counts as zero.
 */
fun percentToRupees(percent: String, total: Int): Int {
    val value = percent.trim().toBigDecimalOrNull() ?: return 0
    return (value * total.toBigDecimal()).divide(100.toBigDecimal(), 0, RoundingMode.HALF_UP).toInt()
}

/** What a budget field means in whole rupees: [text] is rupees, or with [byPercent] a percentage of [monthlyBudget]. */
fun budgetInputRupees(text: String, byPercent: Boolean, monthlyBudget: Int): Int =
    if (byPercent) percentToRupees(text, monthlyBudget) else text.toIntOrNull() ?: 0

/**
 * What a budget field shows for [rupees] after switching to percent (or back),
 * so the amount stays the same. Empty for nothing.
 */
fun budgetInputText(rupees: Int, percent: Boolean, monthlyBudget: Int): String = when {
    rupees == 0 -> ""
    percent -> percentText(rupees, monthlyBudget).removeSuffix("%")
    else -> rupees.toString()
}

/**
 * The most, in whole rupees, that category [name] may be budgeted without the
 * category budgets adding up to more than [monthlyBudget]. [name] null asks for
 * a new category. A category may always keep (or lower) the budget it already
 * has, even when the others already overshoot. With no monthly budget set
 * (zero) there is no cap: [Int.MAX_VALUE].
 *
 * The one rule every screen, dialog and [Repository] uses, so they can't disagree.
 */
fun categoryRoom(monthlyBudget: Int, categories: List<Category>, name: String?): Int {
    if (monthlyBudget <= 0) return Int.MAX_VALUE
    val own = categories.firstOrNull { it.name == name }?.monthlyBudget ?: 0
    val others = categories.filter { it.name != name }.sumOf { it.monthlyBudget.toLong() }
    val free = (monthlyBudget - others).coerceAtLeast(0).toInt()
    return maxOf(free, own)
}

/** Where spending stands against a budget, in the words every screen uses. */
enum class StandingKind {
    /** Comfortably under budget. */
    LEFT,

    /** 85% or more spent (exactly all of it included), but not over. */
    NEAR,

    /** Past the budget by at least a paisa. */
    OVER,
}

/**
 * [kind] and the exact paise behind it: what is left for [StandingKind.LEFT] and
 * [StandingKind.NEAR], how far over for [StandingKind.OVER]. Never negative.
 */
data class BudgetStanding(val kind: StandingKind, val amountPaise: Long) {
    /** "₹1,200 left" or "₹500 over": never a minus sign, which reads like a refund. */
    val words: String
        get() = if (kind == StandingKind.OVER) "${formatPaise(amountPaise)} over" else "${formatPaise(amountPaise)} left"
}

/** How [spentPaise] stands against [budgetRupees]. With no budget (zero or less) it is all "left". */
fun budgetStanding(spentPaise: Long, budgetRupees: Int): BudgetStanding {
    val left = rupeesToPaise(budgetRupees.coerceAtLeast(0)) - spentPaise
    return when {
        left < 0 -> BudgetStanding(StandingKind.OVER, -left)
        budgetLevel(spentPaise, budgetRupees) != BudgetLevel.OK -> BudgetStanding(StandingKind.NEAR, left)
        else -> BudgetStanding(StandingKind.LEFT, left)
    }
}

/** What a payment being added or edited does to its category's budget, in the month it falls in. */
data class PaymentImpact(
    val category: String,
    /** The category's budget, whole rupees; always above zero. */
    val budgetRupees: Int,
    /** The category's spending that month with this payment at its new amount, exact paise. */
    val spentAfterPaise: Long,
    /** The month the payment is in: the edited payment's own month, or now's for a new one. */
    val month: YearMonth,
    /** True when [month] is not the month it is now. */
    val otherMonth: Boolean,
) {
    val standing: BudgetStanding get() = budgetStanding(spentAfterPaise, budgetRupees)

    /** The warning line on Pay, e.g. "This puts Food ₹100 over budget for October". */
    val words: String
        get() {
            val forMonth = if (otherMonth) " for " + monthName(month) else ""
            val money = formatPaise(standing.amountPaise)
            return when (standing.kind) {
                StandingKind.OVER -> "This puts $category $money over budget$forMonth"
                StandingKind.NEAR -> "Only $money left in $category$forMonth after this"
                StandingKind.LEFT -> "$money left in $category$forMonth after this"
            }
        }
}

/**
 * What saving a payment of [amountPaise] under [category] does to that
 * category's budget. [editing] is the saved payment being changed, or null for
 * a new one made at [now]. The month is the payment's own month, picked here so
 * no caller can pass the wrong one; the edited payment's old amount is left out
 * so it isn't counted twice. Only confirmed (paid) payments count as spent.
 *
 * Null when there is nothing to say: no amount, or the category has no budget.
 */
fun paymentImpact(
    payments: List<Payment>,
    categories: List<Category>,
    editing: Payment?,
    amountPaise: Long,
    category: String,
    now: ZonedDateTime,
): PaymentImpact? {
    if (amountPaise <= 0) return null
    val budget = categories.firstOrNull { it.name == category }?.monthlyBudget ?: return null
    if (budget <= 0) return null
    val at = editing?.createdAt?.let { Instant.ofEpochMilli(it).atZone(now.zone) } ?: now
    val month = YearMonth.from(at)
    val (from, until) = monthBounds(month, now.zone)
    val spentOthers = payments
        .filter {
            it.status == PaymentStatus.PAID && it.category == category &&
                it.createdAt >= from && it.createdAt < until && it.id != editing?.id
        }
        .sumOf { it.amountPaise }
    return PaymentImpact(
        category = category,
        budgetRupees = budget,
        spentAfterPaise = spentOthers + amountPaise,
        month = month,
        otherMonth = month != YearMonth.from(now),
    )
}

/** One part of the month's spending, for the Budget screen's half donut. */
data class SpendShare(
    /** A category's name, or [OTHER_SPENDING] for spending under no current category. */
    val name: String,
    val spentPaise: Long,
    /** Share of the total, rounded down, so it never claims more than it has. */
    val percent: Int,
)

/** Name of the share for spending whose category no longer exists. */
const val OTHER_SPENDING = "Other"

/**
 * The month's spending split by category, largest first, for the half donut.
 * Categories that spent nothing are left out. Whatever [totalPaise] holds
 * beyond the categories (payments in a category since removed) becomes an
 * [OTHER_SPENDING] share, so the parts always add up to the total, to the
 * paisa. Equal amounts keep the order of [byCategory].
 */
fun spendShares(byCategory: List<Pair<String, Long>>, totalPaise: Long): List<SpendShare> {
    if (totalPaise <= 0) return emptyList()
    val known = byCategory.filter { it.second > 0 }
    val other = totalPaise - known.sumOf { it.second }
    val parts = if (other > 0) known + (OTHER_SPENDING to other) else known
    return parts
        .sortedByDescending { it.second }
        .map { (name, paise) -> SpendShare(name, paise, (paise * 100 / totalPaise).toInt()) }
}
