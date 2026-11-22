package com.budgetapp.ui.data

import com.budgetapp.ui.components.scrollFade
import com.budgetapp.ui.theme.PhosphorLight
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.time.LocalDate
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.budgetapp.ui.nav.rememberEntrance
import com.budgetapp.ui.nav.riseIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.semantics.Role
import com.budgetapp.data.Appearance
import com.budgetapp.data.AutoBackupStatus
import com.budgetapp.data.ThemeMode
import com.budgetapp.ui.components.SegmentedToggle
import com.budgetapp.ui.theme.BudgetAppTheme
import com.budgetapp.ui.theme.cardBorder
import com.budgetapp.ui.theme.cardOutline
import java.io.File
import java.time.ZoneId
import java.util.Locale
import com.budgetapp.ui.theme.CardShape
import com.budgetapp.data.dayTimeText

/** The title, six cards and the closing note: the pieces that arrive one after another. */
private const val SECTIONS = 8

/** In the order of [ThemeMode]: the phone's own setting, then the two fixed looks. */
private val THEME_LABELS = listOf("Auto", "Light", "Dark")

/**
 * Tells the user where their data lives: one JSON [file] on the phone.
 * The counts change whenever the file does, which also refreshes its size and time.
 */
@Composable
fun DataScreen(
    file: File,
    categoryCount: Int,
    paymentCount: Int,
    appearance: Appearance = Appearance(),
    onTheme: (ThemeMode) -> Unit = {},
    onBlackAndWhite: (Boolean) -> Unit = {},
    /** Write a backup to the file the user just created. */
    onExport: (Uri) -> Unit = {},
    /** Replace everything with the backup the user just picked. */
    onImport: (Uri) -> Unit = {},
    autoBackup: AutoBackupStatus = AutoBackupStatus(),
    /** Back up automatically into the folder the user just picked. */
    onBackupFolder: (Uri) -> Unit = {},
    onBackUpNow: () -> Unit = {},
    onAutoBackupOff: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // Re-read when the data changes; the file is saved before the counts update.
    val details = remember(file, categoryCount, paymentCount) {
        if (file.exists()) "${sizeText(file.length())} · saved ${dayTimeText(file.lastModified(), ZoneId.systemDefault())}" else "Not saved yet"
    }

    // Each section rises a short way into its own place once the sheet is up.
    val arrive = rememberEntrance()

    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) onExport(uri)
    }
    val importFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    var confirmingImport by rememberSaveable { mutableStateOf(false) }
    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onBackupFolder(uri)
    }

    val scroll = rememberScrollState()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .scrollFade(scroll)
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // No back arrow: this opens as a sheet, closed by pulling it down or
            // with the system back gesture.
            Text(
                "Your data",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                    .riseIn(arrive, order = 0, of = SECTIONS),
            )

            Surface(
                shape = CardShape,
                border = cardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .riseIn(arrive, order = 1, of = SECTIONS),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    SegmentedToggle(
                        options = THEME_LABELS,
                        selected = ThemeMode.entries.indexOf(appearance.theme),
                        onSelect = { onTheme(ThemeMode.entries[it]) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = appearance.blackAndWhite,
                                role = Role.Switch,
                                onValueChange = onBlackAndWhite,
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Black and white", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "No colours, for the strongest contrast. The charts use greys.",
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        // The whole row is the switch, so this one takes no taps of its own.
                        // Off, the stock switch is grey on grey and all but vanishes
                        // on a dark card: give it a clear thumb and rim instead.
                        Switch(
                            checked = appearance.blackAndWhite,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                uncheckedThumbColor = muted,
                                uncheckedTrackColor = MaterialTheme.colorScheme.background,
                                uncheckedBorderColor = muted,
                            ),
                        )
                    }
                }
            }

            PaymentAlertsCard(Modifier.riseIn(arrive, order = 2, of = SECTIONS))

            Surface(
                shape = CardShape,
                border = cardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .riseIn(arrive, order = 3, of = SECTIONS),
            ) {
                Column(Modifier.padding(20.dp)) {
                    IconBadge(PhosphorLight.DeviceMobile)
                    Spacer(Modifier.height(16.dp))
                    Text("Stored only on this phone", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Your budgets and payments are saved in a single file. " +
                            "Nothing is uploaded, and no account is needed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                    )
                }
            }

            Surface(
                shape = CardShape,
                border = cardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .riseIn(arrive, order = 4, of = SECTIONS),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(PhosphorLight.BracketsCurly)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(file.name, style = MaterialTheme.typography.titleMedium)
                            Text(details, style = MaterialTheme.typography.bodySmall, color = muted)
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text("Folder", style = MaterialTheme.typography.labelMedium, color = muted)
                    Spacer(Modifier.height(4.dp))
                    Text(folderText(file), style = MaterialTheme.typography.bodyLarge)

                    Spacer(Modifier.height(16.dp))
                    Text("Full path", style = MaterialTheme.typography.labelMedium, color = muted)
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer {
                        Text(
                            file.path,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                                .cardOutline(RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openFile(context, file) }, modifier = Modifier.weight(1f)) {
                            Icon(
                                PhosphorLight.ArrowSquareOut,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Open file")
                        }
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(file.path))
                                // Android 13 and later show their own "copied" message.
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                PhosphorLight.Copy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Copy path")
                        }
                    }
                }
            }

            Surface(
                shape = CardShape,
                border = cardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .riseIn(arrive, order = 5, of = SECTIONS),
            ) {
                Column(Modifier.padding(20.dp)) {
                    IconBadge(PhosphorLight.ShieldCheck)
                    Spacer(Modifier.height(16.dp))
                    Text("Backup", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Export saves your budgets, payments and voice notes in one .zip file. " +
                            "Keep it in Drive or Downloads: uninstalling the app deletes everything " +
                            "stored here, but not that file.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { exportTo.launch("budget-backup-${LocalDate.now()}.zip") },
                            modifier = Modifier.weight(1f),
                        ) { Text("Export") }
                        OutlinedButton(
                            onClick = { confirmingImport = true },
                            modifier = Modifier.weight(1f),
                        ) { Text("Import") }
                    }
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))
                    AutoBackupSection(
                        status = autoBackup,
                        onChooseFolder = { pickBackupFolder.launch(null) },
                        onBackUpNow = onBackUpNow,
                        onTurnOff = onAutoBackupOff,
                    )
                }
            }

            Surface(
                shape = CardShape,
                border = cardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .riseIn(arrive, order = 6, of = SECTIONS),
            ) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    CountRow("Categories", categoryCount)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CountRow("Payments", paymentCount)
                }
            }

            Text(
                "You can also copy ${file.name} itself. Some file managers hide the " +
                    "Android/data folder; connecting the phone to a computer always shows it. " +
                    "That copy has no voice notes; an export does.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .riseIn(arrive, order = 7, of = SECTIONS),
            )
        }
    }

    if (confirmingImport) {
        AlertDialog(
            onDismissRequest = { confirmingImport = false },
            title = { Text("Replace your data?") },
            text = {
                Text(
                    "Everything on this phone (budgets, categories, payments, voice notes) is " +
                        "replaced by the backup you pick. The current data is kept as a copy in " +
                        "the app's folder, in case you need it back.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingImport = false
                    // Some file apps call a .json "octet-stream" or plain text.
                    importFrom.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "text/*"))
                }) { Text("Pick backup") }
            },
            dismissButton = { TextButton(onClick = { confirmingImport = false }) { Text("Cancel") } },
        )
    }
}

/** Shows [file] in whichever viewer the user picks, read-only. */
private fun openFile(context: Context, file: File) {
    if (!file.exists()) {
        Toast.makeText(context, "Nothing saved yet", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    // Offered as plain text: few phones have a JSON viewer, and the one app that
    // claims JSON may be somewhere the user never meant to send their finances.
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "text/plain")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // Always ask which app, so the file never opens somewhere by surprise.
    val chooser = Intent.createChooser(view, "Open ${file.name} with")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can open it", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.background, CircleShape)
            .cardOutline(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CountRow(label: String, count: Int) {
    Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(count.toString(), fontWeight = FontWeight.Medium)
    }
}

/** The folder as a file manager shows it: "Internal storage › Android › data › …". */
private fun folderText(file: File): String {
    val folder = file.parentFile?.path.orEmpty()
    val shared = "/storage/emulated/0"
    val shown = if (folder.startsWith(shared)) "Internal storage" + folder.removePrefix(shared) else folder
    return shown.trim('/').split('/').joinToString(" › ")
}

private fun sizeText(bytes: Long): String = when {
    bytes < 1024 -> "$bytes bytes"
    bytes < 1024 * 1024 -> String.format(Locale.ENGLISH, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024.0))
}


@Preview(showBackground = true, widthDp = 380, heightDp = 820)
@Composable
private fun DataScreenPreview() {
    BudgetAppTheme {
        DataScreen(
            file = File("/storage/emulated/0/Android/data/com.budgetapp/files/budget.json"),
            categoryCount = 11,
            paymentCount = 24,
        )
    }
}
