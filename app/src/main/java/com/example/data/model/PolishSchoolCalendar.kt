package com.example.data.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class DayOffCategory {
    SCHOOL_DAY,
    WEEKEND,
    PUBLIC_HOLIDAY,
    SCHOOL_BREAK,
    SUMMER_BREAK,
    PRINCIPAL_DAY_OFF
}

data class CalendarDayInfo(
    val dateIso: String,
    val year: Int,
    val monthZeroBased: Int,
    val dayOfMonth: Int,
    val dayOfWeek: Int,
    val category: DayOffCategory,
    val title: String,
    val description: String = "",
    val customPrincipalDay: PrincipalDayOff? = null
) {
    val isFreeFromSchool: Boolean
        get() = category != DayOffCategory.SCHOOL_DAY

    val isSpecialNonWeekendFree: Boolean
        get() = category == DayOffCategory.PUBLIC_HOLIDAY ||
            category == DayOffCategory.SCHOOL_BREAK ||
            category == DayOffCategory.SUMMER_BREAK ||
            category == DayOffCategory.PRINCIPAL_DAY_OFF

    val isWeekendDay: Boolean
        get() = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

    val isSchoolDayOffExcludingWeekends: Boolean
        get() = when (category) {
            DayOffCategory.PRINCIPAL_DAY_OFF -> true
            DayOffCategory.PUBLIC_HOLIDAY,
            DayOffCategory.SCHOOL_BREAK,
            DayOffCategory.SUMMER_BREAK -> !isWeekendDay
            DayOffCategory.WEEKEND,
            DayOffCategory.SCHOOL_DAY -> false
        }
}

data class HomeHolidayCountdowns(
    val isTodayFree: Boolean,
    val todayFreeTitle: String?,
    val nextFreeDayDays: Int,
    val nextFreeDayTitle: String,
    val nextFreeDayDateLabel: String,
    val nextSpecialDayDays: Int?,
    val nextSpecialDayTitle: String?,
    val nextSpecialDayDateLabel: String?,
    val isCurrentlySummerBreak: Boolean,
    val daysToVacation: Int,
    val vacationTargetDateLabel: String,
    val vacationTitle: String
)

object PolishSchoolCalendar {

    private val polishMonthGenitive = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "września", "października", "listopada", "grudnia"
    )

    val polishMonthNames = listOf(
        "Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
        "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień"
    )

    fun toIsoDate(year: Int, monthZeroBased: Int, dayOfMonth: Int): String {
        return String.format(Locale.US, "%04d-%02d-%02d", year, monthZeroBased + 1, dayOfMonth)
    }

    fun parseIsoDate(iso: String): Triple<Int, Int, Int>? {
        val parts = iso.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        return Triple(y, m - 1, d)
    }

    fun formatPlDate(year: Int, monthZeroBased: Int, dayOfMonth: Int): String {
        val monthName = polishMonthGenitive.getOrElse(monthZeroBased) { "" }
        return if (monthName.isNotBlank()) {
            "$dayOfMonth $monthName $year"
        } else {
            String.format(Locale.US, "%02d.%02d.%04d", dayOfMonth, monthZeroBased + 1, year)
        }
    }

    fun formatShortPlDate(year: Int, monthZeroBased: Int, dayOfMonth: Int): String {
        val monthName = polishMonthGenitive.getOrElse(monthZeroBased) { "" }
        return "$dayOfMonth $monthName"
    }

    /**
     * Anonymous Gregorian algorithm (Meeus/Jones/Butcher) returning Easter Sunday at 00:00.
     */
    fun computeEasterSunday(
        year: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Calendar {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31 // 3 = March, 4 = April
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return startOfDayCalendar(year, month - 1, day, timeZone)
    }

    /**
     * Polish MEN rule: school year ends on the first Friday following June 20
     * (i.e. earliest Friday >= June 21).
     */
    fun computeSchoolYearEndFriday(
        year: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Calendar {
        val cal = startOfDayCalendar(year, Calendar.JUNE, 21, timeZone)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val offset = (Calendar.FRIDAY - dow + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, offset)
        return cal
    }

    fun computeSummerBreakStart(
        year: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Calendar {
        return computeSchoolYearEndFriday(year, timeZone).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    /**
     * Polish MEN rule: new school year begins on Sept 1 (or next Monday if Sept 1 falls on Fri/Sat/Sun).
     */
    fun computeSchoolYearStart(
        year: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Calendar {
        val cal = startOfDayCalendar(year, Calendar.SEPTEMBER, 1, timeZone)
        val offset = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.FRIDAY -> 3
            Calendar.SATURDAY -> 2
            Calendar.SUNDAY -> 1
            else -> 0
        }
        cal.add(Calendar.DAY_OF_MONTH, offset)
        return cal
    }

    fun startOfDayCalendar(
        year: Int,
        monthZeroBased: Int,
        dayOfMonth: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Calendar {
        return Calendar.getInstance(timeZone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun daysBetween(startCal: Calendar, endCal: Calendar): Int {
        val diffMillis = endCal.timeInMillis - startCal.timeInMillis
        return (diffMillis / 86_400_000.0).roundToInt()
    }

    fun getStatutoryHolidayName(
        year: Int,
        monthZeroBased: Int,
        dayOfMonth: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String? {
        when (monthZeroBased) {
            Calendar.JANUARY -> when (dayOfMonth) {
                1 -> return "Nowy Rok"
                6 -> return "Święto Trzech Króli"
            }
            Calendar.MAY -> when (dayOfMonth) {
                1 -> return "Święto Pracy"
                3 -> return "Święto Konstytucji 3 Maja"
            }
            Calendar.AUGUST -> if (dayOfMonth == 15) {
                return "Wniebowzięcie NMP / Święto Wojska Polskiego"
            }
            Calendar.NOVEMBER -> when (dayOfMonth) {
                1 -> return "Wszystkich Świętych"
                11 -> return "Narodowe Święto Niepodległości"
            }
            Calendar.DECEMBER -> when (dayOfMonth) {
                24 -> if (year >= 2025) return "Wigilia Bożego Narodzenia"
                25 -> return "Boże Narodzenie (1. dzień)"
                26 -> return "Boże Narodzenie (2. dzień)"
            }
        }

        val easter = computeEasterSunday(year, timeZone)
        val target = startOfDayCalendar(year, monthZeroBased, dayOfMonth, timeZone)
        val deltaEaster = daysBetween(easter, target)
        return when (deltaEaster) {
            0 -> "Wielkanoc"
            1 -> "Poniedziałek Wielkanocny"
            49 -> "Zielone Świątki"
            60 -> "Boże Ciało"
            else -> null
        }
    }

    fun getSchoolBreakName(
        year: Int,
        monthZeroBased: Int,
        dayOfMonth: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Pair<DayOffCategory, String>? {
        val target = startOfDayCalendar(year, monthZeroBased, dayOfMonth, timeZone)

        // Summer break (wakacje letnie)
        val summerStart = computeSummerBreakStart(year, timeZone)
        val schoolStart = computeSchoolYearStart(year, timeZone)
        if (target.timeInMillis >= summerStart.timeInMillis && target.timeInMillis < schoolStart.timeInMillis) {
            return DayOffCategory.SUMMER_BREAK to "Wakacje letnie"
        }

        // Winter Christmas break: Dec 23 .. Dec 31
        if (monthZeroBased == Calendar.DECEMBER && dayOfMonth >= 23) {
            return DayOffCategory.SCHOOL_BREAK to "Zimowa przerwa świąteczna"
        }

        // Spring Easter break: Holy Thursday (-3) .. Tuesday after Easter (+2)
        val easter = computeEasterSunday(year, timeZone)
        val deltaEaster = daysBetween(easter, target)
        if (deltaEaster in -3..2) {
            return DayOffCategory.SCHOOL_BREAK to "Wiosenna przerwa świąteczna"
        }

        return null
    }

    fun getDayInfo(
        year: Int,
        monthZeroBased: Int,
        dayOfMonth: Int,
        customDaysOff: List<PrincipalDayOff> = emptyList(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): CalendarDayInfo {
        val iso = toIsoDate(year, monthZeroBased, dayOfMonth)
        val cal = startOfDayCalendar(year, monthZeroBased, dayOfMonth, timeZone)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        val customMatch = customDaysOff.firstOrNull { it.dateIso == iso }

        val statutory = getStatutoryHolidayName(year, monthZeroBased, dayOfMonth, timeZone)
        if (statutory != null) {
            return CalendarDayInfo(
                dateIso = iso,
                year = year,
                monthZeroBased = monthZeroBased,
                dayOfMonth = dayOfMonth,
                dayOfWeek = dow,
                category = DayOffCategory.PUBLIC_HOLIDAY,
                title = statutory,
                description = "Święto ustawowe w Polsce",
                customPrincipalDay = customMatch
            )
        }

        if (customMatch != null) {
            val cleanTitle = customMatch.title.trim().ifBlank { "Dzień dyrektorski" }
            return CalendarDayInfo(
                dateIso = iso,
                year = year,
                monthZeroBased = monthZeroBased,
                dayOfMonth = dayOfMonth,
                dayOfWeek = dow,
                category = DayOffCategory.PRINCIPAL_DAY_OFF,
                title = cleanTitle,
                description = "Dzień wolny od zajęć dydaktycznych",
                customPrincipalDay = customMatch
            )
        }

        val schoolBreak = getSchoolBreakName(year, monthZeroBased, dayOfMonth, timeZone)
        if (schoolBreak != null) {
            return CalendarDayInfo(
                dateIso = iso,
                year = year,
                monthZeroBased = monthZeroBased,
                dayOfMonth = dayOfMonth,
                dayOfWeek = dow,
                category = schoolBreak.first,
                title = schoolBreak.second,
                description = if (schoolBreak.first == DayOffCategory.SUMMER_BREAK) {
                    "Przerwa wakacyjna"
                } else {
                    "Przerwa świąteczna w kalendarzu roku szkolnego"
                },
                customPrincipalDay = null
            )
        }

        if (isWeekend) {
            val dayName = if (dow == Calendar.SATURDAY) "Sobota" else "Niedziela"
            return CalendarDayInfo(
                dateIso = iso,
                year = year,
                monthZeroBased = monthZeroBased,
                dayOfMonth = dayOfMonth,
                dayOfWeek = dow,
                category = DayOffCategory.WEEKEND,
                title = dayName,
                description = "Weekend — brak zajęć"
            )
        }

        return CalendarDayInfo(
            dateIso = iso,
            year = year,
            monthZeroBased = monthZeroBased,
            dayOfMonth = dayOfMonth,
            dayOfWeek = dow,
            category = DayOffCategory.SCHOOL_DAY,
            title = "Dzień nauki szkolnej"
        )
    }

    fun getDayInfo(
        cal: Calendar,
        customDaysOff: List<PrincipalDayOff> = emptyList(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): CalendarDayInfo {
        return getDayInfo(
            year = cal.get(Calendar.YEAR),
            monthZeroBased = cal.get(Calendar.MONTH),
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
            customDaysOff = customDaysOff,
            timeZone = timeZone
        )
    }

    fun expandPrincipalDateRange(
        startIso: String,
        dayCount: Int,
        title: String,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<PrincipalDayOff> {
        val parsed = parseIsoDate(startIso) ?: return emptyList()
        val count = dayCount.coerceIn(1, 30)
        val cleanTitle = title.trim().ifBlank { "Dzień dyrektorski" }
        val cal = startOfDayCalendar(parsed.first, parsed.second, parsed.third, timeZone)
        val out = mutableListOf<PrincipalDayOff>()
        repeat(count) {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val d = cal.get(Calendar.DAY_OF_MONTH)
            out += PrincipalDayOff(
                dateIso = toIsoDate(y, m, d),
                title = cleanTitle
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out
    }

    fun evaluateHomeCountdowns(
        customDaysOff: List<PrincipalDayOff> = emptyList(),
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): HomeHolidayCountdowns {
        val nowCal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val todayCal = startOfDayCalendar(
            nowCal.get(Calendar.YEAR),
            nowCal.get(Calendar.MONTH),
            nowCal.get(Calendar.DAY_OF_MONTH),
            timeZone
        )
        val todayInfo = getDayInfo(todayCal, customDaysOff, timeZone)

        val isTodayRealDayOff = todayInfo.isSchoolDayOffExcludingWeekends

        var nextFreeDays = 0
        var nextFreeInfo = todayInfo
        if (!isTodayRealDayOff) {
            val probe = todayCal.clone() as Calendar
            for (offset in 1..400) {
                probe.add(Calendar.DAY_OF_MONTH, 1)
                val info = getDayInfo(probe, customDaysOff, timeZone)
                if (info.isSchoolDayOffExcludingWeekends) {
                    nextFreeDays = offset
                    nextFreeInfo = info
                    break
                }
            }
        }

        // Next special holiday / school break / principal day off excluding summer break and weekends
        var nextSpecialDays: Int? = null
        var nextSpecialInfo: CalendarDayInfo? = null
        val specialProbe = todayCal.clone() as Calendar
        for (offset in 0..400) {
            if (offset > 0) specialProbe.add(Calendar.DAY_OF_MONTH, 1)
            val info = getDayInfo(specialProbe, customDaysOff, timeZone)
            if (info.isSchoolDayOffExcludingWeekends && info.category != DayOffCategory.SUMMER_BREAK) {
                nextSpecialDays = offset
                nextSpecialInfo = info
                break
            }
        }

        val currentYear = todayCal.get(Calendar.YEAR)
        val summerStartThisYear = computeSummerBreakStart(currentYear, timeZone)
        val schoolStartThisYear = computeSchoolYearStart(currentYear, timeZone)
        val isSummer = todayCal.timeInMillis >= summerStartThisYear.timeInMillis &&
            todayCal.timeInMillis < schoolStartThisYear.timeInMillis

        val (daysToVac, vacTargetCal, vacTitle) = if (isSummer) {
            val diff = daysBetween(todayCal, schoolStartThisYear).coerceAtLeast(1)
            Triple(diff, schoolStartThisYear, "Trwają wakacje")
        } else {
            val targetSummer = if (todayCal.timeInMillis < summerStartThisYear.timeInMillis) {
                summerStartThisYear
            } else {
                computeSummerBreakStart(currentYear + 1, timeZone)
            }
            val diff = daysBetween(todayCal, targetSummer).coerceAtLeast(0)
            Triple(diff, targetSummer, "Do wakacji")
        }

        return HomeHolidayCountdowns(
            isTodayFree = isTodayRealDayOff,
            todayFreeTitle = if (isTodayRealDayOff) todayInfo.title else null,
            nextFreeDayDays = nextFreeDays,
            nextFreeDayTitle = nextFreeInfo.title,
            nextFreeDayDateLabel = formatShortPlDate(
                nextFreeInfo.year,
                nextFreeInfo.monthZeroBased,
                nextFreeInfo.dayOfMonth
            ),
            nextSpecialDayDays = nextSpecialDays,
            nextSpecialDayTitle = nextSpecialInfo?.title,
            nextSpecialDayDateLabel = nextSpecialInfo?.let {
                formatShortPlDate(it.year, it.monthZeroBased, it.dayOfMonth)
            },
            isCurrentlySummerBreak = isSummer,
            daysToVacation = daysToVac,
            vacationTargetDateLabel = formatPlDate(
                vacTargetCal.get(Calendar.YEAR),
                vacTargetCal.get(Calendar.MONTH),
                vacTargetCal.get(Calendar.DAY_OF_MONTH)
            ),
            vacationTitle = vacTitle
        )
    }

    fun formatDaysPl(days: Int): String {
        return when {
            days == 0 -> "Dziś"
            days == 1 -> "1 dzień"
            else -> "$days dni"
        }
    }
}
