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
