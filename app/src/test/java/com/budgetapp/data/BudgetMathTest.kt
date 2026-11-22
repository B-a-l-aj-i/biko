package com.budgetapp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetMathTest {

    @Test
    fun `fully allocated budgets fill the new total exactly`() {
        val budgets = listOf(4_000, 5_000, 3_000, 6_000, 4_000, 2_000, 1_500, 1_500, 1_500, 1_000, 500)
        val scaled = rescaleBudgets(budgets, oldTotal = 30_000, newTotal = 5_000)

        assertEquals(5_000, scaled.sum())
        assertEquals(listOf(667, 833, 500, 1_000, 667, 333, 250, 250, 250, 167, 83), scaled)
    }

    @Test
    fun `doubling the total doubles every budget`() {
        assertEquals(listOf(2_000, 6_000, 0), rescaleBudgets(listOf(1_000, 3_000, 0), 5_000, 10_000))
    }

    @Test
    fun `the unallocated share stays unallocated`() {
        // 80% given out before, 80% given out after.
        val scaled = rescaleBudgets(listOf(3_000, 5_000), oldTotal = 10_000, newTotal = 7_000)
        assertEquals(listOf(2_100, 3_500), scaled)
    }

    @Test
    fun `budgets over the old total are fitted into the new one`() {
        // The state an earlier build left behind: 26,000 of categories on a 5,000 budget.
        val budgets = listOf(1_000, 5_000, 3_000, 5_500, 4_000, 2_000, 1_500, 1_500, 1_500, 500, 500)
        val scaled = rescaleBudgets(budgets, oldTotal = 5_000, newTotal = 5_000)

        assertEquals(5_000, scaled.sum())
        assertEquals(192, scaled[0])
        assertEquals(1_058, scaled[3])
    }

    @Test
    fun `same total changes nothing`() {
        val budgets = listOf(1_234, 766, 500)
        assertEquals(budgets, rescaleBudgets(budgets, 3_000, 3_000))
    }

    @Test
    fun `nothing to scale`() {
        assertEquals(listOf(0, 0), rescaleBudgets(listOf(0, 0), 0, 5_000))
        assertEquals(emptyList<Int>(), rescaleBudgets(emptyList(), 5_000, 8_000))
    }

    @Test
    fun `percent text keeps one decimal only when needed`() {
        assertEquals("20%", percentText(1_000, 5_000))
        assertEquals("13.3%", percentText(667, 5_000))
        assertEquals("0%", percentText(500, 0))
    }

    @Test
    fun `percent to rupees rounds to the nearest rupee`() {
        assertEquals(1_000, percentToRupees("20", 5_000))
        assertEquals(1_665, percentToRupees("33.3", 5_000))
        assertEquals(63, percentToRupees("12.5", 500))
        // 1.5 exactly: a double would have to be trusted to land on the right side.
        assertEquals(2, percentToRupees("0.5", 300))
        assertEquals(0, percentToRupees("", 5_000))
        assertEquals(0, percentToRupees(".", 5_000))
    }

    @Test
    fun `spend shares add up to the total, largest first`() {
        val shares = spendShares(
            listOf("Food" to 101_00L, "Groceries" to 0L, "Shopping" to 2_100_00L, "Bills" to 141_00L),
            totalPaise = 2_342_00L,
        )
        assertEquals(listOf("Shopping", "Bills", "Food"), shares.map { it.name })
        assertEquals(2_342_00L, shares.sumOf { it.spentPaise })
    }

    @Test
    fun `spend share percents round down`() {
        // 1 of 3 is 33.3%: shown as 33, never 34.
        val shares = spendShares(listOf("A" to 1L, "B" to 1L, "C" to 1L), totalPaise = 3L)
        assertEquals(listOf(33, 33, 33), shares.map { it.percent })
        // 49.50 of 99.50 is 49.7%: 49.
        assertEquals(49, spendShares(listOf("A" to 49_50L, "B" to 50_00L), 99_50L).single { it.name == "A" }.percent)
    }

    @Test
    fun `spending outside any category becomes Other, so the parts still add up`() {
        val shares = spendShares(listOf("Food" to 100_00L), totalPaise = 149_50L)
        assertEquals(listOf("Food", OTHER_SPENDING), shares.map { it.name })
        assertEquals(49_50L, shares.single { it.name == OTHER_SPENDING }.spentPaise)
        assertEquals(149_50L, shares.sumOf { it.spentPaise })
    }

    @Test
    fun `equal shares keep category order, and nothing spent means no shares`() {
        assertEquals(listOf("A", "B"), spendShares(listOf("A" to 50L, "B" to 50L), 100L).map { it.name })
        assertEquals(emptyList<SpendShare>(), spendShares(listOf("A" to 0L), 0L))
    }
}
