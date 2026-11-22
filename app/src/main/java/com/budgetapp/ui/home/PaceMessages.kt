package com.budgetapp.ui.home

import com.budgetapp.data.SpendingPace

// The lines shown at the top of the home screen, in the cheeky style of a food
// app's push notifications: most sarcastic when spending runs ahead. They
// deliberately carry no amounts: showing how much is left nudges people to spend it.
// Each must fit on one line of the home card: at most [PACE_MESSAGE_MAX] characters.

/** Longest pace line that still fits the home card's one line on a narrow phone. */
const val PACE_MESSAGE_MAX = 36

private val onTrack = listOf(
    "Perfectly balanced, as it should be.",
    "Not rich, not broke. Just right.",
    "Spending like an adult. Who are you?",
    "Dal chawal month: simple, solid.",
    "Your budget gives a thumbs up.",
    "No drama this month. Love that.",
    "Boring spending? Best spending.",
    "Wallet and you: still friends.",
    "Right on pace. Keep it chill.",
    "Smooth month. Don't jinx it.",
    "Calm wallet, calm you.",
    "Not a rupee out of line. Yet.",
    "On track. Your cart is behaving.",
    "Like a tidy cricket over.",
    "Budget and you: in sync.",
    "So balanced, it's almost boring.",
    "Steady hands, steady wallet.",
    "Nothing to fix. Carry on, boss.",
    "Slow and steady wins the month.",
    "Your money is minding its manners.",
    "Spending with a plan? Fancy.",
    "Mom would approve. Mostly.",
    "Cool, calm, and on budget.",
    "Bills paid, peace intact.",
    "Even pace. Even mood.",
)

private val over = listOf(
    "Wallet called. It's crying.",
    "Budget? Never heard of her.",
    "Bold of you to spend like that.",
    "Your money is on a world tour.",
    "Salary: arrived. Salary: gone.",
    "Main character spending. Rich? No.",
    "Delivery guy has your face saved.",
    "Ordering again? We noticed.",
    "Budget left. Didn't say bye.",
    "Big spender energy, small balance.",
    "Cart full. Account empty. Iconic.",
    "Your bank app needs therapy.",
    "Treating yourself? Every day?",
    "That's not a budget. That's a wish.",
    "Spend now, cry at month-end.",
    "Retail therapy is not covered.",
    "Your wallet has left the chat.",
    "Month is long. Money is not.",
    "At this rate, last week is Maggi.",
    "\"One more order,\" four orders ago.",
    "Your UPI PIN deserves a holiday.",
    "Plot twist: money isn't infinite.",
    "Rich in taste, poor by the 20th.",
    "You and your cart: too close.",
    "Even your budget is skipping meals.",
    "Payday called. It wants a refund.",
    "You beat your budget. Not a win.",
)

private val saving = listOf(
    "Spent nothing? Are you okay?",
    "Sharma ji's kid is taking notes.",
    "Your wallet is gaining weight.",
    "Money staying put? Suspicious.",
    "Your bank thinks you got hacked.",
    "Delivery apps miss you. Good.",
    "Cart abandoned. Wallet saved.",
    "Savings doing push-ups today.",
    "Look who's ahead of the month.",
    "Your future self just fist-bumped.",
    "Discipline unlocked. Show-off.",
    "Piggy bank is doing a happy dance.",
    "Budget boss mode: activated.",
    "Money left? That's a flex.",
    "Saving like it's your job.",
    "Wallet thick, worries thin.",
    "Quiet month. Loud savings.",
    "Month-end you sends a thank you.",
    "Mom would frame this month.",
    "Said no to the sale. Legend.",
    "Low spend, high vibes.",
    "Your budget is bored. In a good way.",
    "Rich by the 30th. Who's this?",
    "Your savings account says hi.",
    "Underspending? Unheard of. Proud.",
)

private fun linesFor(pace: SpendingPace): List<String> = when (pace) {
    SpendingPace.ON_TRACK -> onTrack
    SpendingPace.OVER -> over
    SpendingPace.SAVING -> saving
}

/** How many different lines [pace] has; the line comes round again after this many days. */
fun paceMessageCount(pace: SpendingPace): Int = linesFor(pace).size

/**
 * The line for [pace]. [epochDay] picks which one, so it stays the same all
 * day instead of changing every time the screen redraws, and moves on tomorrow.
 * [refreshes] counts the user's pulls to refresh: each one moves to the next
 * line for the same pace, so a pull always shows a different line.
 */
fun paceMessage(pace: SpendingPace, epochDay: Long, refreshes: Int = 0): String {
    val lines = linesFor(pace)
    return lines[Math.floorMod(epochDay + refreshes, lines.size.toLong()).toInt()]
}
