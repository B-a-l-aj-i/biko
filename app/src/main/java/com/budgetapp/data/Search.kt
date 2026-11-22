package com.budgetapp.data

/**
 * The search screen's list: confirmed payments made between [fromMillis] and
 * [untilMillis] (exclusive) that match [query], newest first. An empty query
 * lists them all. A query matches a payee, a UPI ID or a category by any part
 * of its text, ignoring case; a query that is just an amount, such as "100",
 * "₹1,200" or "49.50", also matches payments of exactly that amount.
 */
fun searchPayments(
    payments: List<Payment>,
    query: String,
    fromMillis: Long,
    untilMillis: Long,
): List<Payment> {
    val inRange = payments
        .filter { it.status == PaymentStatus.PAID && it.createdAt >= fromMillis && it.createdAt < untilMillis }
        .sortedByDescending { it.createdAt }
    val text = query.trim()
    if (text.isEmpty()) return inRange
    val amount = amountQuery(text)
    return inRange.filter { p ->
        p.amountPaise == amount ||
            p.payeeName.contains(text, ignoreCase = true) ||
            p.category.contains(text, ignoreCase = true) ||
            p.note?.contains(text, ignoreCase = true) == true ||
            p.payeeVpa?.contains(text, ignoreCase = true) == true
    }
}

/**
 * Exact paise for a query that is only an amount, such as "₹1,200" or "49.50";
 * null for anything else, including a third decimal place, which no payment has.
 */
fun amountQuery(query: String): Long? {
    val cleaned = query.replace("₹", "").replace(",", "").trim()
    if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1 || !cleaned.all { it.isDigit() || it == '.' }) return null
    if (cleaned.substringAfter('.', "").length > 2) return null
    return parsePaise(cleaned)?.takeIf { it > 0 }
}
