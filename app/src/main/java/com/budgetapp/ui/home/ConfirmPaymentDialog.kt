package com.budgetapp.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.budgetapp.data.title

/** Asks how a payment left pending (from an older version of the app) ended. */
@Composable
fun ConfirmPaymentDialog(payee: String?, amount: String, onAnswer: (paid: Boolean) -> Unit) {
    AlertDialog(
        // Dismissing without answering would leave the payment unresolved.
        onDismissRequest = {},
        title = { Text("Did the payment go through?") },
        text = { Text(if (payee != null) "$amount to $payee" else amount) },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Yes, paid") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Not paid") } },
    )
}
