package com.budgetapp.data

import java.time.ZonedDateTime

/**
 * Made-up data for the demo build (screenshots only): a ₹30,000 month with
 * about a third spent across a few categories, dated in the month of [now] so
 * Home always has something to show. Never used by the real app.
 */
fun demoData(now: ZonedDateTime): BudgetData {
    val categories = listOf(
        Category("Food", "🍔", 4_000),
        Category("Groceries", "🛒", 5_000),
        Category("Travel", "🚕", 3_000),
        Category("Shopping", "🛍️", 6_000),
        Category("Bills", "🧾", 4_000),
        Category("Fuel", "⛽", 2_000),
        Category("Health", "💊", 1_500),
        Category("Entertainment", "🎬", 1_500),
        Category("Clothes", "👕", 1_500),
        Category("Gifts", "🎁", 1_000),
        Category("Education", "📚", 500),
    )
    // Days ago, hour, payee, paise, category, note.
    val rows = listOf(
        Row(0, 13, "Swiggy", 349_00, "Food", null),
        Row(0, 9, "Uber", 210_00, "Travel", null),
        Row(1, 19, "BigBasket", 1_842_50, "Groceries", "Weekly groceries"),
        Row(2, 21, "PVR Cinemas", 640_00, "Entertainment", null),
        Row(3, 8, "Indian Oil", 1_500_00, "Fuel", null),
        Row(4, 12, "Chai Point", 85_50, "Food", null),
        Row(5, 18, "Airtel", 799_00, "Bills", "Postpaid"),
        Row(6, 16, "Myntra", 2_199_00, "Shopping", null),
        Row(7, 11, "Apollo Pharmacy", 412_00, "Health", null),
        Row(8, 20, "Zomato", 525_00, "Food", null),
        Row(9, 10, "DMart", 2_310_00, "Groceries", null),
        Row(10, 15, "Rapido", 96_00, "Travel", null),
        Row(11, 17, "BESCOM", 1_431_00, "Bills", "Electricity"),
    )
    val monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.zone)
    val payments = rows.mapIndexed { index, row ->
        // Never before the 1st, so every payment lands in this month.
        val at = now.minusDays(row.daysAgo.toLong()).withHour(row.hour).withMinute(10 + index)
            .let { if (it < monthStart) monthStart.plusHours(row.hour.toLong()) else it }
            .let { if (it > now) now.minusMinutes(5L + index) else it }
        Payment(
            id = index + 1L,
            payeeName = row.payee,
            payeeVpa = null,
            amountPaise = row.paise,
            category = row.category,
            voiceNotePath = null,
            upiApp = null,
            status = PaymentStatus.PAID,
            createdAt = at.toInstant().toEpochMilli(),
            note = row.note,
        )
    }
    return BudgetData(overallBudget = 30_000, categories = categories, payments = payments)
}

private data class Row(val daysAgo: Int, val hour: Int, val payee: String, val paise: Long, val category: String, val note: String?)
