package com.budgetapp.data

import com.budgetapp.ui.home.buildHomeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DemoDataTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `demo month lands in the current month and its parts add up`() {
        for (now in listOf(
            ZonedDateTime.of(2026, 11, 22, 21, 0, 0, 0, zone),
            ZonedDateTime.of(2026, 12, 1, 7, 30, 0, 0, zone), // first morning of a month
        )) {
            val demo = demoData(now)
            val home = buildHomeState(demo.categories, demo.payments, demo.overallBudget, now)
            assertEquals(demo.payments.sumOf { it.amountPaise }, home.spentPaise)
            assertEquals(home.spentPaise, home.allCategories.sumOf { it.spentPaise })
            assertEquals(demo.overallBudget, demo.categories.sumOf { it.monthlyBudget })
            assertTrue(demo.payments.all { it.createdAt <= now.toInstant().toEpochMilli() })
            assertEquals(demo.payments.size, demo.payments.map { it.id }.distinct().size)
        }
    }
}
