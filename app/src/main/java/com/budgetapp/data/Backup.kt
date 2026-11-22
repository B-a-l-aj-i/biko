package com.budgetapp.data

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * A backup is one .zip the user keeps somewhere outside the app (Drive,
 * Downloads), so it survives an uninstall. It holds:
 *
 *   budget.json          the same JSON the app saves, with each voice note's
 *                        path changed to its place inside the zip
 *   voice-notes/<file>   the audio of every voice note that still exists
 *
 * No Android classes here, so it is unit tested on the JVM.
 */

private const val BACKUP_JSON = "budget.json"
private const val BACKUP_VOICE_DIR = "voice-notes/"

/** A budget.json bigger than this is not one this app wrote; refuse it rather than run out of memory. */
private const val MAX_JSON_BYTES = 20L * 1024 * 1024

/** What went into, or came out of, a backup. */
data class BackupSummary(val payments: Int, val voiceNotes: Int)

/**
 * Writes [data] and the voice notes it points to into [out] as a zip.
 * A voice note whose file is gone is left out, and its payment saved with no voice note.
 */
fun writeBackup(data: BudgetData, out: OutputStream): BackupSummary {
    // One entry per audio file, even if two payments share it. The payment id
    // in front keeps two files with the same name apart.
    val entryFor = LinkedHashMap<String, String>()
    data.payments.forEach { payment ->
        val path = payment.voiceNotePath ?: return@forEach
        if (path !in entryFor && File(path).isFile) {
            entryFor[path] = BACKUP_VOICE_DIR + "${payment.id}-${File(path).name}"
        }
    }
    val inZip = data.copy(
        payments = data.payments.map { it.copy(voiceNotePath = it.voiceNotePath?.let(entryFor::get)) },
    )

    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry(BACKUP_JSON))
        zip.write(inZip.toJson().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        entryFor.forEach { (path, entry) ->
            zip.putNextEntry(ZipEntry(entry))
            File(path).inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
    return BackupSummary(payments = data.payments.size, voiceNotes = entryFor.size)
}

/**
 * Reads a backup made by [writeBackup], copying its voice notes into [voiceDir]
 * and pointing each payment at its copy. Existing files there are never
 * overwritten. Also takes a bare budget.json (a copy of the app's own file); its
 * voice notes are kept only where the file is still in [voiceDir].
 *
 * Throws when [input] is neither, leaving [voiceDir] as it was.
 */
fun readBackup(input: InputStream, voiceDir: File): BudgetData {
    val stream = BufferedInputStream(input)
    stream.mark(4)
    val zipped = stream.read() == 'P'.code && stream.read() == 'K'.code
    stream.reset()
    return if (zipped) readZip(stream, voiceDir) else readBareJson(stream, voiceDir)
}

private fun readZip(input: InputStream, voiceDir: File): BudgetData {
    var json: String? = null
    val copied = mutableMapOf<String, String>()
    try {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.isDirectory -> Unit
                    entry.name == BACKUP_JSON -> json = zip.readCapped(MAX_JSON_BYTES).toString(Charsets.UTF_8)
                    entry.name.startsWith(BACKUP_VOICE_DIR) -> {
                        val name = entry.name.removePrefix(BACKUP_VOICE_DIR)
                        // Only a plain file name: nothing may land outside voiceDir.
                        if (!isPlainName(name)) continue
                        voiceDir.mkdirs()
                        val target = freeFile(voiceDir, name)
                        target.outputStream().use { zip.copyTo(it) }
                        copied[entry.name] = target.absolutePath
                    }
                    // Anything else is not ours; skip it.
                }
            }
        }
        val data = parseBudgetData(json ?: throw IllegalArgumentException("No $BACKUP_JSON in this backup"))
        return data.copy(payments = data.payments.map { it.copy(voiceNotePath = it.voiceNotePath?.let(copied::get)) })
    } catch (e: Exception) {
        copied.values.forEach { File(it).delete() }
        throw e
    }
}

private fun readBareJson(input: InputStream, voiceDir: File): BudgetData {
    val data = parseBudgetData(input.readCapped(MAX_JSON_BYTES).toString(Charsets.UTF_8))
    val home = voiceDir.canonicalFile
    return data.copy(
        payments = data.payments.map { payment ->
            val kept = payment.voiceNotePath?.takeIf { path ->
                val file = File(path).canonicalFile
                file.parentFile == home && file.isFile
            }
            payment.copy(voiceNotePath = kept)
        },
    )
}

private fun isPlainName(name: String): Boolean =
    name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name

/** [name] in [dir], or the same name with a number added when that is taken. */
private fun freeFile(dir: File, name: String): File {
    val first = File(dir, name)
    if (!first.exists()) return first
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val stem = name.substring(0, dot)
    val ext = name.substring(dot)
    var n = 2
    while (true) {
        val next = File(dir, "$stem-$n$ext")
        if (!next.exists()) return next
        n++
    }
}

private fun InputStream.readCapped(limit: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) throw IllegalArgumentException("Backup file is too large")
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
