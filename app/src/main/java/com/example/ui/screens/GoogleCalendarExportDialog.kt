package com.example.ui.screens

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.BuildConfig
import com.example.data.calendar.CalendarExportPlanner
import com.example.data.calendar.DeviceCalendarRepository
import com.example.data.calendar.GoogleCalendarApiClient
import com.example.data.model.CalendarColorOptions
import com.example.data.model.CalendarExportBackend
import com.example.data.model.CalendarExportOptions
import com.example.data.model.CalendarExportResult
import com.example.data.model.CalendarRecurrenceMode
import com.example.data.model.CalendarStartWeekMode
import com.example.data.model.ClassTimetable
import com.example.data.model.GoogleCalendarAccountInfo
import com.example.data.model.LessonEntry
import com.example.data.model.TargetCalendarItem
import com.example.data.model.TeacherSchoolConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private val calendarOAuthScopes = listOf(
    Scope("https://www.googleapis.com/auth/calendar"),
    Scope("https://www.googleapis.com/auth/userinfo.email"),
    Scope("https://www.googleapis.com/auth/userinfo.profile")
)

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
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val apiClient = remember { GoogleCalendarApiClient() }

    var backend by remember { mutableStateOf(CalendarExportBackend.GOOGLE_CLOUD_OAUTH) }

    // Google Cloud OAuth state
    var cloudAccount by remember { mutableStateOf<GoogleCalendarAccountInfo?>(null) }
    var cloudCalendars by remember { mutableStateOf<List<TargetCalendarItem>>(emptyList()) }
    var selectedCloudCalendarId by remember { mutableStateOf<String?>(null) }
    var isLoadingCloud by remember { mutableStateOf(false) }
    var cloudError by remember { mutableStateOf<String?>(null) }
    var pendingAccountHint by remember { mutableStateOf<String?>(null) }

    // Device Calendar Provider state
    var hasDeviceCalPermission by remember {
        mutableStateOf(DeviceCalendarRepository.hasCalendarPermissions(context))
    }
    var deviceCalendars by remember { mutableStateOf<List<TargetCalendarItem>>(emptyList()) }
    var selectedDeviceAccount by remember { mutableStateOf<String?>(null) }
    var selectedDeviceCalendarId by remember { mutableStateOf<String?>(null) }
    var isLoadingDevice by remember { mutableStateOf(false) }
    var deviceError by remember { mutableStateOf<String?>(null) }

    // Create new calendar inline form
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

    // Export options & progress
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

    fun loadCloudAccountAndCalendars(accessToken: String, fallbackEmail: String = "") {
        scope.launch {
            isLoadingCloud = true
            cloudError = null
            val info = apiClient.fetchAccountInfo(accessToken).getOrNull()
            val email = info?.email?.takeIf { it.isNotBlank() } ?: fallbackEmail.ifBlank { "Konto Google" }
            val name = info?.displayName.orEmpty()

            apiClient.listWritableCalendars(accessToken, email).onSuccess { list ->
                cloudAccount = GoogleCalendarAccountInfo(
                    email = email,
                    displayName = name,
                    accessToken = accessToken
                )
                cloudCalendars = list
                if (selectedCloudCalendarId == null || list.none { it.id == selectedCloudCalendarId }) {
                    selectedCloudCalendarId = list.firstOrNull { it.isPrimary }?.id ?: list.firstOrNull()?.id
                }
            }.onFailure { err ->
                cloudError = err.message ?: "Nie udało się pobrać listy kalendarzy Google."
            }
            isLoadingCloud = false
        }
    }

    val authorizationIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val act = activity
        if (act == null || activityResult.resultCode != Activity.RESULT_OK) {
            isLoadingCloud = false
            return@rememberLauncherForActivityResult
        }
        runCatching {
            val authResult = Identity.getAuthorizationClient(act)
                .getAuthorizationResultFromIntent(activityResult.data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                loadCloudAccountAndCalendars(token, pendingAccountHint.orEmpty())
            } else {
                isLoadingCloud = false
                cloudError = "Nie otrzymano tokenu dostępu do Kalendarza Google."
            }
        }.onFailure { err ->
            isLoadingCloud = false
            cloudError = formatAuthErrorMessage(err)
        }
    }

    fun authorizeGoogleAccess(accountEmailHint: String? = null) {
        val act = activity ?: run {
            cloudError = "Brak aktywnego okna aplikacji do autoryzacji Google."
            return
        }
        isLoadingCloud = true
        cloudError = null
        pendingAccountHint = accountEmailHint

        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(calendarOAuthScopes)
        if (!accountEmailHint.isNullOrBlank()) {
            builder.setAccount(Account(accountEmailHint, "com.google"))
        }

        Identity.getAuthorizationClient(act)
            .authorize(builder.build())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        authorizationIntentLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        isLoadingCloud = false
                        cloudError = "Brak okna zgody Google."
                    }
                } else {
                    val token = result.accessToken
                    if (!token.isNullOrBlank()) {
                        loadCloudAccountAndCalendars(token, accountEmailHint.orEmpty())
                    } else {
                        isLoadingCloud = false
                        cloudError = "Pusty token dostępu Google."
                    }
                }
            }
            .addOnFailureListener { err ->
                isLoadingCloud = false
                cloudError = formatAuthErrorMessage(err)
            }
    }

    val chooseSystemGoogleAccountLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val chosenEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!chosenEmail.isNullOrBlank()) {
                authorizeGoogleAccess(chosenEmail)
            } else {
                authorizeGoogleAccess(null)
            }
        } else {
            isLoadingCloud = false
        }
    }

    fun startGoogleAccountSelection(forceSwitchAccount: Boolean) {
        val act = activity ?: return
        scope.launch {
            isLoadingCloud = true
            cloudError = null
            val oauthClientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID
            var chosenEmail: String? = null

            if (forceSwitchAccount) {
                runCatching {
                    CredentialManager.create(act).clearCredentialState(ClearCredentialStateRequest())
                }
            }

            if (oauthClientId.isNotBlank()) {
                val credentialManager = CredentialManager.create(act)
                val option = GetSignInWithGoogleOption.Builder(oauthClientId).build()
                val req = GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build()
                val credResult = runCatching {
                    credentialManager.getCredential(act, req)
                }
                val err = credResult.exceptionOrNull()
                if (err is GetCredentialCancellationException) {
                    isLoadingCloud = false
                    return@launch
                }
                val cred = credResult.getOrNull()?.credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCred = runCatching { GoogleIdTokenCredential.createFrom(cred.data) }.getOrNull()
                    chosenEmail = googleCred?.id
                }
            }

            if (chosenEmail != null) {
                authorizeGoogleAccess(chosenEmail)
            } else if (forceSwitchAccount) {
                // Offer system Google account chooser so user can always switch accounts even if Credential Manager has no match
                val intent = AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    arrayOf("com.google"),
                    null,
                    null,
                    null,
                    null
                )
                runCatching { chooseSystemGoogleAccountLauncher.launch(intent) }
                    .onFailure { authorizeGoogleAccess(null) }
            } else {
                authorizeGoogleAccess(null)
            }
        }
    }

    fun refreshDeviceCalendars() {
        scope.launch {
            isLoadingDevice = true
            deviceError = null
            DeviceCalendarRepository.listWritableCalendars(context).onSuccess { list ->
                deviceCalendars = list
                val accounts = list.map { it.accountName }.distinct()
                if (selectedDeviceAccount == null || selectedDeviceAccount !in accounts) {
                    selectedDeviceAccount = accounts.firstOrNull()
                }
                val accountCalendars = list.filter { it.accountName == selectedDeviceAccount }
                if (selectedDeviceCalendarId == null || accountCalendars.none { it.id == selectedDeviceCalendarId }) {
                    selectedDeviceCalendarId = accountCalendars.firstOrNull { it.isPrimary }?.id
                        ?: accountCalendars.firstOrNull()?.id
                }
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
            deviceError = "Przyznaj uprawnienia do kalendarza, aby wybrać konto i zapisać plan."
        }
    }

    LaunchedEffect(backend, hasDeviceCalPermission) {
        if (backend == CalendarExportBackend.DEVICE_CALENDAR_PROVIDER && hasDeviceCalPermission && deviceCalendars.isEmpty()) {
            refreshDeviceCalendars()
        }
    }

    val activeAccountCalendars = remember(backend, cloudCalendars, deviceCalendars, selectedDeviceAccount) {
        if (backend == CalendarExportBackend.GOOGLE_CLOUD_OAUTH) {
            cloudCalendars
        } else {
            deviceCalendars.filter { it.accountName == selectedDeviceAccount }
        }
    }

    val selectedCalendar = remember(
        backend,
        cloudCalendars,
        selectedCloudCalendarId,
        deviceCalendars,
        selectedDeviceCalendarId
    ) {
        if (backend == CalendarExportBackend.GOOGLE_CLOUD_OAUTH) {
            cloudCalendars.firstOrNull { it.id == selectedCloudCalendarId }
        } else {
            deviceCalendars.firstOrNull { it.id == selectedDeviceCalendarId }
        }
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
                    // Summary banner of lessons ready to export
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

                    // Backend switcher: Cloud OAuth vs Device Synced Calendar
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CalendarExportBackend.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = backend == mode,
                                onClick = {
                                    backend = mode
                                    showCreateCalendarForm = false
                                    exportError = null
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = CalendarExportBackend.entries.size
                                ),
                                icon = {
                                    SegmentedButtonDefaults.Icon(active = backend == mode) {
                                        Icon(
                                            imageVector = if (mode == CalendarExportBackend.GOOGLE_CLOUD_OAUTH) {
                                                Icons.Filled.CloudDone
                                            } else {
                                                Icons.Filled.PhoneAndroid
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("export_backend_${mode.name.lowercase()}")
                            ) {
                                Text(
                                    text = if (mode == CalendarExportBackend.GOOGLE_CLOUD_OAUTH) "Konto Google" else "Z telefonu",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 1. Account selection section
                    if (backend == CalendarExportBackend.GOOGLE_CLOUD_OAUTH) {
                        Text(
                            text = "1. Wybierz konto Google",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (cloudAccount == null) {
                            Button(
                                onClick = { startGoogleAccountSelection(forceSwitchAccount = false) },
                                enabled = !isLoadingCloud && !isExporting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("google_connect_account_button")
                            ) {
                                if (isLoadingCloud) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Łączenie z Google...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Wybierz i połącz konto Google")
                                }
                            }
                        } else {
                            val acc = cloudAccount!!
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (acc.displayName.isNotBlank()) {
                                            Text(
                                                text = acc.displayName,
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = acc.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = { startGoogleAccountSelection(forceSwitchAccount = true) },
                                        enabled = !isLoadingCloud && !isExporting,
                                        modifier = Modifier.testTag("google_switch_account_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SwapHoriz,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Zmień")
                                    }
                                }
                            }
                        }

                        if (cloudError != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = cloudError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            backend = CalendarExportBackend.DEVICE_CALENDAR_PROVIDER
                                            cloudError = null
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text("Użyj kont i kalendarzy z telefonu")
                                    }
                                }
                            }
                        }
                    } else {
                        // Device Calendar Provider Account Selection
                        Text(
                            text = "1. Konto na urządzeniu",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!hasDeviceCalPermission) {
                            OutlinedButton(
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
                                Text("Wczytuję konta i kalendarze...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            val accounts = remember(deviceCalendars) {
                                deviceCalendars.map { it.accountName to it.accountType }.distinct()
                            }
                            if (accounts.isEmpty()) {
                                Text(
                                    text = "Nie wykryto zapisywalnych kalendarzy na urządzeniu. Możesz utworzyć nowy kalendarz poniżej.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    accounts.forEach { (accName, accType) ->
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
                    }

                    // 2. Calendar picker + Create New Calendar
                    val canManageCalendars = (backend == CalendarExportBackend.GOOGLE_CLOUD_OAUTH && cloudAccount != null) ||
                        (backend == CalendarExportBackend.DEVICE_CALENDAR_PROVIDER && hasDeviceCalPermission)

                    if (canManageCalendars) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. Wybierz kalendarz lub utwórz nowy",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (backend == CalendarExportBackend.GOOGLE_CLOUD_OAUTH) {
                                        cloudAccount?.let { loadCloudAccountAndCalendars(it.accessToken, it.email) }
                                    } else {
                                        refreshDeviceCalendars()
                                    }
                                },
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
                                text = "Brak kalendarzy na tym koncie — utwórz nowy poniżej.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                activeAccountCalendars.forEach { cal ->
                                    val isSelected = selectedCalendar?.id == cal.id
                                    Surface(
                                        onClick = {
                                            if (backend == CalendarExportBackend.GOOGLE_CLOUD_OAUTH) {
                                                selectedCloudCalendarId = cal.id
                                            } else {
                                                selectedDeviceCalendarId = cal.id
                                            }
                                        },
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

                        // Create New Calendar toggle & inline form
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
                            Text(if (showCreateCalendarForm) "Ukryj formularz nowego kalendarza" else "Utwórz nowy kalendarz")
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
                                        text = if (backend == CalendarExportBackend.GOOGLE_CLOUD_OAUTH) {
                                            "Nowy kalendarz na koncie ${cloudAccount?.email.orEmpty()}"
                                        } else {
                                            "Nowy kalendarz lokalny na urządzeniu"
                                        },
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
                                                if (backend == CalendarExportBackend.GOOGLE_CLOUD_OAUTH) {
                                                    val acc = cloudAccount ?: return@launch
                                                    apiClient.createCalendar(
                                                        accessToken = acc.accessToken,
                                                        summary = newCalendarName,
                                                        description = "Plan lekcji wyeksportowany z aplikacji ZSME Plan",
                                                        accountEmail = acc.email,
                                                        colorHex = newCalendarColorHex
                                                    ).onSuccess { created ->
                                                        cloudCalendars = listOf(created) + cloudCalendars
                                                        selectedCloudCalendarId = created.id
                                                        showCreateCalendarForm = false
                                                    }.onFailure { err ->
                                                        exportError = err.message ?: "Nie udało się utworzyć kalendarza Google."
                                                    }
                                                } else {
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
                                                }
                                                isCreatingCalendar = false
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

                        // 3. Export options
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

                            val result = if (backend == CalendarExportBackend.GOOGLE_CLOUD_OAUTH) {
                                val token = cloudAccount?.accessToken.orEmpty()
                                apiClient.exportEvents(
                                    accessToken = token,
                                    calendar = targetCal,
                                    events = eventSpecs,
                                    replacePrevious = replacePrevious,
                                    onProgress = { done, total ->
                                        exportDoneCount = done
                                        exportTotalCount = total
                                    }
                                )
                            } else {
                                DeviceCalendarRepository.exportEvents(
                                    context = context,
                                    calendar = targetCal,
                                    events = eventSpecs,
                                    replacePrevious = replacePrevious,
                                    onProgress = { done, total ->
                                        exportDoneCount = done
                                        exportTotalCount = total
                                    }
                                )
                            }

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

private fun formatAuthErrorMessage(err: Throwable): String {
    if (err is ApiException) {
        return when (err.statusCode) {
            10 -> "Konfiguracja OAuth dla aplikacji nie ma jeszcze dodanego certyfikatu Android (kod 10). Przełącz się na tryb „Z telefonu”, aby użyć konta Google zsynchronizowanego na urządzeniu."
            12501 -> "Logowanie Google zostało anulowane."
            else -> "Błąd autoryzacji Google (kod ${err.statusCode}). Możesz też użyć trybu „Z telefonu”."
        }
    }
    return err.message ?: "Nie udało się autoryzować konta Google."
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
