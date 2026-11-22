package com.budgetapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AutoBackupTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `first backup is due at once`() {
        assertTrue(isAutoBackupDue(null, at(22, 10)))
    }

    @Test
    fun `not due again within three days`() {
        val last = at(22, 10)
        assertFalse(isAutoBackupDue(last, last))
        assertFalse(isAutoBackupDue(last, last + 1 * day))
        assertFalse(isAutoBackupDue(last, last + 2 * day))
        // Just short of the early allowance.
        assertFalse(isAutoBackupDue(last, last + 3 * day - 6 * hour - 1))
    }

    @Test
    fun `due from a few hours before three days`() {
        val last = at(22, 10)
        assertTrue(isAutoBackupDue(last, last + 3 * day - 6 * hour))
        assertTrue(isAutoBackupDue(last, last + 3 * day))
        assertTrue(isAutoBackupDue(last, last + 10 * day))
    }

    @Test
    fun `clock set back before the last backup makes it due`() {
        val last = at(22, 10)
        assertTrue(isAutoBackupDue(last, last - 1))
    }

    @Test
    fun `an older backup in the folder is replaced only when the user asks`() {
        // Fresh install picking the folder that holds the old backup: keep it.
        assertFalse(mayReplaceBackup(fileExists = true, lastBackupAt = null, byUser = false))
        assertTrue(mayReplaceBackup(fileExists = true, lastBackupAt = null, byUser = true))
        // This install wrote it: replace it on schedule.
        assertTrue(mayReplaceBackup(fileExists = true, lastBackupAt = at(20, 9), byUser = false))
        // Nothing there yet.
        assertTrue(mayReplaceBackup(fileExists = false, lastBackupAt = null, byUser = false))
    }

    @Test
    fun `status line says when the last and next backups are`() {
        val status = AutoBackupStatus(folderName = "Documents", lastBackupAt = at(22, 15, 10))
        assertEquals("Last backup 22 Nov, 3:10 pm · next in 3 days", autoBackupLine(status, at(22, 16), zone))
        assertEquals("Last backup 22 Nov, 3:10 pm · next in 2 days", autoBackupLine(status, at(23, 9), zone))
        assertEquals("Last backup 22 Nov, 3:10 pm · next tomorrow", autoBackupLine(status, at(24, 23), zone))
        assertEquals("Last backup 22 Nov, 3:10 pm · next one today", autoBackupLine(status, at(25, 1), zone))
        // Overdue (phone was off): it runs at the next chance, so still "today".
        assertEquals("Last backup 22 Nov, 3:10 pm · next one today", autoBackupLine(status, at(28, 1), zone))
    }

    @Test
    fun `status line before the first backup and on problems`() {
        assertEquals("No backup yet", autoBackupLine(AutoBackupStatus(folderName = "Documents"), at(22, 10), zone))
        val lost = AutoBackupStatus("Documents", at(20, 9), AutoBackupProblem.FOLDER_LOST)
        assertTrue(autoBackupLine(lost, at(22, 10), zone).startsWith("Can't reach the folder"))
        val older = AutoBackupStatus("Documents", null, AutoBackupProblem.FOUND_OLDER_BACKUP)
        assertTrue(autoBackupLine(older, at(22, 10), zone).contains("Import it"))
    }

    @Test
    fun `on only once a folder is picked`() {
        assertFalse(AutoBackupStatus().isOn)
        assertTrue(AutoBackupStatus(folderName = "Documents").isOn)
    }
}
