package com.budgetapp.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/*
 * How dates and times read across the app, in one place so every screen says
 * them the same way. Always English, whatever the phone's language, to match
 * the rest of the app's text. Times are lower case: "3:19 pm".
 */

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val monthNameFormat = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
private val monthShortFormat = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

/** "3:19 pm". */
fun timeText(at: TemporalAccessor): String = timeFormat.format(at).lowercase()

/** "12 Nov". */
fun dayText(day: TemporalAccessor): String = dayFormat.format(day)

/** "November". */
fun monthName(month: TemporalAccessor): String = monthNameFormat.format(month)

/** "Nov". */
fun monthShortName(month: TemporalAccessor): String = monthShortFormat.format(month)

/** "12 Nov, 3:19 pm". */
fun dayTimeText(millis: Long, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    return "${dayText(at)}, ${timeText(at)}"
}

/** "Today, 3:19 pm", or "12 Nov, 3:19 pm" for an earlier day. */
fun whenText(millis: Long, now: ZonedDateTime): String {
    val at = Instant.ofEpochMilli(millis).atZone(now.zone)
    val day = if (at.toLocalDate() == now.toLocalDate()) "Today" else dayText(at)
    return "$day, ${timeText(at)}"
}
