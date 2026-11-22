package com.budgetapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The texts below are written the way each app's "paid" notification reads
 * (title and body joined, as the listener joins them). They are typical forms,
 * not copies of real notifications from the user's phone.
 */
class DebitAlertsTest {

    @Test
    fun `google pay paid notice`() {
        val alert = parseUpiAppAlert("₹250.00 paid to Ravi Kumar\nUPI transaction ID: 431234567890")!!
        assertEquals(250_00L, alert.amountPaise)
        assertEquals("Ravi Kumar", alert.payee)
        assertEquals("431234567890", alert.ref)
    }

    @Test
    fun `phonepe payment successful notice`() {
        val alert = parseUpiAppAlert("Payment Successful\n₹1,249.50 paid to SWIGGY on 22 Nov")!!
        assertEquals(1_249_50L, alert.amountPaise)
        assertEquals("SWIGGY", alert.payee)
    }

    @Test
    fun `phonepe sent notice with upi id`() {
        val alert = parseUpiAppAlert("Sent ₹500 to ravi.k@okicici")!!
        assertEquals(500_00L, alert.amountPaise)
        assertEquals("ravi.k@okicici", alert.vpa)
        assertEquals("ravi.k@okicici", alert.payee)
    }

    @Test
    fun `paytm paid notice`() {
        val alert = parseUpiAppAlert("Paid Rs.85 to Chai Point\nFrom your SBI account. UPI Ref No: 532109876543")!!
        assertEquals(85_00L, alert.amountPaise)
        assertEquals("Chai Point", alert.payee)
        assertEquals("532109876543", alert.ref)
    }

    @Test
    fun `navi payment successful notice`() {
        val alert = parseUpiAppAlert("Payment of ₹49.5 to Uber India successful")!!
        assertEquals(49_50L, alert.amountPaise)
        assertEquals("Uber India", alert.payee)
    }

    @Test
    fun `a notice that names no payee still counts`() {
        val alert = parseUpiAppAlert("₹120 paid successfully")!!
        assertEquals(120_00L, alert.amountPaise)
        assertNull(alert.payee)
    }

    @Test
    fun `amount is exact paise, commas and all`() {
        assertEquals(1_25_000_75L, parseUpiAppAlert("₹1,25,000.75 paid to Landlord")!!.amountPaise)
        assertEquals(1L, parseUpiAppAlert("₹0.01 sent to Test")!!.amountPaise)
    }

    @Test
    fun `money coming in is never a payment`() {
        assertNull(parseUpiAppAlert("Ravi Kumar paid you ₹500"))
        assertNull(parseUpiAppAlert("Ravi sent you ₹500"))
        assertNull(parseUpiAppAlert("₹500 received from Ravi"))
        assertNull(parseUpiAppAlert("₹500 credited to your account"))
    }

    @Test
    fun `failed, pending, requested and due payments are never a payment`() {
        assertNull(parseUpiAppAlert("Payment of ₹500 to Ravi failed"))
        assertNull(parseUpiAppAlert("Payment of ₹500 to Ravi is pending"))
        assertNull(parseUpiAppAlert("Ravi requested ₹500"))
        assertNull(parseUpiAppAlert("Your electricity bill of ₹1,200 is due on 25 Nov"))
        assertNull(parseUpiAppAlert("₹500 refund to your account"))
        assertNull(parseUpiAppAlert("Your OTP to pay ₹500 is 123456"))
    }

    @Test
    fun `offers that quote a rupee figure are never a payment`() {
        assertNull(parseUpiAppAlert("Get ₹100 cashback when you pay your bill"))
        assertNull(parseUpiAppAlert("You won a scratch card worth ₹50"))
        assertNull(parseUpiAppAlert("Flat 10% off on recharges above ₹199"))
        assertNull(parseUpiAppAlert("Refer a friend and earn ₹200"))
        assertNull(parseUpiAppAlert("Pre-approved loan of ₹50,000 sent for you"))
    }

    @Test
    fun `no amount, or no word for paying, is not a payment`() {
        assertNull(parseUpiAppAlert("Payment successful"))
        assertNull(parseUpiAppAlert("₹500 to Ravi"))
        assertNull(parseUpiAppAlert(""))
    }

    @Test
    fun `an email address is not a upi id`() {
        assertNull(parseUpiAppAlert("₹100 paid to Ravi. Receipt sent to me@gmail.com")!!.vpa)
    }

    @Test
    fun `each app shows by its own name`() {
        assertEquals("PhonePe", alertSourceLabel("com.phonepe.app"))
        assertEquals("Paytm", alertSourceLabel("net.one97.paytm"))
        assertEquals("Google Pay", alertSourceLabel("com.google.android.apps.nbu.paisa.user"))
        assertEquals("Navi", alertSourceLabel("com.naviapp"))
        assertEquals(setOf("com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user", "com.naviapp"), UPI_APPS.keys)
    }

    private fun payment(id: Long, paise: Long, at: Long, status: PaymentStatus = PaymentStatus.PAID) = Payment(
        id = id,
        payeeName = "Shop",
        payeeVpa = null,
        amountPaise = paise,
        category = "Food",
        voiceNotePath = null,
        upiApp = null,
        status = status,
        createdAt = at,
    )

    private val minute = 60_000L
    private val none = { _: Payment -> emptyList<String>() }

    @Test
    fun `alert lands on the payment the app already recorded`() {
        val paid = payment(1, 50_000, at = 0)
        assertEquals(paid, matchAlert(listOf(paid), DebitAlert(50_000, null, null, "1"), "gpay", 30 * minute, none, none))
    }

    @Test
    fun `different amount, too late, or cancelled is not a match`() {
        val alert = DebitAlert(50_000, null, null, null)
        assertNull(matchAlert(listOf(payment(1, 50_001, 0)), alert, "gpay", 0, none, none))
        assertNull(matchAlert(listOf(payment(1, 50_000, 0)), alert, "gpay", ALERT_MATCH_WINDOW_MS + 1, none, none))
        assertNull(matchAlert(listOf(payment(1, 50_000, 0, PaymentStatus.CANCELLED)), alert, "gpay", 0, none, none))
    }

    @Test
    fun `second alert from the same app with no shared ref is a second payment`() {
        // Two real ₹20 teas a minute apart.
        val first = payment(1, 2_000, 0)
        assertNull(matchAlert(listOf(first), DebitAlert(2_000, null, null, null), "gpay", minute, { listOf("gpay") }, none))
    }

    @Test
    fun `a second notice from the same app with the same ref is the same payment`() {
        val first = payment(1, 2_000, 0)
        val refs = { _: Payment -> listOf("431234567890") }
        val match = matchAlert(listOf(first), DebitAlert(2_000, null, null, "431234567890"), "gpay", minute, { listOf("gpay") }, refs)
        assertEquals(first, match)
    }

    @Test
    fun `different refs never match, same ref wins over nearer time`() {
        val a = payment(1, 2_000, 0)
        val b = payment(2, 2_000, 10 * minute)
        val refs = { p: Payment -> if (p.id == 1L) listOf("111111111") else listOf("222222222") }
        assertEquals(a, matchAlert(listOf(a, b), DebitAlert(2_000, null, null, "111111111"), "paytm", 10 * minute, none, refs))
        assertNull(matchAlert(listOf(a, b), DebitAlert(2_000, null, null, "333333333"), "paytm", 10 * minute, none, refs))
    }

    @Test
    fun `an alert never changes the amount, and names a payee only when there was none`() {
        val saved = payment(1, 2_000, 0).copy(payeeName = UNKNOWN_PAYEE)
        val after = applyAlert(saved, DebitAlert(2_000, "Ravi", "ravi@okicici", "431234567890"), "gpay")
        assertEquals(2_000L, after.amountPaise)
        assertEquals("Ravi", after.payeeName)
        assertEquals("ravi@okicici", after.payeeVpa)
        assertEquals(listOf("gpay"), applyAlert(after, DebitAlert(2_000, null, null, null), "gpay").alertSources)
        assertEquals("Shop", applyAlert(payment(1, 2_000, 0), DebitAlert(2_000, "Ravi", null, null), "gpay").payeeName)
    }
}
