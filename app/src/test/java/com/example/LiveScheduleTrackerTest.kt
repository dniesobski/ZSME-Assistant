package com.example

import com.example.data.model.ClassTimetable
import com.example.data.model.DaySchedule
import com.example.data.model.LessonEntry
import com.example.data.model.LiveScheduleStatus
import com.example.data.model.LiveScheduleTracker
import com.example.data.model.StudentGroup
import com.example.data.model.SubstitutionItem
import com.example.data.model.SubstitutionsSnapshot
import com.example.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LiveScheduleTrackerTest {

    private val warsawTz = TimeZone.getTimeZone("Europe/Warsaw")

    private fun millisFor(year: Int, monthZeroBased: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        return Calendar.getInstance(warsawTz).apply {
            set(year, monthZeroBased, day, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun sampleTimetable(): ClassTimetable {
        val mondayLessons = listOf(
            LessonEntry(1, "8:00-8:45", "Fizyka", "JK", "204"),
            LessonEntry(2, "8:50-9:35", "Matematyka", "AB", "105"),
            LessonEntry(3, "9:50-10:35", "Informatyka", "MK", "301", groupTag = "1/2"),
            LessonEntry(3, "9:50-10:35", "J. angielski", "EN", "202", groupTag = "2/2")
        )
        val tuesdayLessons = listOf(
            LessonEntry(1, "8:00-8:45", "J. polski", "EW", "112")
        )
        return ClassTimetable(
            className = "2TI",
            classUrl = "https://plan.zsme.pl/plany/o1.html",
            days = listOf(
                DaySchedule(dayIndex = 0, dayName = "Poniedziałek", lessons = mondayLessons),
                DaySchedule(dayIndex = 1, dayName = "Wtorek", lessons = tuesdayLessons),
                DaySchedule(dayIndex = 2, dayName = "Środa", lessons = emptyList()),
                DaySchedule(dayIndex = 3, dayName = "Czwartek", lessons = emptyList()),
                DaySchedule(dayIndex = 4, dayName = "Piątek", lessons = emptyList())
            )
        )
    }

    @Test
    fun `during lesson evaluates remaining countdown and next slot`() {
        // Monday 2026-11-16 at 08:20:00 (lesson 1: 8:00-8:45)
        val now = millisFor(2026, Calendar.NOVEMBER, 16, 8, 20, 0)
        val eval = LiveScheduleTracker.evaluate(
            timetable = sampleTimetable(),
            substitutions = null,
            userRole = UserRole.STUDENT,
            selectedClassName = "2TI",
            selectedGroup = StudentGroup.GROUP_1,
            customGroupFilter = "",
            teacherAbbr = "",
            teacherFullName = "",
            nowMillis = now,
            timeZone = warsawTz
        )

        assertTrue(eval.status is LiveScheduleStatus.DuringLesson)
        val status = eval.status as LiveScheduleStatus.DuringLesson
        assertEquals(1, status.currentSlot.period)
        assertEquals("Fizyka", status.currentSlot.items.first().lesson.subject)
        assertEquals(25 * 60, status.remainingSeconds)
        assertEquals(45 * 60, status.totalSeconds)
        assertNotNull(status.nextSlot)
        assertEquals(2, status.nextSlot!!.period)
        assertEquals("Matematyka", status.nextSlot!!.items.first().lesson.subject)
    }

    @Test
    fun `during break evaluates remaining break countdown and next lesson`() {
        // Monday 2026-11-16 at 09:40:30 (break between lesson 2 ending 9:35 and lesson 3 starting 9:50)
        val now = millisFor(2026, Calendar.NOVEMBER, 16, 9, 40, 30)
        val eval = LiveScheduleTracker.evaluate(
            timetable = sampleTimetable(),
            substitutions = null,
            userRole = UserRole.STUDENT,
            selectedClassName = "2TI",
            selectedGroup = StudentGroup.GROUP_1,
            customGroupFilter = "",
            teacherAbbr = "",
            teacherFullName = "",
            nowMillis = now,
            timeZone = warsawTz
        )

        assertTrue(eval.status is LiveScheduleStatus.Break)
        val status = eval.status as LiveScheduleStatus.Break
        assertEquals(2, status.previousSlot.period)
        assertEquals(3, status.nextSlot.period)
        assertEquals(15 * 60, status.totalSeconds)
        assertEquals(9 * 60 + 30, status.remainingSeconds)
        assertEquals("Informatyka", status.nextSlot.items.single().lesson.subject)
        assertEquals("09:30", LiveScheduleTracker.formatCountdown(status.remainingSeconds))
    }

    @Test
    fun `before lessons and after lessons show appropriate states and next day preview`() {
        val beforeNow = millisFor(2026, Calendar.NOVEMBER, 16, 7, 45, 0)
        val beforeEval = LiveScheduleTracker.evaluate(
            timetable = sampleTimetable(),
            substitutions = null,
            userRole = UserRole.STUDENT,
            selectedClassName = "2TI",
            selectedGroup = StudentGroup.GROUP_1,
            customGroupFilter = "",
            teacherAbbr = "",
            teacherFullName = "",
            nowMillis = beforeNow,
            timeZone = warsawTz
        )
        assertTrue(beforeEval.status is LiveScheduleStatus.BeforeLessons)
        val before = beforeEval.status as LiveScheduleStatus.BeforeLessons
        assertEquals(1, before.firstSlot.period)
        assertEquals(15 * 60, before.remainingSeconds)

        val afterNow = millisFor(2026, Calendar.NOVEMBER, 16, 15, 0, 0)
        val afterEval = LiveScheduleTracker.evaluate(
            timetable = sampleTimetable(),
            substitutions = null,
            userRole = UserRole.STUDENT,
            selectedClassName = "2TI",
            selectedGroup = StudentGroup.GROUP_1,
            customGroupFilter = "",
            teacherAbbr = "",
            teacherFullName = "",
            nowMillis = afterNow,
            timeZone = warsawTz
        )
        assertTrue(afterEval.status is LiveScheduleStatus.AfterLessons)
        val after = afterEval.status as LiveScheduleStatus.AfterLessons
        assertEquals("Jutro (Wtorek)", after.nextDayLabel)
        assertEquals("J. polski", after.nextDayFirstSlot?.items?.first()?.lesson?.subject)
    }

    @Test
    fun `weekend shows Monday first lesson`() {
        // Saturday 2026-11-21 at 11:00:00
        val weekendNow = millisFor(2026, Calendar.NOVEMBER, 21, 11, 0, 0)
        val eval = LiveScheduleTracker.evaluate(
            timetable = sampleTimetable(),
            substitutions = null,
            userRole = UserRole.STUDENT,
            selectedClassName = "2TI",
            selectedGroup = StudentGroup.GROUP_1,
            customGroupFilter = "",
            teacherAbbr = "",
            teacherFullName = "",
            nowMillis = weekendNow,
            timeZone = warsawTz
        )

        assertTrue(eval.status is LiveScheduleStatus.Weekend)
        val weekend = eval.status as LiveScheduleStatus.Weekend
        assertNull(eval.todayDayIndex)
        assertEquals("Sobota", eval.todayDayName)
        assertEquals("Poniedziałek", weekend.nextDayLabel)
        assertEquals("Fizyka", weekend.nextDayFirstSlot?.items?.first()?.lesson?.subject)
    }

    @Test
    fun `cancelled first lesson shifts before-lessons target to second lesson`() {
        val subs = SubstitutionsSnapshot(
            dateHeading = "Zastępstwa na poniedziałek 16.11.2026",
            items = listOf(
                SubstitutionItem(
                    id = "c1",
                    className = "2TI",
                    period = "1",
                    absentTeacher = "JK",
                    subject = "Fizyka",
                    note = "Klasa przychodzi później - odwołane",
                    isCancellation = true
                )
            )
        )

        // At 08:20, lesson 1 is cancelled so student is in BeforeLessons waiting for lesson 2 at 8:50
        val now = millisFor(2026, Calendar.NOVEMBER, 16, 8, 20, 0)
        val eval = LiveScheduleTracker.evaluate(
            timetable = sampleTimetable(),
            substitutions = subs,
            userRole = UserRole.STUDENT,
            selectedClassName = "2TI",
            selectedGroup = StudentGroup.GROUP_1,
            customGroupFilter = "",
            teacherAbbr = "",
            teacherFullName = "",
            nowMillis = now,
            timeZone = warsawTz
        )

        assertEquals(1, eval.cancelledCountToday)
        assertTrue(eval.status is LiveScheduleStatus.BeforeLessons)
        val status = eval.status as LiveScheduleStatus.BeforeLessons
        assertEquals(2, status.firstSlot.period)
        assertEquals("Matematyka", status.firstSlot.items.first().lesson.subject)
        assertEquals(30 * 60, status.remainingSeconds)
    }

    @Test
    fun `public holiday and custom principal day off return Holiday status and find next real school day`() {
        // Wednesday Nov 11, 2026 -> Narodowe Święto Niepodległości
        val nov11Now = millisFor(2026, Calendar.NOVEMBER, 11, 10, 0, 0)
        val holidayEval = LiveScheduleTracker.evaluate(
            timetable = sampleTimetable(),
            substitutions = null,
            userRole = UserRole.STUDENT,
            selectedClassName = "2TI",
            selectedGroup = StudentGroup.GROUP_1,
            customGroupFilter = "",
            teacherAbbr = "",
            teacherFullName = "",
            nowMillis = nov11Now,
            timeZone = warsawTz
        )
        assertTrue(holidayEval.status is LiveScheduleStatus.Holiday)
        val holidayStatus = holidayEval.status as LiveScheduleStatus.Holiday
        assertEquals("Narodowe Święto Niepodległości", holidayStatus.title)

        // Monday Nov 16, 2026 marked as PrincipalDayOff -> skips Nov 16 and points to Jutro (Wtorek)
        val monNow = millisFor(2026, Calendar.NOVEMBER, 16, 9, 0, 0)
        val principalEval = LiveScheduleTracker.evaluate(
            timetable = sampleTimetable(),
            substitutions = null,
            userRole = UserRole.STUDENT,
            selectedClassName = "2TI",
            selectedGroup = StudentGroup.GROUP_1,
            customGroupFilter = "",
            teacherAbbr = "",
            teacherFullName = "",
            customDaysOff = listOf(com.example.data.model.PrincipalDayOff("2026-11-16", "Dzień dyrektorski")),
            nowMillis = monNow,
            timeZone = warsawTz
        )
        assertTrue(principalEval.status is LiveScheduleStatus.Holiday)
        val pStatus = principalEval.status as LiveScheduleStatus.Holiday
        assertEquals("Dzień dyrektorski", pStatus.title)
        assertEquals("Jutro (Wtorek)", pStatus.nextSchoolDayLabel)
        assertEquals("J. polski", pStatus.nextDayFirstSlot?.items?.first()?.lesson?.subject)
    }
}
