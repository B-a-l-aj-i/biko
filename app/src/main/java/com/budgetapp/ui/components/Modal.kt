package com.budgetapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.budgetapp.ui.theme.cardBorder

/** Corner radius and inner spacing shared by every pop-up card in the app. */
val ModalShape = RoundedCornerShape(28.dp)
val ModalFieldShape = RoundedCornerShape(14.dp)

/**
 * The app's pop-up: a title, the [content], and Cancel beside one main button.
 * Tighter than the stock Material dialog, which leaves wide empty margins
 * around short forms.
 */
@Composable
fun AppDialog(
    title: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // Lets the card choose its own width instead of the platform's narrow default.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = ModalShape,
            color = MaterialTheme.colorScheme.surface,
            border = cardBorder(),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
                ModalButtons(
                    confirmLabel = confirmLabel,
                    confirmEnabled = confirmEnabled,
                    onConfirm = onConfirm,
                    onCancel = onDismiss,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Cancel and the main action, side by side at equal width. */
@Composable
fun ModalButtons(
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = cardBorder(),
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
        ) { Text("Cancel") }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
        ) { Text(confirmLabel) }
    }
}
