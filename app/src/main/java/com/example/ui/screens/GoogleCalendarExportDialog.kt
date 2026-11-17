package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.calendar.CalendarExportPlanner
import com.example.data.calendar.DeviceCalendarRepository
import com.example.data.model.CalendarColorOptions
import com.example.data.model.CalendarExportOptions
import com.example.data.model.CalendarExportResult
import com.example.data.model.CalendarRecurrenceMode
import com.example.data.model.CalendarStartWeekMode
import com.example.data.model.ClassTimetable
import com.example.data.model.LessonEntry
import com.example.data.model.TargetCalendarItem
import com.example.data.model.TeacherSchoolConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoogleCalendarExportDialog(
    timetable: ClassTimetable,
    filteredDayLessons: Map<Int, List<LessonEntry>>,
    isTeacherMode: Boolean,
    teacherSchools: List<TeacherSchoolConfig>,
    onExportCompleted: (CalendarExportResult) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasDeviceCalPermission by remember {
        mutableStateOf(DeviceCalendarRepository.hasCalendarPermissions(context))
    }
    var deviceCalendars by remember { mutableStateOf<List<TargetCalendarItem>>(emptyList()) }
    var selectedDeviceAccount by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDeviceCalendarId by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoadingDevice by remember { mutableStateOf(false) }
    var deviceError by remember { mutableStateOf<String?>(null) }

    var showCreateCalendarForm by remember { mutableStateOf(false) }
    var newCalendarName by remember(timetable.classUrl, isTeacherMode) {
        mutableStateOf(
            CalendarExportPlanner.suggestedCalendarName(
                timetable = timetable,
                isTeacherMode = isTeacherMode,
                teacherSchools = teacherSchools
            )
        )
    }
    var newCalendarColorHex by remember { mutableIntStateOf(CalendarColorOptions.presets.first().colorHex) }
    var isCreatingCalendar by remember { mutableStateOf(false) }

    var recurrenceMode by remember { mutableStateOf(CalendarRecurrenceMode.UNTIL_END_OF_SCHOOL_YEAR) }
    var startWeekMode by remember {
        mutableStateOf(CalendarExportPlanner.defaultStartWeekModeForTime())
    }
    var addReminder by remember { mutableStateOf(true) }
    var replacePrevious by remember { mutableStateOf(true) }

    var isExporting by remember { mutableStateOf(false) }
    var exportDoneCount by remember { mutableIntStateOf(0) }
    var exportTotalCount by remember { mutableIntStateOf(0) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var exportResult by remember { mutableStateOf<CalendarExportResult?>(null) }

    val exportOptions = remember(recurrenceMode, startWeekMode, addReminder, replacePrevious) {
        CalendarExportOptions(
            recurrenceMode = recurrenceMode,
            startWeekMode = startWeekMode,
            reminderMinutes = if (addReminder) 10 else null,
            replacePreviousExports = replacePrevious
        )
    }

    val eventSpecs = remember(timetable, filteredDayLessons, exportOptions, isTeacherMode, teacherSchools) {
        CalendarExportPlanner.buildEventSpecs(
            timetable = timetable,
            filteredDayLessons = filteredDayLessons,
            options = exportOptions,
            isTeacherMode = isTeacherMode,
            hasMultipleSchools = isTeacherMode && teacherSchools.size > 1
        )
    }

    fun refreshDeviceCalendars() {
        scope.launch {
            isLoadingDevice = true
            deviceError = null
            DeviceCalendarRepository.listWritableCalendars(context).onSuccess { list ->
                deviceCalendars = list
                val (resolvedAccount, resolvedCalId) = DeviceCalendarRepository.resolveSelection(
                    calendars = list,
                    preferredAccount = null,
                    currentSelectedAccount = selectedDeviceAccount,
                    currentSelectedCalendarId = selectedDeviceCalendarId
                )
                selectedDeviceAccount = resolvedAccount
                selectedDeviceCalendarId = resolvedCalId
            }.onFailure { err ->
                deviceError = err.message ?: "Nie udało się odczytać kalendarzy z urządzenia."
            }
            isLoadingDevice = false
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        hasDeviceCalPermission = granted
        if (granted) {
            refreshDeviceCalendars()
        } else {
            deviceError = "Przyznaj uprawnienia do kalendarza, aby wybrać konto Google z telefonu i zapisać plan."
        }
    }

    LaunchedEffect(hasDeviceCalPermission) {
        if (hasDeviceCalPermission && deviceCalendars.isEmpty()) {
            refreshDeviceCalendars()
        }
    }

    val googleAccounts = remember(deviceCalendars) {
        val googleOnly = deviceCalendars.filter { it.accountType == "com.google" }
        val source = if (googleOnly.isNotEmpty()) googleOnly else deviceCalendars
        source.map { it.accountName to it.accountType }.distinct()
    }

    val activeAccountCalendars = remember(deviceCalendars, selectedDeviceAccount) {
        deviceCalendars.filter { it.accountName == selectedDeviceAccount }
    }

    val selectedCalendar = remember(deviceCalendars, selectedDeviceCalendarId) {
        deviceCalendars.firstOrNull { it.id == selectedDeviceCalendarId }
    }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Filled.EventAvailable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Eksport do Kalendarza Google",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (exportResult != null) {
                    val done = exportResult!!
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Wyeksportowano ${done.exportedCount} zajęć!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "Konto: ${done.accountName}\nKalendarz: ${done.calendarName}" +
                                    if (done.deletedOldCount > 0) "\nZastąpiono poprzednie wpisy: ${done.deletedOldCount}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            OutlinedButton(
                                onClick = { openGoogleCalendarApp(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Otwórz Kalendarz")
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gotowe do eksportu: ${eventSpecs.size} zajęć (Pn–Pt)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = if (isTeacherMode) {
                                        "Eksport obejmuje widoczne placówki z Twojego planu."
                                    } else {
                                        "Eksport uwzględnia wybraną klasę i grupę."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Text(
                        text = "1. Konto Google z telefonu",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (!hasDeviceCalPermission) {
                        Text(
                            text = "Przyznaj aplikacji dostęp do kalendarzy, aby wybrać konto Google zsynchronizowane na telefonie.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                calendarPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("grant_calendar_permission_button")
                        ) {
                            Text("Zezwól na dostęp do kalendarzy")
                        }
                    } else if (isLoadingDevice) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Wczytuję kalendarze Google z telefonu...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        if (googleAccounts.isEmpty()) {
                            Text(
                                text = "Nie wykryto zsynchronizowanych kalendarzy Google na telefonie. Upewnij się, że synchronizacja w aplikacji Kalendarz Google jest włączona, lub utwórz kalendarz poniżej.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                googleAccounts.forEach { (accName, accType) ->
                                    FilterChip(
                                        selected = selectedDeviceAccount == accName,
                                        onClick = {
                                            selectedDeviceAccount = accName
                                            val firstCal = deviceCalendars.firstOrNull { it.accountName == accName }
                                            selectedDeviceCalendarId = deviceCalendars
                                                .firstOrNull { it.accountName == accName && it.isPrimary }?.id
                                                ?: firstCal?.id
                                        },
                                        label = {
                                            val badge = if (accType == "com.google") "Google: " else ""
                                            Text("$badge$accName", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.AccountCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    )
                                }
                            }
                        }
                    }

                    if (deviceError != null) {
                        Text(
                            text = deviceError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (hasDeviceCalPermission) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. Wybierz kalendarz z telefonu",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { refreshDeviceCalendars() },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Odśwież listę kalendarzy",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (activeAccountCalendars.isEmpty()) {
                            Text(
                                text = "Brak zapisywalnych kalendarzy dla wybranego konta — utwórz lokalny kalendarz poniżej.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                activeAccountCalendars.forEach { cal ->
                                    val isSelected = selectedCalendar?.id == cal.id
                                    Surface(
                                        onClick = { selectedDeviceCalendarId = cal.id },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        },
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                            .testTag("calendar_option_${cal.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = null
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(cal.colorHex))
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cal.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (cal.isPrimary) {
                                                    Text(
                                                        text = "Kalendarz główny",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showCreateCalendarForm = !showCreateCalendarForm },
                            enabled = !isCreatingCalendar && !isExporting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("toggle_create_calendar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showCreateCalendarForm) "Ukryj formularz nowego kalendarza" else "Utwórz nowy kalendarz na telefonie")
                        }

                        AnimatedVisibility(visible = showCreateCalendarForm) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Nowy kalendarz na telefonie (widoczny w aplikacji Kalendarz Google)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    OutlinedTextField(
                                        value = newCalendarName,
                                        onValueChange = { newCalendarName = it },
                                        label = { Text("Nazwa nowego kalendarza") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("new_calendar_name_input")
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Kolor:",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        CalendarColorOptions.presets.forEach { preset ->
                                            val chosen = newCalendarColorHex == preset.colorHex
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(preset.colorHex))
                                                    .border(
                                                        width = if (chosen) 2.5.dp else 0.dp,
                                                        color = if (chosen) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { newCalendarColorHex = preset.colorHex },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (chosen) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = preset.label,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isCreatingCalendar = true
                                                exportError = null
                                                try {
                                                    DeviceCalendarRepository.createLocalDeviceCalendar(
                                                        context = context,
                                                        name = newCalendarName,
                                                        colorHex = newCalendarColorHex
                                                    ).onSuccess { created ->
                                                        deviceCalendars = listOf(created) + deviceCalendars
                                                        selectedDeviceAccount = created.accountName
                                                        selectedDeviceCalendarId = created.id
                                                        showCreateCalendarForm = false
                                                    }.onFailure { err ->
                                                        exportError = err.message ?: "Nie udało się utworzyć kalendarza."
                                                    }
                                                } finally {
                                                    isCreatingCalendar = false
                                                }
                                            }
                                        },
                                        enabled = newCalendarName.isNotBlank() && !isCreatingCalendar,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                            .testTag("calendar_create_confirm_button")
                                    ) {
                                        if (isCreatingCalendar) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text("Utwórz i wybierz kalendarz")
                                    }
                                }
                            }
                        }

                        HorizontalDivider()
                        Text(
                            text = "3. Zakres powtarzania i opcje",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CalendarRecurrenceMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = recurrenceMode == mode,
                                    onClick = { recurrenceMode = mode },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CalendarStartWeekMode.entries.forEach { weekMode ->
                                FilterChip(
                                    selected = startWeekMode == weekMode,
                                    onClick = { startWeekMode = weekMode },
                                    label = { Text(weekMode.label) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { addReminder = !addReminder }
                        ) {
                            Checkbox(checked = addReminder, onCheckedChange = { addReminder = it })
                            Text(
                                text = "Przypomnienie 10 min przed lekcją",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { replacePrevious = !replacePrevious }
                        ) {
                            Checkbox(checked = replacePrevious, onCheckedChange = { replacePrevious = it })
                            Text(
                                text = "Zastąp poprzednio wyeksportowane lekcje w wybranym kalendarzu",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (isExporting) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val progress = if (exportTotalCount > 0) {
                                exportDoneCount.toFloat() / exportTotalCount.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Zapisuję zajęcia: $exportDoneCount / $exportTotalCount...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (exportError != null) {
                        Text(
                            text = exportError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (exportResult != null) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Gotowe")
                }
            } else {
                val canExport = selectedCalendar != null && eventSpecs.isNotEmpty() && !isExporting && !isCreatingCalendar
                Button(
                    onClick = {
                        val targetCal = selectedCalendar ?: return@Button
                        scope.launch {
                            isExporting = true
                            exportDoneCount = 0
                            exportTotalCount = eventSpecs.size
                            exportError = null

                            val result = DeviceCalendarRepository.exportEvents(
                                context = context,
                                calendar = targetCal,
                                events = eventSpecs,
                                replacePrevious = replacePrevious,
                                onProgress = { done, total ->
                                    exportDoneCount = done
                                    exportTotalCount = total
                                }
                            )

                            result.onSuccess { res ->
                                exportResult = res
                                onExportCompleted(res)
                            }.onFailure { err ->
                                exportError = err.message ?: "Błąd podczas eksportu do kalendarza."
                            }
                            isExporting = false
                        }
                    },
                    enabled = canExport,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("calendar_export_confirm_button")
                ) {
                    Text("Eksportuj (${eventSpecs.size})")
                }
            }
        },
        dismissButton = {
            if (exportResult == null) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isExporting,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Anuluj")
                }
            }
        }
    )
}

private fun openGoogleCalendarApp(context: Context) {
    val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
    val intent = Intent(Intent.ACTION_VIEW).setData(builder.build()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
