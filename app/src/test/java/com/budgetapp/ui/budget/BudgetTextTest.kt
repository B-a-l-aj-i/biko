package com.budgetapp.ui.budget

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetTextTest {

    @Test
    fun `no category budgets says so, with no zero amount to misread as spending`() {
        assertEquals("No category has its own budget yet", allocationText(5_000, 0))
        assertEquals("No category has its own budget yet", allocationText(0, 0))
    }

    @Test
    fun `part split shows both parts, which add up to the monthly budget`() {
        assertEquals(
            "Category budgets add up to ₹3,000 of ₹5,000 · ₹2,000 not split yet",
            allocationText(5_000, 3_000),
        )
        // One rupee either side of the edges.
        assertEquals(
            "Category budgets add up to ₹1 of ₹5,000 · ₹4,999 not split yet",
            allocationText(5_000, 1),
        )
        assertEquals(
            "Category budgets add up to ₹4,999 of ₹5,000 · ₹1 not split yet",
            allocationText(5_000, 4_999),
        )
    }

    @Test
    fun `fully split names the full amount`() {
        assertEquals("Category budgets add up to the full ₹1,25,000", allocationText(1_25_000, 1_25_000))
    }

    @Test
    fun `split past the monthly budget says by how much`() {
        assertEquals(
            "Category budgets add up to ₹5,001, ₹1 more than the monthly budget",
            allocationText(5_000, 5_001),
        )
    }
}
