package com.example.data.calendar

import com.example.data.model.CalendarEventSpec
import com.example.data.model.CalendarExportOptions
import com.example.data.model.CalendarRecurrenceMode
import com.example.data.model.CalendarStartWeekMode
import com.example.data.model.ClassTimetable
import com.example.data.model.LessonEntry
import com.example.data.model.TeacherSchoolConfig
import com.example.data.model.TimetableCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object CalendarExportPlanner {

    const val EXPORT_MARKER = "[ZSME_PLAN_EXPORT]"
    const val EXTENDED_PROP_KEY = "zsmePlanExport"
    const val EXTENDED_PROP_VALUE = "true"
    const val WARSAW_TIME_ZONE_ID = "Europe/Warsaw"

    private val rangeRegex = Regex("""(\d{1,2})\s*[:.]\s*(\d{2})\s*[-–]\s*(\d{1,2})\s*[:.]\s*(\d{2})""")
    private val singleTimeRegex = Regex("""(\d{1,2})\s*[:.]\s*(\d{2})""")

    private val defaultBellSlots: Map<Int, Pair<Pair<Int, Int>, Pair<Int, Int>>> = mapOf(
        0 to ((7 to 10) to (7 to 55)),
        1 to ((8 to 0) to (8 to 45)),
        2 to ((8 to 50) to (9 to 35)),
        3 to ((9 to 45) to (10 to 30)),
        4 to ((10 to 40) to (11 to 25)),
        5 to ((11 to 35) to (12 to 20)),
        6 to ((12 to 40) to (13 to 25)),
        7 to ((13 to 35) to (14 to 20)),
        8 to ((14 to 25) to (15 to 10)),
        9 to ((15 to 15) to (16 to 0)),
        10 to ((16 to 5) to (16 to 50)),
        11 to ((16 to 55) to (17 to 40)),
        12 to ((17 to 45) to (18 to 30))
    )

    fun parseTimeRange(
        timeRange: String,
        period: Int
    ): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val clean = timeRange.trim()
        rangeRegex.find(clean)?.let { m ->
            val sh = m.groupValues[1].toIntOrNull()?.coerceIn(0, 23)
            val sm = m.groupValues[2].toIntOrNull()?.coerceIn(0, 59)
            val eh = m.groupValues[3].toIntOrNull()?.coerceIn(0, 23)
            val em = m.groupValues[4].toIntOrNull()?.coerceIn(0, 59)
            if (sh != null && sm != null && eh != null && em != null) {
                return (sh to sm) to (eh to em)
            }
        }
        singleTimeRegex.find(clean)?.let { m ->
            val sh = m.groupValues[1].toIntOrNull()?.coerceIn(0, 23)
            val sm = m.groupValues[2].toIntOrNull()?.coerceIn(0, 59)
            if (sh != null && sm != null) {
                val endTotal = sh * 60 + sm + 45
                return (sh to sm) to ((endTotal / 60).coerceIn(0, 23) to (endTotal % 60))
            }
        }
        return defaultBellSlots[period] ?: run {
            val baseMins = (8 * 60 + (period.coerceAtLeast(1) - 1) * 55).coerceAtMost(20 * 60)
            val endMins = (baseMins + 45).coerceAtMost(21 * 60)
            (baseMins / 60 to baseMins % 60) to (endMins / 60 to endMins % 60)
        }
    }

    fun defaultStartWeekModeForTime(
        referenceMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getTimeZone(WARSAW_TIME_ZONE_ID)
    ): CalendarStartWeekMode {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = referenceMillis }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
            CalendarStartWeekMode.NEXT_MONDAY
        } else {
            CalendarStartWeekMode.CURRENT_WEEK
        }
    }

    fun computeWeekMonday(
        referenceMillis: Long,
        startWeekMode: CalendarStartWeekMode,
        timeZone: TimeZone = TimeZone.getTimeZone(WARSAW_TIME_ZONE_ID)
    ): Calendar {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = referenceMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = when (dow) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
        cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
        if (startWeekMode == CalendarStartWeekMode.NEXT_MONDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 7)
        }
        return cal
    }

    fun computeRRule(
        weekMondayMillis: Long,
        recurrenceMode: CalendarRecurrenceMode,
        timeZone: TimeZone = TimeZone.getTimeZone(WARSAW_TIME_ZONE_ID)
    ): String? {
        if (recurrenceMode == CalendarRecurrenceMode.SINGLE_WEEK) return null

        val endCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = weekMondayMillis
        }
        when (recurrenceMode) {
            CalendarRecurrenceMode.UNTIL_END_OF_SCHOOL_YEAR -> {
                val month = endCal.get(Calendar.MONTH)
                val targetYear = if (month >= Calendar.JULY) {
                    endCal.get(Calendar.YEAR) + 1
                } else {
                    endCal.get(Calendar.YEAR)
                }
                endCal.set(targetYear, Calendar.JUNE, 30, 23, 59, 59)
                endCal.set(Calendar.MILLISECOND, 0)
            }
            CalendarRecurrenceMode.NEXT_16_WEEKS -> {
                endCal.add(Calendar.DAY_OF_YEAR, 16 * 7 - 1)
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                endCal.set(Calendar.MILLISECOND, 0)
            }
            CalendarRecurrenceMode.SINGLE_WEEK -> return null
        }

        val utcFmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            this.timeZone = TimeZone.getTimeZone("UTC")
        }
        val untilUtc = utcFmt.format(Date(endCal.timeInMillis))
        return "RRULE:FREQ=WEEKLY;UNTIL=$untilUtc"
    }

    fun formatRfc3339(
        timeMillis: Long,
        timeZone: TimeZone = TimeZone.getTimeZone(WARSAW_TIME_ZONE_ID)
    ): String {
        val localFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            this.timeZone = timeZone
        }
        val base = localFmt.format(Date(timeMillis))
        val offsetMillis = timeZone.getOffset(timeMillis)
        val sign = if (offsetMillis >= 0) "+" else "-"
        val totalMinutes = abs(offsetMillis) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return String.format(Locale.US, "%s%s%02d:%02d", base, sign, hours, minutes)
    }

    fun suggestedCalendarName(
        timetable: ClassTimetable,
        isTeacherMode: Boolean,
        teacherSchools: List<TeacherSchoolConfig>
    ): String {
        return if (isTeacherMode) {
            val schoolNames = teacherSchools.map { it.schoolName.trim() }.filter { it.isNotBlank() }.distinct()
            when {
                schoolNames.size > 1 -> "Plan zajęć — ${schoolNames.joinToString(", ")}"
                schoolNames.size == 1 -> "Plan nauczyciela — ${schoolNames.first()}"
                timetable.className.isNotBlank() -> "Plan nauczyciela — ${timetable.className}"
                else -> "Plan zajęć nauczyciela"
            }
        } else {
            when (timetable.category) {
                TimetableCategory.CLASS -> "Plan lekcji — ${timetable.className.ifBlank { "ZSME" }}"
                TimetableCategory.TEACHER -> "Plan nauczyciela — ${timetable.className.ifBlank { "ZSME" }}"
                TimetableCategory.ROOM -> "Plan sali — ${timetable.className.ifBlank { "ZSME" }}"
            }
        }
    }

    fun buildEventSpecs(
        timetable: ClassTimetable,
        filteredDayLessons: Map<Int, List<LessonEntry>>,
        options: CalendarExportOptions,
        isTeacherMode: Boolean,
        hasMultipleSchools: Boolean,
        referenceMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getTimeZone(WARSAW_TIME_ZONE_ID)
    ): List<CalendarEventSpec> {
        val weekMonday = computeWeekMonday(referenceMillis, options.startWeekMode, timeZone)
        val rrule = computeRRule(weekMonday.timeInMillis, options.recurrenceMode, timeZone)
        val specs = mutableListOf<CalendarEventSpec>()

        for (dayIndex in 0..4) {
            val lessons = filteredDayLessons[dayIndex].orEmpty()
            if (lessons.isEmpty()) continue

            for (lesson in lessons) {
                val (startPair, endPair) = parseTimeRange(lesson.timeRange, lesson.period)
                val startCal = Calendar.getInstance(timeZone).apply {
                    timeInMillis = weekMonday.timeInMillis
                    add(Calendar.DAY_OF_YEAR, dayIndex)
                    set(Calendar.HOUR_OF_DAY, startPair.first)
                    set(Calendar.MINUTE, startPair.second)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance(timeZone).apply {
                    timeInMillis = weekMonday.timeInMillis
                    add(Calendar.DAY_OF_YEAR, dayIndex)
                    set(Calendar.HOUR_OF_DAY, endPair.first)
                    set(Calendar.MINUTE, endPair.second)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (endCal.timeInMillis <= startCal.timeInMillis) {
                    endCal.timeInMillis = startCal.timeInMillis + 45 * 60_000L
                }

                val subjectTitle = lesson.subject.ifBlank { lesson.rawText }.ifBlank { "Lekcja ${lesson.period}" }
                val title = buildString {
                    append(subjectTitle)
                    if (isTeacherMode && lesson.classGroupLabel.isNotBlank()) {
                        append(" (kl. ${lesson.classGroupLabel})")
                    } else if (!isTeacherMode && !lesson.groupTag.isNullOrBlank() && !subjectTitle.contains(lesson.groupTag)) {
                        append(" (${lesson.groupTag})")
                    }
                    if (isTeacherMode && hasMultipleSchools && lesson.schoolName.isNotBlank()) {
                        append(" • ${lesson.schoolName}")
                    }
                }

                val location = buildList {
                    if (lesson.schoolName.isNotBlank() && isTeacherMode && hasMultipleSchools) {
                        add(lesson.schoolName)
                    }
                    if (lesson.room.isNotBlank()) {
                        add("Sala ${lesson.room}")
                    }
                }.joinToString(" • ")

                val descriptionLines = buildList {
                    add("Lekcja nr ${lesson.period}${if (lesson.timeRange.isNotBlank()) " (${lesson.timeRange})" else ""}")
                    if (lesson.schoolName.isNotBlank()) add("Placówka: ${lesson.schoolName}")
                    if (lesson.classGroupLabel.isNotBlank()) {
                        add("Klasa: ${lesson.classGroupLabel}")
                    } else if (!isTeacherMode && timetable.category == TimetableCategory.CLASS && timetable.className.isNotBlank()) {
                        add("Klasa: ${timetable.className}")
                    }
                    if (lesson.teacher.isNotBlank()) add("Nauczyciel: ${lesson.teacher}")
                    if (lesson.room.isNotBlank()) add("Sala: ${lesson.room}")
                    if (!lesson.groupTag.isNullOrBlank()) add("Grupa: ${lesson.groupTag}")
                    add(EXPORT_MARKER)
                }

                specs.add(
                    CalendarEventSpec(
                        dayIndex = dayIndex,
                        title = title,
                        location = location,
                        description = descriptionLines.joinToString("\n"),
                        startMillis = startCal.timeInMillis,
                        endMillis = endCal.timeInMillis,
                        startIso = formatRfc3339(startCal.timeInMillis, timeZone),
                        endIso = formatRfc3339(endCal.timeInMillis, timeZone),
                        rrule = rrule,
                        reminderMinutes = options.reminderMinutes
                    )
                )
            }
        }
        return specs
    }
}
