package com.budgetapp.data

/**
 * A debit read from a UPI app's own "paid" notification (PhonePe, Paytm,
 * Google Pay, Navi). Only money leaving the account counts; money received,
 * requests, failures, reminders and offers are never a [DebitAlert].
 */
data class DebitAlert(
    /** Exact paise, read straight from the text. */
    val amountPaise: Long,
    /** Who was paid, when the text says so. */
    val payee: String?,
    /** The payee's UPI ID, when the text has one. */
    val vpa: String?,
    /** The UPI reference (UTR / RRN / transaction id), when the text has one. */
    val ref: String?,
)

/** Category a detected debit is filed under until the user picks one. */
const val ALERT_CATEGORY = "Other"

/**
 * Shown as the payee when a notice doesn't say who was paid. The text is what
 * older builds saved, so payments already stored with it are still recognised.
 */
const val UNKNOWN_PAYEE = "Bank debit"

/** How far apart a payment and an alert may be and still be the same payment. */
const val ALERT_MATCH_WINDOW_MS = 45 * 60 * 1000L

/**
 * How far apart two alerts from different apps may be and still describe one
 * payment, when no reference ties them together.
 */
const val ALERT_PAIR_WINDOW_MS = 5 * 60 * 1000L

/** The UPI apps whose notifications are read, by package name, with the name shown for each. */
val UPI_APPS: Map<String, String> = linkedMapOf(
    "com.phonepe.app" to "PhonePe",
    "net.one97.paytm" to "Paytm",
    "com.google.android.apps.nbu.paisa.user" to "Google Pay",
    "com.naviapp" to "Navi",
)

/** The app an alert came from, in words: "PhonePe", "Google Pay"; "UPI app" for any other. */
fun alertSourceLabel(packageName: String): String = UPI_APPS[packageName] ?: "UPI app"

/** The app that first reported [this] payment, in words. */
val Payment.sourceLabel: String get() = alertSourceLabel(alertSources.firstOrNull().orEmpty())

/**
 * Reads [text], the title and body of a UPI app's notification, as a payment
 * the user made. Returns null unless it clearly says money was paid or sent,
 * with an amount. The app posting it is the proof it is a real payment, so no
 * bank account or reference is required, but offers ("Get ₹100 cashback"),
 * money received ("Ravi sent you ₹500"), requests, failures and reminders are
 * always refused.
 */
fun parseUpiAppAlert(text: String): DebitAlert? {
    val flat = text.replace(Regex("\\s+"), " ").trim()
    if (flat.isEmpty()) return null
    val lower = flat.lowercase()

    if (NOT_A_DEBIT.any { it in lower }) return null
    if (PROMO.containsMatchIn(lower)) return null
    if (!DEBIT.containsMatchIn(lower)) return null

    val amountPaise = AMOUNT.find(flat)
        ?.groupValues?.get(1)
        ?.replace(",", "")
        ?.let(::parsePaise)
        ?.takeIf { it > 0 }
        ?: return null

    val vpa = VPA.find(flat)?.value?.trimEnd('.')
    return DebitAlert(
        amountPaise = amountPaise,
        payee = payeeIn(flat, vpa),
        vpa = vpa,
        ref = REF.find(flat)?.groupValues?.get(1),
    )
}

/** Debits found in alerts that wait for the user's tick or cross, newest first. */
fun paymentsToReview(payments: List<Payment>): List<Payment> =
    payments.filter { it.status == PaymentStatus.DETECTED }.sortedByDescending { it.createdAt }

/**
 * [payment] once [alert] from [source] has been matched to it. The alert
 * confirms the money moved, and fills in a payee or UPI ID the payment lacked.
 */
fun applyAlert(payment: Payment, alert: DebitAlert, source: String): Payment = payment.copy(
    alertSources = (payment.alertSources + source).distinct(),
    alertRefs = (payment.alertRefs + listOfNotNull(alert.ref)).distinct(),
    status = if (payment.status == PaymentStatus.PENDING) PaymentStatus.PAID else payment.status,
    payeeVpa = payment.payeeVpa ?: alert.vpa,
    payeeName = if (payment.payeeName == UNKNOWN_PAYEE && alert.payee != null) alert.payee else payment.payeeName,
)

/**
 * The saved payment an alert describes, or null when it is a payment the app
 * hasn't recorded yet.
 *
 * The same UPI reference is always the same payment: an app that posts a
 * second, different notice for one payment doesn't make it count twice.
 * Without that, a payment matches when the amount is the same to the paisa, it
 * was made within [ALERT_MATCH_WINDOW_MS] of the alert, the references don't
 * contradict each other, and no earlier alert from the same [source] app has
 * already been matched to it. That last rule keeps two real ₹20 payments apart.
 */
fun matchAlert(
    payments: List<Payment>,
    alert: DebitAlert,
    source: String,
    at: Long,
    sourcesOf: (Payment) -> List<String>,
    refsOf: (Payment) -> List<String>,
): Payment? = payments
    .asSequence()
    .filter { it.status != PaymentStatus.CANCELLED }
    .filter { payment ->
        val sameRef = alert.ref != null && alert.ref in refsOf(payment)
        if (sameRef) return@filter true
        if (payment.amountPaise != alert.amountPaise) return@filter false
        if (alert.ref != null && refsOf(payment).isNotEmpty()) return@filter false
        if (source in sourcesOf(payment)) return@filter false
        val apart = kotlin.math.abs(payment.createdAt - at)
        // Only known from another app's alert: two alerts of one payment arrive close together.
        if (sourcesOf(payment).isEmpty()) apart <= ALERT_MATCH_WINDOW_MS else apart <= ALERT_PAIR_WINDOW_MS
    }
    // A matching reference is the surest; otherwise the nearest in time.
    .sortedWith(
        compareByDescending<Payment> { alert.ref != null && alert.ref in refsOf(it) }
            .thenBy { kotlin.math.abs(it.createdAt - at) },
    )
    .firstOrNull()

/** Words that mean this isn't money the user paid, even if it mentions paying. */
private val NOT_A_DEBIT = listOf(
    "otp", "one time password", "one-time password", "verification code",
    "received", "credited", "paid you", "sent you", "added to your", "added to wallet",
    "requested", "request", "requesting", "collect",
    "failed", "declined", "unsuccessful", "not successful", "reversed", "refund", "cancelled", "pending",
    "will be debited", "to be debited", "is due", "due on", "due date", "reminder", "mandate", "scheduled", "autopay set",
)

/** Offers and adverts that quote a rupee figure. Whole words, so a payee named "Gold Gym" is still fine. */
private val PROMO = Regex(
    "\\b(?:cashback|offers?|rewards?|win|won|earn|coupons?|vouchers?|upto|up to|scratch|claim|lucky|refer|invite|" +
        "pre-approved|get|flat)\\b|%\\s*off",
)

/** What a UPI app says when a payment went through. */
private val DEBIT = Regex(
    "\\b(?:paid|sent|debited|transferred)\\b|\\bpayment\\b.*\\bsuccessful(?:ly)?\\b|\\bsuccessful(?:ly)?\\b.*\\bpayment\\b",
)

/** "Rs. 1,250.50", "INR 500", "₹ 49.5", "Rs500" */
private val AMOUNT = Regex("(?:rs\\.?|inr|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)

/**
 * A UPI ID: "ravi.k@okicici". Not an email address: "name@gmail.com" has a dot
 * and more letters after the handle, so it is skipped. Possessive, so "@gmail"
 * can't back off to "@gmai".
 */
private val VPA = Regex("[a-zA-Z0-9._-]{2,}@[a-zA-Z][a-zA-Z0-9]++(?!\\.[a-zA-Z])")

/** UPI ref, UTR, RRN or transaction id: the long number the app quotes. */
private val REF = Regex(
    "(?:upi\\s*ref(?:erence)?|ref(?:erence)?|rrn|utr|txn\\s*id|transaction\\s*id|upi)\\.?\\s*" +
        "(?:no\\.?|number|id)?\\s*(?:is)?\\s*[:#./-]?\\s*([0-9]{9,})",
    RegexOption.IGNORE_CASE,
)

/** Where a name ends: the next field of the notice. */
private val NAME_END = Regex(
    "\\s+(?:on|via|ref|refno|upi|using|from|at|thru|through|for|successful|successfully|is|was|has)\\b|[,;:(?!|•·]|\\.\\s|\\.$|\\s-\\s|$",
    RegexOption.IGNORE_CASE,
)

/** What follows "to" that is never a payee. */
private val NOT_A_NAME = listOf("ac", "acct", "account", "your", "you", "bank", "check", "view")

private fun payeeIn(flat: String, vpa: String?): String? {
    // "paid to RAVI KUMAR", "₹500 sent to Ravi", "to ravi@okicici RAVI KUMAR on ..."
    val afterTo = Regex("\\b(?:to|towards)\\s+", RegexOption.IGNORE_CASE)
    for (start in afterTo.findAll(flat)) {
        // The UPI ID is taken out first; its dots would otherwise end the name early.
        var rest = flat.substring(start.range.last + 1)
        if (vpa != null && rest.startsWith(vpa)) rest = rest.removePrefix(vpa).trimStart()
        // Padded so a field word right at the start ("on 15-11") also ends the name.
        rest = " $rest"
        val end = NAME_END.find(rest)?.range?.first ?: rest.length
        val name = rest.substring(0, end).trim().trimEnd('.')
        if (name.isEmpty()) {
            if (vpa != null && flat.substring(start.range.last + 1).startsWith(vpa)) return vpa
            continue
        }
        // Whole first word only, so "Usha" isn't mistaken for "us".
        if (name.lowercase().substringBefore(' ') in NOT_A_NAME) continue
        if (name.any { it.isLetter() }) return name.take(MAX_PAYEE).trim()
    }
    return vpa
}

private const val MAX_PAYEE = 40

