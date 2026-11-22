package com.budgetapp.backup

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.budgetapp.data.AUTO_BACKUP_FILE
import com.budgetapp.data.AutoBackupProblem
import com.budgetapp.data.AutoBackupStatus
import com.budgetapp.data.Graph
import com.budgetapp.data.isAutoBackupDue
import com.budgetapp.data.mayReplaceBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Writes the backup zip into the folder the user picked, as [AUTO_BACKUP_FILE],
 * every few days ([com.budgetapp.data.AUTO_BACKUP_DAYS]). A background job
 * checks a few times a day and the app checks at every start, so a backup is
 * written soon after it falls due even if one check is missed.
 *
 * The folder and the last backup's time are kept in the app's preferences,
 * not in the budget data: they describe this phone, not the budget.
 */
object AutoBackup {
    private const val PREFS = "auto-backup"
    private const val KEY_FOLDER = "folder"
    private const val KEY_FOLDER_NAME = "folderName"
    private const val KEY_LAST = "lastBackupAt"
    private const val KEY_PROBLEM = "problem"
    private const val JOB_ID = 4_201

    private val _status = MutableStateFlow(AutoBackupStatus())

    /** What the Data screen shows; updated after every change and every backup. */
    val status: StateFlow<AutoBackupStatus> = _status

    /** One backup at a time: the job and the button can't write the file together. */
    private val writing = Mutex()

    /** Reads the saved settings. Call once at startup. */
    fun init(context: Context) {
        _status.value = read(context)
    }

    /**
     * Starts backing up to [folder], a folder the user just picked, and writes
     * the first backup at once. A folder picked earlier is let go.
     */
    suspend fun setFolder(context: Context, folder: Uri): Result {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(folder, flags)
        savedFolder(context)?.takeIf { it != folder }?.let { old ->
            runCatching { resolver.releasePersistableUriPermission(old, flags) }
        }
        prefs(context).edit()
            .putString(KEY_FOLDER, folder.toString())
            .putString(KEY_FOLDER_NAME, folderName(context, folder))
            // A new folder starts fresh: what is in it was not written by this install.
            .remove(KEY_LAST)
            .remove(KEY_PROBLEM)
            .apply()
        _status.value = read(context)
        schedule(context)
        return backUp(context, byUser = false)
    }

    /** Stops automatic backup. The last backup file stays where it is. */
    fun turnOff(context: Context) {
        savedFolder(context)?.let { folder ->
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.releasePersistableUriPermission(folder, flags) }
        }
        prefs(context).edit().clear().apply()
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        _status.value = AutoBackupStatus()
    }

    /** The "Back up now" button: writes at once, replacing whatever backup file is there. */
    suspend fun backUpNow(context: Context): Result = backUp(context, byUser = true)

    /** For the job and app start: writes only when a backup is due. */
    suspend fun backUpIfDue(context: Context) {
        val saved = read(context)
        if (saved.isOn && isAutoBackupDue(saved.lastBackupAt, System.currentTimeMillis())) {
            backUp(context, byUser = false)
        }
    }

    /** Makes sure the background check is scheduled while a folder is set. Cheap to call often. */
    fun schedule(context: Context) {
        val jobs = context.getSystemService(JobScheduler::class.java)
        if (savedFolder(context) == null) {
            jobs.cancel(JOB_ID)
            return
        }
        // Scheduling again would restart the period, so leave a pending job alone.
        if (jobs.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, BackupJobService::class.java))
            .setPeriodic(TimeUnit.HOURS.toMillis(8), TimeUnit.HOURS.toMillis(2))
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)
            .build()
        jobs.schedule(job)
    }

    /** How a backup went, for a short message. */
    sealed interface Result {
        data class Written(val payments: Int, val voiceNotes: Int) : Result
        data class NotWritten(val problem: AutoBackupProblem) : Result
    }

    private suspend fun backUp(context: Context, byUser: Boolean): Result = writing.withLock {
        withContext(Dispatchers.IO) {
            val result = try {
                write(context, byUser)
            } catch (e: SecurityException) {
                Result.NotWritten(AutoBackupProblem.FOLDER_LOST)
            } catch (e: Exception) {
                Result.NotWritten(AutoBackupProblem.WRITE_FAILED)
            }
            val edit = prefs(context).edit()
            when (result) {
                is Result.Written -> edit.putLong(KEY_LAST, System.currentTimeMillis()).remove(KEY_PROBLEM)
                is Result.NotWritten -> edit.putString(KEY_PROBLEM, result.problem.name)
            }
            edit.apply()
            _status.value = read(context)
            result
        }
    }

    private suspend fun write(context: Context, byUser: Boolean): Result {
        val folder = savedFolder(context) ?: return Result.NotWritten(AutoBackupProblem.FOLDER_LOST)
        val resolver = context.contentResolver
        val stillAllowed = resolver.persistedUriPermissions.any { it.uri == folder && it.isWritePermission }
        if (!stillAllowed) return Result.NotWritten(AutoBackupProblem.FOLDER_LOST)

        val folderDoc = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val existing = findFile(context, folder)
        if (!mayReplaceBackup(existing != null, read(context).lastBackupAt, byUser)) {
            return Result.NotWritten(AutoBackupProblem.FOUND_OLDER_BACKUP)
        }

        // The whole zip is made on the phone first, so a failure while making it
        // never touches the backup already in the folder.
        val temp = File(context.cacheDir, "auto-backup.zip")
        try {
            val summary = temp.outputStream().use { Graph.repository.exportBackup(it) }
            val target = existing
                ?: DocumentsContract.createDocument(resolver, folderDoc, "application/zip", AUTO_BACKUP_FILE)
                ?: return Result.NotWritten(AutoBackupProblem.WRITE_FAILED)
            val out = resolver.openOutputStream(target, "wt") ?: return Result.NotWritten(AutoBackupProblem.WRITE_FAILED)
            out.use { sink -> temp.inputStream().use { it.copyTo(sink) } }
            return Result.Written(summary.payments, summary.voiceNotes)
        } finally {
            temp.delete()
        }
    }

    /** The backup file already in [folder], if any. */
    private fun findFile(context: Context, folder: Uri): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        context.contentResolver.query(children, columns, null, null, null)?.use { rows ->
            while (rows.moveToNext()) {
                if (rows.getString(1) == AUTO_BACKUP_FILE) {
                    return DocumentsContract.buildDocumentUriUsingTree(folder, rows.getString(0))
                }
            }
        }
        return null
    }

    private fun folderName(context: Context, folder: Uri): String {
        val doc = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val name = runCatching {
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        return name ?: "the picked folder"
    }

    private fun savedFolder(context: Context): Uri? = prefs(context).getString(KEY_FOLDER, null)?.let(Uri::parse)

    private fun read(context: Context): AutoBackupStatus {
        val prefs = prefs(context)
        if (prefs.getString(KEY_FOLDER, null) == null) return AutoBackupStatus()
        return AutoBackupStatus(
            folderName = prefs.getString(KEY_FOLDER_NAME, null) ?: "the picked folder",
            lastBackupAt = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0L },
            problem = prefs.getString(KEY_PROBLEM, null)
                ?.let { name -> AutoBackupProblem.entries.firstOrNull { it.name == name } },
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
