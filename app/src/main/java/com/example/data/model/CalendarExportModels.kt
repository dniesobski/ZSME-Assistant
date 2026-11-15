package com.example.data.model

enum class CalendarExportBackend(val label: String) {
    GOOGLE_CLOUD_OAUTH("Konto Google (Chmura)"),
    DEVICE_CALENDAR_PROVIDER("Kalendarz na urządzeniu")
}

enum class CalendarRecurrenceMode(
    val label: String,
    val subtitle: String
) {
    UNTIL_END_OF_SCHOOL_YEAR(
        label = "Do końca roku szkolnego (30.06)",
        subtitle = "Powtarzaj co tydzień aż do wakacji"
    ),
    NEXT_16_WEEKS(
        label = "Przez 16 tygodni (semestr)",
        subtitle = "Powtarzaj co tydzień przez ok. 4 miesiące"
    ),
    SINGLE_WEEK(
        label = "Tylko 1 tydzień",
        subtitle = "Pojedyncze wpisy bez powtarzania"
    )
}

enum class CalendarStartWeekMode(val label: String) {
    CURRENT_WEEK("Bieżący tydzień"),
    NEXT_MONDAY("Od przyszłego poniedziałku")
}

data class GoogleCalendarAccountInfo(
    val email: String,
    val displayName: String = "",
    val accessToken: String = ""
)

data class TargetCalendarItem(
    val id: String,
    val name: String,
    val accountName: String,
    val accountType: String = "com.google",
    val colorHex: Int = 0xFF1A73E8.toInt(),
    val isPrimary: Boolean = false,
    val accessRole: String = "writer",
    val backend: CalendarExportBackend
)

data class CalendarExportOptions(
    val recurrenceMode: CalendarRecurrenceMode = CalendarRecurrenceMode.UNTIL_END_OF_SCHOOL_YEAR,
    val startWeekMode: CalendarStartWeekMode = CalendarStartWeekMode.CURRENT_WEEK,
    val reminderMinutes: Int? = 10,
    val replacePreviousExports: Boolean = true
)

data class CalendarEventSpec(
    val dayIndex: Int,
    val title: String,
    val location: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val startIso: String,
    val endIso: String,
    val rrule: String?,
    val reminderMinutes: Int?
)

data class CalendarExportResult(
    val exportedCount: Int,
    val deletedOldCount: Int,
    val calendarName: String,
    val accountName: String,
    val backend: CalendarExportBackend
)

data class CalendarColorOption(
    val label: String,
    val colorHex: Int
)

object CalendarColorOptions {
    val presets: List<CalendarColorOption> = listOf(
        CalendarColorOption("Niebieski", 0xFF1A73E8.toInt()),
        CalendarColorOption("Szmaragdowy", 0xFF0B8043.toInt()),
        CalendarColorOption("Fioletowy", 0xFF8E24AA.toInt()),
        CalendarColorOption("Koralowy", 0xFFE67C73.toInt()),
        CalendarColorOption("Bursztynowy", 0xFFF4511E.toInt()),
        CalendarColorOption("Grafitowy", 0xFF616161.toInt())
    )
}
