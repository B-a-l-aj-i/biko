package com.budgetapp.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Typed text: the Pay keypad and the budget fields. */
class InputTextTest {

    private fun type(vararg keys: String) = keys.fold("") { text, key -> text.afterKey(key) }

    @Test
    fun `keypad builds exact paise`() {
        assertEquals(49_50L, type("4", "9", ".", "5").toPaise())
        assertEquals(49_50L, type("4", "9", ".", "5", "0").toPaise())
        assertEquals(0L, "".toPaise())
    }

    @Test
    fun `keypad allows two decimals and seven rupee digits`() {
        assertEquals("1.23", type("1", ".", "2", "3", "4"))
        assertEquals("1234567", type("1", "2", "3", "4", "5", "6", "7", "8"))
        assertEquals("0.", type("."))
        assertEquals("5", type("0", "5"))
        assertEquals("1.", type("1", ".", "."))
        assertEquals("1", type("1", "2", BACKSPACE))
    }

    @Test
    fun `budget fields keep only what fits`() {
        assertEquals("12000", cleanAmount("₹12,000"))
        assertEquals("12345678", cleanAmount("123456789"))
        assertEquals("12.5", cleanPercent("12.55%"))
        assertEquals("100", cleanPercent("1000"))
    }

    @Test
    fun `budget field means the same rupees in either mode`() {
        assertEquals(3_000, budgetInputRupees("3000", byPercent = false, monthlyBudget = 30_000))
        assertEquals(3_000, budgetInputRupees("10", byPercent = true, monthlyBudget = 30_000))
        assertEquals(0, budgetInputRupees("", byPercent = false, monthlyBudget = 30_000))

        // Switching keeps the amount: 3,000 of 30,000 is 10%, and back again.
        assertEquals("10", budgetInputText(3_000, percent = true, monthlyBudget = 30_000))
        assertEquals("3000", budgetInputText(3_000, percent = false, monthlyBudget = 30_000))
        assertEquals("", budgetInputText(0, percent = true, monthlyBudget = 30_000))
        val back = budgetInputRupees(budgetInputText(3_000, true, 30_000), true, 30_000)
        assertEquals(3_000, back)
    }
}
