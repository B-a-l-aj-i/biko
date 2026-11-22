package com.budgetapp.data

data class Category(
    /** Unique; payments refer to their category by this name. */
    val name: String,
    val emoji: String,
    /** Whole rupees. Zero means no budget set. */
    val monthlyBudget: Int,
)

enum class PaymentStatus {
    /** Sent to a UPI app; not yet known whether the money moved. */
    PENDING,
    PAID,
    CANCELLED,

    /**
     * Found in a UPI app's notification but not recorded here. It waits on the
     * review page and counts nowhere until the user ticks it (it becomes
     * [PAID]) or crosses it out (it is deleted).
     */
    DETECTED,
}

data class Payment(
    /** Zero until saved; the store hands out the real id. */
    val id: Long = 0,
    val payeeName: String,
    /** Null when the user paid inside the UPI app without scanning here. */
    val payeeVpa: String?,
    val amountPaise: Long,
    val category: String,
    val voiceNotePath: String?,
    /** Package name of the UPI app the payment was sent to. */
    val upiApp: String?,
    val status: PaymentStatus,
    val createdAt: Long,
    /**
     * True for a payment found in a bank or UPI app alert that the user hasn't
     * looked at yet. It is filed under [ALERT_CATEGORY] until they do.
     */
    val needsReview: Boolean = false,
    /** Apps whose alerts were matched to this payment, so one app's alert is never counted twice. */
    val alertSources: List<String> = emptyList(),
    /** Bank references (UPI ref, RRN) quoted by those alerts. */
    val alertRefs: List<String> = emptyList(),
    /** What the user typed about it; null when there is none. Never blank. */
    val note: String? = null,
)

/** Longest text note a payment keeps. */
const val NOTE_MAX_LENGTH = 100

/** A typed note as it is saved: trimmed, cut to [NOTE_MAX_LENGTH], and null when nothing is left. */
fun cleanNote(text: String?): String? = text?.trim()?.take(NOTE_MAX_LENGTH)?.trim()?.ifEmpty { null }

enum class ThemeMode {
    /** Light or dark, whichever the phone is set to. */
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * How the app looks. Nothing here changes a figure. The app stays light, its
 * original design, until the user picks something else: a phone in dark mode
 * does not change it on its own.
 */
data class Appearance(
    val theme: ThemeMode = ThemeMode.LIGHT,
    /** Pure black and white, with greys for the donuts only. */
    val blackAndWhite: Boolean = false,
) {
    /** Whether the dark look is on, given whether the phone itself is in dark mode. */
    fun isDark(systemDark: Boolean): Boolean = when (theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

/** Everything the app remembers; this is exactly what the JSON file holds. */
data class BudgetData(
    /** Whole rupees. Zero only on a fresh install, before the budgets are settled. */
    val overallBudget: Int = 0,
    val categories: List<Category> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val appearance: Appearance = Appearance(),
    /**
     * Bank references of payments the user deleted. A later alert for one of
     * them (a second notice from the app) must not bring it back.
     */
    val deletedAlertRefs: List<String> = emptyList(),
)

/**
 * Saved as the payee of a payment the user added by hand. It marks the payment
 * as hand-added and is never shown: lists show [title] and a "Manual" tag.
 */
const val MANUAL_PAYEE = "Added by hand"

/** True for a payment the user added by hand rather than one found in a bank alert. */
val Payment.isManual: Boolean get() = payeeName == MANUAL_PAYEE

/**
 * What a list shows as the payment's name. A hand-added payment has no payee,
 * so it shows the user's note, or its category when there is no note.
 */
val Payment.title: String
    get() = if (isManual) note?.trim()?.takeIf { it.isNotEmpty() } ?: category else payeeName

/** The note to show under [title]; null when there is none or it is already the title. */
val Payment.noteLine: String?
    get() = if (isManual) null else note?.trim()?.takeIf { it.isNotEmpty() }

/**
 * A payment made outside this app (cash, or a UPI app opened directly) that the
 * user records after the fact. It already happened, so it is saved as paid and
 * counts as spent straight away.
 */
fun manualPayment(amountPaise: Long, category: String, voiceNotePath: String?, createdAt: Long) = Payment(
    payeeName = MANUAL_PAYEE,
    payeeVpa = null,
    amountPaise = amountPaise,
    category = category,
    voiceNotePath = voiceNotePath,
    upiApp = null,
    status = PaymentStatus.PAID,
    createdAt = createdAt,
)
