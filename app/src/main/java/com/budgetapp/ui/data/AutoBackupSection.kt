package com.budgetapp.ui.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.budgetapp.data.AUTO_BACKUP_DAYS
import com.budgetapp.data.AUTO_BACKUP_FILE
import com.budgetapp.data.AutoBackupProblem
import com.budgetapp.data.AutoBackupStatus
import com.budgetapp.data.autoBackupLine
import com.budgetapp.ui.theme.LocalStatusColors
import java.time.ZoneId

/**
 * Automatic backup: off until the user picks a folder, then the folder, when
 * the last backup was written and when the next one is due.
 */
@Composable
internal fun AutoBackupSection(
    status: AutoBackupStatus,
    onChooseFolder: () -> Unit,
    onBackUpNow: () -> Unit,
    onTurnOff: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var confirmingReplace by rememberSaveable { mutableStateOf(false) }

    Text("Automatic backup", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    if (!status.isOn) {
        Text(
            "Every $AUTO_BACKUP_DAYS days, saves the same backup as Export into a folder you pick, " +
                "as $AUTO_BACKUP_FILE. Each one replaces the last, so the folder always holds one " +
                "full, up-to-date copy.",
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) { Text("Choose folder") }
        return
    }

    Text("Every $AUTO_BACKUP_DAYS days into ${status.folderName}", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(4.dp))
    // Read once per status: the line only needs to be right when the screen opens or a backup runs.
    val now = remember(status) { System.currentTimeMillis() }
    val failed = status.problem == AutoBackupProblem.FOLDER_LOST || status.problem == AutoBackupProblem.WRITE_FAILED
    Text(
        autoBackupLine(status, now, ZoneId.systemDefault()),
        style = MaterialTheme.typography.bodyMedium,
        color = if (failed) LocalStatusColors.current.danger else muted,
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                // Replacing a backup this install did not write needs a yes first.
                if (status.problem == AutoBackupProblem.FOUND_OLDER_BACKUP) confirmingReplace = true else onBackUpNow()
            },
            modifier = Modifier.weight(1f),
        ) { Text("Back up now") }
        OutlinedButton(onClick = onChooseFolder, modifier = Modifier.weight(1f)) { Text("Change folder") }
    }
    TextButton(onClick = onTurnOff) { Text("Turn off") }

    if (confirmingReplace) {
        AlertDialog(
            onDismissRequest = { confirmingReplace = false },
            title = { Text("Replace the older backup?") },
            text = {
                Text(
                    "$AUTO_BACKUP_FILE in this folder was saved before, maybe before the app was " +
                        "reinstalled. It is replaced by what is on this phone now, and can't be " +
                        "brought back. To restore it instead, use Import.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingReplace = false
                    onBackUpNow()
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmingReplace = false }) { Text("Cancel") } },
        )
    }
}
