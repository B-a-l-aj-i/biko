package com.budgetapp.data

/** A category with what was spent in it in the shown month, for the screens that list categories. */
data class CategoryBudget(
    val name: String,
    val spentPaise: Long,
    /** Whole rupees. Zero means no budget set. */
    val budget: Int,
    val emoji: String = "",
) {
    /** Negative once over budget. */
    val leftPaise: Long
        get() = rupeesToPaise(budget) - spentPaise
}
