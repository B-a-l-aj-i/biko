package com.budgetapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Bump when the layout of the file changes, so older files can be recognised. */
private const val FORMAT_VERSION = 1

/**
 * Keeps all the app's data in one JSON [file] the user can find, read and back up.
 * The whole file is rewritten on every change; it stays small enough for that.
 */
class JsonStore(val file: File) {

    /** True when there was no file at startup, i.e. nothing has been saved as JSON yet. */
    val startedEmpty: Boolean = !file.exists()

    private val mutex = Mutex()
    private val state = MutableStateFlow(load())

    val data: StateFlow<BudgetData> = state

    /** Applies [change] and saves the result before anyone sees it. Returns the new data. */
    suspend fun update(change: (BudgetData) -> BudgetData): BudgetData = mutex.withLock {
        val next = change(state.value)
        if (next != state.value) {
            withContext(Dispatchers.IO) { save(next) }
            state.value = next
        }
        next
    }

    /**
     * Swaps in [next] wholesale, as an import does. The file it replaces is
     * first copied to [keepOldAs], so an import can always be undone by hand.
     */
    suspend fun replace(next: BudgetData, keepOldAs: File) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (file.exists()) file.copyTo(keepOldAs, overwrite = true)
            save(next)
        }
        state.value = next
    }

    private fun load(): BudgetData {
        if (!file.exists()) return BudgetData()
        return try {
            parseBudgetData(file.readText())
        } catch (e: Exception) {
            // Probably a hand edit gone wrong. Set the file aside rather than
            // overwrite it, so nothing in it is lost.
            file.renameTo(File(file.parentFile, "budget-unreadable-${System.currentTimeMillis()}.json"))
            BudgetData()
        }
    }

    private fun save(data: BudgetData) {
        file.parentFile?.mkdirs()
        // Write beside the file and swap it in, so a crash mid-write can't leave half a file.
        val draft = File(file.path + ".tmp")
        draft.writeText(data.toJson())
        Files.move(
            draft.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }
}

fun BudgetData.toJson(): String = JSONObject()
    .put("version", FORMAT_VERSION)
    .put("overallBudget", overallBudget)
    .put(
        "categories",
        JSONArray(
            categories.map {
                JSONObject()
                    .put("name", it.name)
                    .put("emoji", it.emoji)
                    .put("monthlyBudget", it.monthlyBudget)
            },
        ),
    )
    .put(
        "payments",
        JSONArray(
            payments.map {
                JSONObject()
                    .put("id", it.id)
                    .put("payeeName", it.payeeName)
                    .put("payeeVpa", it.payeeVpa ?: JSONObject.NULL)
                    .put("amountPaise", it.amountPaise)
                    .put("category", it.category)
                    .put("voiceNotePath", it.voiceNotePath ?: JSONObject.NULL)
                    .put("upiApp", it.upiApp ?: JSONObject.NULL)
                    .put("status", it.status.name)
                    .put("createdAt", it.createdAt)
                    .put("needsReview", it.needsReview)
                    .put("alertSources", JSONArray(it.alertSources))
                    .put("alertRefs", JSONArray(it.alertRefs))
                    .put("note", it.note ?: JSONObject.NULL)
            },
        ),
    )
    .put(
        "appearance",
        JSONObject()
            .put("theme", appearance.theme.name)
            .put("blackAndWhite", appearance.blackAndWhite),
    )
    .put("deletedAlertRefs", JSONArray(deletedAlertRefs))
    .toString(2)

/** Throws when [text] isn't a budget file this build understands. */
fun parseBudgetData(text: String): BudgetData {
    val root = JSONObject(text)
    val categories = root.optJSONArray("categories") ?: JSONArray()
    val payments = root.optJSONArray("payments") ?: JSONArray()
    // Files saved before there were themes have no "appearance", and stay
    // light. A theme this build doesn't know falls back to light too: how the
    // app looks is never a reason to refuse a file that holds the user's money data.
    val look = root.optJSONObject("appearance")
    val theme = look?.optString("theme").orEmpty()
    return BudgetData(
        appearance = Appearance(
            theme = ThemeMode.entries.firstOrNull { it.name == theme } ?: ThemeMode.LIGHT,
            blackAndWhite = look?.optBoolean("blackAndWhite", false) ?: false,
        ),
        overallBudget = root.optInt("overallBudget", 0),
        categories = List(categories.length()) { index ->
            val item = categories.getJSONObject(index)
            Category(
                name = item.getString("name"),
                emoji = item.optString("emoji", ""),
                monthlyBudget = item.optInt("monthlyBudget", 0),
            )
        },
        payments = List(payments.length()) { index ->
            val item = payments.getJSONObject(index)
            Payment(
                id = item.getLong("id"),
                payeeName = item.getString("payeeName"),
                payeeVpa = item.stringOrNull("payeeVpa"),
                amountPaise = item.getLong("amountPaise"),
                category = item.getString("category"),
                voiceNotePath = item.stringOrNull("voiceNotePath"),
                upiApp = item.stringOrNull("upiApp"),
                status = PaymentStatus.valueOf(item.getString("status")),
                createdAt = item.getLong("createdAt"),
                // Absent in files saved before payment alerts existed.
                needsReview = item.optBoolean("needsReview", false),
                alertSources = item.stringList("alertSources"),
                alertRefs = item.stringList("alertRefs"),
                // Absent in files saved before text notes existed.
                note = if (item.has("note")) cleanNote(item.stringOrNull("note")) else null,
            )
        },
        // Absent in files saved before payments could be deleted.
        deletedAlertRefs = root.stringList("deletedAlertRefs"),
    )
}

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

private fun JSONObject.stringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return List(array.length()) { array.getString(it) }
}
