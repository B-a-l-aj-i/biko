package com.budgetapp.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.budgetapp.MainActivity
import com.budgetapp.R
import com.budgetapp.data.Payment
import com.budgetapp.data.UNKNOWN_PAYEE
import com.budgetapp.data.alertSourceLabel
import com.budgetapp.data.formatPaise

/**
 * Our own notification for a debit the app found but the user never recorded:
 * "₹500.00 to RAVI KUMAR isn't tracked". Tapping it opens the review page,
 * where the user ticks or crosses it out. Swiping it away leaves it waiting there.
 */
object AlertNotifier {

    private const val CHANNEL = "untracked_payments"

    /** Extra on the tap intent: the id of the payment to review. */
    const val EXTRA_REVIEW_PAYMENT = "com.budgetapp.REVIEW_PAYMENT"

    fun show(context: Context, payment: Payment) {
        if (!canPost(context)) return
        ensureChannel(context)
        val id = payment.id.toInt()

        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_REVIEW_PAYMENT, payment.id)
            // Reuse the open app if there is one, instead of stacking a second copy.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val dismissed = Intent(context, AlertDismissedReceiver::class.java)
            .putExtra(EXTRA_REVIEW_PAYMENT, payment.id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val amount = formatPaise(payment.amountPaise)
        val what = if (payment.payeeName == UNKNOWN_PAYEE) "$amount debit" else "$amount to ${payment.payeeName}"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_payment)
            .setContentTitle("$what isn't tracked")
            .setContentText("Tap to review it")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${alertSourceLabel(payment.alertSources.firstOrNull().orEmpty())} reported $what. It doesn't count as spent until you confirm it. " +
                        "Tap to review it.",
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Amounts stay off the lock screen, as they stay hidden in the app until asked for.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_payment)
                    .setContentTitle("A payment isn't tracked")
                    .build(),
            )
            .setWhen(payment.createdAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, id, open, flags))
            .setDeleteIntent(PendingIntent.getBroadcast(context, id, dismissed, flags))
            .build()
        NotificationManagerCompat.from(context).notify(TAG, id, notification)
    }

    /** Takes the notification down once the payment has been opened or saved. */
    fun cancel(context: Context, paymentId: Long) {
        NotificationManagerCompat.from(context).cancel(TAG, paymentId.toInt())
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Untracked payments", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Payments seen in your UPI apps' notifications that aren't in the app yet"
            },
        )
    }

    private const val TAG = "untracked"
}
