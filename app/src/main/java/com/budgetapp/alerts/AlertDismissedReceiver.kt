package com.budgetapp.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.budgetapp.data.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The user swiped an untracked-payment notification away. A debit waiting on
 * the review page stays there; an older one already under "Other" ends its review.
 */
class AlertDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlertNotifier.EXTRA_REVIEW_PAYMENT, -1L)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Graph.repository.markReviewed(id)
            } finally {
                pending.finish()
            }
        }
    }
}
