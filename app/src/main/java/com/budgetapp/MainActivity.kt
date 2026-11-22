package com.budgetapp

import java.time.YearMonth
import android.widget.Toast
import android.content.Intent
import android.content.res.Configuration
import com.budgetapp.alerts.AlertNotifier
import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetapp.backup.AutoBackup
import com.budgetapp.data.Graph
import com.budgetapp.data.PaymentStatus
import com.budgetapp.ui.budget.BudgetScreen
import com.budgetapp.ui.data.DataScreen
import com.budgetapp.ui.home.HomeScreen
import com.budgetapp.ui.home.HomeViewModel
import com.budgetapp.ui.home.PendingPayment
import com.budgetapp.data.formatPaise
import com.budgetapp.ui.nav.StackHost
import com.budgetapp.ui.nav.rememberNavigator
import com.budgetapp.ui.pay.PayScreen
import com.budgetapp.ui.theme.BudgetAppTheme
import com.budgetapp.ui.search.SearchScreen
import com.budgetapp.ui.review.ReviewScreen
import com.budgetapp.data.monthBounds
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch
import com.budgetapp.ui.home.ConfirmPaymentDialog
import com.budgetapp.data.voiceNotesDir

class MainActivity : ComponentActivity() {

    /** A payment to open for review, from a tap on an untracked-payment notification. */
    private var reviewRequest by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The saved look is already in memory, so the bars are right from the first frame.
        val phoneDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        styleBars(Graph.repository.snapshot.value.appearance.isDark(phoneDark))
        // Not on a restore: the tap was already handled before the activity was recreated.
        if (savedInstanceState == null) takeReviewRequest(intent)
        setContent {
            val appearance by remember { Graph.repository.appearance }
                .collectAsStateWithLifecycle(Graph.repository.snapshot.value.appearance)
            val dark = appearance.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) { styleBars(dark) }
            BudgetAppTheme(darkTheme = dark, blackAndWhite = appearance.blackAndWhite) {
                BudgetApp(
                    reviewRequest = reviewRequest,
                    onReviewOpened = { reviewRequest = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeReviewRequest(intent)
    }

    private fun takeReviewRequest(intent: Intent?) {
        val id = intent?.getLongExtra(AlertNotifier.EXTRA_REVIEW_PAYMENT, -1L) ?: -1L
        if (id >= 0) reviewRequest = id
    }

    /** Clear system bars, with icons that show on the app's page: dark on light, light on dark. */
    private fun styleBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

/** Screens that open above the home screen. */
private sealed interface Screen {

    data object Budget : Screen

    /** Where the user's data is stored. */
    data object Data : Screen

    /** Finds a month's payments by payee, category or amount; [month] null is the current month. */
    data class Search(val month: YearMonth? = null) : Screen

    /** Adds a payment made outside the app (the + button). */
    data object Pay : Screen

    /** The pay page, filled in with a saved payment to change it. */
    data class Edit(val paymentId: Long) : Screen

    /** Debits found in UPI app notifications in [month], waiting for a tick or a cross. */
    data class Review(val month: YearMonth) : Screen
}

/** The month [millis] falls in, on this phone's clock. */
private fun monthOf(millis: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
private fun BudgetApp(
    /** A payment found in a bank or UPI alert, to open for the user to say what it was for. */
    reviewRequest: Long? = null,
    onReviewOpened: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val homeViewModel: HomeViewModel = viewModel { HomeViewModel(Graph.repository) }
    val state by homeViewModel.state.collectAsStateWithLifecycle()
    // Home alone follows the month picked from its pill; everything else stays on this month.
    val homeState by homeViewModel.homeState.collectAsStateWithLifecycle()
    // Opening the app again always starts on the current month.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { homeViewModel.showMonth(null) }

    val navigator = rememberNavigator<Screen>()
    var confirming by remember { mutableStateOf<PendingPayment?>(null) }

    LaunchedEffect(reviewRequest) {
        val id = reviewRequest ?: return@LaunchedEffect
        onReviewOpened()
        AlertNotifier.cancel(context, id)
        val payment = Graph.repository.snapshot.value.payments.firstOrNull { it.id == id }
        // A debit still waiting goes to the review page; an older notice opens the payment itself.
        if (payment == null || payment.status == PaymentStatus.DETECTED) {
            // The month the payment was made in, which may have ended since the alert came.
            val month = payment?.let { monthOf(it.createdAt) } ?: YearMonth.now()
            navigator.push(Screen.Review(month))
        } else {
            navigator.push(Screen.Edit(id))
        }
    }

    StackHost(
        navigator = navigator,
        root = {
            HomeScreen(
                state = homeState,
                onRefresh = homeViewModel::refresh,
                onBudget = { navigator.push(Screen.Budget) },
                // Placeholder until splitting is built.
                onSplit = { Toast.makeText(context, "Split is coming soon", Toast.LENGTH_SHORT).show() },
                onProfile = { navigator.push(Screen.Data) },
                onSearch = { navigator.push(Screen.Search()) },
                onMonth = homeViewModel::showMonth,
                onAdd = { navigator.push(Screen.Pay) },
                // Both act on the month Home is showing, the same one its card and banner list.
                onReview = { navigator.push(Screen.Review(homeState.shownMonth ?: YearMonth.now())) },
                onPending = { confirming = homeState.pending.firstOrNull() },
                onTransaction = { id -> navigator.push(Screen.Edit(id)) },
            )
        },
    ) { entry ->
        when (val screen = entry.key) {
            Screen.Budget -> BudgetScreen(
                state = state,
                onSetOverall = homeViewModel::setOverallBudget,
                onSetCategory = homeViewModel::setCategoryBudget,
                onAddCategory = { name, emoji, rupees ->
                    scope.launch { Graph.repository.addCategory(name, emoji, rupees) }
                },
            )

            Screen.Data -> {
                val data by Graph.repository.snapshot.collectAsStateWithLifecycle()
                val autoBackup by AutoBackup.status.collectAsStateWithLifecycle()
                DataScreen(
                    file = Graph.repository.dataFile,
                    categoryCount = data.categories.size,
                    paymentCount = data.payments.size,
                    appearance = data.appearance,
                    onTheme = { theme -> scope.launch { Graph.repository.setTheme(theme) } },
                    onBlackAndWhite = { on -> scope.launch { Graph.repository.setBlackAndWhite(on) } },
                    onExport = { uri ->
                        scope.launch {
                            val message = try {
                                val out = context.contentResolver.openOutputStream(uri, "wt")
                                    ?: error("Could not open the file")
                                val saved = out.use { Graph.repository.exportBackup(it) }
                                "Exported ${saved.payments} payments and ${saved.voiceNotes} voice notes"
                            } catch (e: Exception) {
                                "Export failed: ${e.message ?: "unknown error"}"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onImport = { uri ->
                        scope.launch {
                            val message = try {
                                val input = context.contentResolver.openInputStream(uri)
                                    ?: error("Could not open the file")
                                val voiceDir = voiceNotesDir(context)
                                val loaded = input.use {
                                    Graph.repository.importBackup(it, voiceDir, System.currentTimeMillis())
                                }
                                "Imported ${loaded.payments} payments and ${loaded.voiceNotes} voice notes"
                            } catch (e: Exception) {
                                // Nothing was changed: the backup is read in full before anything is replaced.
                                "Not a backup this app can read. Nothing was changed."
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    autoBackup = autoBackup,
                    onBackupFolder = { uri ->
                        scope.launch {
                            val message = try {
                                backupMessage(AutoBackup.setFolder(context, uri))
                            } catch (e: SecurityException) {
                                "That folder can't be used. Pick another one."
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onBackUpNow = {
                        scope.launch {
                            Toast.makeText(context, backupMessage(AutoBackup.backUpNow(context)), Toast.LENGTH_LONG).show()
                        }
                    },
                    onAutoBackupOff = { AutoBackup.turnOff(context) },
                )
            }

            is Screen.Search -> {
                val data by Graph.repository.snapshot.collectAsStateWithLifecycle()
                SearchScreen(
                    payments = data.payments,
                    month = screen.month,
                    // The edit sheet rises over the results; closing it lands back on them.
                    onOpen = { id -> navigator.push(Screen.Edit(id)) },
                    onClose = { navigator.pop(entry) },
                )
            }

            is Screen.Review -> {
                val data by Graph.repository.snapshot.collectAsStateWithLifecycle()
                val (from, until) = monthBounds(screen.month, ZoneId.systemDefault())
                ReviewScreen(
                    // Only that month's: another month's debits wait in that month.
                    payments = data.payments.filter { it.createdAt >= from && it.createdAt < until },
                    onConfirm = { payment ->
                        AlertNotifier.cancel(context, payment.id)
                        scope.launch { Graph.repository.confirmDetected(payment.id) }
                    },
                    onRemove = { payment ->
                        AlertNotifier.cancel(context, payment.id)
                        scope.launch { Graph.repository.deletePayment(payment.id) }
                    },
                    onUndo = { before -> scope.launch { Graph.repository.restorePayment(before) } },
                    // Picking a category there and tapping Add confirms it too.
                    onOpen = { id -> navigator.push(Screen.Edit(id)) },
                    onClose = { navigator.pop(entry) },
                )
            }

            Screen.Pay -> PayScreen(
                categories = state.allCategories,
                onDone = { navigator.pop(entry) },
            )

            is Screen.Edit -> {
                // Read once: the page keeps its own copy while the user edits.
                val payment = remember(screen.paymentId) {
                    Graph.repository.snapshot.value.payments.firstOrNull { it.id == screen.paymentId }
                }
                if (payment == null) {
                    LaunchedEffect(Unit) { navigator.pop(entry) }
                } else {
                    PayScreen(
                        categories = state.allCategories,
                        onDone = { navigator.pop(entry) },
                        editing = payment,
                    )
                }
            }
        }
    }

    confirming?.let { payment ->
        ConfirmPaymentDialog(
            payee = payment.payee,
            amount = formatPaise(payment.amountPaise),
            onAnswer = { paid ->
                confirming = null
                scope.launch {
                    Graph.repository.setStatus(
                        payment.id,
                        if (paid) PaymentStatus.PAID else PaymentStatus.CANCELLED,
                    )
                }
            },
        )
    }
}

/** The toast after a backup to the picked folder; the Data screen explains any problem in full. */
private fun backupMessage(result: AutoBackup.Result): String = when (result) {
    is AutoBackup.Result.Written -> "Backed up ${result.payments} payments and ${result.voiceNotes} voice notes"
    is AutoBackup.Result.NotWritten -> "Backup not written; see the Backup card"
}
