package com.budgetapp.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * What earlier builds saved, back when the data lived in a database and
 * preferences instead of the JSON file. Null if there is nothing to bring over.
 * The old database is left where it is.
 */
fun readLegacyData(context: Context): BudgetData? {
    val database = context.getDatabasePath("budget.db")
    if (!database.exists()) return null
    return try {
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val categories = db.rawQuery(
                "SELECT name, emoji, monthlyBudget FROM Category ORDER BY rowid",
                null,
            ).use { row ->
                buildList {
                    while (row.moveToNext()) add(Category(row.getString(0), row.getString(1), row.getInt(2)))
                }
            }
            val payments = db.rawQuery(
                "SELECT id, payeeName, payeeVpa, amountPaise, category, voiceNotePath, upiApp, status, createdAt " +
                    "FROM Payment ORDER BY id",
                null,
            ).use { row ->
                buildList {
                    while (row.moveToNext()) {
                        add(
                            Payment(
                                id = row.getLong(0),
                                payeeName = row.getString(1),
                                payeeVpa = row.getString(2),
                                amountPaise = row.getLong(3),
                                category = row.getString(4),
                                voiceNotePath = row.getString(5),
                                upiApp = row.getString(6),
                                status = PaymentStatus.valueOf(row.getString(7)),
                                createdAt = row.getLong(8),
                            ),
                        )
                    }
                }
            }

            val overall = context.getSharedPreferences("budget", Context.MODE_PRIVATE).getInt("overall_budget", 0)
            // One old build changed the monthly budget without touching the
            // categories, which could leave them adding up to more than it.
            val fitted = if (overall > 0 && categories.sumOf { it.monthlyBudget } > overall) {
                val scaled = rescaleBudgets(categories.map { it.monthlyBudget }, overall, overall)
                categories.mapIndexed { index, it -> it.copy(monthlyBudget = scaled[index]) }
            } else {
                categories
            }
            BudgetData(overall, fitted, payments)
        }
    } catch (e: Exception) {
        Log.w("LegacyImport", "Couldn't read the old database; starting without it", e)
        null
    }
}
