package com.example.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.model.CalendarEventSpec
import com.example.data.model.CalendarExportBackend
import com.example.data.model.CalendarExportResult
import com.example.data.model.TargetCalendarItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeviceCalendarRepository {

    fun hasCalendarPermissions(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    suspend fun listWritableCalendars(context: Context): Result<List<TargetCalendarItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!hasCalendarPermissions(context)) {
                    error("Brak uprawnień do odczytu i zapisu kalendarza.")
                }

                val projection = arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                    CalendarContract.Calendars.IS_PRIMARY,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    CalendarContract.Calendars.DELETED
                )

                val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
                val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

                val items = mutableListOf<TargetCalendarItem>()
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idxId = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                    val idxName = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                    val idxAccName = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                    val idxAccType = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                    val idxColor = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
                    val idxPrimary = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                    val idxAccess = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                    val idxDeleted = cursor.getColumnIndex(CalendarContract.Calendars.DELETED)

                    while (cursor.moveToNext()) {
                        val isDeleted = idxDeleted >= 0 && cursor.getInt(idxDeleted) == 1
                        if (isDeleted) continue
                        val id = if (idxId >= 0) cursor.getLong(idxId).toString() else continue
                        val displayName = if (idxName >= 0) cursor.getString(idxName).orEmpty() else ""
                        val accountName = if (idxAccName >= 0) cursor.getString(idxAccName).orEmpty() else ""
                        val accountType = if (idxAccType >= 0) cursor.getString(idxAccType).orEmpty() else ""
                        val color = if (idxColor >= 0) cursor.getInt(idxColor) else 0xFF1A73E8.toInt()
                        val isPrimary = idxPrimary >= 0 && cursor.getInt(idxPrimary) == 1
                        val accessLevel = if (idxAccess >= 0) cursor.getInt(idxAccess) else 0

                        items += TargetCalendarItem(
                            id = id,
                            name = displayName.ifBlank { accountName.ifBlank { "Kalendarz $id" } },
                            accountName = accountName.ifBlank { "Lokalny na urządzeniu" },
                            accountType = accountType,
                            colorHex = color,
                            isPrimary = isPrimary,
                            accessRole = if (accessLevel >= CalendarContract.Calendars.CAL_ACCESS_OWNER) "owner" else "writer",
                            backend = CalendarExportBackend.DEVICE_CALENDAR_PROVIDER
                        )
                    }
                }

                items.sortedWith(
                    compareByDescending<TargetCalendarItem> { it.accountType == "com.google" }
                        .thenBy { it.accountName.lowercase() }
                        .thenByDescending { it.isPrimary }
                        .thenBy { it.name.lowercase() }
                )
            }
        }

    fun resolveSelection(
        calendars: List<TargetCalendarItem>,
        preferredAccount: String?,
        currentSelectedAccount: String?,
        currentSelectedCalendarId: String?
    ): Pair<String?, String?> {
        if (calendars.isEmpty()) return null to null
        val accounts = calendars.map { it.accountName }.distinct()
        val matchedPreferred = preferredAccount?.trim()?.takeIf { it.isNotBlank() }?.let { pref ->
            accounts.firstOrNull { it.equals(pref, ignoreCase = true) }
        }
        val chosenAccount = matchedPreferred
            ?: currentSelectedAccount?.takeIf { it in accounts }
            ?: accounts.firstOrNull()
        val accountCalendars = calendars.filter { it.accountName == chosenAccount }
        val chosenCalendarId = currentSelectedCalendarId
            ?.takeIf { id -> accountCalendars.any { it.id == id } }
            ?: accountCalendars.firstOrNull { it.isPrimary }?.id
            ?: accountCalendars.firstOrNull()?.id
        return chosenAccount to chosenCalendarId
    }

    suspend fun createLocalDeviceCalendar(
        context: Context,
        name: String,
        colorHex: Int
    ): Result<TargetCalendarItem> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasCalendarPermissions(context)) {
                error("Brak uprawnień do kalendarza urządzenia.")
            }
            val cleanName = name.trim().ifBlank { "Plan zajęć ZSME" }
            val localAccountName = "ZSME Plan (lokalny)"
            val accountType = CalendarContract.ACCOUNT_TYPE_LOCAL

            val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, localAccountName)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, accountType)
                .build()

            val values = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, localAccountName)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, accountType)
                put(CalendarContract.Calendars.NAME, cleanName)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, cleanName)
                put(CalendarContract.Calendars.CALENDAR_COLOR, colorHex)
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, localAccountName)
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, CalendarExportPlanner.WARSAW_TIME_ZONE_ID)
            }

            val inserted: Uri = context.contentResolver.insert(uri, values)
                ?: error("System Android nie zezwolił na utworzenie lokalnego kalendarza.")
            val newId = ContentUris.parseId(inserted).toString()

            TargetCalendarItem(
                id = newId,
                name = cleanName,
                accountName = localAccountName,
                accountType = accountType,
                colorHex = colorHex,
                isPrimary = false,
                accessRole = "owner",
                backend = CalendarExportBackend.DEVICE_CALENDAR_PROVIDER
            )
        }
    }

    suspend fun exportEvents(
        context: Context,
        calendar: TargetCalendarItem,
        events: List<CalendarEventSpec>,
        replacePrevious: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<CalendarExportResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasCalendarPermissions(context)) {
                error("Brak uprawnień do zapisu w kalendarzu.")
            }
            if (events.isEmpty()) {
                error("Brak lekcji do wyeksportowania w bieżącym planie.")
            }
            val calendarIdLong = calendar.id.toLongOrNull()
                ?: error("Nieprawidłowy identyfikator kalendarza urządzenia.")

            var deletedCount = 0
            if (replacePrevious) {
                val where = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} LIKE ?"
                val args = arrayOf(calendarIdLong.toString(), "%${CalendarExportPlanner.EXPORT_MARKER}%")
                deletedCount = runCatching {
                    context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, where, args)
                }.getOrDefault(0)
            }

            var exportedCount = 0
            val total = events.size
            for ((index, spec) in events.withIndex()) {
                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarIdLong)
                    put(CalendarContract.Events.TITLE, spec.title)
                    put(CalendarContract.Events.EVENT_LOCATION, spec.location)
                    put(CalendarContract.Events.DESCRIPTION, spec.description)
                    put(CalendarContract.Events.DTSTART, spec.startMillis)
                    put(CalendarContract.Events.EVENT_TIMEZONE, CalendarExportPlanner.WARSAW_TIME_ZONE_ID)
                    if (!spec.rrule.isNullOrBlank()) {
                        val durationMin = ((spec.endMillis - spec.startMillis) / 60_000L).coerceAtLeast(15L)
                        put(CalendarContract.Events.RRULE, spec.rrule.removePrefix("RRULE:"))
                        put(CalendarContract.Events.DURATION, "PT${durationMin}M")
                    } else {
                        put(CalendarContract.Events.DTEND, spec.endMillis)
                    }
                    put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                    put(CalendarContract.Events.HAS_ALARM, if (spec.reminderMinutes != null) 1 else 0)
                }

                val insertedUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                if (insertedUri != null) {
                    val eventId = runCatching { ContentUris.parseId(insertedUri) }.getOrNull()
                    if (eventId != null && spec.reminderMinutes != null && spec.reminderMinutes >= 0) {
                        val reminderValues = ContentValues().apply {
                            put(CalendarContract.Reminders.EVENT_ID, eventId)
                            put(CalendarContract.Reminders.MINUTES, spec.reminderMinutes)
                            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        }
                        runCatching {
                            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                        }
                    }
                    exportedCount++
                }
                onProgress(index + 1, total)
            }

            CalendarExportResult(
                exportedCount = exportedCount,
                deletedOldCount = deletedCount,
                calendarName = calendar.name,
                accountName = calendar.accountName,
                backend = CalendarExportBackend.DEVICE_CALENDAR_PROVIDER
            )
        }
    }
}
