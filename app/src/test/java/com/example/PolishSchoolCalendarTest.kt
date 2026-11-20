package com.example

import com.example.data.model.DayOffCategory
import com.example.data.model.PolishSchoolCalendar
import com.example.data.model.PrincipalDayOff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PolishSchoolCalendarTest {

    private val warsawTz = TimeZone.getTimeZone("Europe/Warsaw")

    @Test
    fun `computeEasterSunday matches official Gregorian dates for 2025 2026 2027`() {
        val e2025 = PolishSchoolCalendar.computeEasterSunday(2025, warsawTz)
        assertEquals(2025, e2025.get(Calendar.YEAR))
        assertEquals(Calendar.APRIL, e2025.get(Calendar.MONTH))
        assertEquals(20, e2025.get(Calendar.DAY_OF_MONTH))

        val e2026 = PolishSchoolCalendar.computeEasterSunday(2026, warsawTz)
        assertEquals(2026, e2026.get(Calendar.YEAR))
        assertEquals(Calendar.APRIL, e2026.get(Calendar.MONTH))
        assertEquals(5, e2026.get(Calendar.DAY_OF_MONTH))

        val e2027 = PolishSchoolCalendar.computeEasterSunday(2027, warsawTz)
        assertEquals(2027, e2027.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, e2027.get(Calendar.MONTH))
        assertEquals(28, e2027.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `statutory Polish holidays including movable feasts and Wigilia from 2025 are recognized`() {
        // Easter Monday 2026: 6 April 2026
        val easterMon = PolishSchoolCalendar.getDayInfo(2026, Calendar.APRIL, 6, timeZone = warsawTz)
        assertEquals(DayOffCategory.PUBLIC_HOLIDAY, easterMon.category)
        assertEquals("Poniedziałek Wielkanocny", easterMon.title)

        // Boże Ciało 2026: Easter April 5 + 60 days = June 4, 2026
        val corpusChristi = PolishSchoolCalendar.getDayInfo(2026, Calendar.JUNE, 4, timeZone = warsawTz)
        assertEquals(DayOffCategory.PUBLIC_HOLIDAY, corpusChristi.category)
        assertEquals("Boże Ciało", corpusChristi.title)

        // Narodowe Święto Niepodległości: Nov 11
        val nov11 = PolishSchoolCalendar.getDayInfo(2026, Calendar.NOVEMBER, 11, timeZone = warsawTz)
        assertEquals(DayOffCategory.PUBLIC_HOLIDAY, nov11.category)
        assertEquals("Narodowe Święto Niepodległości", nov11.title)

        // Wigilia Dec 24 is statutory holiday since 2025 (break before 2025)
        val wigilia2024 = PolishSchoolCalendar.getDayInfo(2024, Calendar.DECEMBER, 24, timeZone = warsawTz)
        assertEquals(DayOffCategory.SCHOOL_BREAK, wigilia2024.category)

        val wigilia2026 = PolishSchoolCalendar.getDayInfo(2026, Calendar.DECEMBER, 24, timeZone = warsawTz)
        assertEquals(DayOffCategory.PUBLIC_HOLIDAY, wigilia2026.category)
        assertEquals("Wigilia Bożego Narodzenia", wigilia2026.title)
    }

    @Test
    fun `computeSummerBreakStart follows Polish MEN rule first Friday after June 20`() {
        val start2025 = PolishSchoolCalendar.computeSummerBreakStart(2025, warsawTz)
        assertEquals(Calendar.JUNE, start2025.get(Calendar.MONTH))
        assertEquals(28, start2025.get(Calendar.DAY_OF_MONTH))

        val start2026 = PolishSchoolCalendar.computeSummerBreakStart(2026, warsawTz)
        assertEquals(Calendar.JUNE, start2026.get(Calendar.MONTH))
        assertEquals(27, start2026.get(Calendar.DAY_OF_MONTH))

        val start2027 = PolishSchoolCalendar.computeSummerBreakStart(2027, warsawTz)
        assertEquals(Calendar.JUNE, start2027.get(Calendar.MONTH))
        assertEquals(26, start2027.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `principal days off expand cleanly and update HomeHolidayCountdowns`() {
        val custom = PolishSchoolCalendar.expandPrincipalDateRange(
            startIso = "2026-05-04",
            dayCount = 3,
            title = "Matura pisemna",
            timeZone = warsawTz
        )
        assertEquals(listOf("2026-05-04", "2026-05-05", "2026-05-06"), custom.map { it.dateIso })

        // Wednesday April 29, 2026
        val nowCal = Calendar.getInstance(warsawTz).apply {
            set(2026, Calendar.APRIL, 29, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val countdowns = PolishSchoolCalendar.evaluateHomeCountdowns(
            customDaysOff = custom,
            nowMillis = nowCal.timeInMillis,
            timeZone = warsawTz
        )

        assertFalse(countdowns.isTodayFree)
        // Next free day from April 29 is May 1 (Święto Pracy) -> 2 days
        assertEquals(2, countdowns.nextFreeDayDays)
        assertEquals("Święto Pracy", countdowns.nextFreeDayTitle)
        assertFalse(countdowns.isCurrentlySummerBreak)
        // April 29 -> June 27 = 59 days
        assertEquals(59, countdowns.daysToVacation)

        // Check on May 4 itself -> today is PrincipalDayOff
        val maturaCal = Calendar.getInstance(warsawTz).apply {
            set(2026, Calendar.MAY, 4, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val maturaDay = PolishSchoolCalendar.getDayInfo(maturaCal, custom, warsawTz)
        assertEquals(DayOffCategory.PRINCIPAL_DAY_OFF, maturaDay.category)
        assertEquals("Matura pisemna", maturaDay.title)
        assertTrue(maturaDay.isFreeFromSchool)

        val maturaCountdowns = PolishSchoolCalendar.evaluateHomeCountdowns(
            customDaysOff = listOf(PrincipalDayOff("2026-05-04", "Matura pisemna")),
            nowMillis = maturaCal.timeInMillis,
            timeZone = warsawTz
        )
        assertTrue(maturaCountdowns.isTodayFree)
        assertEquals(0, maturaCountdowns.nextFreeDayDays)
        assertEquals("Matura pisemna", maturaCountdowns.todayFreeTitle)
    }

    @Test
    fun `evaluateHomeCountdowns ignores regular weekends and Sunday-only holidays`() {
        // Friday Nov 20, 2026 -> next day off must NOT be Saturday Nov 21; next real school day off is Dec 23 (33 days)
        val friNow = Calendar.getInstance(warsawTz).apply {
            set(2026, Calendar.NOVEMBER, 20, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val friCountdowns = PolishSchoolCalendar.evaluateHomeCountdowns(
            customDaysOff = emptyList(),
            nowMillis = friNow.timeInMillis,
            timeZone = warsawTz
        )
        assertFalse(friCountdowns.isTodayFree)
        assertEquals(33, friCountdowns.nextFreeDayDays)
        assertEquals("Zimowa przerwa świąteczna", friCountdowns.nextFreeDayTitle)

        // Saturday Nov 21, 2026 -> not counted as a day off; 32 days to Dec 23
        val satNow = Calendar.getInstance(warsawTz).apply {
            set(2026, Calendar.NOVEMBER, 21, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val satCountdowns = PolishSchoolCalendar.evaluateHomeCountdowns(
            customDaysOff = emptyList(),
            nowMillis = satNow.timeInMillis,
            timeZone = warsawTz
        )
        assertFalse(satCountdowns.isTodayFree)
        assertEquals(32, satCountdowns.nextFreeDayDays)
        assertEquals("Zimowa przerwa świąteczna", satCountdowns.nextFreeDayTitle)

        // Friday May 22, 2026 -> skips Sunday Zielone Świątki (May 24) and counts to Boże Ciało (Thu June 4 = 13 days)
        val mayFri = Calendar.getInstance(warsawTz).apply {
            set(2026, Calendar.MAY, 22, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val mayCountdowns = PolishSchoolCalendar.evaluateHomeCountdowns(
            customDaysOff = emptyList(),
            nowMillis = mayFri.timeInMillis,
            timeZone = warsawTz
        )
        assertFalse(mayCountdowns.isTodayFree)
        assertEquals(13, mayCountdowns.nextFreeDayDays)
        assertEquals("Boże Ciało", mayCountdowns.nextFreeDayTitle)
    }
}
