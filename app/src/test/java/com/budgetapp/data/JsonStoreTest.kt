package com.budgetapp.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val sample = BudgetData(
        overallBudget = 5_000,
        categories = listOf(Category("Food", "🍔", 667), Category("Fuel", "⛽", 0)),
        payments = listOf(
            Payment(1, "Chai Point", "chai@upi", 8_550, "Food", "/notes/1.m4a", "com.phonepe.app", PaymentStatus.PAID, 1_700_000_000_000),
            Payment(2, "Ravi", null, 20_000, "Fuel", null, null, PaymentStatus.PENDING, 1_700_000_100_000),
        ),
    )

    @Test
    fun `json round trip keeps everything, nulls and emoji included`() {
        assertEquals(sample, parseBudgetData(sample.toJson()))
    }

    @Test
    fun `json round trip keeps a text note, and an old file without notes still reads`() {
        val noted = sample.copy(payments = sample.payments.map { if (it.id == 1L) it.copy(note = "Lunch with team ☕") else it })
        assertEquals(noted, parseBudgetData(noted.toJson()))

        val old = org.json.JSONObject(noted.toJson()).apply {
            val list = getJSONArray("payments")
            for (i in 0 until list.length()) list.getJSONObject(i).remove("note")
        }.toString()
        assertEquals(sample, parseBudgetData(old))
    }

    @Test
    fun `notes are trimmed, capped, and blank means none`() {
        assertEquals(null, cleanNote(null))
        assertEquals(null, cleanNote("   "))
        assertEquals("Lunch", cleanNote("  Lunch \n"))
        assertEquals(NOTE_MAX_LENGTH, cleanNote("a".repeat(NOTE_MAX_LENGTH + 20))!!.length)
    }

    @Test
    fun `editing a payment's note changes no figure`() = runBlocking {
        val repo = Repository(JsonStore(File(folder.root, "budget.json")))
        val id = repo.addPayment(Payment(0, "Shop", null, 49_50, "Food", null, null, PaymentStatus.PAID, 1_700_000_000_000))
        repo.updatePayment(id, 49_50, "Food", null, "  Dosa  ")
        val saved = repo.snapshot.value.payments.single()
        assertEquals("Dosa", saved.note)
        assertEquals(49_50L, saved.amountPaise)
        repo.updatePayment(id, 49_50, "Food", null, "")
        assertEquals(null, repo.snapshot.value.payments.single().note)
    }

    @Test
    fun `json round trip keeps the chosen look`() {
        for (theme in ThemeMode.entries) {
            for (blackAndWhite in listOf(false, true)) {
                val data = sample.copy(appearance = Appearance(theme, blackAndWhite))
                assertEquals(data, parseBudgetData(data.toJson()))
            }
        }
    }

    @Test
    fun `a file saved before themes stays light, in colour, with all its money data`() {
        val old = org.json.JSONObject(sample.toJson()).apply { remove("appearance") }.toString()
        assertEquals(sample, parseBudgetData(old))
        assertEquals(Appearance(ThemeMode.LIGHT, blackAndWhite = false), parseBudgetData(old).appearance)
    }

    @Test
    fun `a fresh install is light and in colour, even on a phone in dark mode`() {
        assertEquals(Appearance(ThemeMode.LIGHT, blackAndWhite = false), BudgetData().appearance)
        assertFalse(BudgetData().appearance.isDark(systemDark = true))
    }

    @Test
    fun `an unknown theme falls back to light instead of refusing the file`() {
        val edited = org.json.JSONObject(sample.toJson())
            .put("appearance", org.json.JSONObject().put("theme", "SEPIA").put("blackAndWhite", true))
            .toString()
        assertEquals(sample.copy(appearance = Appearance(ThemeMode.LIGHT, blackAndWhite = true)), parseBudgetData(edited))
    }

    @Test
    fun `dark follows the phone only when the theme is left to it`() {
        assertTrue(Appearance(ThemeMode.SYSTEM).isDark(systemDark = true))
        assertFalse(Appearance(ThemeMode.SYSTEM).isDark(systemDark = false))
        assertFalse(Appearance(ThemeMode.LIGHT).isDark(systemDark = true))
        assertTrue(Appearance(ThemeMode.DARK).isDark(systemDark = false))
    }

    @Test
    fun `changing the look changes no figure`() = runBlocking {
        val store = JsonStore(File(folder.root, "budget.json"))
        store.update { sample }
        val repository = Repository(store)
        repository.setTheme(ThemeMode.DARK)
        repository.setBlackAndWhite(true)
        val after = repository.snapshot.value
        assertEquals(Appearance(ThemeMode.DARK, blackAndWhite = true), after.appearance)
        assertEquals(sample, after.copy(appearance = Appearance()))
    }

    @Test
    fun `saved data is read back by a new store`() = runBlocking {
        val file = File(folder.root, "budget.json")
        val first = JsonStore(file)
        assertTrue(first.startedEmpty)
        first.update { sample }

        val second = JsonStore(file)
        assertFalse(second.startedEmpty)
        assertEquals(sample, second.data.value)
        assertFalse("draft file left behind", File(file.path + ".tmp").exists())
    }

    @Test
    fun `an unreadable file is set aside, not overwritten`() = runBlocking {
        val file = File(folder.root, "budget.json")
        file.writeText("{ not json")

        val store = JsonStore(file)
        assertEquals(BudgetData(), store.data.value)
        store.update { sample }

        val kept = folder.root.listFiles { f -> f.name.startsWith("budget-unreadable-") }.orEmpty()
        assertEquals(1, kept.size)
        assertEquals("{ not json", kept.single().readText())
    }

    @Test
    fun `repository hands out payment ids and refuses duplicate categories`() = runBlocking {
        val repository = Repository(JsonStore(File(folder.root, "budget.json")))
        repository.seedDefaultCategories()
        repository.settleBudgets()
        assertEquals(30_000, repository.snapshot.value.overallBudget)

        assertFalse(repository.addCategory("Food", "🍕", 100))
        assertTrue(repository.addCategory("Pets", "🐶", 0))

        val payment = sample.payments[1].copy(id = 0)
        assertEquals(1L, repository.addPayment(payment))
        assertEquals(2L, repository.addPayment(payment))

        repository.setStatus(2, PaymentStatus.PAID)
        assertEquals(PaymentStatus.PAID, repository.snapshot.value.payments.single { it.id == 2L }.status)

        repository.setOverallBudget(5_000)
        assertEquals(5_000, repository.snapshot.value.categories.sumOf { it.monthlyBudget })
    }

    @Test
    fun `deleting a payment removes only that one, and the rest still add up`() = runBlocking {
        val store = JsonStore(File(folder.root, "budget.json"))
        val repository = Repository(store)
        val base = Payment(0, "Shop", null, 0, "Food", null, null, PaymentStatus.PAID, 1_000)
        val a = repository.addPayment(base.copy(amountPaise = 100_49))
        val b = repository.addPayment(base.copy(amountPaise = 49_50))
        val c = repository.addPayment(base.copy(amountPaise = 1))

        repository.deletePayment(b)

        val left = repository.snapshot.value.payments
        assertEquals(listOf(a, c), left.map { it.id })
        assertEquals(100_50L, left.sumOf { it.amountPaise })
        // Saved to the file, not just in memory.
        assertEquals(left, JsonStore(File(folder.root, "budget.json")).data.value.payments)
        // An id that isn't there changes nothing.
        repository.deletePayment(999)
        assertEquals(left, repository.snapshot.value.payments)
    }
}
