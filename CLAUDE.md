# Budget app

Android app (Kotlin, Jetpack Compose) that warns about the budget before a UPI
payment. Data stays on the phone in one JSON file.

## Money math must be exact

This is a budget app: one wrong figure and there is no reason to use it. Treat
every number the user sees as something that must be provably right.

**Any change that touches a number, do all of this before calling it done:**

1. Follow the rules below.
2. Add or update unit tests for the new math, including the edge cases.
3. Re-check every screen that shows the same figure, not only the one you
   changed. The same number appears on Home, Budget, Pay and in the confirm
   dialogs, and they must agree to the paisa.
4. Run `gradlew :app:testDebugUnitTest` and make sure it passes.

### Rules

- **Whole numbers only.** Payments are exact paise (`Long`). Budgets are whole
  rupees (`Int`). Never hold an amount in `Float` or `Double`, and never parse
  or format one through them. Use `BigDecimal` only at the edge, to read typed
  text.
- **Never round before adding.** Sum paise, then show the sum. Rounding each
  payment or each category first makes the parts stop adding up to the total.
- **Parts must add up.** Category spending sums to total spending; spent plus
  left equals the budget; category budgets rescaled to a new monthly budget sum
  to exactly what they should. Write a test that asserts the sum.
- **Show amounts exactly.** Use `formatPaise` for anything spent or paid, so
  ₹49.50 is never shown as ₹50. `formatRupees` is only for whole-rupee budgets.
- **Round only where a whole number is unavoidable, and in the safe direction.**
  Percent spent rounds down (never says 100% early). The per-day allowance
  rounds down (following it never overshoots).
- **Thresholds are compared in integers.** "85% of budget" is
  `spent * 100 >= budget * 85`, not a float compared to `0.85`. Floats
  (`fraction`, `spentFraction`) are for drawing bars and rings only.
- **Put math in the pure files**, not in composables: `data/Money.kt`,
  `data/BudgetMath.kt`, and `buildHomeState` in `ui/home/HomeViewModel.kt`.
  They have no Android dependencies so they can be unit tested. Screens only
  format and display.
- **Dates are part of the math.** "This month" and "days left" come from the
  current time passed into `buildHomeState`; never cache a month boundary.
  Days left includes today.
- **Wording counts.** A correct figure with an unclear label reads as a bug.
  Say what the number is: spent, left, over, or still allowed.

### Where the math is tested

`app/src/test/java/com/budgetapp/`: `data/MoneyTest.kt`,
`data/BudgetMathTest.kt`, `data/JsonStoreTest.kt`, `ui/home/HomeStateTest.kt`.

## Build

Gradle needs a JDK; Android Studio's works:
`JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`.
