package com.example

import com.example.data.calendar.CalendarExportPlanner
import com.example.data.calendar.DeviceCalendarRepository
import com.example.data.model.CalendarExportBackend
import com.example.data.model.CalendarExportOptions
import com.example.data.model.CalendarRecurrenceMode
import com.example.data.model.CalendarStartWeekMode
import com.example.data.model.ClassTimetable
import com.example.data.model.DaySchedule
import com.example.data.model.LessonEntry
import com.example.data.model.TargetCalendarItem
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

    @Test
    fun `resolveSelection matches preferred Google account case-insensitively and picks primary calendar`() {
        val calendars = listOf(
            TargetCalendarItem(
                id = "1",
                name = "Praca",
                accountName = "jan.kowalski@gmail.com",
                isPrimary = false,
                backend = CalendarExportBackend.DEVICE_CALENDAR_PROVIDER
            ),
            TargetCalendarItem(
                id = "2",
                name = "Główny Google",
                accountName = "nauczyciel@zsme.pl",
                isPrimary = false,
                backend = CalendarExportBackend.DEVICE_CALENDAR_PROVIDER
            ),
            TargetCalendarItem(
                id = "3",
                name = "Szkoła ZSME",
                accountName = "nauczyciel@zsme.pl",
                isPrimary = true,
                backend = CalendarExportBackend.DEVICE_CALENDAR_PROVIDER
            )
        )

        val (acc, calId) = DeviceCalendarRepository.resolveSelection(
            calendars = calendars,
            preferredAccount = "NAUCZYCIEL@zsme.pl",
            currentSelectedAccount = "jan.kowalski@gmail.com",
            currentSelectedCalendarId = "1"
        )
        assertEquals("nauczyciel@zsme.pl", acc)
        assertEquals("3", calId)

        val (fallbackAcc, fallbackCalId) = DeviceCalendarRepository.resolveSelection(
            calendars = calendars,
            preferredAccount = "missing@gmail.com",
            currentSelectedAccount = null,
            currentSelectedCalendarId = null
        )
        assertEquals("jan.kowalski@gmail.com", fallbackAcc)
        assertEquals("1", fallbackCalId)
    }

    @Test
    fun `GoogleCredentialAuthManager generates URL-safe nonce and parses JWT claims`() {
        val nonce1 = com.example.data.calendar.GoogleCredentialAuthManager.generateSecureRandomNonce()
        val nonce2 = com.example.data.calendar.GoogleCredentialAuthManager.generateSecureRandomNonce()
        assertTrue(nonce1.length >= 22)
        assertTrue(nonce1 != nonce2)
        assertTrue(nonce1.none { it == '=' || it == '+' || it == '/' })

        // {"email":"jan@zsme.pl","name":"Jan Kowalski","given_name":"Jan","sub":"12345"}
        val payloadJson = """{"email":"jan@zsme.pl","name":"Jan Kowalski","given_name":"Jan","sub":"12345"}"""
        val header = "eyJhbGciOiJSUzI1NiJ9"
        val encodedPayload = encodeBase64UrlForTest(payloadJson.toByteArray(Charsets.UTF_8))
        val fakeJwt = "$header.$encodedPayload.sig"

        val claims = com.example.data.calendar.GoogleCredentialAuthManager.parseJwtClaims(fakeJwt)
        assertEquals("jan@zsme.pl", claims.email)
        assertEquals("Jan Kowalski", claims.name)
        assertEquals("Jan", claims.givenName)
        assertEquals("12345", claims.sub)

        val emptyClaims = com.example.data.calendar.GoogleCredentialAuthManager.parseJwtClaims("invalid")
        assertEquals("", emptyClaims.email)
    }

    @Test
    fun `GoogleCredentialAuthManager formats SHA-1 colon hex and defines calendar scopes`() {
        val sampleBytes = byteArrayOf(0x0A, 0x1B, 0x2C, 0xFF.toByte())
        assertEquals(
            "0A:1B:2C:FF",
            com.example.data.calendar.GoogleCredentialAuthManager.formatSha1ColonHex(sampleBytes)
        )
        assertEquals(
            "",
            com.example.data.calendar.GoogleCredentialAuthManager.formatSha1ColonHex(byteArrayOf())
        )
        assertEquals(
            "https://www.googleapis.com/auth/calendar",
            com.example.data.calendar.GoogleCredentialAuthManager.CALENDAR_SCOPE_URL
        )
        assertEquals(
            "oauth2:https://www.googleapis.com/auth/calendar",
            com.example.data.calendar.GoogleCredentialAuthManager.GOOGLE_AUTH_UTIL_CALENDAR_SCOPE
        )
    }

    private fun encodeBase64UrlForTest(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val hasB1 = i + 1 < bytes.size
            val hasB2 = i + 2 < bytes.size
            val b1 = if (hasB1) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) bytes[i + 2].toInt() and 0xFF else 0
            val triple = (b0 shl 16) or (b1 shl 8) or b2
            sb.append(alphabet[(triple ushr 18) and 0x3F])
            sb.append(alphabet[(triple ushr 12) and 0x3F])
            if (hasB1) sb.append(alphabet[(triple ushr 6) and 0x3F])
            if (hasB2) sb.append(alphabet[triple and 0x3F])
            i += 3
        }
        return sb.toString()
    }
}
