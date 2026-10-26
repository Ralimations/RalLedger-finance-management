package com.ralledger.ui.components

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialDateTimeTest {
    private val manila = TimeZone.getTimeZone("Asia/Manila")
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun datePickerMillisRepresentsTheLocalCalendarDate() {
        val localTimestamp = timestamp(manila, 2026, Calendar.JANUARY, 1, 0, 15, 0, 0)

        val pickerDate = Calendar.getInstance(utc).apply {
            timeInMillis = localTimestampToPickerDateMillis(localTimestamp, manila)
        }

        assertEquals(2026, pickerDate.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, pickerDate.get(Calendar.MONTH))
        assertEquals(1, pickerDate.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun choosingDatePreservesTheLocalTimeAndSubsecondValue() {
        val original = timestamp(manila, 2026, Calendar.JANUARY, 3, 23, 48, 17, 321)
        val chosenDate = timestamp(utc, 2026, Calendar.FEBRUARY, 7, 12, 0, 0, 0)

        val result = Calendar.getInstance(manila).apply {
            timeInMillis = pickerDateToLocalTimestamp(chosenDate, original, manila)
        }

        assertEquals(2026, result.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, result.get(Calendar.MONTH))
        assertEquals(7, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(48, result.get(Calendar.MINUTE))
        assertEquals(17, result.get(Calendar.SECOND))
        assertEquals(321, result.get(Calendar.MILLISECOND))
    }

    @Test
    fun choosingTimePreservesTheLocalDateAndClearsSeconds() {
        val original = timestamp(manila, 2026, Calendar.MARCH, 14, 9, 20, 35, 712)

        val result = Calendar.getInstance(manila).apply {
            timeInMillis = localTimestampWithTime(original, 18, 5, manila)
        }

        assertEquals(2026, result.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, result.get(Calendar.MONTH))
        assertEquals(14, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, result.get(Calendar.MINUTE))
        assertEquals(0, result.get(Calendar.SECOND))
        assertEquals(0, result.get(Calendar.MILLISECOND))
    }

    private fun timestamp(
        zone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millisecond: Int
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month, day, hour, minute, second)
        set(Calendar.MILLISECOND, millisecond)
    }.timeInMillis
}
