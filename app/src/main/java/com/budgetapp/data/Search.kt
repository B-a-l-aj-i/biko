package com.budgetapp.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** Start (inclusive) and end (exclusive) of [month] in [zone], as epoch millis. */
fun monthBounds(month: YearMonth, zone: ZoneId): Pair<Long, Long> {
    val first = month.atDay(1)
    val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to end
}

/** How many months the month picker always offers, the current one included. */
const val RECENT_MONTHS = 6

/**
 * The months the month picker offers, newest first: the current month and the
 * [RECENT_MONTHS] - 1 before it always, even with nothing paid in them (their
 * list then shows an empty state), then any older month that has a confirmed
 * payment. Dates after [now] are never offered.
 */
fun monthsToOffer(payments: List<Payment>, now: ZonedDateTime): List<YearMonth> {
    val current = YearMonth.from(now)
    val recent = (0 until RECENT_MONTHS).map { current.minusMonths(it.toLong()) }
    val older = payments
        .filter { it.status == PaymentStatus.PAID }
        .map { YearMonth.from(Instant.ofEpochMilli(it.createdAt).atZone(now.zone)) }
        .filter { it < recent.last() }
        .toSortedSet(compareByDescending { it })
    return recent + older
}

/**
 * The search screen's list: confirmed payments made between [fromMillis] and
 * [untilMillis] (exclusive) that match [query], newest first. An empty query
 * lists them all. A query matches a payee, a UPI ID or a category by any part
 * of its text, ignoring case; a query that is just an amount, such as "100",
 * "₹1,200" or "49.50", also matches payments of exactly that amount.
 */
fun searchPayments(
    payments: List<Payment>,
    query: String,
    fromMillis: Long,
    untilMillis: Long,
): List<Payment> {
    val inRange = payments
        .filter { it.status == PaymentStatus.PAID && it.createdAt >= fromMillis && it.createdAt < untilMillis }
        .sortedByDescending { it.createdAt }
    val text = query.trim()
    if (text.isEmpty()) return inRange
    val amount = amountQuery(text)
    return inRange.filter { p ->
        p.amountPaise == amount ||
            p.payeeName.contains(text, ignoreCase = true) ||
            p.category.contains(text, ignoreCase = true) ||
            p.note?.contains(text, ignoreCase = true) == true ||
            p.payeeVpa?.contains(text, ignoreCase = true) == true
    }
}

/**
 * Exact paise for a query that is only an amount, such as "₹1,200" or "49.50";
 * null for anything else, including a third decimal place, which no payment has.
 */
fun amountQuery(query: String): Long? {
    val cleaned = query.replace("₹", "").replace(",", "").trim()
    if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1 || !cleaned.all { it.isDigit() || it == '.' }) return null
    if (cleaned.substringAfter('.', "").length > 2) return null
    return parsePaise(cleaned)?.takeIf { it > 0 }
}
