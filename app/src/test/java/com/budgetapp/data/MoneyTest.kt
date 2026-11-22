package com.budgetapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `typed amounts become exact paise`() {
        assertEquals(4_950L, parsePaise("49.5"))
        assertEquals(4_950L, parsePaise("49.50"))
        assertEquals(10_000L, parsePaise("100"))
        assertEquals(0L, parsePaise("0."))
        assertEquals(1L, parsePaise("0.01"))
        // The classic float traps.
        assertEquals(30L, parsePaise("0.30"))
        assertEquals(1_999L, parsePaise("19.99"))
        assertEquals(999_999_999L, parsePaise("9999999.99"))
        assertNull(parsePaise(""))
        assertNull(parsePaise("."))
        assertNull(parsePaise("abc"))
    }

    @Test
    fun `a third decimal rounds half up`() {
        assertEquals(1_001L, parsePaise("10.005"))
        assertEquals(1_000L, parsePaise("10.004"))
    }

    @Test
    fun `upi amount always has two decimals`() {
        assertEquals("49.50", paiseToUpiAmount(4_950))
        assertEquals("100.00", paiseToUpiAmount(10_000))
        assertEquals("0.05", paiseToUpiAmount(5))
        assertEquals("9999999.99", paiseToUpiAmount(999_999_999))
    }

    @Test
    fun `what is typed is what is sent`() {
        for (text in listOf("0.01", "0.3", "1.1", "19.99", "33.33", "1234.56", "9999999.99")) {
            assertEquals(parsePaise(text), parsePaise(paiseToUpiAmount(parsePaise(text)!!)))
        }
    }

    @Test
    fun `percent used rounds down and never reaches 100 early`() {
        assertEquals(0, percentUsed(0, 6_000))
        assertEquals(0, percentUsed(59_99, 6_000))
        assertEquals(1, percentUsed(60_00, 6_000))
        assertEquals(99, percentUsed(5_999_99, 6_000))
        assertEquals(100, percentUsed(6_000_00, 6_000))
        assertEquals(150, percentUsed(9_000_00, 6_000))
        assertEquals(0, percentUsed(500_00, 0))
    }

    @Test
    fun `budget level changes exactly at 85 percent and at the full budget`() {
        assertEquals(BudgetLevel.OK, budgetLevel(849_99, 1_000))
        assertEquals(BudgetLevel.NEAR, budgetLevel(850_00, 1_000))
        assertEquals(BudgetLevel.NEAR, budgetLevel(999_99, 1_000))
        assertEquals(BudgetLevel.USED_UP, budgetLevel(1_000_00, 1_000))
        assertEquals(BudgetLevel.USED_UP, budgetLevel(1_000_01, 1_000))
        assertEquals(BudgetLevel.OK, budgetLevel(5_000_00, 0))
    }

    @Test
    fun `pace compares spending with an even spread, 10 percent either side`() {
        // ₹6,000 over 30 days: by the end of day 15 an even pace has spent ₹3,000.
        assertEquals(SpendingPace.SAVING, spendingPace(0, 6_000, 15, 30))
        assertEquals(SpendingPace.SAVING, spendingPace(2_699_99, 6_000, 15, 30))
        assertEquals(SpendingPace.ON_TRACK, spendingPace(2_700_00, 6_000, 15, 30))
        assertEquals(SpendingPace.ON_TRACK, spendingPace(3_000_00, 6_000, 15, 30))
        assertEquals(SpendingPace.ON_TRACK, spendingPace(3_300_00, 6_000, 15, 30))
        assertEquals(SpendingPace.OVER, spendingPace(3_300_01, 6_000, 15, 30))
        assertEquals(SpendingPace.OVER, spendingPace(7_000_00, 6_000, 15, 30))
    }

    @Test
    fun `pace at the ends of the month and without a budget`() {
        // Day 1 of 31: an even pace is ₹6,000 ÷ 31 = ₹193.55, so ₹212.90 is the upper edge.
        assertEquals(SpendingPace.ON_TRACK, spendingPace(212_90, 6_000, 1, 31))
        assertEquals(SpendingPace.OVER, spendingPace(212_91, 6_000, 1, 31))
        // Last day: the whole budget is the even pace.
        assertEquals(SpendingPace.ON_TRACK, spendingPace(6_000_00, 6_000, 30, 30))
        assertEquals(SpendingPace.SAVING, spendingPace(5_399_99, 6_000, 30, 30))
        assertEquals(SpendingPace.OVER, spendingPace(6_600_01, 6_000, 30, 30))
        assertEquals(SpendingPace.ON_TRACK, spendingPace(5_000_00, 0, 15, 30))
    }

    @Test
    fun `amounts are printed exactly`() {
        assertEquals("₹0", formatPaise(0))
        assertEquals("₹49.50", formatPaise(4_950))
        assertEquals("₹0.05", formatPaise(5))
        assertEquals("₹1,25,000", formatPaise(1_25_000_00))
        assertEquals("₹1,25,000.01", formatPaise(1_25_000_01))
        assertEquals("−₹1.50", formatPaise(-150))
        assertEquals("₹1,25,000", formatRupees(1_25_000))
        assertEquals("₹999", formatRupees(999))
        assertEquals("₹1,000", formatRupees(1_000))
        assertEquals("₹12,34,56,789", formatRupees(12_34_56_789))
    }

    @Test
    fun `a saved amount opens on the keypad exactly as it was`() {
        assertEquals("100", paiseToKeypadText(100_00))
        assertEquals("49.50", paiseToKeypadText(49_50))
        assertEquals("0.05", paiseToKeypadText(5))
        assertEquals("9999999.99", paiseToKeypadText(9_999_999_99))
        for (paise in listOf(1L, 5L, 10L, 99L, 100L, 49_50L, 100_49L, 1_00_000_00L, 9_999_999_99L)) {
            assertEquals(paise, parsePaise(paiseToKeypadText(paise)))
        }
    }
}
