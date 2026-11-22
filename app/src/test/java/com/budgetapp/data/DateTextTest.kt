package com.budgetapp.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class DateTextTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone)

    @Test
    fun `times are lower case and days are short`() {
        assertEquals("3:19 pm", timeText(at(22, 15, 19)))
        assertEquals("9:05 am", timeText(at(22, 9, 5)))
        assertEquals("12 Nov", dayText(at(12, 9, 0)))
        assertEquals("12 Nov, 12:00 am", dayTimeText(at(12, 0, 0).toInstant().toEpochMilli(), zone))
    }

    @Test
    fun `when text says today only for today`() {
        val now = at(22, 18, 0)
        assertEquals("Today, 3:19 pm", whenText(at(22, 15, 19).toInstant().toEpochMilli(), now))
        assertEquals("21 Nov, 11:59 pm", whenText(at(21, 23, 59).toInstant().toEpochMilli(), now))
    }

    @Test
    fun `month names`() {
        assertEquals("November", monthName(YearMonth.of(2026, 11)))
        assertEquals("Nov", monthShortName(YearMonth.of(2026, 11)))
    }
}
