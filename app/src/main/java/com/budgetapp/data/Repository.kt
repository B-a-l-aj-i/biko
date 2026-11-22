package com.budgetapp.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class Repository(private val store: JsonStore) {

    /** The JSON file everything is saved in. */
    val dataFile: File get() = store.file

    /** All saved data, for screens that describe the file itself. */
    val snapshot: StateFlow<BudgetData> = store.data

    val categories: Flow<List<Category>> = store.data.map { it.categories }.distinctUntilChanged()

    /** Whole rupees. Zero only until [settleBudgets] has run on a fresh install. */
    val overallBudget: Flow<Int> = store.data.map { it.overallBudget }.distinctUntilChanged()

    /** Every payment, all months. Whoever shows a month picks it out, so the month is never stale. */
    val payments: Flow<List<Payment>> = store.data.map { it.payments }.distinctUntilChanged()

    /** Light, dark or the phone's choice, and whether black-and-white mode is on. */
    val appearance: Flow<Appearance> = store.data.map { it.appearance }.distinctUntilChanged()

    suspend fun setTheme(theme: ThemeMode) {
        store.update { it.copy(appearance = it.appearance.copy(theme = theme)) }
    }

    suspend fun setBlackAndWhite(on: Boolean) {
        store.update { it.copy(appearance = it.appearance.copy(blackAndWhite = on)) }
    }


    /** Changes the monthly budget; category budgets keep their percentage of it. */
    suspend fun setOverallBudget(rupees: Int) {
        if (rupees <= 0) return
        store.update { data ->
            val old = data.overallBudget.takeIf { it > 0 } ?: data.categories.sumOf { it.monthlyBudget }
            val scaled = rescaleBudgets(data.categories.map { it.monthlyBudget }, old, rupees)
            data.copy(
                overallBudget = rupees,
                categories = data.categories.mapIndexed { index, it -> it.copy(monthlyBudget = scaled[index]) },
            )
        }
    }

    /** Run once at startup, after seeding, so there is always a monthly budget to take shares of. */
    suspend fun settleBudgets() {
        store.update { data ->
            // Nothing chosen yet: start from what the categories add up to.
            if (data.overallBudget == 0) data.copy(overallBudget = data.categories.sumOf { it.monthlyBudget }) else data
        }
    }

    /** Ignored when it would take the category budgets past the monthly budget (see [categoryRoom]). */
    suspend fun setCategoryBudget(name: String, rupees: Int) {
        store.update { data ->
            if (rupees < 0 || rupees > categoryRoom(data.overallBudget, data.categories, name)) return@update data
            data.copy(categories = data.categories.map { if (it.name == name) it.copy(monthlyBudget = rupees) else it })
        }
    }

    /** Writes everything, voice notes included, as one zip into [out]. */
    suspend fun exportBackup(out: OutputStream): BackupSummary =
        withContext(Dispatchers.IO) { writeBackup(store.data.value, out) }

    /**
     * Replaces everything with the backup in [input]; its voice notes are copied
     * into [voiceDir]. The data it replaces is kept beside the JSON file as
     * budget-before-import-<time>.json, and the old voice notes stay where they are.
     * Throws, changing nothing, when [input] is not a backup.
     */
    suspend fun importBackup(input: InputStream, voiceDir: File, now: Long): BackupSummary {
        val imported = withContext(Dispatchers.IO) { readBackup(input, voiceDir) }
        val keep = File(store.file.parentFile, "budget-before-import-$now.json")
        store.replace(imported, keep)
        // Same steps as startup, so a backup from a fresh install still has categories and a budget.
        seedDefaultCategories()
        settleBudgets()
        val data = store.data.value
        return BackupSummary(payments = data.payments.size, voiceNotes = data.payments.mapNotNull { it.voiceNotePath }.distinct().size)
    }

    /** Demo build only: fills an empty store with [demo]; anything already saved is kept. */
    suspend fun loadDemo(demo: BudgetData) {
        store.update { if (it.payments.isEmpty() && it.categories.isEmpty()) demo else it }
    }

    /** Brings over what builds before the JSON file had saved. Run first at startup. */
    suspend fun importOlderData(context: Context) {
        if (!store.startedEmpty) return
        val old = withContext(Dispatchers.IO) { readLegacyData(context) } ?: return
        store.update { if (it == BudgetData()) old else it }
    }

    suspend fun seedDefaultCategories() {
        store.update { data ->
            val categories = data.categories.ifEmpty { defaultCategories }
            // Earlier builds seeded "Other"; it made lazy tagging too easy.
            // It stays only if a payment already uses it.
            val otherInUse = data.payments.any { it.category == "Other" }
            data.copy(categories = if (otherInUse) categories else categories.filter { it.name != "Other" })
        }
    }

    /**
     * Returns false, adding nothing, when a category with that name already exists
     * or its budget is more than the monthly budget has free (see [categoryRoom]).
     */
    suspend fun addCategory(name: String, emoji: String, monthlyBudget: Int): Boolean {
        var added = false
        store.update { data ->
            added = data.categories.none { it.name == name } &&
                monthlyBudget >= 0 && monthlyBudget <= categoryRoom(data.overallBudget, data.categories, null)
            if (added) data.copy(categories = data.categories + Category(name, emoji, monthlyBudget)) else data
        }
        return added
    }

    /** Payments already tagged with it keep the name, so past spending still reads correctly. */
    suspend fun removeCategory(name: String) {
        store.update { data -> data.copy(categories = data.categories.filter { it.name != name }) }
    }

    /** Returns the id the payment was saved under. */
    suspend fun addPayment(payment: Payment): Long {
        var id = 0L
        store.update { data ->
            id = (data.payments.maxOfOrNull { it.id } ?: 0L) + 1
            data.copy(payments = data.payments + payment.copy(id = id))
        }
        return id
    }

    /** Changes what the user can edit on a saved payment; who was paid, when, and its status stay. */
    suspend fun updatePayment(
        id: Long,
        amountPaise: Long,
        category: String,
        voiceNotePath: String?,
        note: String? = null,
    ) {
        if (amountPaise <= 0) return
        store.update { data ->
            data.copy(
                payments = data.payments.map {
                    // Saving it is the user looking at it, so an alert's payment is reviewed.
                    if (it.id == id) {
                        it.copy(
                            amountPaise = amountPaise,
                            category = category,
                            voiceNotePath = voiceNotePath,
                            note = cleanNote(note),
                            needsReview = false,
                            // Saving one waiting for review is the user confirming it.
                            status = if (it.status == PaymentStatus.DETECTED) PaymentStatus.PAID else it.status,
                        )
                    } else {
                        it
                    }
                },
            )
        }
    }

    /**
     * Records a debit seen in another app's notification. When it describes a
     * payment already saved (paid here, or alerted by another app), that payment
     * just notes the alert and null is returned. Otherwise it is saved as
     * [PaymentStatus.DETECTED] under [ALERT_CATEGORY], counting nowhere until the
     * user confirms it on the review page, and its id is returned.
     */
    suspend fun recordDebitAlert(alert: DebitAlert, source: String, at: Long): Long? {
        var newId: Long? = null
        store.update { data ->
            // The user deleted this payment; leave it deleted.
            if (alert.ref != null && alert.ref in data.deletedAlertRefs) return@update data
            val match = matchAlert(data.payments, alert, source, at, { it.alertSources }, { it.alertRefs })
            if (match != null) {
                newId = null
                data.copy(
                    payments = data.payments.map { if (it.id == match.id) applyAlert(it, alert, source) else it },
                )
            } else {
                val id = (data.payments.maxOfOrNull { it.id } ?: 0L) + 1
                newId = id
                val payment = Payment(
                    id = id,
                    payeeName = alert.payee ?: UNKNOWN_PAYEE,
                    payeeVpa = alert.vpa,
                    amountPaise = alert.amountPaise,
                    category = ALERT_CATEGORY,
                    voiceNotePath = null,
                    upiApp = null,
                    status = PaymentStatus.DETECTED,
                    createdAt = at,
                    needsReview = true,
                    alertSources = listOf(source),
                    alertRefs = listOfNotNull(alert.ref),
                )
                data.copy(payments = data.payments + payment)
            }
        }
        return newId
    }

    /** The user dismissed an old-style alert notice: the payment stays under [ALERT_CATEGORY]. */
    suspend fun markReviewed(id: Long) {
        store.update { data ->
            // One still waiting for review stays waiting; only a tick or a cross settles it.
            data.copy(
                payments = data.payments.map {
                    if (it.id == id && it.status != PaymentStatus.DETECTED) it.copy(needsReview = false) else it
                },
            )
        }
    }

    /** The green tick on the review page: the payment now counts as spent, under [ALERT_CATEGORY]. */
    suspend fun confirmDetected(id: Long) {
        store.update { data ->
            if (data.payments.none { it.id == id && it.status == PaymentStatus.DETECTED }) return@update data
            data.copy(
                categories = withAlertCategory(data.categories),
                payments = data.payments.map {
                    if (it.id == id) it.copy(status = PaymentStatus.PAID, needsReview = false) else it
                },
            )
        }
    }

    /**
     * Undo for a tick or a cross: puts [payment] back exactly as it was before,
     * and forgets that its bank references were deleted.
     */
    suspend fun restorePayment(payment: Payment) {
        store.update { data ->
            val others = data.payments.filter { it.id != payment.id }
            data.copy(
                payments = (others + payment).sortedBy { it.id },
                deletedAlertRefs = data.deletedAlertRefs - payment.alertRefs.toSet(),
            )
        }
    }

    /** Removes a saved payment for good, so it no longer counts anywhere. */
    suspend fun deletePayment(id: Long) {
        store.update { data ->
            val refs = data.payments.firstOrNull { it.id == id }?.alertRefs.orEmpty()
            data.copy(
                payments = data.payments.filter { it.id != id },
                // Deleting is the user's choice; an alert for it arriving later must not undo it.
                deletedAlertRefs = (data.deletedAlertRefs + refs).distinct().takeLast(MAX_DELETED_REFS),
            )
        }
    }

    suspend fun setStatus(id: Long, status: PaymentStatus) {
        store.update { data ->
            data.copy(payments = data.payments.map { if (it.id == id) it.copy(status = status) else it })
        }
    }
}

/** "Other" must be a category for the spending under it to show on Budget. */
private fun withAlertCategory(categories: List<Category>): List<Category> =
    if (categories.any { it.name == ALERT_CATEGORY }) categories else categories + Category(ALERT_CATEGORY, "📦", 0)

/** Starter categories; budgets total ₹30,000 until the user edits them on the budget screen. */
private val defaultCategories = listOf(
    Category("Food", "🍔", 4_000),
    Category("Groceries", "🛒", 5_000),
    Category("Travel", "🚕", 3_000),
    Category("Shopping", "🛍️", 6_000),
    Category("Bills", "🧾", 4_000),
    Category("Fuel", "⛽", 2_000),
    Category("Health", "💊", 1_500),
    Category("Entertainment", "🎬", 1_500),
    Category("Clothes", "👕", 1_500),
    Category("Gifts", "🎁", 1_000),
    Category("Education", "📚", 500),
    // No "Other": people would pick it most of the time. They create a category instead.
)

/** App-wide singletons. Set up once in [com.budgetapp.BudgetApplication]. */
object Graph {
    lateinit var repository: Repository
        private set

    fun init(context: Context) {
        // The app's own folder on shared storage: no permission needed, and the
        // user can reach the file. Falls back to private storage if that is missing.
        val folder = context.getExternalFilesDir(null) ?: context.filesDir
        repository = Repository(JsonStore(File(folder, "budget.json")))
    }
}
