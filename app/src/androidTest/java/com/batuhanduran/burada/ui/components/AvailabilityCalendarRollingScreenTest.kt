package com.batuhanduran.burada.ui.components

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.ui.theme.BuradaTheme
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AvailabilityCalendarRollingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun renderedCalendarUsesInjectedCurrentLocalDayNotHardcodedOctoberNinth() {
        val localToday = GregorianCalendar().apply {
            clear()
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0)
        }.time
        var selected: String? = null
        compose.setContent {
            BuradaTheme {
                AvailabilityCalendarView(
                    bookedDatesJson = "[]",
                    isOpenForOffers = true,
                    isEditable = true,
                    today = localToday,
                    onDateToggle = { selected = it }
                )
            }
        }
        compose.onNodeWithTag("calendar_day_2026-10-09").assertDoesNotExist()
        compose.onNodeWithTag("calendar_day_2026-10-10").assertExists().performClick()
        compose.onNodeWithTag("calendar_day_2026-10-19").assertExists()
        compose.runOnIdle { assertEquals("2026-10-10", selected) }
    }
}
