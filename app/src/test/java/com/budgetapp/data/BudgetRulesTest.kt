package com.budgetapp.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** The worked examples of the architecture review, one per test. */
class BudgetRulesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12): ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(y, m, d, h, 0), zone)
    private fun millis(y: Int, m: Int, d: Int, h: Int = 12) = at(y, m, d, h).toInstant().toEpochMilli()

    private fun paid(id: Long, paise: Long, category: String, whenMillis: Long, status: PaymentStatus = PaymentStatus.PAID) =
        Payment(id, "Shop $id", null, paise, category, null, null, status, whenMillis)

    // ---- Fix 1: Pay's warning uses the payment's own month ----

    private val food = listOf(Category("Food", "🍔", 5_000))
    private val today = at(2026, 11, 22)
    private val swiggy = paid(1, 300_00, "Food", millis(2026, 10, 10))
    private val payments = listOf(
        swiggy,
        paid(2, 4_500_00, "Food", millis(2026, 10, 15)), // October Food: 4,800 with Swiggy
        paid(3, 1_000_00, "Food", millis(2026, 11, 5)), // November Food: 1,000
    )

    @Test
    fun `editing an october payment is measured against october`() {
        val impact = paymentImpact(payments, food, swiggy, 600_00, "Food", today)!!
        assertEquals(YearMonth.of(2026, 10), impact.month)
        assertTrue(impact.otherMonth)
        assertEquals(5_100_00L, impact.spentAfterPaise) // 4,800 − 300 + 600
        assertEquals(BudgetStanding(StandingKind.OVER, 100_00), impact.standing)
        assertEquals("This puts Food ₹100 over budget for October", impact.words)
    }

    @Test
    fun `a new payment is measured against this month`() {
        val impact = paymentImpact(payments, food, null, 600_00, "Food", today)!!
        assertEquals(YearMonth.of(2026, 11), impact.month)
        assertFalse(impact.otherMonth)
        assertEquals(1_600_00L, impact.spentAfterPaise)
        assertEquals("₹3,400 left in Food after this", impact.words)
    }

    @Test
    fun `early in a month an old edit never works from negative spending`() {
        val impact = paymentImpact(payments, food, swiggy, 300_00, "Food", at(2026, 12, 1, 9))!!
        assertEquals(4_800_00L, impact.spentAfterPaise)
        assertEquals(StandingKind.NEAR, impact.standing.kind)
        assertEquals("Only ₹200 left in Food for October after this", impact.words)
    }

    @Test
    fun `moving a payment to another category counts it there, not twice`() {
        val cats = food + Category("Fuel", "⛽", 2_000)
        val impact = paymentImpact(payments, cats, swiggy, 300_00, "Fuel", today)!!
        assertEquals(300_00L, impact.spentAfterPaise)
    }

    @Test
    fun `exactly the budget is not over, one paisa more is`() {
        val exact = paymentImpact(payments, food, null, 4_000_00, "Food", today)!!
        assertEquals(BudgetStanding(StandingKind.NEAR, 0), exact.standing)
        assertEquals("Only ₹0 left in Food after this", exact.words)
        val over = paymentImpact(payments, food, null, 4_000_01, "Food", today)!!
        assertEquals(BudgetStanding(StandingKind.OVER, 1), over.standing)
        assertEquals("This puts Food ₹0.01 over budget", over.words)
    }

    @Test
    fun `pending, detected and cancelled payments are not spent`() {
        val others = listOf(
            paid(5, 1_000_00, "Food", millis(2026, 11, 6), PaymentStatus.PENDING),
            paid(6, 1_000_00, "Food", millis(2026, 11, 6), PaymentStatus.DETECTED),
            paid(7, 1_000_00, "Food", millis(2026, 11, 6), PaymentStatus.CANCELLED),
        )
        assertEquals(100L, paymentImpact(others, food, null, 100, "Food", today)!!.spentAfterPaise)
    }

    @Test
    fun `no amount or no budget means no warning`() {
        assertNull(paymentImpact(payments, food, null, 0, "Food", today))
        assertNull(paymentImpact(payments, listOf(Category("Food", "🍔", 0)), null, 100, "Food", today))
        assertNull(paymentImpact(payments, food, null, 100, "Gone", today))
    }

    // ---- Fix 2: one cap for every way of creating or changing a category budget ----

    private val split = listOf(Category("Rent", "🏠", 10_000), Category("Food", "🍔", 8_000))

    @Test
    fun `a new category may take only what is not split`() {
        assertEquals(2_000, categoryRoom(20_000, split, null))
        // Category budgets plus what is not split always make the monthly budget.
        assertEquals(20_000, split.sumOf { it.monthlyBudget } + categoryRoom(20_000, split, null))
    }

    @Test
    fun `an existing category may take its own budget plus what is free, and may always keep it`() {
        assertEquals(10_000, categoryRoom(20_000, split, "Food"))
        val overshoot = listOf(Category("Rent", "🏠", 15_000), Category("Food", "🍔", 8_000))
        assertEquals(8_000, categoryRoom(20_000, overshoot, "Food"))
        assertEquals(0, categoryRoom(20_000, overshoot, null))
    }

    @Test
    fun `no monthly budget means no cap`() {
        assertEquals(Int.MAX_VALUE, categoryRoom(0, split, null))
    }

    @Test
    fun `repository refuses a category or budget past the cap`() = runBlocking {
        val repo = Repository(JsonStore(File(folder.root, "budget.json")))
        repo.setOverallBudget(20_000)
        assertTrue(repo.addCategory("Rent", "🏠", 10_000))
        assertTrue(repo.addCategory("Food", "🍔", 8_000))
        assertFalse(repo.addCategory("Travel", "🚕", 5_000))
        assertTrue(repo.addCategory("Travel", "🚕", 2_000))
        repo.setCategoryBudget("Food", 9_000) // nothing is free now
        val data = repo.snapshot.value
        assertEquals(8_000, data.categories.single { it.name == "Food" }.monthlyBudget)
        assertEquals(data.overallBudget, data.categories.sumOf { it.monthlyBudget })
        repo.setCategoryBudget("Food", 5_000) // lowering is always allowed
        assertEquals(5_000, repo.snapshot.value.categories.single { it.name == "Food" }.monthlyBudget)
    }

    // ---- Fix 4: one wording for left and over ----

    @Test
    fun `standing says left or over, never a minus`() {
        assertEquals("₹500 over", budgetStanding(20_500_00, 20_000).words)
        assertEquals("₹1,200 left", budgetStanding(18_800_00, 20_000).words)
        assertEquals("₹49.50 left", budgetStanding(19_950_50, 20_000).words)
        assertEquals(StandingKind.LEFT, budgetStanding(16_999_99, 20_000).kind)
        assertEquals(StandingKind.NEAR, budgetStanding(17_000_00, 20_000).kind) // 85% exactly
        assertEquals(StandingKind.NEAR, budgetStanding(20_000_00, 20_000).kind)
        assertEquals(StandingKind.OVER, budgetStanding(20_000_01, 20_000).kind)
    }

    @Test
    fun `left plus spent is the budget, and over minus is the spending past it`() {
        for (spent in listOf(0L, 1L, 49_50L, 19_999_99L, 20_000_00L, 20_000_01L, 99_999_99L)) {
            val s = budgetStanding(spent, 20_000)
            if (s.kind == StandingKind.OVER) {
                assertEquals(spent, 20_000_00L + s.amountPaise)
            } else {
                assertEquals(20_000_00L, spent + s.amountPaise)
            }
        }
    }
}
