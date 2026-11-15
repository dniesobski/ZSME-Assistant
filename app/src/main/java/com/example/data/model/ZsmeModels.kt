package com.example.data.model

import kotlinx.serialization.Serializable

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

    private fun foldPolishDiacritics(input: String): String {
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
