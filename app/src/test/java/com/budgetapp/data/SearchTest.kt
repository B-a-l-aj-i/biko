package com.budgetapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.YearMonth
import java.time.ZonedDateTime

class SearchTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 11, 15, 14, 0, 0, 0, zone)
    private val bounds = monthBounds(YearMonth.from(now), now.zone)

    private fun at(t: ZonedDateTime) = t.toInstant().toEpochMilli()

    private fun payment(
        id: Long,
        payee: String,
        paise: Long,
        category: String,
        at: ZonedDateTime = now.minusHours(id),
        status: PaymentStatus = PaymentStatus.PAID,
        vpa: String? = null,
    ) = Payment(id, payee, vpa, paise, category, null, null, status, at(at))

    private val payments = listOf(
        payment(1, "Swiggy", 349_00, "Food", vpa = "swiggy@icici"),
        payment(2, "Uber", 100_00, "Travel"),
        payment(3, MANUAL_PAYEE, 1_000_00, "Shopping"),
        payment(4, "Chai Point", 49_50, "Food"),
        payment(5, "Pending shop", 100_00, "Food", status = PaymentStatus.PENDING),
        payment(6, "Cancelled shop", 100_00, "Food", status = PaymentStatus.CANCELLED),
        payment(7, "Last month", 100_00, "Food", at = now.minusMonths(1)),
    )

    private fun search(q: String) = searchPayments(payments, q, bounds.first, bounds.second).map { it.id }

    @Test
    fun `empty query lists this month's paid payments, newest first`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), search(""))
        assertEquals(listOf(1L, 2L, 3L, 4L), search("   "))
    }

    @Test
    fun `text matches payee, category and UPI ID, ignoring case`() {
        assertEquals(listOf(1L), search("swig"))
        assertEquals(listOf(1L, 4L), search("food"))
        assertEquals(listOf(1L), search("ICICI"))
        assertEquals(listOf(3L), search("by hand"))
        assertEquals(emptyList<Long>(), search("zomato"))
    }

    @Test
    fun `text matches a payment's note`() {
        val noted = payments.map { if (it.id == 3L) it.copy(note = "Diwali dress for Amma") else it }
        assertEquals(listOf(3L), searchPayments(noted, "amma", bounds.first, bounds.second).map { it.id })
        assertEquals(emptyList<Long>(), search("amma"))
    }

    @Test
    fun `an amount matches exactly, never by rounding or by digits inside`() {
        assertEquals(listOf(2L), search("100"))
        assertEquals(listOf(3L), search("₹1,000"))
        assertEquals(listOf(4L), search("49.50"))
        assertEquals(listOf(4L), search("49.5"))
        // ₹49.50 is not ₹50, and 10 is not a part of 100.
        assertEquals(emptyList<Long>(), search("50"))
        assertEquals(emptyList<Long>(), search("10"))
    }

    @Test
    fun `pending, cancelled and other months never show`() {
        assertEquals(listOf(2L), search("100"))
        assertEquals(emptyList<Long>(), search("shop").filter { it >= 5 })
    }

    @Test
    fun `the sum of the results is exact paise`() {
        val found = searchPayments(payments, "food", bounds.first, bounds.second)
        assertEquals(398_50L, found.sumOf { it.amountPaise })
    }

    @Test
    fun `amount query reads only plain amounts`() {
        assertEquals(1_200_00L, amountQuery("₹1,200"))
        assertEquals(49_50L, amountQuery("49.50"))
        assertNull(amountQuery("49.505"))
        assertNull(amountQuery("1.2.3"))
        assertNull(amountQuery("uber"))
        assertNull(amountQuery("0"))
        assertNull(amountQuery("."))
    }

    @Test
    fun `month bounds start on the 1st and end at the next 1st`() {
        assertEquals(at(ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone)), bounds.first)
        assertEquals(at(ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, zone)), bounds.second)
    }

    @Test
    fun `the month list is the last six months always, then older months with payments, newest first`() {
        val list = listOf(
            // Inside the last six months: changes nothing, they are listed anyway.
            payment(10, "A", 100, "Food", at = ZonedDateTime.of(2026, 9, 30, 23, 59, 0, 0, zone)),
            // Older months: March is listed for its payment; April is not (only cancelled).
            payment(11, "B", 100, "Food", at = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, zone)),
            payment(12, "C", 100, "Food", at = ZonedDateTime.of(2026, 4, 2, 9, 0, 0, 0, zone), status = PaymentStatus.CANCELLED),
            payment(13, "D", 100, "Food", at = ZonedDateTime.of(2025, 12, 5, 9, 0, 0, 0, zone)),
            // A date after now is never offered.
            payment(14, "E", 100, "Food", at = ZonedDateTime.of(2026, 12, 3, 9, 0, 0, 0, zone)),
        )
        val lastSix = (11 downTo 6).map { YearMonth.of(2026, it) }
        assertEquals(lastSix + YearMonth.of(2026, 3) + YearMonth.of(2025, 12), monthsToOffer(list, now))
        // With no payments at all, the last six months are still offered, empty.
        assertEquals(lastSix, monthsToOffer(emptyList(), now))
        // Across a year end: January offers back to August of the year before.
        val january = ZonedDateTime.of(2027, 1, 10, 9, 0, 0, 0, zone)
        assertEquals(
            listOf(YearMonth.of(2027, 1)) + (12 downTo 8).map { YearMonth.of(2026, it) },
            monthsToOffer(emptyList(), january),
        )
    }

    @Test
    fun `a picked month lists only its own payments, edges included, and sums exactly`() {
        val list = listOf(
            payment(20, "Last minute of Sep", 49_50, "Food", at = ZonedDateTime.of(2026, 9, 30, 23, 59, 59, 999_000_000, zone)),
            payment(21, "First minute of Oct", 100_49, "Food", at = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, zone)),
            payment(22, "Last minute of Oct", 1, "Fuel", at = ZonedDateTime.of(2026, 10, 31, 23, 59, 59, 999_000_000, zone)),
            payment(23, "First minute of Nov", 200_00, "Fuel", at = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone)),
        )
        val (from, until) = monthBounds(YearMonth.of(2026, 10), zone)
        val october = searchPayments(list, "", from, until)
        assertEquals(listOf(22L, 21L), october.map { it.id })
        assertEquals(100_50L, october.sumOf { it.amountPaise })
    }
}
