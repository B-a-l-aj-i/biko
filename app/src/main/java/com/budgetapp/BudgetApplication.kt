package com.budgetapp

import android.app.Application
import com.budgetapp.backup.AutoBackup
import com.budgetapp.data.Graph
import com.budgetapp.data.demoData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class BudgetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        AutoBackup.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // The demo build (screenshots only) starts with made-up payments instead.
            if (BuildConfig.DEMO) Graph.repository.loadDemo(demoData(ZonedDateTime.now()))
            Graph.repository.importOlderData(this@BudgetApplication)
            Graph.repository.seedDefaultCategories()
            Graph.repository.settleBudgets()
            // Catches up on a backup the background job missed. Never in the
            // demo build, whose made-up payments must not replace a real backup.
            if (!BuildConfig.DEMO) {
                AutoBackup.schedule(this@BudgetApplication)
                AutoBackup.backUpIfDue(this@BudgetApplication)
            }
        }
    }
}
