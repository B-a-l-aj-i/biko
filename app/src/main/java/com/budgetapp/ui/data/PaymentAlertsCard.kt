package com.budgetapp.ui.data

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.budgetapp.alerts.AlertNotifier
import com.budgetapp.alerts.PaymentAlertListener
import com.budgetapp.data.ALERT_CATEGORY
import com.budgetapp.ui.theme.cardBorder
import com.budgetapp.ui.theme.CardShape

/**
 * Turns on payment alerts: the app reads the payment notifications of PhonePe,
 * Paytm, Google Pay and Navi
 * to catch payments the user didn't record. Play Store policy
 * wants this said plainly before the user is sent to grant access.
 */
@Composable
fun PaymentAlertsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(PaymentAlertListener.isEnabled(context)) }
    var canNotify by remember { mutableStateOf(AlertNotifier.canPost(context)) }
    // Coming back from Settings is when either of these may have changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        listening = PaymentAlertListener.isEnabled(context)
        canNotify = AlertNotifier.canPost(context)
    }

    fun openAccessSettings() {
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    PaymentAlertListener.component(context).flattenToString(),
                )
        } else {
            null
        }
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        for (intent in listOfNotNull(detail, list)) {
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Some phones lack the detail page; fall through to the list.
            }
        }
    }

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted
        if (!listening) openAccessSettings()
    }

    fun turnOn() {
        if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAccessSettings()
        }
    }

    Surface(shape = CardShape, border = cardBorder(), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Payment alerts", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    listening && canNotify -> "On. Payments in PhonePe, Paytm, Google Pay or Navi that aren't in the app " +
                        "send you a notification asking what they were for."
                    listening -> "Reading alerts, but the app can't notify you. Allow notifications to be asked " +
                        "what each payment was for; until then they're filed under $ALERT_CATEGORY."
                    else -> "Catch payments you didn't add. The app reads the payment notifications of PhonePe, " +
                        "Paytm, Google Pay and Navi, and asks what each new payment was for. Only payments are kept; " +
                        "everything is read on this phone and nothing is sent anywhere."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!listening || !canNotify) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = ::turnOn, modifier = Modifier.fillMaxWidth()) {
                    Text(if (listening) "Allow notifications" else "Turn on")
                }
                if (!listening) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "In Settings, turn on Notification access for Budget. If it's greyed out, open " +
                            "App info, tap ⋮ and choose Allow restricted settings first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open App info") }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = ::openAccessSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Turn off in Settings")
                }
            }
        }
    }
}
