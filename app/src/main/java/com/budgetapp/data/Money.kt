package com.budgetapp.data

import java.math.RoundingMode
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// All money math lives here and in BudgetMath.kt, in whole numbers only.
// Payments are exact paise (Long); budgets are whole rupees (Int). Never use
// Float or Double for an amount, and never round paise to rupees before adding.

fun rupeesToPaise(rupees: Int): Long = rupees * 100L

/**
 * Exact paise for an amount as typed or scanned, such as "49.5". Null when it
 * isn't a number. A third decimal, which rupees don't have, rounds half up.
 */
fun parsePaise(text: String): Long? = try {
    text.trim().toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.movePointRight(2)?.longValueExact()
} catch (e: ArithmeticException) {
    null
}

/** The amount as UPI wants it: 4950 paise is "49.50". */
fun paiseToUpiAmount(paise: Long): String = "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"

/**
 * The amount as the keypad types it, so an edit starts from the saved figure:
 * 10000 paise is "100", 4950 is "49.50", 5 is "0.05".
 */
fun paiseToKeypadText(paise: Long): String =
    if (paise % 100 == 0L) "${paise / 100}" else paiseToUpiAmount(paise)

/** Whole percent of the budget spent, rounded down so it never says 100% early. */
fun percentUsed(spentPaise: Long, budgetRupees: Int): Int =
    if (budgetRupees <= 0) 0 else (spentPaise * 100 / rupeesToPaise(budgetRupees)).toInt()

enum class BudgetLevel {
    OK,

    /** 85% or more of the budget is spent. */
    NEAR,

    /** All of the budget is spent, or more. */
    USED_UP,
}

/** Where spending stands against a budget. With no budget there is nothing to warn about. */
fun budgetLevel(spentPaise: Long, budgetRupees: Int): BudgetLevel {
    if (budgetRupees <= 0) return BudgetLevel.OK
    val budget = rupeesToPaise(budgetRupees)
    return when {
        spentPaise >= budget -> BudgetLevel.USED_UP
        spentPaise * 100 >= budget * 85 -> BudgetLevel.NEAR
        else -> BudgetLevel.OK
    }
}

enum class SpendingPace {
    /** Spending less than the even pace: money will be left at month end. */
    SAVING,
    ON_TRACK,

    /** Spending faster than the even pace: the budget will run out early. */
    OVER,
}

/**
 * How spending so far compares with spreading the budget evenly over the month.
 *
 * By the end of day [dayOfMonth], an even pace would have spent
 * `budget × dayOfMonth ÷ daysInMonth`. More than 10% above that is [SpendingPace.OVER],
 * more than 10% below is [SpendingPace.SAVING], anything between is on track.
 * Compared by cross-multiplying, so there is no division and nothing to round.
 */
fun spendingPace(spentPaise: Long, budgetRupees: Int, dayOfMonth: Int, daysInMonth: Int): SpendingPace {
    if (budgetRupees <= 0 || daysInMonth <= 0) return SpendingPace.ON_TRACK
    // spent ÷ (budget × day ÷ days) against 1.10 and 0.90, with both sides scaled up.
    val spent = spentPaise * daysInMonth * 100
    val expected = rupeesToPaise(budgetRupees) * dayOfMonth
    return when {
        spent > expected * 110 -> SpendingPace.OVER
        spent < expected * 90 -> SpendingPace.SAVING
        else -> SpendingPace.ON_TRACK
    }
}

/** Formats with Indian digit grouping, e.g. 125000 becomes ₹1,25,000. */
fun formatRupees(amount: Int): String = formatPaise(amount * 100L)

/** Like [formatRupees] but exact: paise are shown whenever there are any, e.g. ₹49.50. */
fun formatPaise(paise: Long): String {
    val sign = if (paise < 0) "−" else ""
    val rupees = abs(paise) / 100
    val rest = abs(paise) % 100
    val text = sign + "₹" + groupIndian(rupees)
    return if (rest == 0L) text else text + "." + rest.toString().padStart(2, '0')
}

/**
 * Last three digits, then pairs: 1,25,000. Done by hand because the system's
 * number formatter groups differently depending on the device's locale data.
 */
private fun groupIndian(rupees: Long): String {
    val digits = rupees.toString()
    if (digits.length <= 3) return digits
    val pairs = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return pairs + "," + digits.takeLast(3)
}

internal const val BACKSPACE = "⌫"

internal fun String.toPaise(): Long = parsePaise(this) ?: 0L

/** Applies one keypad press, allowing at most 7 rupee digits and 2 decimals. */
internal fun String.afterKey(key: String): String = when (key) {
    BACKSPACE -> dropLast(1)
    "." -> if (contains('.')) this else if (isEmpty()) "0." else "$this."
    else -> {
        val decimals = substringAfter('.', missingDelimiterValue = "")
        when {
            contains('.') -> if (decimals.length < 2) this + key else this
            this == "0" -> key
            length >= 7 -> this
            else -> this + key
        }
    }
}

/** Digits only; eight of them keeps the figure inside an Int. */
fun cleanAmount(raw: String): String = raw.filter(Char::isDigit).take(8)

/** Up to three digits and one decimal place, e.g. "12.5". */
fun cleanPercent(raw: String): String {
    val kept = raw.filter { it.isDigit() || it == '.' }
    val whole = kept.substringBefore('.').take(3)
    return if ('.' in kept) whole + "." + kept.substringAfter('.').filter(Char::isDigit).take(1) else whole
}
