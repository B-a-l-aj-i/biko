package com.budgetapp.alerts

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.budgetapp.data.Graph
import com.budgetapp.data.UPI_APPS
import com.budgetapp.data.parseUpiAppAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watches notifications from PhonePe, Paytm, Google Pay and Navi for payments
 * the user made. Everything else is dropped on its package name, unread.
 * A debit the app hasn't recorded is saved under "Other" and the user is asked
 * what it was for. Nothing read here leaves the phone.
 */
class PaymentAlertListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = sbn.packageName
        if (source == packageName || source !in watchedPackages) return
        val notification = sbn.notification ?: return
        // A bundle's summary repeats what its children already said.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val texts = alertTexts(notification.extras ?: return)
        if (texts.isEmpty()) return
        scope.launch {
            for (text in texts) {
                // Apps re-post a notification as it updates, and messaging-style ones
                // repeat earlier messages; the same text is only ever read once.
                if (!SeenAlerts.firstTime(this@PaymentAlertListener, source, text)) continue
                val alert = parseUpiAppAlert(text) ?: continue
                val id = Graph.repository.recordDebitAlert(alert, source, sbn.postTime) ?: continue
                val payment = Graph.repository.snapshot.value.payments.firstOrNull { it.id == id } ?: continue
                AlertNotifier.show(this@PaymentAlertListener, payment)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {

        /** Whether the user has given the app notification access in Settings. */
        fun isEnabled(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        fun component(context: Context) = ComponentName(context, PaymentAlertListener::class.java)

        /**
         * Where payment alerts come from: only the UPI apps' own notifications
         * (PhonePe, Paytm, Google Pay, Navi). SMS and email are never read.
         */
        val watchedPackages: Set<String> get() = UPI_APPS.keys
    }
}

/**
 * The texts in a notification worth reading as alerts, each on its own:
 * the newest message of a conversation, each line of an inbox summary
 * (several notices bundled), or else the title with the full body.
 */
private fun alertTexts(extras: Bundle): List<String> {
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

    val messages = parcelables(extras, Notification.EXTRA_MESSAGES)
    if (messages.isNotEmpty()) {
        val newest = (messages.last() as? Bundle)?.getCharSequence("text")?.toString()
        return listOfNotNull(newest?.takeIf { it.isNotBlank() })
    }

    val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
    if (!lines.isNullOrEmpty()) return lines.map { it.toString() }.filter { it.isNotBlank() }

    val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
        ?.toString().orEmpty()
    // The title carries the amount on some apps ("₹500 paid"), the body on others.
    val text = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")
    return if (text.isBlank()) emptyList() else listOf(text)
}

@Suppress("DEPRECATION")
private fun parcelables(extras: Bundle, key: String): Array<out Parcelable> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        extras.getParcelableArray(key, Parcelable::class.java) ?: emptyArray()
    } else {
        extras.getParcelableArray(key) ?: emptyArray()
    }
