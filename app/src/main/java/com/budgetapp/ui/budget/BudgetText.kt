package com.budgetapp.ui.budget

import com.budgetapp.data.formatRupees

/**
 * The line under the monthly budget that says how much of it has been split
 * into category budgets. This is about the plan, not about spending, so the
 * wording never uses a bare amount that could be read as money spent: it
 * always names "category budgets".
 *
 * [monthlyBudget] and [allocated] (the sum of the category budgets) are whole
 * rupees, and the amounts shown always add up: split plus not split equals
 * the monthly budget.
 */
internal fun allocationText(monthlyBudget: Int, allocated: Int): String {
    val notSplit = monthlyBudget - allocated
    return when {
        allocated <= 0 -> "No category has its own budget yet"
        notSplit > 0 ->
            "Category budgets add up to ${formatRupees(allocated)} of ${formatRupees(monthlyBudget)} · " +
                "${formatRupees(notSplit)} not split yet"
        notSplit < 0 ->
            "Category budgets add up to ${formatRupees(allocated)}, " +
                "${formatRupees(-notSplit)} more than the monthly budget"
        else -> "Category budgets add up to the full ${formatRupees(monthlyBudget)}"
    }
}
