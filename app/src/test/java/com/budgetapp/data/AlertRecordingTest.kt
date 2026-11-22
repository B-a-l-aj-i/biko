package com.budgetapp.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AlertRecordingTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun repository() = Repository(JsonStore(File(folder.root, "budget.json")))

    private val minute = 60_000L

    @Test
    fun `alert fields survive the json round trip, and old files read as no alerts`() {
        val payment = Payment(
            7, "RAVI KUMAR", "ravi@okicici", 50_000, ALERT_CATEGORY, null, null, PaymentStatus.PAID, 1_700_000_000_000,
            needsReview = true, alertSources = listOf("com.google.android.gm"), alertRefs = listOf("431234567890"),
        )
        val data = BudgetData(payments = listOf(payment))
        assertEquals(data, parseBudgetData(data.toJson()))

        val old = org.json.JSONObject(data.toJson())
        old.getJSONArray("payments").getJSONObject(0).apply {
            remove("needsReview"); remove("alertSources"); remove("alertRefs")
        }
        assertEquals(
            payment.copy(needsReview = false, alertSources = emptyList(), alertRefs = emptyList()),
            parseBudgetData(old.toString()).payments.single(),
        )
    }

    @Test
    fun `new debit waits for review and counts nowhere until ticked`() = runBlocking {
        val repo = repository()
        val id = repo.recordDebitAlert(DebitAlert(50_000, "RAVI KUMAR", null, "431234567890"), "sms", 0)!!
        val saved = repo.snapshot.value.payments.single()
        assertEquals(id, saved.id)
        assertEquals(ALERT_CATEGORY, saved.category)
        assertEquals(PaymentStatus.DETECTED, saved.status)
        assertEquals(50_000L, saved.amountPaise)
        assertTrue(saved.needsReview)
        assertEquals(listOf(saved), paymentsToReview(repo.snapshot.value.payments))

        // Not spent anywhere: Home's total, its categories and Search all leave it out.
        val now = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(0), java.time.ZoneId.of("Asia/Kolkata"))
        val home = com.budgetapp.ui.home.buildHomeState(repo.snapshot.value.categories, repo.snapshot.value.payments, 6_000, now)
        assertEquals(0L, home.spentPaise)
        assertEquals(0L, home.allCategories.sumOf { it.spentPaise })
        assertEquals(0, home.recent.size)
        assertEquals(0, home.pending.size)
        assertEquals(1, home.toReview)
        val (from, until) = monthBounds(java.time.YearMonth.from(now), now.zone)
        assertTrue(searchPayments(repo.snapshot.value.payments, "", from, until).isEmpty())

        // The tick: now it is spent, under Other, which becomes a category.
        repo.confirmDetected(id)
        val ticked = repo.snapshot.value
        assertEquals(PaymentStatus.PAID, ticked.payments.single().status)
        assertTrue(ticked.categories.any { it.name == ALERT_CATEGORY })
        val after = com.budgetapp.ui.home.buildHomeState(ticked.categories, ticked.payments, 6_000, now)
        assertEquals(50_000L, after.spentPaise)
        assertEquals(after.spentPaise, after.allCategories.sumOf { it.spentPaise })
        assertEquals(0, after.toReview)
    }

    @Test
    fun `undo puts a ticked or crossed debit back exactly as it was`() = runBlocking {
        val repo = repository()
        val id = repo.recordDebitAlert(DebitAlert(9_900, "SHOP", null, "431234567890"), "gmail", 0)!!
        val before = repo.snapshot.value.payments.single()

        repo.confirmDetected(id)
        repo.restorePayment(before)
        assertEquals(listOf(before), repo.snapshot.value.payments)

        repo.deletePayment(id)
        assertTrue(repo.snapshot.value.payments.isEmpty())
        assertEquals(listOf("431234567890"), repo.snapshot.value.deletedAlertRefs)
        repo.restorePayment(before)
        assertEquals(listOf(before), repo.snapshot.value.payments)
        // Its reference is no longer blocked, so the bank's next alert lands on it.
        assertEquals(emptyList<String>(), repo.snapshot.value.deletedAlertRefs)
        assertNull(repo.recordDebitAlert(DebitAlert(9_900, "SHOP", null, "431234567890"), "sms", minute))
        assertEquals(1, repo.snapshot.value.payments.size)
    }

    @Test
    fun `a cross deletes it, and a later alert for it does not bring it back`() = runBlocking {
        val repo = repository()
        val id = repo.recordDebitAlert(DebitAlert(9_900, "SHOP", null, "431234567890"), "gmail", 0)!!
        repo.deletePayment(id)
        assertNull(repo.recordDebitAlert(DebitAlert(9_900, "SHOP", null, "431234567890"), "sms", minute))
        assertTrue(repo.snapshot.value.payments.isEmpty())
    }

    @Test
    fun `swiping the notice away leaves it waiting, and the tick only works on one waiting`() = runBlocking {
        val repo = repository()
        val id = repo.recordDebitAlert(DebitAlert(9_900, "SHOP", null, null), "sms", 0)!!
        repo.markReviewed(id)
        assertEquals(PaymentStatus.DETECTED, repo.snapshot.value.payments.single().status)
        assertTrue(repo.snapshot.value.payments.single().needsReview)

        val manual = repo.addPayment(manualPayment(4_950, "Food", null, 0).copy(status = PaymentStatus.CANCELLED))
        repo.confirmDetected(manual)
        assertEquals(PaymentStatus.CANCELLED, repo.snapshot.value.payments.single { it.id == manual }.status)
    }

    @Test
    fun `same payment from sms, email and upi app is counted once`() = runBlocking {
        val repo = repository()
        assertNotNull(repo.recordDebitAlert(DebitAlert(50_000, "RAVI KUMAR", null, "431234567890"), "sms", 0))
        assertNull(repo.recordDebitAlert(DebitAlert(50_000, "RAVI KUMAR", "ravi@okicici", "431234567890"), "gmail", 2 * minute))
        assertNull(repo.recordDebitAlert(DebitAlert(50_000, "Ravi Kumar", null, null), "gpay", minute))
        val payments = repo.snapshot.value.payments
        assertEquals(1, payments.size)
        assertEquals(50_000L, payments.sumOf { it.amountPaise })
        assertEquals("ravi@okicici", payments.single().payeeVpa)
    }

    @Test
    fun `two real payments of the same amount stay two`() = runBlocking {
        val repo = repository()
        assertNotNull(repo.recordDebitAlert(DebitAlert(2_000, "CHAI", null, null), "sms", 0))
        assertNotNull(repo.recordDebitAlert(DebitAlert(2_000, "CHAI", null, null), "sms", 5 * minute))
        assertEquals(4_000L, repo.snapshot.value.payments.sumOf { it.amountPaise })
    }

    @Test
    fun `alert for a payment made in this app confirms it instead of adding one`() = runBlocking {
        val repo = repository()
        val id = repo.addPayment(Payment(0, "SWIGGY", "swiggy@upi", 24_950, "Food", null, "com.phonepe.app", PaymentStatus.PENDING, 0))
        assertNull(repo.recordDebitAlert(DebitAlert(24_950, "SWIGGY", null, "431234567890"), "sms", 3 * minute))
        val saved = repo.snapshot.value.payments.single()
        assertEquals(id, saved.id)
        assertEquals("Food", saved.category)
        assertEquals(PaymentStatus.PAID, saved.status)
        assertFalse(saved.needsReview)
    }

    @Test
    fun `saving it with a category from the review page confirms it`() = runBlocking {
        val repo = repository()
        val a = repo.recordDebitAlert(DebitAlert(50_000, "A", null, null), "sms", 0)!!
        repo.updatePayment(a, 50_000, "Food", null)
        val saved = repo.snapshot.value.payments.single()
        assertEquals("Food", saved.category)
        assertEquals(PaymentStatus.PAID, saved.status)
        assertFalse(saved.needsReview)
    }

    @Test
    fun `a deleted alert payment stays deleted when another alert for it arrives`() = runBlocking {
        val repo = repository()
        val id = repo.recordDebitAlert(DebitAlert(100, "VIJA", null, "066475153819"), "sms", 0)!!
        repo.deletePayment(id)
        assertTrue(repo.snapshot.value.payments.isEmpty())
        // The bank's email, or a late SMS from another app, for the same payment.
        assertNull(repo.recordDebitAlert(DebitAlert(100, "VIJAY M", null, "066475153819"), "gmail", minute))
        assertTrue(repo.snapshot.value.payments.isEmpty())
        assertTrue("066475153819" in repo.snapshot.value.deletedAlertRefs)
        // A different payment is still recorded.
        assertNotNull(repo.recordDebitAlert(DebitAlert(100, "VIJA", null, "132910279330"), "sms", 2 * minute))
    }

    @Test
    fun `deleted refs survive the json round trip, and old files have none`() {
        val data = BudgetData(deletedAlertRefs = listOf("066475153819"))
        assertEquals(data, parseBudgetData(data.toJson()))
        val old = org.json.JSONObject(data.toJson()).apply { remove("deletedAlertRefs") }
        assertEquals(emptyList<String>(), parseBudgetData(old.toString()).deletedAlertRefs)
    }

    @Test
    fun `deleting a payment with no bank ref remembers nothing`() = runBlocking {
        val repo = repository()
        val id = repo.addPayment(manualPayment(4_950, "Food", null, 0))
        repo.deletePayment(id)
        assertEquals(emptyList<String>(), repo.snapshot.value.deletedAlertRefs)
    }
}
