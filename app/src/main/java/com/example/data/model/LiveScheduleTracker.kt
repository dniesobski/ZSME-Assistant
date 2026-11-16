package com.example.data.model

import com.example.data.calendar.CalendarExportPlanner
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class LiveLessonItem(
    val lesson: LessonEntry,
    val substitution: SubstitutionItem? = null
)

data class LiveLessonSlot(
    val period: Int,
    val startSecondOfDay: Int,
    val endSecondOfDay: Int,
    val timeRangeLabel: String,
    val items: List<LiveLessonItem>
)

sealed interface LiveScheduleStatus {
    data object NoTimetable : LiveScheduleStatus

    data class DuringLesson(
        val currentSlot: LiveLessonSlot,
        val remainingSeconds: Int,
        val totalSeconds: Int,
        val nextSlot: LiveLessonSlot?
    ) : LiveScheduleStatus

    data class Break(
        val previousSlot: LiveLessonSlot,
        val nextSlot: LiveLessonSlot,
        val remainingSeconds: Int,
        val totalSeconds: Int
    ) : LiveScheduleStatus

    data class BeforeLessons(
        val firstSlot: LiveLessonSlot,
        val remainingSeconds: Int
    ) : LiveScheduleStatus

    data class AfterLessons(
        val nextDayLabel: String?,
        val nextDayFirstSlot: LiveLessonSlot?
    ) : LiveScheduleStatus

    data class Weekend(
        val nextDayLabel: String?,
        val nextDayFirstSlot: LiveLessonSlot?
    ) : LiveScheduleStatus
}

data class LiveScheduleEvaluation(
    val status: LiveScheduleStatus,
    val todayDayIndex: Int?,
    val todayDayName: String,
    val todaySlots: List<LiveLessonSlot>,
    val cancelledCountToday: Int = 0,
    val substitutionBanner: String? = null
)

object LiveScheduleTracker {

    private val dayNames = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek")

    fun evaluate(
        timetable: ClassTimetable?,
        substitutions: SubstitutionsSnapshot?,
        userRole: UserRole,
        selectedClassName: String,
        selectedGroup: StudentGroup,
        customGroupFilter: String,
        teacherAbbr: String,
        teacherFullName: String,
        hiddenSchoolIds: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): LiveScheduleEvaluation {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val todayDayIndex = when (dow) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            else -> null
        }
        val todayDayName = when (dow) {
            Calendar.MONDAY -> "Poniedziałek"
            Calendar.TUESDAY -> "Wtorek"
            Calendar.WEDNESDAY -> "Środa"
            Calendar.THURSDAY -> "Czwartek"
            Calendar.FRIDAY -> "Piątek"
            Calendar.SATURDAY -> "Sobota"
            Calendar.SUNDAY -> "Niedziela"
            else -> ""
        }

        if (timetable == null || timetable.days.all { it.lessons.isEmpty() }) {
            return LiveScheduleEvaluation(
                status = LiveScheduleStatus.NoTimetable,
                todayDayIndex = todayDayIndex,
                todayDayName = todayDayName,
                todaySlots = emptyList()
            )
        }

        data class DayBuildResult(
            val slots: List<LiveLessonSlot>,
            val cancelledCount: Int,
            val banner: String?
        )

        fun buildSlotsForDay(dayIndex: Int, applySubs: Boolean): DayBuildResult {
            val rawLessons = timetable.days.getOrNull(dayIndex)?.lessons.orEmpty()
            val visibleLessons = if (userRole == UserRole.TEACHER) {
                ScheduleFilterMatcher.filterLessonsByVisibleSchools(rawLessons, hiddenSchoolIds)
            } else {
                rawLessons.filter { lesson ->
                    ScheduleFilterMatcher.matchesGroup(
                        groupTag = lesson.groupTag,
                        combinedText = "${lesson.subject} ${lesson.rawText}",
                        selectedGroup = selectedGroup,
                        customGroupFilter = customGroupFilter
                    )
                }
            }

            if (visibleLessons.isEmpty()) {
                return DayBuildResult(emptyList(), 0, null)
            }

            val subMap: Map<Int, SubstitutionItem>
            val cancelledCount: Int
            val banner: String?
            if (applySubs && userRole == UserRole.STUDENT) {
                val res = ScheduleFilterMatcher.resolveClassDaySubstitutions(
                    dayLessons = visibleLessons,
                    dayIndex = dayIndex,
                    todayWeekdayIndex = todayDayIndex,
                    snapshot = substitutions,
                    className = selectedClassName.ifBlank { timetable.className },
                    selectedGroup = selectedGroup,
                    customGroupFilter = customGroupFilter
                )
                subMap = res.matchByLessonIndex
                cancelledCount = res.cancelledCount
                banner = res.summaryBanner
            } else if (applySubs && userRole == UserRole.TEACHER) {
                val teacherSubs = substitutions?.items.orEmpty().filter { item ->
                    ScheduleFilterMatcher.matchesTeacherSubstitution(
                        item = item,
                        teacherAbbr = teacherAbbr,
                        teacherFullName = teacherFullName
                    )
                }
                val dateHeading = substitutions?.dateHeading.orEmpty()
                val mapped = mutableMapOf<Int, SubstitutionItem>()
                var cancelled = 0
                visibleLessons.forEachIndexed { idx, lesson ->
                    val sub = ScheduleFilterMatcher.findTeacherSubstitutionForLesson(
                        substitutions = teacherSubs,
                        dateHeading = dateHeading,
                        lessonPeriod = lesson.period,
                        dayIndex = dayIndex,
                        todayWeekdayIndex = todayDayIndex
                    )
                    if (sub != null) {
                        mapped[idx] = sub
                        val roleMatch = ScheduleFilterMatcher.classifyTeacherSubstitution(
                            sub,
                            teacherAbbr,
                            teacherFullName
                        )
                        if (sub.isCancellation && roleMatch == ScheduleFilterMatcher.TeacherSubstitutionMatch.REPLACED) {
                            cancelled++
                        }
                    }
                }
                subMap = mapped
                cancelledCount = cancelled
                banner = null
            } else {
                subMap = emptyMap()
                cancelledCount = 0
                banner = null
            }

            val activeItems = visibleLessons.mapIndexedNotNull { idx, lesson ->
                val sub = subMap[idx]
                val isCancelledForUser = if (userRole == UserRole.TEACHER) {
                    sub != null && sub.isCancellation &&
                        ScheduleFilterMatcher.classifyTeacherSubstitution(sub, teacherAbbr, teacherFullName) ==
                        ScheduleFilterMatcher.TeacherSubstitutionMatch.REPLACED
                } else {
                    sub?.isCancellation == true
                }
                if (isCancelledForUser) null else LiveLessonItem(lesson = lesson, substitution = sub)
            }

            val groupedByWindow = linkedMapOf<Pair<Int, Int>, MutableList<LiveLessonItem>>()
            for (item in activeItems) {
                val (startPair, endPair) = CalendarExportPlanner.parseTimeRange(
                    item.lesson.timeRange,
                    item.lesson.period
                )
                val startSec = startPair.first * 3600 + startPair.second * 60
                var endSec = endPair.first * 3600 + endPair.second * 60
                if (endSec <= startSec) {
                    endSec = startSec + 45 * 60
                }
                groupedByWindow.getOrPut(startSec to endSec) { mutableListOf() }.add(item)
            }

            val slots = groupedByWindow.entries
                .sortedWith(
                    compareBy<Map.Entry<Pair<Int, Int>, MutableList<LiveLessonItem>>> { it.key.first }
                        .thenBy { it.value.minOfOrNull { item -> item.lesson.period } ?: Int.MAX_VALUE }
                )
                .map { (window, items) ->
                    val firstLesson = items.first().lesson
                    val startH = window.first / 3600
                    val startM = (window.first % 3600) / 60
                    val endH = window.second / 3600
                    val endM = (window.second % 3600) / 60
                    val fallbackRange = String.format(
                        Locale.US,
                        "%d:%02d–%d:%02d",
                        startH,
                        startM,
                        endH,
                        endM
                    )
                    LiveLessonSlot(
                        period = firstLesson.period,
                        startSecondOfDay = window.first,
                        endSecondOfDay = window.second,
                        timeRangeLabel = firstLesson.timeRange.ifBlank { fallbackRange },
                        items = items
                    )
                }

            return DayBuildResult(slots, cancelledCount, banner)
        }

        fun findNextSchoolDayFirstSlot(fromDayIndex: Int?): Pair<String?, LiveLessonSlot?> {
            val orderedDays = if (fromDayIndex == null) {
                (0..4).toList()
            } else {
                ((fromDayIndex + 1)..4).toList() + (0..fromDayIndex).toList()
            }
            for (dIdx in orderedDays) {
                val daySlots = buildSlotsForDay(dIdx, applySubs = false).slots
                if (daySlots.isNotEmpty()) {
                    val label = if (fromDayIndex != null && dIdx == fromDayIndex + 1) {
                        "Jutro (${dayNames[dIdx]})"
                    } else {
                        dayNames[dIdx]
                    }
                    return label to daySlots.first()
                }
            }
            return null to null
        }

        if (todayDayIndex == null) {
            val (nextLabel, nextSlot) = findNextSchoolDayFirstSlot(null)
            return LiveScheduleEvaluation(
                status = LiveScheduleStatus.Weekend(
                    nextDayLabel = nextLabel,
                    nextDayFirstSlot = nextSlot
                ),
                todayDayIndex = null,
                todayDayName = todayDayName,
                todaySlots = emptyList()
            )
        }

        val todayBuilt = buildSlotsForDay(todayDayIndex, applySubs = true)
        val todaySlots = todayBuilt.slots
        if (todaySlots.isEmpty()) {
            val (nextLabel, nextSlot) = findNextSchoolDayFirstSlot(todayDayIndex)
            return LiveScheduleEvaluation(
                status = LiveScheduleStatus.AfterLessons(
                    nextDayLabel = nextLabel,
                    nextDayFirstSlot = nextSlot
                ),
                todayDayIndex = todayDayIndex,
                todayDayName = todayDayName,
                todaySlots = emptyList(),
                cancelledCountToday = todayBuilt.cancelledCount,
                substitutionBanner = todayBuilt.banner
            )
        }

        val nowSec = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
            cal.get(Calendar.MINUTE) * 60 +
            cal.get(Calendar.SECOND)

        val currentIdx = todaySlots.indexOfFirst {
            nowSec in it.startSecondOfDay until it.endSecondOfDay
        }
        if (currentIdx >= 0) {
            val cur = todaySlots[currentIdx]
            val total = (cur.endSecondOfDay - cur.startSecondOfDay).coerceAtLeast(1)
            val remaining = (cur.endSecondOfDay - nowSec).coerceAtLeast(0)
            return LiveScheduleEvaluation(
                status = LiveScheduleStatus.DuringLesson(
                    currentSlot = cur,
                    remainingSeconds = remaining,
                    totalSeconds = total,
                    nextSlot = todaySlots.getOrNull(currentIdx + 1)
                ),
                todayDayIndex = todayDayIndex,
                todayDayName = todayDayName,
                todaySlots = todaySlots,
                cancelledCountToday = todayBuilt.cancelledCount,
                substitutionBanner = todayBuilt.banner
            )
        }

        val firstSlot = todaySlots.first()
        if (nowSec < firstSlot.startSecondOfDay) {
            return LiveScheduleEvaluation(
                status = LiveScheduleStatus.BeforeLessons(
                    firstSlot = firstSlot,
                    remainingSeconds = (firstSlot.startSecondOfDay - nowSec).coerceAtLeast(0)
                ),
                todayDayIndex = todayDayIndex,
                todayDayName = todayDayName,
                todaySlots = todaySlots,
                cancelledCountToday = todayBuilt.cancelledCount,
                substitutionBanner = todayBuilt.banner
            )
        }

        for (i in 0 until todaySlots.lastIndex) {
            val prev = todaySlots[i]
            val next = todaySlots[i + 1]
            if (nowSec >= prev.endSecondOfDay && nowSec < next.startSecondOfDay) {
                val totalBreak = (next.startSecondOfDay - prev.endSecondOfDay).coerceAtLeast(1)
                val remaining = (next.startSecondOfDay - nowSec).coerceAtLeast(0)
                return LiveScheduleEvaluation(
                    status = LiveScheduleStatus.Break(
                        previousSlot = prev,
                        nextSlot = next,
                        remainingSeconds = remaining,
                        totalSeconds = totalBreak
                    ),
                    todayDayIndex = todayDayIndex,
                    todayDayName = todayDayName,
                    todaySlots = todaySlots,
                    cancelledCountToday = todayBuilt.cancelledCount,
                    substitutionBanner = todayBuilt.banner
                )
            }
        }

        val (nextLabel, nextSlot) = findNextSchoolDayFirstSlot(todayDayIndex)
        return LiveScheduleEvaluation(
            status = LiveScheduleStatus.AfterLessons(
                nextDayLabel = nextLabel,
                nextDayFirstSlot = nextSlot
            ),
            todayDayIndex = todayDayIndex,
            todayDayName = todayDayName,
            todaySlots = todaySlots,
            cancelledCountToday = todayBuilt.cancelledCount,
            substitutionBanner = todayBuilt.banner
        )
    }

    fun formatCountdown(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val seconds = s % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatDurationWordsPl(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val seconds = s % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours godz. $minutes min"
            hours > 0 -> "$hours godz."
            minutes > 0 -> "$minutes min"
            else -> "$seconds sek."
        }
    }
}
