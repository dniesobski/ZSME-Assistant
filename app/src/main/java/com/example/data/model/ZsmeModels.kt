package com.example.data.model

import kotlinx.serialization.Serializable
import java.util.Calendar

@Serializable
enum class UserRole(val label: String) {
    STUDENT("Uczeń"),
    TEACHER("Nauczyciel")
}

@Serializable
enum class TimetableCategory(val label: String) {
    CLASS("Klasy"),
    TEACHER("Nauczyciele"),
    ROOM("Sale")
}

@Serializable
data class SchoolClass(
    val id: String,
    val name: String,
    val url: String
)

@Serializable
data class TimetableTarget(
    val id: String,
    val name: String,
    val url: String,
    val category: TimetableCategory
)

@Serializable
data class TimetableCatalog(
    val classes: List<SchoolClass> = emptyList(),
    val teachers: List<TimetableTarget> = emptyList(),
    val rooms: List<TimetableTarget> = emptyList()
)

@Serializable
enum class StudentGroup(
    val displayName: String,
    val shortLabel: String
) {
    ALL("Wszystkie grupy (cała klasa)", "Cała klasa"),
    GROUP_1("Grupa 1 (1/2)", "Grupa 1"),
    GROUP_2("Grupa 2 (2/2)", "Grupa 2")
}

@Serializable
enum class ThemeMode(val displayName: String) {
    SYSTEM("Systemowy"),
    LIGHT("Jasny"),
    DARK("Ciemny")
}

@Serializable
data class TeacherSchoolConfig(
    val id: String,
    val schoolName: String,
    val teacherAbbreviation: String,
    val planUrl: String,
    val colorIndex: Int = 0
)

@Serializable
data class LessonEntry(
    val period: Int,
    val timeRange: String,
    val subject: String,
    val teacher: String,
    val room: String,
    val classGroupLabel: String = "",
    val groupTag: String? = null,
    val rawText: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val schoolColorIndex: Int = -1
)

@Serializable
data class DaySchedule(
    val dayIndex: Int, // 0 = Poniedziałek .. 4 = Piątek
    val dayName: String,
    val lessons: List<LessonEntry>
)

@Serializable
data class ClassTimetable(
    val className: String,
    val classUrl: String,
    val category: TimetableCategory = TimetableCategory.CLASS,
    val generatedInfo: String = "",
    val days: List<DaySchedule> = emptyList(),
    val fetchedAtMillis: Long = 0L,
    val contentHash: String = ""
)

@Serializable
data class SubstitutionItem(
    val id: String,
    val dateLabel: String = "",
    val className: String = "",
    val period: String = "",
    val timeRange: String = "",
    val absentTeacher: String = "",
    val subject: String = "",
    val substituteTeacher: String = "",
    val room: String = "",
    val note: String = "",
    val groupTag: String? = null,
    val isCancellation: Boolean = false
)

@Serializable
data class SubstitutionsSnapshot(
    val dateHeading: String = "",
    val items: List<SubstitutionItem> = emptyList(),
    val announcements: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val fetchedAtMillis: Long = 0L,
    val rawHash: String = "",
    val authSucceeded: Boolean = true,
    val sourceUrl: String = ""
)

@Serializable
data class NotificationLogEntry(
    val id: String,
    val title: String,
    val message: String,
    val timestampMillis: Long,
    val category: String // "SUBSTITUTION" or "TIMETABLE"
)

@Serializable
data class ApkCandidate(
    val fileName: String,
    val downloadUrl: String,
    val versionName: String = "",
    val directoryDateText: String = "",
    val lastModifiedHeader: String = "",
    val lastModifiedMillis: Long = 0L,
    val eTag: String = "",
    val contentLength: Long = 0L
)

data class AppUpdateCheckResult(
    val candidate: ApkCandidate?,
    val updateAvailable: Boolean,
    val reason: String,
    val currentVersionName: String,
    val checkedAtMillis: Long = System.currentTimeMillis()
)

object AppVersionHelper {
    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.trim().trimStart('v', 'V').split(Regex("""[^0-9]+""")).filter { it.isNotBlank() }.map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.trim().trimStart('v', 'V').split(Regex("""[^0-9]+""")).filter { it.isNotBlank() }.map { it.toIntOrNull() ?: 0 }
        if (parts1.isEmpty() && parts2.isEmpty()) return 0
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    fun extractVersionFromText(text: String): String {
        val explicit = Regex("""(?:v|ver|version|[-_])(\d+(?:\.\d+){1,3})(?=\D|$)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)
        if (!explicit.isNullOrBlank()) return explicit
        return Regex("""\b(\d+\.\d+(?:\.\d+){0,2})\b""").find(text)?.groupValues?.get(1).orEmpty()
    }
}

@Serializable
data class UserPreferences(
    val userRole: UserRole = UserRole.STUDENT,
    val teacherAbbreviation: String = "",
    val teacherFullName: String = "",
    val selectedClassId: String = "",
    val selectedClassName: String = "",
    val selectedClassUrl: String = "",
    val selectedGroup: StudentGroup = StudentGroup.ALL,
    val customGroupFilter: String = "",
    val substitutionsUrl: String = DEFAULT_SUBSTITUTIONS_URL,
    val planUrl: String = DEFAULT_PLAN_URL,
    val secretariatKey: String = DEFAULT_SECRETARIAT_KEY,
    val notificationsEnabled: Boolean = true,
    val checkIntervalMinutes: Long = 15L,
    val lastNotifiedSubstitutionsHash: String = "",
    val lastNotifiedTimetableHash: String = "",
    val useDynamicColor: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val recentNotifications: List<NotificationLogEntry> = emptyList(),
    val meshNickname: String = "",
    val lastApkUrl: String = "",
    val lastApkFileName: String = "",
    val lastApkVersion: String = "",
    val lastApkLastModified: String = "",
    val lastApkETag: String = "",
    val lastApkContentLength: Long = 0L,
    val appUpdateUrl: String = DEFAULT_GITHUB_RELEASES_URL,
    val additionalSchools: List<TeacherSchoolConfig> = emptyList()
) {
    fun allTeacherSchools(): List<TeacherSchoolConfig> {
        val list = mutableListOf<TeacherSchoolConfig>()
        if (teacherAbbreviation.isNotBlank()) {
            val cleanPlanUrl = planUrl.trim().ifBlank { DEFAULT_PLAN_URL }
            list += TeacherSchoolConfig(
                id = PRIMARY_SCHOOL_ID,
                schoolName = deriveSchoolLabel(cleanPlanUrl, fallback = "ZSME"),
                teacherAbbreviation = teacherAbbreviation.trim().uppercase(),
                planUrl = cleanPlanUrl,
                colorIndex = 0
            )
        }
        additionalSchools.forEachIndexed { idx, school ->
            if (school.teacherAbbreviation.isNotBlank() && school.planUrl.isNotBlank()) {
                val cleanUrl = school.planUrl.trim()
                val label = school.schoolName.trim().ifBlank {
                    deriveSchoolLabel(cleanUrl, fallback = "Szkoła ${idx + 2}")
                }
                list += school.copy(
                    schoolName = label,
                    teacherAbbreviation = school.teacherAbbreviation.trim().uppercase(),
                    planUrl = cleanUrl,
                    colorIndex = if (school.colorIndex > 0) school.colorIndex else (idx + 1)
                )
            }
        }
        return list
    }

    companion object {
        const val PRIMARY_SCHOOL_ID = "primary_zsme"
        const val DEFAULT_SECRETARIAT_KEY = "zsmeedukacja"
        const val TEACHER_ACCESS_PASSWORD = "zsmeedukacja"
        const val DEFAULT_PLAN_URL = "https://plan.zsme.pl/"
        const val DEFAULT_SUBSTITUTIONS_URL = "https://sekretariat.zsme.pl/tv.php"
        const val DEFAULT_APP_UPDATE_URL = "https://sekretariat.zsme.pl/app"
        const val LEGACY_GITHUB_V106_URL =
            "https://github.com/dniesobski/ZSME-Assistant/releases/download/v1.0.6/ZSMEAssist.apk"
        const val DEFAULT_GITHUB_RELEASES_URL =
            "https://github.com/dniesobski/ZSME-Assistant/releases/latest"
        const val PLAN_BASE_URL = DEFAULT_PLAN_URL
        const val SECRETARIAT_BASE_URL = DEFAULT_SUBSTITUTIONS_URL

        fun isValidTeacherAccessPassword(input: String): Boolean =
            input.trim() == TEACHER_ACCESS_PASSWORD

        fun deriveSchoolLabel(url: String, fallback: String = "Szkoła"): String {
            return runCatching {
                val host = java.net.URI(url.trim()).host.orEmpty()
                    .removePrefix("www.")
                    .removePrefix("plan.")
                when {
                    host.contains("zsme", ignoreCase = true) -> "ZSME"
                    host.isNotBlank() -> host.substringBefore('.').uppercase().ifBlank { host }
                    else -> fallback
                }
            }.getOrDefault(fallback)
        }
    }
}

object ScheduleFilterMatcher {
    private val group1Markers = listOf("1/2", "gr.1", "gr. 1", "gr 1", "grupa 1", "-1/2", "/1", "(1)", "g1", "gr1")
    private val group2Markers = listOf("2/2", "gr.2", "gr. 2", "gr 2", "grupa 2", "-2/2", "/2", "(2)", "g2", "gr2")
    private val primaryClassCodeRegex = Regex("""\b([1-6][A-ZĄĆĘŁŃÓŚŹŻ]{1,5}\d?)\b""")

    fun detectGroupTag(text: String): String? {
        val lower = text.lowercase()
        return when {
            group1Markers.any { containsToken(lower, it) } -> "1/2"
            group2Markers.any { containsToken(lower, it) } -> "2/2"
            else -> null
        }
    }

    private fun containsToken(lowerText: String, marker: String): Boolean {
        if (marker == "/1" || marker == "/2") {
            val regex = Regex("""(?<!\d)${Regex.escape(marker)}(?!\d)""")
            return regex.containsMatchIn(lowerText)
        }
        return lowerText.contains(marker)
    }

    fun matchesGroup(
        groupTag: String?,
        combinedText: String,
        selectedGroup: StudentGroup,
        customGroupFilter: String
    ): Boolean {
        val detected = groupTag ?: detectGroupTag(combinedText)
        val groupMatches = when (selectedGroup) {
            StudentGroup.ALL -> true
            StudentGroup.GROUP_1 -> detected == null || detected == "1/2"
            StudentGroup.GROUP_2 -> detected == null || detected == "2/2"
        }
        if (!groupMatches) return false

        val custom = customGroupFilter.trim().lowercase()
        if (custom.isNotEmpty() && detected != null) {
            val lower = combinedText.lowercase()
            if (lower.contains("gr.") || lower.contains("grupa") || lower.contains("/2")) {
                return lower.contains(custom) || !looksLikeSpecificElective(lower)
            }
        }
        return true
    }

    private fun looksLikeSpecificElective(lower: String): Boolean {
        return lower.contains("ang") || lower.contains("niem") || lower.contains("inf") ||
            lower.contains("wf") || lower.contains("rel") || lower.contains("dz") || lower.contains("ch")
    }

    fun matchesClass(
        selectedClassName: String,
        itemClassName: String,
        fallbackText: String = ""
    ): Boolean {
        val target = extractPrimaryClassCode(selectedClassName)
        if (target.isEmpty()) return false

        val candidate = normalizeClassToken(itemClassName)
        if (candidate.isNotEmpty()) {
            val regex = Regex("""(?<![A-Z0-9])${Regex.escape(target)}(?![A-Z])""")
            if (regex.containsMatchIn(candidate)) return true
        }

        val fallback = normalizeClassToken(fallbackText)
        if (fallback.isNotEmpty()) {
            val regex = Regex("""(?<![A-Z0-9])${Regex.escape(target)}(?![A-Z])""")
            if (regex.containsMatchIn(fallback)) return true
        }
        return false
    }

    fun matchesSubstitution(
        item: SubstitutionItem,
        selectedClassName: String,
        selectedGroup: StudentGroup,
        customGroupFilter: String
    ): Boolean {
        if (selectedClassName.isBlank()) return true
        val combined = listOf(item.className, item.subject, item.note, item.groupTag.orEmpty()).joinToString(" ")
        if (!matchesClass(selectedClassName, item.className, combined)) {
            return false
        }
        return matchesGroup(item.groupTag, combined, selectedGroup, customGroupFilter)
    }

    enum class TeacherSubstitutionMatch {
        NONE,
        CONDUCTING,
        REPLACED
    }

    fun matchesTeacherToken(text: String, teacherAbbr: String): Boolean {
        val cleanAbbr = teacherAbbr.trim()
        if (cleanAbbr.isBlank() || text.isBlank()) return false

        // Build dot-optional pattern for initials like "JK" matching "JK", "J.K.", "J. K."
        val lettersOnly = cleanAbbr.filter { it.isLetterOrDigit() }
        val escaped = Regex.escape(cleanAbbr)
        val exactTokenRegex = Regex(
            """(?<![A-ZĄĆĘŁŃÓŚŹŻa-ząćęłńóśźż0-9])$escaped(?![A-ZĄĆĘŁŃÓŚŹŻa-ząćęłńóśźż0-9])""",
            RegexOption.IGNORE_CASE
        )
        if (exactTokenRegex.containsMatchIn(text)) return true

        if (lettersOnly.length in 2..4 && lettersOnly.all { it.isLetter() }) {
            val dotted = lettersOnly.toCharArray().joinToString("""\.?\s*""") { Regex.escape(it.toString()) } + """\.?"""
            val dottedRegex = Regex(
                """(?<![A-ZĄĆĘŁŃÓŚŹŻa-ząćęłńóśźż0-9])$dotted(?![A-ZĄĆĘŁŃÓŚŹŻa-ząćęłńóśźż0-9])""",
                RegexOption.IGNORE_CASE
            )
            if (dottedRegex.containsMatchIn(text)) return true
        }
        return false
    }

    fun classifyTeacherSubstitution(
        item: SubstitutionItem,
        teacherAbbr: String,
        teacherFullName: String
    ): TeacherSubstitutionMatch {
        val cleanAbbr = teacherAbbr.trim()
        val cleanName = teacherFullName.trim()
        if (cleanAbbr.isBlank() && cleanName.isBlank()) return TeacherSubstitutionMatch.NONE

        val nameParts = cleanName
            .split(Regex("""[\s,.;:/()-]+"""))
            .map { it.trim() }
            .filter { it.length >= 3 }
        val foldedNameParts = nameParts.map { foldPolishDiacritics(it) }.distinct()

        fun fieldMatches(fieldText: String): Boolean {
            if (fieldText.isBlank()) return false
            if (cleanAbbr.isNotBlank()) {
                if (cleanAbbr.length <= 4) {
                    if (matchesTeacherToken(fieldText, cleanAbbr)) return true
                } else {
                    if (matchesNameQuery(fieldText, cleanAbbr, listOf(foldPolishDiacritics(cleanAbbr)))) return true
                }
            }
            if (cleanName.isNotBlank()) {
                if (matchesNameQuery(fieldText, cleanName, foldedNameParts)) return true
            }
            return false
        }

        val inSubstitute = fieldMatches(item.substituteTeacher)
        val inAbsent = fieldMatches(item.absentTeacher)
        return when {
            inSubstitute -> TeacherSubstitutionMatch.CONDUCTING
            inAbsent -> TeacherSubstitutionMatch.REPLACED
            fieldMatches(item.note) -> {
                val lowerNote = item.note.lowercase()
                if (lowerNote.contains("zastęp") || lowerNote.contains("zastep") || lowerNote.contains("prowadzi")) {
                    TeacherSubstitutionMatch.CONDUCTING
                } else {
                    TeacherSubstitutionMatch.REPLACED
                }
            }
            else -> TeacherSubstitutionMatch.NONE
        }
    }

    fun matchesTeacherSubstitution(
        item: SubstitutionItem,
        teacherAbbr: String,
        teacherFullName: String
    ): Boolean = classifyTeacherSubstitution(item, teacherAbbr, teacherFullName) != TeacherSubstitutionMatch.NONE

    private fun matchesNameQuery(fieldText: String, rawQuery: String, foldedParts: List<String>): Boolean {
        if (rawQuery.length >= 3 && fieldText.contains(rawQuery, ignoreCase = true)) return true
        val foldedField = foldPolishDiacritics(fieldText).lowercase()
        val foldedQuery = foldPolishDiacritics(rawQuery).lowercase()
        if (foldedQuery.length >= 3 && foldedField.contains(foldedQuery)) return true
        return foldedParts.any { part ->
            val lp = part.lowercase()
            val regex = Regex("""(?<![a-z0-9])${Regex.escape(lp)}(?![a-z0-9])""")
            regex.containsMatchIn(foldedField)
        }
    }

    fun foldPolishDiacritics(input: String): String {
        return input
            .replace('ą', 'a').replace('Ą', 'A')
            .replace('ć', 'c').replace('Ć', 'C')
            .replace('ę', 'e').replace('Ę', 'E')
            .replace('ł', 'l').replace('Ł', 'L')
            .replace('ń', 'n').replace('Ń', 'N')
            .replace('ó', 'o').replace('Ó', 'O')
            .replace('ś', 's').replace('Ś', 'S')
            .replace('ź', 'z').replace('Ź', 'Z')
            .replace('ż', 'z').replace('Ż', 'Z')
    }

    fun isCancellationText(text: String): Boolean {
        if (text.isBlank()) return false
        val folded = foldPolishDiacritics(text).lowercase()
        return folded.contains("odwolan") ||
            folded.contains("zwoln") ||
            folded.contains("okienk") ||
            folded.contains("brak zajec") ||
            folded.contains("brak lekcji") ||
            folded.contains("nie ma lekcji") ||
            folded.contains("nie ma zajec") ||
            folded.contains("nie odbywa sie") ||
            folded.contains("do domu") ||
            folded.contains("anulowan") ||
            folded.contains("przychodzi pozniej") ||
            folded.contains("przychodza pozniej") ||
            folded.contains("przychodzi na") ||
            folded.contains("przychodza na") ||
            folded.contains("zaczyna pozniej") ||
            folded.contains("zaczynaja pozniej") ||
            folded.contains("rozpoczyna pozniej") ||
            folded.contains("rozpoczynaja pozniej") ||
            folded.contains("konczy szybciej") ||
            folded.contains("koncza szybciej") ||
            folded.contains("konczy wczesniej") ||
            folded.contains("koncza wczesniej") ||
            folded.contains("konczy po") ||
            folded.contains("koncza po") ||
            Regex("""\b(?:zaczyna|zaczynaja|rozpoczyna|rozpoczynaja)\s+(?:pozniej\s+)?od\s+\d{1,2}\b""").containsMatchIn(folded) ||
            (Regex("""\b(?:wolne|wolna|odpada|odpadaja)\b""").containsMatchIn(folded) && !folded.contains("wolne miejsc"))
    }

    fun resolveWeekdayIndex(dateText: String): Int? {
        if (dateText.isBlank()) return null

        // Try dd.MM.yyyy / dd-MM-yyyy / dd/MM/yyyy
        val dmyMatch = Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})\b""").find(dateText)
        if (dmyMatch != null) {
            val day = dmyMatch.groupValues[1].toIntOrNull()
            val month = dmyMatch.groupValues[2].toIntOrNull()
            val rawYear = dmyMatch.groupValues[3].toIntOrNull()
            if (day != null && month != null && rawYear != null && month in 1..12 && day in 1..31) {
                val year = if (rawYear < 100) 2000 + rawYear else rawYear
                val cal = Calendar.getInstance().apply {
                    isLenient = false
                    clear()
                    set(year, month - 1, day)
                }
                val dow = runCatching { cal.get(Calendar.DAY_OF_WEEK) }.getOrNull()
                return calendarDowToWeekdayIndex(dow)
            }
        }

        // Try yyyy-MM-dd
        val ymdMatch = Regex("""\b(\d{4})[./-](\d{1,2})[./-](\d{1,2})\b""").find(dateText)
        if (ymdMatch != null) {
            val year = ymdMatch.groupValues[1].toIntOrNull()
            val month = ymdMatch.groupValues[2].toIntOrNull()
            val day = ymdMatch.groupValues[3].toIntOrNull()
            if (year != null && month != null && day != null && month in 1..12 && day in 1..31) {
                val cal = Calendar.getInstance().apply {
                    isLenient = false
                    clear()
                    set(year, month - 1, day)
                }
                val dow = runCatching { cal.get(Calendar.DAY_OF_WEEK) }.getOrNull()
                return calendarDowToWeekdayIndex(dow)
            }
        }

        val folded = foldPolishDiacritics(dateText).lowercase()
        return when {
            folded.contains("poniedzial") -> 0
            folded.contains("wtorek") || folded.contains("wtork") -> 1
            folded.contains("srod") -> 2
            folded.contains("czwart") -> 3
            folded.contains("piat") -> 4
            else -> null
        }
    }

    private fun calendarDowToWeekdayIndex(dayOfWeek: Int?): Int? {
        return when (dayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            else -> null
        }
    }

    fun substitutionAppliesToDay(
        dateLabel: String,
        fallbackDateHeading: String,
        dayIndex: Int,
        todayWeekdayIndex: Int?
    ): Boolean {
        val label = dateLabel.ifBlank { fallbackDateHeading }
        val resolvedDay = resolveWeekdayIndex(label)
        return if (resolvedDay != null) {
            resolvedDay == dayIndex
        } else {
            todayWeekdayIndex != null && dayIndex == todayWeekdayIndex
        }
    }

    fun parseSubstitutionPeriods(periodText: String): Set<Int> {
        if (periodText.isBlank()) return emptySet()
        // Strip parenthetical group tags like "(1/2)" or "(gr. 1)" before reading period numbers
        val cleaned = periodText.replace(Regex("""\(.*?\)"""), " ")
        val result = linkedSetOf<Int>()

        val rangeRegex = Regex("""(\d{1,2})\s*[-–]\s*(\d{1,2})""")
        val withoutRanges = rangeRegex.replace(cleaned) { match ->
            val start = match.groupValues[1].toIntOrNull()
            val end = match.groupValues[2].toIntOrNull()
            if (start != null && end != null && start in 0..16 && end in start..16 && end - start <= 12) {
                for (p in start..end) result.add(p)
            }
            " "
        }

        Regex("""\b(\d{1,2})\b""").findAll(withoutRanges).forEach { match ->
            val n = match.groupValues[1].toIntOrNull()
            if (n != null && n in 0..16) {
                result.add(n)
            }
        }
        return result
    }

    data class ClassDaySubstitutionResolution(
        val matchByLessonIndex: Map<Int, SubstitutionItem> = emptyMap(),
        val summaryBanner: String? = null,
        val cancelledCount: Int = 0,
        val activeSubCount: Int = 0
    )

    fun resolveClassDaySubstitutions(
        dayLessons: List<LessonEntry>,
        dayIndex: Int,
        todayWeekdayIndex: Int?,
        snapshot: SubstitutionsSnapshot?,
        className: String,
        selectedGroup: StudentGroup,
        customGroupFilter: String
    ): ClassDaySubstitutionResolution {
        if (snapshot == null || dayLessons.isEmpty() || className.isBlank()) {
            return ClassDaySubstitutionResolution()
        }

        val matchingItems = snapshot.items.filter { item ->
            matchesSubstitution(
                item = item,
                selectedClassName = className,
                selectedGroup = selectedGroup,
                customGroupFilter = customGroupFilter
            ) && substitutionAppliesToDay(
                dateLabel = item.dateLabel,
                fallbackDateHeading = snapshot.dateHeading,
                dayIndex = dayIndex,
                todayWeekdayIndex = todayWeekdayIndex
            )
        }

        val snapshotMatchesDay = substitutionAppliesToDay(
            dateLabel = snapshot.dateHeading,
            fallbackDateHeading = "",
            dayIndex = dayIndex,
            todayWeekdayIndex = todayWeekdayIndex
        )
        val matchingAnnouncements = if (snapshotMatchesDay) {
            (snapshot.announcements + snapshot.notes)
                .distinct()
                .filter { line ->
                    matchesClass(className, "", line) &&
                        matchesGroup(null, line, selectedGroup, customGroupFilter)
                }
        } else {
            emptyList()
        }

        if (matchingItems.isEmpty() && matchingAnnouncements.isEmpty()) {
            return ClassDaySubstitutionResolution()
        }

        val resolvedByIndex = linkedMapOf<Int, SubstitutionItem>()

        // 1. Direct / explicit substitution item matching + relative boundary rules inside items
        for (item in matchingItems) {
            val itemText = listOf(item.subject, item.substituteTeacher, item.note)
                .filter { it.isNotBlank() }
                .joinToString(" • ")

            val relativePeriods = detectBoundaryAffectedPeriods(
                text = itemText,
                dayLessons = dayLessons
            )

            var explicitPeriods = parseSubstitutionPeriods(item.period)
            if (explicitPeriods.isEmpty() && itemText.isNotBlank() && isCancellationText(itemText)) {
                explicitPeriods = extractPeriodsFromCancellationText(itemText)
            }

            // Guard: if row explicitly says "przychodzi na 3 lekcję" and colPeriod contains "3" (or "kończy po 5" and colPeriod contains "5"),
            // don't cross out the boundary lesson itself when relative rule applies to lessons before/after it.
            val startBoundaryPeriod = extractArrivalPeriod(itemText)
            val endAfterBoundaryPeriod = extractFinishAfterPeriod(itemText)

            val candidatePairs = mutableListOf<Pair<Int, Int>>() // lessonIdx to score
            dayLessons.forEachIndexed { idx, lesson ->
                val matchesExplicit = lesson.period in explicitPeriods &&
                    !(startBoundaryPeriod != null && lesson.period == startBoundaryPeriod && explicitPeriods == setOf(startBoundaryPeriod)) &&
                    !(endAfterBoundaryPeriod != null && lesson.period == endAfterBoundaryPeriod && explicitPeriods == setOf(endAfterBoundaryPeriod))
                val matchesRelative = lesson.period in relativePeriods

                if (matchesExplicit || matchesRelative) {
                    val compatibility = scoreLessonCompatibility(lesson, item)
                    if (compatibility != Int.MIN_VALUE) {
                        candidatePairs.add(idx to compatibility)
                    }
                }
            }

            if (candidatePairs.isNotEmpty()) {
                // Within the same period, if multiple lessons exist and some match teacher/group strongly while others clash, keep the best matches per period
                val byPeriod = candidatePairs.groupBy { dayLessons[it.first].period }
                for ((_, pairsInPeriod) in byPeriod) {
                    val bestScore = pairsInPeriod.maxOf { it.second }
                    val chosen = if (pairsInPeriod.size > 1 && bestScore > 0) {
                        pairsInPeriod.filter { it.second == bestScore }
                    } else {
                        pairsInPeriod
                    }
                    for ((lessonIdx, _) in chosen) {
                        val effectiveItem = if (item.isCancellation || isCancellationText(itemText)) {
                            if (item.isCancellation) item else item.copy(isCancellation = true)
                        } else {
                            item
                        }
                        val existing = resolvedByIndex[lessonIdx]
                        if (existing == null || (!existing.isCancellation && effectiveItem.isCancellation)) {
                            resolvedByIndex[lessonIdx] = effectiveItem
                        }
                    }
                }
            }
        }

        // 2. Class-level late-arrival / early-dismissal rules from Informacje / Uwagi
        for (line in matchingAnnouncements) {
            if (!isCancellationText(line)) continue
            val affectedPeriods = detectBoundaryAffectedPeriods(line, dayLessons) +
                extractPeriodsFromCancellationText(line)
            if (affectedPeriods.isEmpty()) continue

            dayLessons.forEachIndexed { idx, lesson ->
                if (lesson.period in affectedPeriods) {
                    val lineGroup = detectGroupTag(line)
                    val lessonGroup = lesson.groupTag ?: detectGroupTag("${lesson.subject} ${lesson.rawText}")
                    if (lineGroup == null || lessonGroup == null || lineGroup == lessonGroup) {
                        if (resolvedByIndex[idx] == null) {
                            resolvedByIndex[idx] = SubstitutionItem(
                                id = "info_${dayIndex}_${lesson.period}_${line.hashCode()}",
                                dateLabel = snapshot.dateHeading,
                                className = className,
                                period = lesson.period.toString(),
                                subject = lesson.subject,
                                note = line,
                                groupTag = lineGroup,
                                isCancellation = true
                            )
                        }
                    }
                }
            }
        }

        if (resolvedByIndex.isEmpty()) {
            return ClassDaySubstitutionResolution()
        }

        val cancelledIndices = resolvedByIndex.filterValues { it.isCancellation }.keys
        val activeSubCount = resolvedByIndex.count { !it.value.isCancellation }
        val remainingLessons = dayLessons.filterIndexed { idx, _ -> idx !in cancelledIndices }

        val firstPeriod = dayLessons.minOf { it.period }
        val lastPeriod = dayLessons.maxOf { it.period }
        val comesLater = cancelledIndices.isNotEmpty() &&
            remainingLessons.isNotEmpty() &&
            remainingLessons.first().period > firstPeriod
        val endsEarlier = cancelledIndices.isNotEmpty() &&
            remainingLessons.isNotEmpty() &&
            remainingLessons.last().period < lastPeriod
        val allCancelled = cancelledIndices.isNotEmpty() && remainingLessons.isEmpty()

        val startTime = remainingLessons.firstOrNull()?.timeRange
            ?.split("-", "–")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        val endTime = remainingLessons.lastOrNull()?.timeRange
            ?.split("-", "–")?.lastOrNull()?.trim()?.takeIf { it.isNotBlank() }

        val summaryBanner = buildString {
            when {
                allCancelled -> append("Wszystkie lekcje odwołane (${cancelledIndices.size})")
                comesLater && endsEarlier -> append(
                    "Klasa przychodzi później (od ${remainingLessons.first().period} lek.${startTime?.let { " • $it" }.orEmpty()}) i kończy szybciej (po ${remainingLessons.last().period} lek.${endTime?.let { " • $it" }.orEmpty()})"
                )
                comesLater -> append(
                    "Klasa przychodzi później • początek od ${remainingLessons.first().period} lek.${startTime?.let { " ($it)" }.orEmpty()}"
                )
                endsEarlier -> append(
                    "Klasa kończy szybciej • koniec po ${remainingLessons.last().period} lek.${endTime?.let { " ($it)" }.orEmpty()}"
                )
                cancelledIndices.isNotEmpty() -> append("Odwołane lekcje: ${cancelledIndices.size}")
            }
            if (activeSubCount > 0) {
                if (isNotEmpty()) append(" • ")
                append("Zastępstwa: $activeSubCount")
            }
        }.ifBlank { null }

        return ClassDaySubstitutionResolution(
            matchByLessonIndex = resolvedByIndex,
            summaryBanner = summaryBanner,
            cancelledCount = cancelledIndices.size,
            activeSubCount = activeSubCount
        )
    }

    fun findTeacherSubstitutionForLesson(
        substitutions: List<SubstitutionItem>,
        dateHeading: String,
        lessonPeriod: Int,
        dayIndex: Int,
        todayWeekdayIndex: Int?
    ): SubstitutionItem? {
        return substitutions.firstOrNull { sub ->
            substitutionAppliesToDay(
                dateLabel = sub.dateLabel,
                fallbackDateHeading = dateHeading,
                dayIndex = dayIndex,
                todayWeekdayIndex = todayWeekdayIndex
            ) && lessonPeriod in parseSubstitutionPeriods(sub.period)
        }
    }

    private fun extractArrivalPeriod(text: String): Int? {
        val folded = foldPolishDiacritics(text).lowercase()
        val match = Regex(
            """(?:przychodzi|przychodza|zaczyna|zaczynaja|rozpoczyna|rozpoczynaja|zajecia\s+od|lekcje\s+od)\s+(?:pozniej\s*[-,:;]?\s*)?(?:na|od)\s+(?:lekcj[ei]?\s*(?:nr\.?\s*)?)?(\d{1,2})(?!\s*[:.]\d{2})\s*(?:\.|lekcj|lek\.?|godz|$|\b)"""
        ).find(folded)
        return match?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..16 }
    }

    private fun extractFinishAfterPeriod(text: String): Int? {
        val folded = foldPolishDiacritics(text).lowercase()
        val match = Regex(
            """(?:konczy|koncza|idzie\s+do\s+domu|wychodzi|zwolnion[aey]?)\s+(?:szybciej\s*[-,:;]?\s*|wczesniej\s*[-,:;]?\s*)?po\s+(?:lekcj[ei]?\s*(?:nr\.?\s*)?)?(\d{1,2})(?!\s*[:.]\d{2})\s*(?:\.|lekcj|lek\.?|godz|$|\b)"""
        ).find(folded)
        return match?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..16 }
    }

    private fun detectBoundaryAffectedPeriods(text: String, dayLessons: List<LessonEntry>): Set<Int> {
        if (text.isBlank() || dayLessons.isEmpty()) return emptySet()
        val folded = foldPolishDiacritics(text).lowercase()
        val affected = linkedSetOf<Int>()

        // 1. Starts at period N -> lessons < N are cancelled
        extractArrivalPeriod(text)?.let { startPeriod ->
            dayLessons.filter { it.period < startPeriod }.forEach { affected.add(it.period) }
        }

        // 2. Starts at time HH:MM -> lessons starting strictly before HH:MM are cancelled
        Regex(
            """(?:przychodzi|przychodza|zaczyna|zaczynaja|rozpoczyna|rozpoczynaja)\s+(?:pozniej\s*[-,:;]?\s*)?(?:na|od)\s+(?:godz\.?\s*)?(\d{1,2}[:.]\d{2})"""
        ).find(folded)?.groupValues?.get(1)?.let { hm ->
            parseTimeToMinutes(hm)?.let { targetMin ->
                dayLessons.filter { lesson ->
                    val startMin = lesson.timeRange.split("-", "–").firstOrNull()?.let(::parseTimeToMinutes)
                    startMin != null && startMin < targetMin
                }.forEach { affected.add(it.period) }
            }
        }

        // 3. Finishes after period N -> lessons > N are cancelled
        extractFinishAfterPeriod(text)?.let { afterPeriod ->
            dayLessons.filter { it.period > afterPeriod }.forEach { affected.add(it.period) }
        }

        // 4. Dismissed / finishes from period N onwards -> lessons >= N are cancelled
        Regex(
            """(?:konczy|koncza|zwolnion[aey]?|odwolan[aey]?)\s+(?:szybciej\s*[-,:;]?\s*|wczesniej\s*[-,:;]?\s*)?od\s+(?:lekcj[ei]?\s*(?:nr\.?\s*)?)?(\d{1,2})(?!\s*[:.]\d{2})\s*(?:\.|lekcj|lek\.?|godz|$|\b)"""
        ).find(folded)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..16 }?.let { fromPeriod ->
            dayLessons.filter { it.period >= fromPeriod }.forEach { affected.add(it.period) }
        }

        // 5. Finishes at time HH:MM -> lessons starting >= HH:MM are cancelled
        Regex(
            """(?:konczy|koncza|zwolnion[aey]?|idzie\s+do\s+domu)\s+(?:szybciej\s*[-,:;]?\s*|wczesniej\s*[-,:;]?\s*)?(?:o|od|po)\s+(?:godz\.?\s*)?(\d{1,2}[:.]\d{2})"""
        ).find(folded)?.groupValues?.get(1)?.let { hm ->
            parseTimeToMinutes(hm)?.let { targetMin ->
                dayLessons.filter { lesson ->
                    val startMin = lesson.timeRange.split("-", "–").firstOrNull()?.let(::parseTimeToMinutes)
                    startMin != null && startMin >= targetMin
                }.forEach { affected.add(it.period) }
            }
        }

        // 6. Generic "klasa przychodzi później" / "klasa kończy szybciej" without period/time numbers
        if (affected.isEmpty() && extractPeriodsFromCancellationText(text).isEmpty()) {
            if (folded.contains("przychodzi pozniej") || folded.contains("przychodza pozniej")) {
                dayLessons.minOfOrNull { it.period }?.let { affected.add(it) }
            }
            if (folded.contains("konczy szybciej") || folded.contains("koncza szybciej") ||
                folded.contains("konczy wczesniej") || folded.contains("koncza wczesniej")
            ) {
                dayLessons.maxOfOrNull { it.period }?.let { affected.add(it) }
            }
        }

        return affected
    }

    private fun extractPeriodsFromCancellationText(text: String): Set<Int> {
        val folded = foldPolishDiacritics(text).lowercase()
        val result = linkedSetOf<Int>()
        // Do not interpret "przychodzi na 3 lekcję" or "kończy po 5 lekcji" as cancelled periods 3 or 5
        val sanitized = folded
            .replace(Regex("""(?:przychodzi|przychodza|zaczyna|zaczynaja|rozpoczyna|rozpoczynaja)\s+(?:pozniej\s*)?(?:na|od)\s+(?:lekcj[ei]?\s*)?\d{1,2}\s*\.?\s*(?:lekcj[aeyi]?|godz)?"""), " ")
            .replace(Regex("""(?:konczy|koncza|zwolnion[aey]?)\s+(?:szybciej\s*|wczesniej\s*)?po\s+(?:lekcj[ei]?\s*)?\d{1,2}\s*\.?\s*(?:lekcj[aeyi]?|godz)?"""), " ")

        val listPattern = """\d{1,2}(?:\s*(?:[-–,;]|i)\s*\d{1,2})*"""
        Regex("""($listPattern)\s*\.?\s*(?:lekcj[aeyi]?|lek\.|godzin[aeyi]?)""").findAll(sanitized).forEach { m ->
            result.addAll(parseSubstitutionPeriods(m.groupValues[1]))
        }
        Regex("""(?:lekcj[aeyi]?|lek\.?|godz\.?)\s*(?:nr\.?\s*)?($listPattern)""").findAll(sanitized).forEach { m ->
            result.addAll(parseSubstitutionPeriods(m.groupValues[1]))
        }
        return result
    }

    private fun parseTimeToMinutes(raw: String): Int? {
        val clean = raw.trim().replace(".", ":")
        val pieces = clean.split(":")
        if (pieces.size != 2) return null
        val h = pieces[0].filter { it.isDigit() }.toIntOrNull() ?: return null
        val m = pieces[1].filter { it.isDigit() }.toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun scoreLessonCompatibility(lesson: LessonEntry, sub: SubstitutionItem): Int {
        val subGroup = sub.groupTag ?: detectGroupTag("${sub.className} ${sub.subject} ${sub.note}")
        val lessonGroup = lesson.groupTag ?: detectGroupTag("${lesson.subject} ${lesson.rawText}")
        if (subGroup != null && lessonGroup != null && subGroup != lessonGroup) {
            return Int.MIN_VALUE
        }

        var score = 0
        if (subGroup != null && lessonGroup == subGroup) {
            score += 10
        }
        score += compareTeacherTokens(sub.absentTeacher, lesson.teacher)

        if (sub.subject.isNotBlank() && lesson.subject.isNotBlank()) {
            val subSubj = foldPolishDiacritics(sub.subject).lowercase()
            val lesSubj = foldPolishDiacritics(lesson.subject).lowercase()
            val overlap = lesSubj.split(Regex("""[\s,./-]+"""))
                .filter { it.length >= 4 }
                .any { subSubj.contains(it) }
            if (overlap) score += 4
        }
        return score
    }

    private fun compareTeacherTokens(subTeacher: String, lessonTeacher: String): Int {
        val cleanSub = subTeacher.trim()
        val cleanLesson = lessonTeacher.trim()
        if (cleanSub.isBlank() || cleanLesson.isBlank()) return 0

        if (matchesTeacherToken(cleanSub, cleanLesson) || matchesTeacherToken(cleanLesson, cleanSub)) {
            return 8
        }

        val foldedSub = foldPolishDiacritics(cleanSub).lowercase()
        val foldedLesson = foldPolishDiacritics(cleanLesson).lowercase()

        val lessonWords = foldedLesson.split(Regex("""[\s,.;:/()-]+""")).filter { it.length >= 3 }
        if (lessonWords.any { w -> Regex("""(?<![a-z0-9])${Regex.escape(w)}(?![a-z0-9])""").containsMatchIn(foldedSub) }) {
            return 6
        }

        // Check abbreviation vs full name (e.g. lesson = "JK", sub = "Jan Kowalski" or "Kowalski Jan")
        val abbrLetters = foldedLesson.filter { it.isLetter() }
        val subWords = foldedSub.split(Regex("""[\s,.;:/()-]+""")).filter { it.isNotBlank() }
        if (abbrLetters.length in 2..3 && subWords.size >= 2) {
            val initials = subWords.map { it.first() }.joinToString("")
            if (initials == abbrLetters || initials.reversed() == abbrLetters) {
                return 6
            }
        }
        // Prefix of surname (e.g. lesson = "KOW", sub = "Jan Kowalski")
        if (abbrLetters.length in 2..4 && subWords.any { it.length >= 4 && it.startsWith(abbrLetters) }) {
            return 4
        }

        return -3
    }

    fun extractPrimaryClassCode(raw: String): String {
        val norm = normalizeClassToken(raw)
        return primaryClassCodeRegex.find(norm)?.groupValues?.get(1) ?: norm
    }

    fun normalizeClassToken(raw: String): String {
        return raw.uppercase()
            .replace("KLASA", "")
            .replace("KL.", "")
            .replace(Regex("""(\d)\s+([A-ZĄĆĘŁŃÓŚŹŻ])"""), "$1$2")
            .trim()
    }

    fun filterLessonsByVisibleSchools(
        lessons: List<LessonEntry>,
        hiddenSchoolIds: Set<String>,
        fallbackSchoolId: String = UserPreferences.PRIMARY_SCHOOL_ID
    ): List<LessonEntry> {
        if (hiddenSchoolIds.isEmpty()) return lessons
        return lessons.filter { lesson ->
            val effectiveId = lesson.schoolId.ifBlank { fallbackSchoolId }
            effectiveId !in hiddenSchoolIds
        }
    }
}
