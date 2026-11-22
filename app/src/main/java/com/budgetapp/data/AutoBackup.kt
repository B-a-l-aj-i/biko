package com.budgetapp.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * Automatic backup: every few days the app writes a full backup (the same zip
 * as Export) into a folder the user picked once, always as the same file, so
 * there is one up-to-date copy outside the app that survives an uninstall.
 *
 * The decisions live here, with no Android classes, so they are unit tested.
 * The Android side (folder access, the background job) is in
 * com.budgetapp.backup.AutoBackup.
 */

/** How often a backup is written. */
const val AUTO_BACKUP_DAYS = 3

/** The file in the picked folder; each backup replaces it. */
const val AUTO_BACKUP_FILE = "biko-backup.zip"

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * The background check runs a few times a day at times Android chooses, so a
 * backup that is a few hours short of [AUTO_BACKUP_DAYS] already counts as due.
 * Without this it could slip almost a whole extra check later each time.
 */
private const val DUE_EARLY_MS = 6L * 60 * 60 * 1000

/** What the Data screen shows about automatic backup. */
data class AutoBackupStatus(
    /** The picked folder's name; null while automatic backup is off. */
    val folderName: String? = null,
    /** When the last backup was written, epoch millis; null before the first one. */
    val lastBackupAt: Long? = null,
    /** Why the last try did not write a backup; null when it worked. */
    val problem: AutoBackupProblem? = null,
) {
    val isOn: Boolean get() = folderName != null
}

/** Why a backup was not written. */
enum class AutoBackupProblem {
    /** The folder was deleted or the app's access to it was taken away. */
    FOLDER_LOST,

    /**
     * The folder already holds a backup this install did not write, such as
     * one from before a reinstall. It is never replaced without the user asking,
     * because the data here may be empty and the file may be all they have.
     */
    FOUND_OLDER_BACKUP,

    /** Writing failed for another reason, such as a full disk. */
    WRITE_FAILED,
}

/** True when a backup should be written now. A clock set back before the last backup also counts. */
fun isAutoBackupDue(lastBackupAt: Long?, now: Long): Boolean =
    lastBackupAt == null || now < lastBackupAt || now - lastBackupAt >= AUTO_BACKUP_DAYS * DAY_MS - DUE_EARLY_MS

/**
 * Whether a backup may write over the file already in the folder.
 * [byUser] is the "Back up now" button: the user chose to replace it.
 * An automatic backup replaces only a file this install wrote, known by
 * [lastBackupAt] being set.
 */
fun mayReplaceBackup(fileExists: Boolean, lastBackupAt: Long?, byUser: Boolean): Boolean =
    !fileExists || byUser || lastBackupAt != null

private val backupTimeFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

/** The status line under "Automatic backup", for example "Last backup 22 Nov, 3:10 am · next in 3 days". */
fun autoBackupLine(status: AutoBackupStatus, now: Long, zone: ZoneId): String {
    when (status.problem) {
        AutoBackupProblem.FOLDER_LOST ->
            return "Can't reach the folder any more. Choose it again to keep backing up."
        AutoBackupProblem.FOUND_OLDER_BACKUP ->
            return "This folder already has $AUTO_BACKUP_FILE from before. Import it to get that data " +
                "back, or tap Back up now to replace it with what is on this phone."
        AutoBackupProblem.WRITE_FAILED ->
            return "The last backup could not be written. It will try again; Back up now tries at once."
        null -> Unit
    }
    val last = status.lastBackupAt ?: return "No backup yet"
    val lastText = backupTimeFormat.format(Instant.ofEpochMilli(last).atZone(zone))
        .replace("AM", "am").replace("PM", "pm")
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val nextDay = Instant.ofEpochMilli(last).atZone(zone).toLocalDate().plusDays(AUTO_BACKUP_DAYS.toLong())
    val days = ChronoUnit.DAYS.between(today, nextDay)
    val next = when {
        days <= 0 -> "next one today"
        days == 1L -> "next tomorrow"
        else -> "next in $days days"
    }
    return "Last backup $lastText · $next"
}
