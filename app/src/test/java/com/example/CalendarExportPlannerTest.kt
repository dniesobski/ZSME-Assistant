package com.example

import com.example.data.calendar.CalendarExportPlanner
import com.example.data.model.CalendarExportOptions
import com.example.data.model.CalendarRecurrenceMode
import com.example.data.model.CalendarStartWeekMode
import com.example.data.model.ClassTimetable
import com.example.data.model.DaySchedule
import com.example.data.model.LessonEntry
import com.example.data.model.TeacherSchoolConfig
import com.example.data.model.TimetableCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CalendarExportPlannerTest {

    private val warsawTz = TimeZone.getTimeZone("Europe/Warsaw")

    @Test
    fun `parseTimeRange handles various formats and period fallback`() {
        assertEquals((8 to 0) to (8 to 45), CalendarExportPlanner.parseTimeRange("8:00- 8:45", 1))
        assertEquals((9 to 50) to (10 to 35), CalendarExportPlanner.parseTimeRange("09.50 – 10.35", 3))
        assertEquals((11 to 35) to (12 to 20), CalendarExportPlanner.parseTimeRange("", 5))
    }

    @Test
    fun `computeWeekMonday and RRULE calculate correct Warsaw dates`() {
        // Wednesday 2026-11-18 12:00 Europe/Warsaw
        val refCal = Calendar.getInstance(warsawTz).apply {
            set(2026, Calendar.NOVEMBER, 18, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentMonday = CalendarExportPlanner.computeWeekMonday(
            refCal.timeInMillis,
            CalendarStartWeekMode.CURRENT_WEEK,
            warsawTz
        )
        assertEquals(2026, currentMonday.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, currentMonday.get(Calendar.MONTH))
        assertEquals(16, currentMonday.get(Calendar.DAY_OF_MONTH))

        val nextMonday = CalendarExportPlanner.computeWeekMonday(
            refCal.timeInMillis,
            CalendarStartWeekMode.NEXT_MONDAY,
            warsawTz
        )
        assertEquals(23, nextMonday.get(Calendar.DAY_OF_MONTH))

        val schoolEndRule = CalendarExportPlanner.computeRRule(
            currentMonday.timeInMillis,
            CalendarRecurrenceMode.UNTIL_END_OF_SCHOOL_YEAR,
            warsawTz
        )
        assertNotNull(schoolEndRule)
        assertTrue(schoolEndRule!!.startsWith("RRULE:FREQ=WEEKLY;UNTIL=20270630T"))

        val singleRule = CalendarExportPlanner.computeRRule(
            currentMonday.timeInMillis,
            CalendarRecurrenceMode.SINGLE_WEEK,
            warsawTz
        )
        assertNull(singleRule)
    }

    @Test
    fun `buildEventSpecs generates formatted Warsaw events with school and class metadata`() {
        val refCal = Calendar.getInstance(warsawTz).apply {
            set(2026, Calendar.NOVEMBER, 16, 7, 0, 0) // Monday 2026-11-16
            set(Calendar.MILLISECOND, 0)
        }
        val timetable = ClassTimetable(
            className = "JK / KOW",
            classUrl = "https://plan.zsme.pl/",
            category = TimetableCategory.TEACHER,
            days = listOf(
                DaySchedule(
                    dayIndex = 0,
                    dayName = "Poniedziałek",
                    lessons = listOf(
                        LessonEntry(
                            period = 1,
                            timeRange = "8:00-8:45",
                            subject = "Fizyka",
                            teacher = "JK",
                            room = "204",
                            classGroupLabel = "2TI",
                            schoolId = "primary_zsme",
                            schoolName = "ZSME",
                            schoolColorIndex = 0
                        )
                    )
                )
            )
        )
        val filteredMap = mapOf(0 to timetable.days[0].lessons)
        val specs = CalendarExportPlanner.buildEventSpecs(
            timetable = timetable,
            filteredDayLessons = filteredMap,
            options = CalendarExportOptions(
                recurrenceMode = CalendarRecurrenceMode.UNTIL_END_OF_SCHOOL_YEAR,
                startWeekMode = CalendarStartWeekMode.CURRENT_WEEK,
                reminderMinutes = 10,
                replacePreviousExports = true
            ),
            isTeacherMode = true,
            hasMultipleSchools = true,
            referenceMillis = refCal.timeInMillis,
            timeZone = warsawTz
        )

        assertEquals(1, specs.size)
        val ev = specs.first()
        assertEquals("Fizyka (kl. 2TI) • ZSME", ev.title)
        assertEquals("ZSME • Sala 204", ev.location)
        assertEquals("2026-11-16T08:00:00+01:00", ev.startIso)
        assertEquals("2026-11-16T08:45:00+01:00", ev.endIso)
        assertTrue(ev.description.contains(CalendarExportPlanner.EXPORT_MARKER))
        assertEquals(10, ev.reminderMinutes)
        assertTrue(ev.rrule!!.contains("FREQ=WEEKLY"))

        val suggested = CalendarExportPlanner.suggestedCalendarName(
            timetable = timetable,
            isTeacherMode = true,
            teacherSchools = listOf(
                TeacherSchoolConfig("1", "ZSME", "JK", "https://plan.zsme.pl/", 0),
                TeacherSchoolConfig("2", "ZS5", "KOW", "http://zs5rybnik.pl/_plan/", 1)
            )
        )
        assertEquals("Plan zajęć — ZSME, ZS5", suggested)
    }
}
