package com.example.data.calendar

import android.graphics.Color
import com.example.data.model.CalendarEventSpec
import com.example.data.model.CalendarExportBackend
import com.example.data.model.CalendarExportResult
import com.example.data.model.GoogleCalendarAccountInfo
import com.example.data.model.TargetCalendarItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GoogleCalendarApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Serializable
    private data class UserInfoDto(
        val email: String = "",
        val name: String = ""
    )

    @Serializable
    private data class CalendarListResponseDto(
        val items: List<CalendarListItemDto> = emptyList(),
        val nextPageToken: String? = null
    )

    @Serializable
    private data class CalendarListItemDto(
        val id: String = "",
        val summary: String = "",
        val summaryOverride: String? = null,
        val primary: Boolean = false,
        val backgroundColor: String? = null,
        val accessRole: String = ""
    )

    @Serializable
    private data class CreateCalendarRequestDto(
        val summary: String,
        val description: String,
        val timeZone: String = CalendarExportPlanner.WARSAW_TIME_ZONE_ID
    )

    @Serializable
    private data class CreateCalendarResponseDto(
        val id: String = "",
        val summary: String = ""
    )

    @Serializable
    private data class PatchCalendarListColorDto(
        val backgroundColor: String,
        val foregroundColor: String = "#ffffff"
    )

    @Serializable
    private data class EventsListResponseDto(
        val items: List<EventIdDto> = emptyList()
    )

    @Serializable
    private data class EventIdDto(
        val id: String = ""
    )

    @Serializable
    private data class EventDateTimeDto(
        val dateTime: String,
        val timeZone: String = CalendarExportPlanner.WARSAW_TIME_ZONE_ID
    )

    @Serializable
    private data class ExtendedPropertiesDto(
        @SerialName("private") val privateProps: Map<String, String>
    )

    @Serializable
    private data class ReminderOverrideDto(
        val method: String = "popup",
        val minutes: Int
    )

    @Serializable
    private data class EventRemindersDto(
        val useDefault: Boolean,
        val overrides: List<ReminderOverrideDto>? = null
    )

    @Serializable
    private data class InsertEventRequestDto(
        val summary: String,
        val location: String? = null,
        val description: String,
        val start: EventDateTimeDto,
        val end: EventDateTimeDto,
        val recurrence: List<String>? = null,
        val extendedProperties: ExtendedPropertiesDto,
        val reminders: EventRemindersDto
    )

    suspend fun fetchAccountInfo(accessToken: String): Result<GoogleCalendarAccountInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("https://www.googleapis.com/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        error("Nie udało się pobrać danych konta Google (HTTP ${resp.code}).")
                    }
                    val dto = json.decodeFromString<UserInfoDto>(body)
                    GoogleCalendarAccountInfo(
                        email = dto.email,
                        displayName = dto.name,
                        accessToken = accessToken
                    )
                }
            }
        }

    suspend fun listWritableCalendars(
        accessToken: String,
        accountEmail: String
    ): Result<List<TargetCalendarItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val collected = mutableListOf<TargetCalendarItem>()
            var pageToken: String? = null
            do {
                val url = buildString {
                    append("https://www.googleapis.com/calendar/v3/users/me/calendarList?minAccessRole=writer&maxResults=100")
                    if (!pageToken.isNullOrBlank()) {
                        append("&pageToken=")
                        append(urlEncode(pageToken!!))
                    }
                }
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                val page = client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        error("Błąd pobierania listy kalendarzy Google (HTTP ${resp.code}): ${extractApiError(body)}")
                    }
                    json.decodeFromString<CalendarListResponseDto>(body)
                }
                for (item in page.items) {
                    if (item.id.isBlank()) continue
                    val displayName = item.summaryOverride?.takeIf { it.isNotBlank() }
                        ?: item.summary.ifBlank { item.id }
                    val parsedColor = parseColorHex(item.backgroundColor) ?: 0xFF1A73E8.toInt()
                    collected += TargetCalendarItem(
                        id = item.id,
                        name = displayName,
                        accountName = accountEmail,
                        accountType = "com.google",
                        colorHex = parsedColor,
                        isPrimary = item.primary,
                        accessRole = item.accessRole,
                        backend = CalendarExportBackend.GOOGLE_CLOUD_OAUTH
                    )
                }
                pageToken = page.nextPageToken
            } while (!pageToken.isNullOrBlank())

            collected.sortedWith(
                compareByDescending<TargetCalendarItem> { it.isPrimary }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    suspend fun createCalendar(
        accessToken: String,
        summary: String,
        description: String,
        accountEmail: String,
        colorHex: Int
    ): Result<TargetCalendarItem> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = summary.trim().ifBlank { "Plan zajęć ZSME" }
            val payload = json.encodeToString(
                CreateCalendarRequestDto(
                    summary = cleanName,
                    description = description.trim()
                )
            )
            val req = Request.Builder()
                .url("https://www.googleapis.com/calendar/v3/calendars")
                .header("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            val created = client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Nie udało się utworzyć kalendarza (HTTP ${resp.code}): ${extractApiError(body)}")
                }
                json.decodeFromString<CreateCalendarResponseDto>(body)
            }

            if (created.id.isBlank()) {
                error("Google Calendar nie zwrócił ID nowego kalendarza.")
            }

            // Apply chosen color on the user's calendarList entry (best-effort)
            runCatching {
                val hexString = String.format("#%06x", 0xFFFFFF and colorHex)
                val patchBody = json.encodeToString(PatchCalendarListColorDto(backgroundColor = hexString))
                val patchReq = Request.Builder()
                    .url("https://www.googleapis.com/calendar/v3/users/me/calendarList/${urlEncode(created.id)}?colorRgbFormat=true")
                    .header("Authorization", "Bearer $accessToken")
                    .patch(patchBody.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(patchReq).execute().close()
            }

            TargetCalendarItem(
                id = created.id,
                name = created.summary.ifBlank { cleanName },
                accountName = accountEmail,
                accountType = "com.google",
                colorHex = colorHex,
                isPrimary = false,
                accessRole = "owner",
                backend = CalendarExportBackend.GOOGLE_CLOUD_OAUTH
            )
        }
    }

    suspend fun exportEvents(
        accessToken: String,
        calendar: TargetCalendarItem,
        events: List<CalendarEventSpec>,
        replacePrevious: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<CalendarExportResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (events.isEmpty()) {
                error("Brak lekcji do wyeksportowania w bieżącym planie.")
            }
            val encodedCalId = urlEncode(calendar.id)

            var deletedCount = 0
            if (replacePrevious) {
                deletedCount = deletePreviouslyExportedEvents(accessToken, encodedCalId)
            }

            var exportedCount = 0
            val total = events.size
            for ((index, spec) in events.withIndex()) {
                val reminders = if (spec.reminderMinutes != null && spec.reminderMinutes >= 0) {
                    EventRemindersDto(
                        useDefault = false,
                        overrides = listOf(ReminderOverrideDto(method = "popup", minutes = spec.reminderMinutes))
                    )
                } else {
                    EventRemindersDto(useDefault = false, overrides = emptyList())
                }

                val dto = InsertEventRequestDto(
                    summary = spec.title,
                    location = spec.location.ifBlank { null },
                    description = spec.description,
                    start = EventDateTimeDto(dateTime = spec.startIso),
                    end = EventDateTimeDto(dateTime = spec.endIso),
                    recurrence = spec.rrule?.let { listOf(it) },
                    extendedProperties = ExtendedPropertiesDto(
                        privateProps = mapOf(
                            CalendarExportPlanner.EXTENDED_PROP_KEY to CalendarExportPlanner.EXTENDED_PROP_VALUE
                        )
                    ),
                    reminders = reminders
                )
                val bodyJson = json.encodeToString(dto)
                val req = Request.Builder()
                    .url("https://www.googleapis.com/calendar/v3/calendars/$encodedCalId/events")
                    .header("Authorization", "Bearer $accessToken")
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        error("Błąd zapisu lekcji „${spec.title}” (HTTP ${resp.code}): ${extractApiError(respBody)}")
                    }
                }
                exportedCount++
                onProgress(index + 1, total)
            }

            CalendarExportResult(
                exportedCount = exportedCount,
                deletedOldCount = deletedCount,
                calendarName = calendar.name,
                accountName = calendar.accountName,
                backend = CalendarExportBackend.GOOGLE_CLOUD_OAUTH
            )
        }
    }

    private fun deletePreviouslyExportedEvents(
        accessToken: String,
        encodedCalendarId: String
    ): Int {
        val propFilter = urlEncode("${CalendarExportPlanner.EXTENDED_PROP_KEY}=${CalendarExportPlanner.EXTENDED_PROP_VALUE}")
        val listUrl = "https://www.googleapis.com/calendar/v3/calendars/$encodedCalendarId/events" +
            "?privateExtendedProperty=$propFilter&maxResults=250&singleEvents=false"
        val req = Request.Builder()
            .url(listUrl)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val existingIds = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string().orEmpty()
                json.decodeFromString<EventsListResponseDto>(body).items.map { it.id }.filter { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())

        var deleted = 0
        for (eventId in existingIds) {
            val delReq = Request.Builder()
                .url("https://www.googleapis.com/calendar/v3/calendars/$encodedCalendarId/events/${urlEncode(eventId)}")
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            runCatching {
                client.newCall(delReq).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 404 || resp.code == 410) {
                        deleted++
                    }
                }
            }
        }
        return deleted
    }

    private fun parseColorHex(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Color.parseColor(raw.trim()) }.getOrNull()
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun extractApiError(body: String): String {
        if (body.isBlank()) return "brak szczegółów"
        val msgMatch = Regex(""""message"\s*:\s*"([^"]+)"""").find(body)
        return msgMatch?.groupValues?.getOrNull(1)?.take(120) ?: body.take(120)
    }
}
