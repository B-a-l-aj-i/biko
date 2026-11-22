package com.budgetapp.alerts

import android.content.Context

/**
 * Fingerprints of alert texts already read, kept for a few days, so a
 * notification that is re-posted or repeats an earlier message never records
 * the same payment twice. Only a hash is kept, never the text.
 */
internal object SeenAlerts {

    private const val PREFS = "seen_alerts"
    private const val KEEP_MS = 3L * 24 * 60 * 60 * 1000

    /** True the first time this text from this app is seen; false after that. */
    @Synchronized
    fun firstTime(context: Context, source: String, text: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "$source:${text.trim().hashCode()}:${text.length}"
        if (prefs.contains(key)) return false
        val now = System.currentTimeMillis()
        val editor = prefs.edit().putLong(key, now)
        prefs.all.forEach { (k, v) -> if (v is Long && now - v > KEEP_MS) editor.remove(k) }
        editor.apply()
        return true
    }
}
