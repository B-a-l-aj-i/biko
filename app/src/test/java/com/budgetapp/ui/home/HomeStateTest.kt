package com.budgetapp.ui.home

import com.budgetapp.data.formatPaise
import com.budgetapp.data.BudgetLevel
import com.budgetapp.data.Category
import com.budgetapp.data.Payment
import com.budgetapp.data.PaymentStatus
import com.budgetapp.data.SpendingPace
import com.budgetapp.data.MANUAL_PAYEE
import com.budgetapp.data.manualPayment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.YearMonth
import java.time.ZonedDateTime

class HomeStateTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 11, 15, 14, 0, 0, 0, zone)

    private val categories = listOf(
        Category("Food", "🍔", 1_000),
        Category("Fuel", "⛽", 2_000),
        Category("Gifts", "🎁", 0),
    )

    private fun payment(
        paise: Long,
        category: String,
        status: PaymentStatus = PaymentStatus.PAID,
        at: ZonedDateTime = now.minusHours(1),
    ) = Payment(0, "Shop", null, paise, category, null, null, status, at.toInstant().toEpochMilli())

    @Test
    fun `nothing spent`() {
        val state = buildHomeState(categories, emptyList(), 6_000, now)

        assertEquals(0L, state.spentPaise)
        assertEquals(0, state.spentPercent)
        assertEquals(6_000_00L, state.leftPaise)
        // 15 Nov counts: 15th to 30th is 16 days.
        assertEquals(16, state.daysLeft)
        assertEquals(SpendingPace.SAVING, state.pace)
        assertEquals(paceMessage(SpendingPace.SAVING, now.toLocalDate().toEpochDay()), state.paceMessage)
        assertEquals(3_000, state.allocated)
    }

    @Test
    fun `categories add up to the total, to the paisa`() {
        val payments = listOf(
            payment(49_50, "Food"),
            payment(49_50, "Food"),
            payment(100_49, "Fuel"),
            payment(1, "Gifts"),
        )
        val state = buildHomeState(categories, payments, 6_000, now)

        assertEquals(199_50L, state.spentPaise)
        assertEquals(state.spentPaise, state.allCategories.sumOf { it.spentPaise })
        assertEquals(99_00L, state.allCategories.single { it.name == "Food" }.spentPaise)
        assertEquals(state.spentPaise + state.leftPaise, 6_000_00L)
    }

    @Test
    fun `only confirmed payments count as spent`() {
        val payments = listOf(
            payment(500_00, "Food"),
            payment(300_00, "Food", PaymentStatus.PENDING),
            payment(200_00, "Food", PaymentStatus.CANCELLED),
        )
        val state = buildHomeState(categories, payments, 6_000, now)

        assertEquals(500_00L, state.spentPaise)
        assertEquals(listOf(300_00L), state.pending.map { it.amountPaise })
    }

    @Test
    fun `other months are left out, right up to midnight`() {
        val monthStart = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone)
        val payments = listOf(
            payment(100_00, "Food", at = monthStart.minusNanos(1_000_000)),
            payment(20_00, "Food", at = monthStart),
            payment(3_00, "Food", at = monthStart.plusMonths(1).minusNanos(1_000_000)),
            payment(4_000_00, "Food", at = monthStart.plusMonths(1)),
        )
        val state = buildHomeState(categories, payments, 6_000, now)

        assertEquals(23_00L, state.spentPaise)
    }

    @Test
    fun `the list holds every confirmed payment this month, newest first, and adds up to spent`() {
        val monthStart = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone)
        val payments = listOf(
            payment(999_00, "Food", at = monthStart.minusNanos(1_000_000)),
            payment(1_01, "Food", at = monthStart),
            payment(10_50, "Food", at = now.minusDays(1)),
            payment(20_25, "Fuel", at = now.minusHours(2)),
            payment(7_00, "Fuel", PaymentStatus.PENDING, at = now.minusHours(1)),
            payment(8_00, "Fuel", PaymentStatus.CANCELLED, at = now.minusHours(1)),
        )
        val state = buildHomeState(categories, payments, 6_000, now)

        assertEquals(listOf(20_25L, 10_50L, 1_01L), state.recent.map { it.amountPaise })
        assertEquals(listOf("Today", "Yesterday", "1 Nov"), state.recent.map { it.day })
        assertEquals(31_76L, state.spentPaise)
        assertEquals(state.spentPaise, state.recent.sumOf { it.amountPaise })
    }

    @Test
    fun `a payment's note reaches the list`() {
        val payments = listOf(payment(49_50, "Food").copy(id = 1, note = "Dosa"), payment(10_00, "Food").copy(id = 2))
        val state = buildHomeState(categories, payments, 6_000, now)
        assertEquals("Dosa", state.recent.single { it.id == 1L }.note)
        assertEquals(null, state.recent.single { it.id == 2L }.note)
    }

    @Test
    fun `day labels`() {
        val today = now.toLocalDate()
        assertEquals("Today", dayLabel(today, today))
        assertEquals("Yesterday", dayLabel(today.minusDays(1), today))
        assertEquals("13 Nov", dayLabel(today.minusDays(2), today))
        // 1 Dec: yesterday crosses back into November.
        val firstDec = today.withMonth(12).withDayOfMonth(1)
        assertEquals("Yesterday", dayLabel(firstDec.minusDays(1), firstDec))
    }

    @Test
    fun `over budget by one paisa is over`() {
        val state = buildHomeState(categories, listOf(payment(1_000_01, "Food")), 1_000, now)

        assertEquals(-1L, state.leftPaise)
        assertEquals(BudgetLevel.USED_UP, state.level)
        assertEquals(100, state.spentPercent)
        assertEquals(SpendingPace.OVER, state.pace)
        assertEquals(-1L, state.allCategories.single { it.name == "Food" }.leftPaise)
    }

    @Test
    fun `without a monthly budget the category total stands in`() {
        assertEquals(3_000, buildHomeState(categories, emptyList(), 0, now).monthlyBudget)
    }

    @Test
    fun `last day of the month leaves one day`() {
        val lastDay = ZonedDateTime.of(2026, 2, 28, 23, 59, 0, 0, zone)
        val state = buildHomeState(categories, emptyList(), 6_000, lastDay)

        assertEquals(1, state.daysLeft)
    }

    @Test
    fun `pace message is one of its pace's lines, the same all day, and blank without a budget`() {
        val day = now.toLocalDate().toEpochDay()
        for (pace in SpendingPace.values()) {
            val count = paceMessageCount(pace)
            val lines = (0L until count.toLong()).map { paceMessage(pace, day + it) }
            assertEquals(count, lines.toSet().size)
            assertEquals(lines[0], paceMessage(pace, day + count))
            assertEquals(paceMessage(pace, day), paceMessage(pace, day))
        }
        assertEquals("", buildHomeState(emptyList(), emptyList(), 0, now).paceMessage)
    }

    @Test
    fun `month label is the current month in three letters`() {
        assertEquals("Nov", buildHomeState(categories, emptyList(), 6_000, now).monthLabel)
        val names = (1..12).map {
            buildHomeState(categories, emptyList(), 6_000, now.withDayOfMonth(1).withMonth(it)).monthLabel
        }
        assertEquals(
            listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
            names,
        )
    }

    @Test
    fun `each pull to refresh shows another line for the same pace, and no figure moves`() {
        val day = now.toLocalDate().toEpochDay()
        for (pace in SpendingPace.values()) {
            val count = paceMessageCount(pace)
            val lines = (0 until count).map { paceMessage(pace, day, it) }
            // As many pulls as the pace has lines show every one of them, then it starts over.
            assertEquals((0L until count.toLong()).map { paceMessage(pace, day + it) }.toSet(), lines.toSet())
            assertEquals(count, lines.toSet().size)
            assertEquals(lines[0], paceMessage(pace, day, count))
        }

        val payments = listOf(payment(49_50, "Food"), payment(100_49, "Fuel"))
        val before = buildHomeState(categories, payments, 6_000, now)
        val after = buildHomeState(categories, payments, 6_000, now, refreshes = 1)
        assertEquals(paceMessage(before.pace, day, 1), after.paceMessage)
        assertEquals(false, before.paceMessage == after.paceMessage)
        assertEquals(before, after.copy(paceMessage = before.paceMessage))
        assertEquals("", buildHomeState(emptyList(), emptyList(), 0, now, refreshes = 3).paceMessage)
    }

    @Test
    fun `a payment added by hand counts as spent at once, to the paisa`() {
        val added = manualPayment(49_50, "Food", null, now.minusMinutes(5).toInstant().toEpochMilli())
        assertEquals(PaymentStatus.PAID, added.status)

        val state = buildHomeState(categories, listOf(payment(100_49, "Food"), added), 6_000, now)

        assertEquals(149_99L, state.spentPaise)
        assertEquals(149_99L, state.allCategories.single { it.name == "Food" }.spentPaise)
        assertEquals(state.spentPaise, state.allCategories.sumOf { it.spentPaise })
        assertEquals(state.spentPaise + state.leftPaise, 6_000_00L)
        assertEquals(0, state.pending.size)
        val listed = state.recent.single { it.manual }
        assertEquals(49_50L, listed.amountPaise)
    }

    @Test
    fun `a hand-added payment is listed by its note, else its category, and tagged manual`() {
        val at = now.minusMinutes(5).toInstant().toEpochMilli()
        val withNote = manualPayment(49_50, "Food", null, at).copy(id = 1, note = "Lunch with team")
        val noNote = manualPayment(20_00, "Travel", null, at).copy(id = 2)
        val fromAlert = payment(100_49, "Food").copy(id = 3, note = "chai")

        val rows = buildHomeState(categories, listOf(withNote, noNote, fromAlert), 6_000, now).recent.associateBy { it.id }

        assertEquals("Lunch with team", rows.getValue(1).payee)
        assertEquals(null, rows.getValue(1).note) // already the title, not repeated
        assertEquals(true, rows.getValue(1).manual)
        assertEquals("Travel", rows.getValue(2).payee)
        assertEquals(true, rows.getValue(2).manual)
        // A payee from the bank keeps its name, its note on the line under it, and no tag.
        assertEquals(false, rows.getValue(3).manual)
        assertEquals("chai", rows.getValue(3).note)
        assertEquals(false, rows.values.any { it.payee == MANUAL_PAYEE })
    }

    @Test
    fun `editing a payment moves its amount, and the parts still add up`() {
        val before = listOf(
            payment(100_49, "Food").copy(id = 1),
            payment(49_50, "Food").copy(id = 2),
        )
        // Payment 2 edited from 49.50 Food to 75.25 Fuel, as Repository.updatePayment does it.
        val after = before.map { if (it.id == 2L) it.copy(amountPaise = 75_25, category = "Fuel") else it }
        val state = buildHomeState(categories, after, 6_000, now)

        assertEquals(175_74L, state.spentPaise)
        assertEquals(100_49L, state.allCategories.single { it.name == "Food" }.spentPaise)
        assertEquals(75_25L, state.allCategories.single { it.name == "Fuel" }.spentPaise)
        assertEquals(state.spentPaise, state.allCategories.sumOf { it.spentPaise })
        assertEquals(state.spentPaise + state.leftPaise, 6_000_00L)
        // Today's list carries each payment's id, so a tap opens the right one.
        assertEquals(setOf(1L, 2L), state.recent.map { it.id }.toSet())
        assertEquals(75_25L, state.recent.single { it.id == 2L }.amountPaise)
    }

    private fun summary(budget: Int, spentPaise: Long) =
        sampleHomeState().copy(monthlyBudget = budget, spentPaise = spentPaise)

    @Test
    fun `half donut labels add up to 100 percent`() {
        // 201 of 5,000 is 4.02%: 4% spent, 96% left.
        val state = summary(5_000, 201_00)
        assertEquals(4, state.spentPercent)
        assertEquals(96, state.leftPercent)
        assertEquals(100, state.spentPercent + state.leftPercent)
        // The centre sum: budget minus spent equals left, to the paisa.
        assertEquals(4_799_00L, state.leftPaise)
        assertEquals(5_000_00L - state.spentPaise, state.leftPaise)
    }

    @Test
    fun `left percent never reads 0 while money is left`() {
        // 4,999.99 of 5,000: spent rounds down to 99%, so 1% left, not 0%.
        val state = summary(5_000, 4_999_99)
        assertEquals(99, state.spentPercent)
        assertEquals(1, state.leftPercent)
        assertEquals(1L, state.leftPaise)
    }

    @Test
    fun `left percent at and over the budget`() {
        val exact = summary(5_000, 5_000_00)
        assertEquals(100, exact.spentPercent)
        assertEquals(0, exact.leftPercent)
        assertEquals(0L, exact.leftPaise)

        val over = summary(5_000, 5_049_50)
        assertEquals(0, over.leftPercent)
        // Over by 49.50: the sum's result line shows it negative, exact, not rounded.
        assertEquals(-49_50L, over.leftPaise)
        assertEquals("−₹49.50", formatPaise(over.leftPaise))
    }

    @Test
    fun `spending well follows the pace and the budget`() {
        assertEquals(true, summary(5_000, 201_00).copy(pace = SpendingPace.SAVING).spendingWell)
        assertEquals(true, summary(5_000, 201_00).copy(pace = SpendingPace.ON_TRACK).spendingWell)
        assertEquals(false, summary(5_000, 201_00).copy(pace = SpendingPace.OVER).spendingWell)
        // Near the budget but on pace, late in the month, is still fine.
        assertEquals(true, summary(5_000, 4_500_00).copy(pace = SpendingPace.ON_TRACK).spendingWell)
        // Used up is never fine, whatever the pace says.
        assertEquals(false, summary(5_000, 5_000_00).copy(pace = SpendingPace.SAVING).spendingWell)
        assertEquals(null, summary(0, 120_00).spendingWell)
    }

    @Test
    fun `no budget means no percent labels`() {
        val state = summary(0, 120_00)
        assertEquals(0, state.spentPercent)
        assertEquals(0, state.leftPercent)
    }

    @Test
    fun `a deleted payment stops counting everywhere, to the paisa`() {
        val all = listOf(
            payment(100_49, "Food").copy(id = 1),
            payment(49_50, "Food").copy(id = 2),
            payment(75_25, "Fuel").copy(id = 3),
        )
        // As Repository.deletePayment leaves the list.
        val state = buildHomeState(categories, all.filter { it.id != 2L }, 6_000, now)

        assertEquals(175_74L, state.spentPaise)
        assertEquals(100_49L, state.allCategories.single { it.name == "Food" }.spentPaise)
        assertEquals(state.spentPaise, state.allCategories.sumOf { it.spentPaise })
        assertEquals(state.spentPaise + state.leftPaise, 6_000_00L)
        assertEquals(setOf(1L, 3L), state.recent.map { it.id }.toSet())
    }

    @Test
    fun `every pace line fits on one line of the home card`() {
        for (pace in SpendingPace.entries) {
            val count = paceMessageCount(pace)
            // At least 25 per pace, all different, so the line doesn't repeat for most of a month.
            assertEquals(true, count >= 25)
            val lines = (0L until count.toLong()).map { paceMessage(pace, it) }
            assertEquals(count, lines.toSet().size)
            lines.forEach { line -> assertEquals(line, true, line.length <= PACE_MESSAGE_MAX) }
        }
    }

    @Test
    fun `a picked past month shows only its own payments, and the parts add up`() {
        val october = YearMonth.of(2026, 10)
        val payments = listOf(
            // 30 Sep 23:59:59.999 and 1 Nov 00:00: just outside October.
            payment(1_00, "Food", at = ZonedDateTime.of(2026, 9, 30, 23, 59, 59, 999_000_000, zone)),
            payment(49_50, "Food", at = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, zone)),
            payment(100_49, "Fuel", at = ZonedDateTime.of(2026, 10, 31, 23, 59, 59, 999_000_000, zone)),
            payment(5_00, "Food", at = ZonedDateTime.of(2026, 10, 15, 9, 0, 0, 0, zone), status = PaymentStatus.CANCELLED),
            payment(7_00, "Food", at = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone)),
        )
        val state = buildHomeState(categories, payments, 6_000, now, month = october)

        assertEquals(october, state.shownMonth)
        assertEquals("Oct", state.monthLabel)
        assertEquals(149_99L, state.spentPaise)
        assertEquals(state.spentPaise, state.allCategories.sumOf { it.spentPaise })
        assertEquals(state.spentPaise + state.leftPaise, 6_000_00L)
        assertEquals(2, state.recent.size)
        // A finished month has no days left, and its line says how it ended.
        assertEquals(0, state.daysLeft)
        assertEquals("October ended within budget", state.paceMessage)
        // The menu still offers the same months, whatever month is shown.
        assertEquals(buildHomeState(categories, payments, 6_000, now).months, state.months)
    }

    @Test
    fun `a past month at exactly its budget ended within it, one paisa more is over`() {
        val october = YearMonth.of(2026, 10)
        assertEquals("October ended within budget", pastMonthLine(october, 6_000_00, 6_000))
        assertEquals("October ended over budget", pastMonthLine(october, 6_000_01, 6_000))
        // A finished month is paced on the whole month: spent equal to the budget is on track.
        val atBudget = listOf(payment(6_000_00, "Food", at = ZonedDateTime.of(2026, 10, 10, 9, 0, 0, 0, zone)))
        assertEquals(SpendingPace.ON_TRACK, buildHomeState(categories, atBudget, 6_000, now, month = october).pace)
        // Every line fits the header.
        assertEquals(true, pastMonthLine(YearMonth.of(2026, 9), 9_000_00, 6_000).length <= PACE_MESSAGE_MAX)
    }

    @Test
    fun `no month, or the current one, gives the current month's figures`() {
        val payments = listOf(payment(49_50, "Food"))
        val plain = buildHomeState(categories, payments, 6_000, now)
        assertEquals(plain, buildHomeState(categories, payments, 6_000, now, month = YearMonth.from(now)))
        assertEquals(YearMonth.of(2026, 11), plain.shownMonth)
        assertEquals(16, plain.daysLeft)
    }

    @Test
    fun `a past month with nothing paid is an empty month, with a neutral page`() {
        val july = YearMonth.of(2026, 7)
        // Only a cancelled payment in July: still nothing spent.
        val payments = listOf(
            payment(5_00, "Food", at = ZonedDateTime.of(2026, 7, 4, 9, 0, 0, 0, zone), status = PaymentStatus.CANCELLED),
        )
        val state = buildHomeState(categories, payments, 6_000, now, month = july)

        assertEquals(true, state.isEmptyPastMonth)
        assertEquals(0L, state.spentPaise)
        assertEquals(0, state.recent.size)
        assertEquals(null, state.spendingWell)
        assertEquals("Nothing spent in July", state.paceMessage)
        // Even with no monthly budget set, the line still says so.
        assertEquals("Nothing spent in July", buildHomeState(emptyList(), payments, 0, now, month = july).paceMessage)
        // One paisa spent is not empty.
        val onePaisa = listOf(payment(1, "Food", at = ZonedDateTime.of(2026, 7, 4, 9, 0, 0, 0, zone)))
        assertEquals(false, buildHomeState(categories, onePaisa, 6_000, now, month = july).isEmptyPastMonth)
        // The current month is never an empty month, even with nothing spent.
        assertEquals(false, buildHomeState(categories, emptyList(), 6_000, now).isEmptyPastMonth)
    }

    @Test
    fun `another month's pending and to-review payments never show in this month`() {
        val october = ZonedDateTime.of(2026, 10, 31, 23, 50, 0, 0, zone)
        val payments = listOf(
            payment(250_00, "Food", PaymentStatus.PENDING, at = october).copy(id = 1, payeeName = "Zomato"),
            payment(400_00, "Food", PaymentStatus.DETECTED, at = october).copy(id = 2),
            payment(180_00, "Fuel", PaymentStatus.PENDING).copy(id = 3, payeeName = "Uber"),
        )
        val november = buildHomeState(categories, payments, 6_000, now)
        assertEquals(listOf(PendingPayment(3, "Uber", 180_00)), november.pending)
        assertEquals(0, november.toReview)

        // Picking October shows October's own, so a tap there acts on Zomato, not Uber.
        val oct = buildHomeState(categories, payments, 6_000, now, month = YearMonth.of(2026, 10))
        assertEquals(listOf(PendingPayment(1, "Zomato", 250_00)), oct.pending)
        assertEquals(1, oct.toReview)
    }

    @Test
    fun `search's month total equals home's spent for the same month`() {
        val payments = listOf(
            payment(49_50, "Food"),
            payment(1_00, "Fuel", at = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone)),
            payment(99_99, "Food", at = ZonedDateTime.of(2026, 10, 31, 23, 59, 59, 0, zone)),
            payment(7_00, "Food", PaymentStatus.PENDING),
        )
        for (month in listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10))) {
            val home = buildHomeState(categories, payments, 6_000, now, month = month)
            val (from, until) = com.budgetapp.data.monthBounds(month, zone)
            assertEquals(home.spentPaise, com.budgetapp.data.searchPayments(payments, "", from, until).sumOf { it.amountPaise })
        }
    }
}
