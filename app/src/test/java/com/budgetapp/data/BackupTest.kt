package com.budgetapp.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun voiceNote(dir: File, name: String, bytes: ByteArray): File =
        File(dir.apply { mkdirs() }, name).apply { writeBytes(bytes) }

    private fun payment(id: Long, paise: Long, category: String, voice: String?) =
        Payment(id, "Payee $id", null, paise, category, voice, null, PaymentStatus.PAID, 1_700_000_000_000 + id)

    private fun zipOf(data: BudgetData): ByteArray = ByteArrayOutputStream().also { writeBackup(data, it) }.toByteArray()

    @Test
    fun `round trip keeps every figure to the paisa and brings the audio back`() {
        val oldPhone = folder.newFolder("old-phone")
        val noteA = voiceNote(oldPhone, "1.m4a", byteArrayOf(1, 2, 3))
        val noteB = voiceNote(oldPhone, "2.m4a", byteArrayOf(9, 8))
        val data = BudgetData(
            overallBudget = 30_000,
            categories = listOf(Category("Food", "🍔", 4_000), Category("Fuel", "⛽", 2_000)),
            payments = listOf(
                payment(1, 49_50, "Food", noteA.path),
                payment(2, 1, "Fuel", noteB.path),
                payment(3, 12_345_67, "Food", null),
            ),
            appearance = Appearance(ThemeMode.DARK, blackAndWhite = true),
            deletedAlertRefs = listOf("RRN123"),
        )

        val zip = zipOf(data)
        val newPhone = folder.newFolder("new-phone")
        val back = readBackup(ByteArrayInputStream(zip), newPhone)

        // Everything but the voice note paths is identical.
        assertEquals(data.copy(payments = data.payments.map { it.copy(voiceNotePath = null) }),
            back.copy(payments = back.payments.map { it.copy(voiceNotePath = null) }))
        assertEquals(data.payments.sumOf { it.amountPaise }, back.payments.sumOf { it.amountPaise })
        assertEquals(12_395_18L, back.payments.sumOf { it.amountPaise })

        // The audio is in the new folder, byte for byte, and each payment points at its own copy.
        val pathA = back.payments[0].voiceNotePath!!
        val pathB = back.payments[1].voiceNotePath!!
        assertEquals(newPhone.absolutePath, File(pathA).parent)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(pathA).readBytes())
        assertArrayEquals(byteArrayOf(9, 8), File(pathB).readBytes())
        assertNull(back.payments[2].voiceNotePath)
    }

    @Test
    fun `a voice note whose file is gone is left out, not fatal`() {
        val data = BudgetData(payments = listOf(payment(1, 100, "Food", File(folder.root, "missing.m4a").path)))
        val zip = zipOf(data)
        val names = ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            generateSequence { z.nextEntry }.map { it.name }.toList()
        }
        assertEquals(listOf("budget.json"), names)
        val back = readBackup(ByteArrayInputStream(zip), folder.newFolder("in"))
        assertNull(back.payments.single().voiceNotePath)
        assertEquals(100L, back.payments.single().amountPaise)
    }

    @Test
    fun `two payments sharing one note store it once and both get it back`() {
        val note = voiceNote(folder.newFolder("notes"), "n.m4a", byteArrayOf(7))
        val data = BudgetData(payments = listOf(payment(1, 100, "Food", note.path), payment(2, 200, "Food", note.path)))
        val back = readBackup(ByteArrayInputStream(zipOf(data)), folder.newFolder("in"))
        assertEquals(back.payments[0].voiceNotePath, back.payments[1].voiceNotePath)
        assertTrue(File(back.payments[0].voiceNotePath!!).isFile)
    }

    @Test
    fun `import never overwrites a voice note already on the phone`() {
        val note = voiceNote(folder.newFolder("old"), "1.m4a", byteArrayOf(1))
        val data = BudgetData(payments = listOf(payment(1, 100, "Food", note.path)))
        val target = folder.newFolder("in")
        val existing = voiceNote(target, "1-1.m4a", byteArrayOf(5, 5))
        val back = readBackup(ByteArrayInputStream(zipOf(data)), target)
        assertArrayEquals(byteArrayOf(5, 5), existing.readBytes())
        assertFalse(back.payments.single().voiceNotePath == existing.absolutePath)
        assertArrayEquals(byteArrayOf(1), File(back.payments.single().voiceNotePath!!).readBytes())
    }

    @Test
    fun `entries trying to escape the voice folder are ignored`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("budget.json"))
            zip.write(BudgetData(overallBudget = 100).toJson().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("voice-notes/../evil.txt"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        val target = File(folder.root, "voice")
        readBackup(ByteArrayInputStream(out.toByteArray()), target)
        assertFalse(File(folder.root, "evil.txt").exists())
        assertTrue(target.listFiles().isNullOrEmpty())
    }

    @Test
    fun `a zip without budget json is refused and leaves no audio behind`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("voice-notes/a.m4a"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        val target = folder.newFolder("in")
        try {
            readBackup(ByteArrayInputStream(out.toByteArray()), target)
            fail("expected a refusal")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        assertTrue(target.listFiles().isNullOrEmpty())
    }

    @Test
    fun `garbage is refused`() {
        try {
            readBackup(ByteArrayInputStream("not a backup".toByteArray()), folder.newFolder("in"))
            fail("expected a refusal")
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun `a bare budget json imports, keeping only voice notes that live in the voice folder`() {
        val voiceDir = folder.newFolder("voice-notes")
        val inside = voiceNote(voiceDir, "1.m4a", byteArrayOf(1))
        val outside = voiceNote(folder.newFolder("elsewhere"), "2.m4a", byteArrayOf(2))
        val data = BudgetData(
            overallBudget = 500,
            payments = listOf(
                payment(1, 100, "Food", inside.path),
                payment(2, 200, "Food", outside.path),
                payment(3, 300, "Food", File(voiceDir, "gone.m4a").path),
            ),
        )
        val back = readBackup(ByteArrayInputStream(data.toJson().toByteArray()), voiceDir)
        assertEquals(inside.canonicalPath, File(back.payments[0].voiceNotePath!!).canonicalPath)
        assertNull(back.payments[1].voiceNotePath)
        assertNull(back.payments[2].voiceNotePath)
        assertEquals(600L, back.payments.sumOf { it.amountPaise })
    }

    @Test
    fun `importing replaces everything and keeps the old file beside it`() = runBlocking {
        val home = folder.newFolder("app")
        val repo = Repository(JsonStore(File(home, "budget.json")))
        repo.addCategory("Food", "🍔", 1_000)
        repo.addPayment(payment(0, 49_50, "Food", null))

        val backup = BudgetData(
            overallBudget = 8_000,
            categories = listOf(Category("Rent", "🏠", 5_000), Category("Food", "🍔", 3_000)),
            payments = listOf(payment(7, 2_500_00, "Rent", null), payment(9, 99, "Food", null)),
        )
        val summary = repo.importBackup(ByteArrayInputStream(zipOf(backup)), File(home, "voice"), now = 42)

        assertEquals(backup, repo.snapshot.value)
        assertEquals(BackupSummary(payments = 2, voiceNotes = 0), summary)
        // Category budgets still add up to the monthly budget after the swap.
        assertEquals(repo.snapshot.value.overallBudget, repo.snapshot.value.categories.sumOf { it.monthlyBudget })
        // A new payment gets an id after the imported ones.
        assertEquals(10L, repo.addPayment(payment(0, 1, "Food", null)))

        val kept = parseBudgetData(File(home, "budget-before-import-42.json").readText())
        assertEquals(49_50L, kept.payments.single().amountPaise)
        // And the saved file holds the import, so it survives a restart.
        assertEquals(2_500_00L + 99 + 1, JsonStore(File(home, "budget.json")).data.value.payments.sumOf { it.amountPaise })
    }

    @Test
    fun `a refused import changes nothing`() = runBlocking {
        val home = folder.newFolder("app")
        val repo = Repository(JsonStore(File(home, "budget.json")))
        repo.addPayment(payment(0, 49_50, "Food", null))
        val before = repo.snapshot.value
        try {
            repo.importBackup(ByteArrayInputStream("nope".toByteArray()), File(home, "voice"), now = 1)
            fail("expected a refusal")
        } catch (e: Exception) {
            // expected
        }
        assertEquals(before, repo.snapshot.value)
        assertFalse(File(home, "budget-before-import-1.json").exists())
    }
}
