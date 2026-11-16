package com.example.ui.screens

import android.Manifest
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
import com.example.data.calendar.CalendarTokenResult
import com.example.data.calendar.CredentialSignInOutcome
import com.example.data.calendar.DeviceCalendarRepository
import com.example.data.calendar.GoogleCalendarApiClient
import com.example.data.calendar.GoogleCredentialAuthManager
import com.example.data.model.CalendarColorOptions
import com.example.data.model.CalendarExportBackend
import com.example.data.model.CalendarExportOptions
import com.example.data.model.CalendarExportResult
import com.example.data.model.CalendarRecurrenceMode
import com.example.data.model.CalendarStartWeekMode
import com.example.data.model.ClassTimetable
import com.example.data.model.GoogleCalendarAccountInfo
import com.example.data.model.GoogleCredentialSession
import com.example.data.model.LessonEntry
import com.example.data.model.TargetCalendarItem
import com.example.data.model.TeacherSchoolConfig
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoogleCalendarExportDialog(
    timetable: ClassTimetable,
    filteredDayLessons: Map<Int, List<LessonEntry>>,
    isTeacherMode: Boolean,
    teacherSchools: List<TeacherSchoolConfig>,
    credentialSession: GoogleCredentialSession? = null,
    onCredentialSignedIn: (GoogleCredentialSession?) -> Unit = {},
    onAccessTokenGranted: (String) -> Unit = {},
    onCredentialSignOut: () -> Unit = {},
    onExportCompleted: (CalendarExportResult) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val apiClient = remember { GoogleCalendarApiClient() }

    var backend by rememberSaveable { mutableStateOf(CalendarExportBackend.GOOGLE_CLOUD_OAUTH) }

    // Google Cloud OAuth state
    var cloudAccount by remember { mutableStateOf<GoogleCalendarAccountInfo?>(null) }
    var cloudCalendars by remember { mutableStateOf<List<TargetCalendarItem>>(emptyList()) }
    var selectedCloudCalendarId by remember { mutableStateOf<String?>(null) }
    var isLoadingCloud by remember { mutableStateOf(false) }
    var cloudError by remember { mutableStateOf<String?>(null) }
    var pendingAccountHint by rememberSaveable { mutableStateOf<String?>(null) }

    // Device Calendar Provider state
    var hasDeviceCalPermission by remember {
        mutableStateOf(DeviceCalendarRepository.hasCalendarPermissions(context))
    }
    var deviceCalendars by remember { mutableStateOf<List<TargetCalendarItem>>(emptyList()) }
    var selectedDeviceAccount by rememberSaveable { mutableStateOf<String?>(null) }
    var preferredDeviceAccount by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDeviceCalendarId by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoadingDevice by remember { mutableStateOf(false) }
    var deviceError by remember { mutableStateOf<String?>(null) }
    var deviceFallbackNotice by remember { mutableStateOf<String?>(null) }

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

    fun refreshDeviceCalendars(preferredAccount: String? = preferredDeviceAccount) {
        scope.launch {
            isLoadingDevice = true
            deviceError = null
            DeviceCalendarRepository.listWritableCalendars(context).onSuccess { list ->
                deviceCalendars = list
                val (resolvedAccount, resolvedCalId) = DeviceCalendarRepository.resolveSelection(
                    calendars = list,
                    preferredAccount = preferredAccount,
                    currentSelectedAccount = selectedDeviceAccount,
                    currentSelectedCalendarId = selectedDeviceCalendarId
                )
                selectedDeviceAccount = resolvedAccount
                selectedDeviceCalendarId = resolvedCalId

                val pref = preferredAccount?.trim().orEmpty()
                if (pref.isNotBlank() && resolvedAccount != null && !resolvedAccount.equals(pref, ignoreCase = true)) {
                    deviceFallbackNotice = "Konto $pref nie ma jeszcze zsynchronizowanych kalendarzy na urządzeniu — wybrano $resolvedAccount. Możesz też utworzyć nowy kalendarz poniżej."
                } else if (pref.isNotBlank() && list.isEmpty()) {
                    deviceFallbackNotice = "Nie wykryto zsynchronizowanych kalendarzy dla konta $pref. Utwórz nowy kalendarz poniżej lub włącz synchronizację w aplikacji Kalendarz Google."
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
            refreshDeviceCalendars(preferredAccount = preferredDeviceAccount)
        } else {
            deviceError = "Przyznaj uprawnienia do kalendarza, aby wyświetlić konta Google i zapisać plan."
        }
    }

    fun switchToDeviceCalendarForAccount(accountEmail: String?, notice: String?) {
        isLoadingCloud = false
        cloudError = null
        preferredDeviceAccount = accountEmail?.trim()?.takeIf { it.isNotBlank() }
        deviceFallbackNotice = notice
        backend = CalendarExportBackend.DEVICE_CALENDAR_PROVIDER
        val granted = DeviceCalendarRepository.hasCalendarPermissions(context)
        hasDeviceCalPermission = granted
        if (granted) {
            refreshDeviceCalendars(preferredAccount = preferredDeviceAccount)
        } else {
            calendarPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }

    fun loadCloudAccountAndCalendars(
        accessToken: String,
        fallbackEmail: String = "",
        fallbackName: String = "",
        allowRefreshOnUnauthorized: Boolean = true,
        onTokenExpired: (String?) -> Unit = {}
    ) {
        scope.launch {
            isLoadingCloud = true
            cloudError = null
            onAccessTokenGranted(accessToken)
            val info = apiClient.fetchAccountInfo(accessToken).getOrNull()
            val preEmail = info?.email?.takeIf { it.isNotBlank() }
                ?: fallbackEmail.ifBlank { credentialSession?.email.orEmpty() }
            val preName = info?.displayName?.takeIf { it.isNotBlank() }
                ?: fallbackName.ifBlank { credentialSession?.displayName.orEmpty() }

            apiClient.listWritableCalendars(accessToken, preEmail.ifBlank { "Konto Google" }).onSuccess { list ->
                val resolvedEmail = preEmail.ifBlank {
                    list.firstOrNull { it.isPrimary && it.id.contains("@") }?.id ?: "Konto Google"
                }
                val normalizedList = if (preEmail.isBlank() && resolvedEmail != "Konto Google") {
                    list.map { it.copy(accountName = resolvedEmail) }
                } else {
                    list
                }

                if (credentialSession == null ||
                    (resolvedEmail != "Konto Google" && !credentialSession.email.equals(resolvedEmail, ignoreCase = true))
                ) {
                    onCredentialSignedIn(
                        GoogleCredentialSession(
                            email = resolvedEmail,
                            displayName = preName,
                            calendarAccessToken = accessToken
                        )
                    )
                }

                cloudAccount = GoogleCalendarAccountInfo(
                    email = resolvedEmail,
                    displayName = preName,
                    accessToken = accessToken
                )
                cloudCalendars = normalizedList
                if (selectedCloudCalendarId == null || normalizedList.none { it.id == selectedCloudCalendarId }) {
                    selectedCloudCalendarId = normalizedList.firstOrNull { it.isPrimary }?.id
                        ?: normalizedList.firstOrNull()?.id
                }
                isLoadingCloud = false
            }.onFailure { err ->
                val isUnauthorized = err.message?.contains("HTTP 401") == true
                if (isUnauthorized && allowRefreshOnUnauthorized) {
                    GoogleCredentialAuthManager.invalidateGoogleAuthToken(context, accessToken)
                    onAccessTokenGranted("")
                    val hint = preEmail.takeIf { it.isNotBlank() && it != "Konto Google" }
                    onTokenExpired(hint)
                } else {
                    isLoadingCloud = false
                    cloudError = err.message ?: "Nie udało się pobrać listy kalendarzy Google."
                }
            }
        }
    }

    var retryAuthorizeRef: ((String?, Boolean) -> Unit)? = null

    val googleAuthUtilConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val act = activity
        val email = pendingAccountHint?.trim().orEmpty()
        if (act == null || email.isBlank()) {
            isLoadingCloud = false
            cloudError = "Brak danych konta Google do dokończenia autoryzacji."
            return@rememberLauncherForActivityResult
        }
        if (activityResult.resultCode != Activity.RESULT_OK) {
            isLoadingCloud = false
            cloudError = "Nie udzielono zgody na dostęp do Kalendarza Google. Możesz spróbować ponownie lub użyć kalendarzy z telefonu."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isLoadingCloud = true
            when (val tokenRes = GoogleCredentialAuthManager.requestCalendarTokenViaGoogleAuthUtil(act, email)) {
                is CalendarTokenResult.Success -> {
                    loadCloudAccountAndCalendars(
                        accessToken = tokenRes.accessToken,
                        fallbackEmail = email,
                        allowRefreshOnUnauthorized = false
                    )
                }
                is CalendarTokenResult.NeedsConsent -> {
                    isLoadingCloud = false
                    cloudError = "Google wymaga ponownego potwierdzenia dostępu. Wybierz „Ponów połączenie” lub użyj kalendarzy z telefonu."
                }
                is CalendarTokenResult.Failure -> {
                    isLoadingCloud = false
                    cloudError = buildCloudFailureDiagnostic(
                        context = act,
                        identityErr = null,
                        fallbackReason = tokenRes.reason,
                        isConfigErr = tokenRes.isConfigurationError
                    )
                }
            }
        }
    }

    fun tryGoogleAuthUtilFallback(accountEmail: String, priorIdentityError: Throwable?) {
        val act = activity ?: run {
            isLoadingCloud = false
            cloudError = "Brak aktywnego okna aplikacji."
            return
        }
        val cleanEmail = accountEmail.trim()
        if (cleanEmail.isBlank()) {
            isLoadingCloud = false
            cloudError = buildCloudFailureDiagnostic(
                context = act,
                identityErr = priorIdentityError,
                fallbackReason = null,
                isConfigErr = (priorIdentityError as? ApiException)?.statusCode == 10
            )
            return
        }
        scope.launch {
            isLoadingCloud = true
            when (val res = GoogleCredentialAuthManager.requestCalendarTokenViaGoogleAuthUtil(act, cleanEmail)) {
                is CalendarTokenResult.Success -> {
                    loadCloudAccountAndCalendars(
                        accessToken = res.accessToken,
                        fallbackEmail = cleanEmail,
                        allowRefreshOnUnauthorized = false
                    )
                }
                is CalendarTokenResult.NeedsConsent -> {
                    pendingAccountHint = cleanEmail
                    runCatching { googleAuthUtilConsentLauncher.launch(res.intent) }
                        .onFailure { launchErr ->
                            isLoadingCloud = false
                            cloudError = launchErr.message
                                ?: "Nie udało się otworzyć ekranu zgody Google."
                        }
                }
                is CalendarTokenResult.Failure -> {
                    isLoadingCloud = false
                    cloudError = buildCloudFailureDiagnostic(
                        context = act,
                        identityErr = priorIdentityError,
                        fallbackReason = res.reason,
                        isConfigErr = res.isConfigurationError ||
                            (priorIdentityError as? ApiException)?.statusCode == 10
                    )
                }
            }
        }
    }

    val authorizationIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val act = activity
        if (act == null) {
            isLoadingCloud = false
            cloudError = "Brak aktywnego okna aplikacji do autoryzacji Google."
            return@rememberLauncherForActivityResult
        }
        val chosenHint = pendingAccountHint?.trim()?.takeIf { it.isNotBlank() }
        if (activityResult.resultCode != Activity.RESULT_OK) {
            val intentErr = activityResult.data?.let { data ->
                runCatching {
                    Identity.getAuthorizationClient(act).getAuthorizationResultFromIntent(data)
                }.exceptionOrNull()
            }
            if (chosenHint != null && intentErr != null) {
                tryGoogleAuthUtilFallback(chosenHint, intentErr)
            } else {
                isLoadingCloud = false
                cloudError = "Nie udzielono zgody na dostęp do Kalendarza Google. Możesz spróbować ponownie lub wybrać kalendarz z telefonu."
            }
            return@rememberLauncherForActivityResult
        }
        runCatching {
            val authResult = Identity.getAuthorizationClient(act)
                .getAuthorizationResultFromIntent(activityResult.data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                loadCloudAccountAndCalendars(
                    accessToken = token,
                    fallbackEmail = chosenHint.orEmpty(),
                    onTokenExpired = { hint -> retryAuthorizeRef?.invoke(hint, false) }
                )
            } else if (chosenHint != null) {
                tryGoogleAuthUtilFallback(chosenHint, null)
            } else {
                isLoadingCloud = false
                cloudError = "Nie otrzymano tokenu dostępu do Kalendarza Google."
            }
        }.onFailure { err ->
            if (chosenHint != null) {
                tryGoogleAuthUtilFallback(chosenHint, err)
            } else {
                isLoadingCloud = false
                cloudError = buildCloudFailureDiagnostic(
                    context = act,
                    identityErr = err,
                    fallbackReason = null,
                    isConfigErr = (err as? ApiException)?.statusCode == 10
                )
            }
        }
    }

    fun authorizeGoogleAccess(accountEmailHint: String? = null, scopeOnlyFallback: Boolean = false) {
        val act = activity ?: run {
            isLoadingCloud = false
            cloudError = "Brak aktywnego okna aplikacji do autoryzacji Google."
            return
        }
        isLoadingCloud = true
        cloudError = null
        val cleanHint = accountEmailHint?.trim()?.takeIf { it.isNotBlank() }
        pendingAccountHint = cleanHint

        val authRequest = if (scopeOnlyFallback) {
            GoogleCredentialAuthManager.buildCalendarScopeOnlyAuthorizationRequest(cleanHint)
        } else {
            GoogleCredentialAuthManager.buildCalendarAuthorizationRequest(cleanHint)
        }

        Identity.getAuthorizationClient(act)
            .authorize(authRequest)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        runCatching {
                            authorizationIntentLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                            )
                        }.onFailure { launchErr ->
                            if (cleanHint != null) {
                                tryGoogleAuthUtilFallback(cleanHint, launchErr)
                            } else {
                                isLoadingCloud = false
                                cloudError = formatAuthErrorMessage(launchErr)
                            }
                        }
                    } else if (cleanHint != null) {
                        tryGoogleAuthUtilFallback(cleanHint, null)
                    } else {
                        isLoadingCloud = false
                        cloudError = "Brak okna zgody Google."
                    }
                } else {
                    val token = result.accessToken
                    if (!token.isNullOrBlank()) {
                        loadCloudAccountAndCalendars(
                            accessToken = token,
                            fallbackEmail = cleanHint.orEmpty(),
                            onTokenExpired = { hint -> authorizeGoogleAccess(hint, scopeOnlyFallback = false) }
                        )
                    } else if (cleanHint != null) {
                        tryGoogleAuthUtilFallback(cleanHint, null)
                    } else {
                        isLoadingCloud = false
                        cloudError = "Pusty token dostępu Google."
                    }
                }
            }
            .addOnFailureListener { err ->
                if (err is ApiException && err.statusCode == 12501) {
                    isLoadingCloud = false
                    cloudError = "Autoryzacja konta Google została anulowana."
                } else if (!scopeOnlyFallback) {
                    authorizeGoogleAccess(cleanHint, scopeOnlyFallback = true)
                } else if (cleanHint != null) {
                    tryGoogleAuthUtilFallback(cleanHint, err)
                } else {
                    isLoadingCloud = false
                    cloudError = buildCloudFailureDiagnostic(
                        context = act,
                        identityErr = err,
                        fallbackReason = null,
                        isConfigErr = (err as? ApiException)?.statusCode == 10
                    )
                }
            }
    }

    retryAuthorizeRef = { hint, scopeOnly -> authorizeGoogleAccess(hint, scopeOnly) }

    val chooseSystemGoogleAccountLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val chosenEmail = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (!chosenEmail.isNullOrBlank()) {
                onCredentialSignedIn(
                    GoogleCredentialSession(
                        email = chosenEmail,
                        displayName = ""
                    )
                )
                authorizeGoogleAccess(chosenEmail)
            } else {
                isLoadingCloud = false
                cloudError = "Nie wybrano konta Google. Możesz użyć kalendarzy zsynchronizowanych na telefonie."
            }
        } else {
            isLoadingCloud = false
            cloudError = "Nie wybrano konta Google. Możesz spróbować ponownie lub użyć kont z telefonu."
        }
    }

    fun launchSystemGoogleAccountChooser() {
        val intent = android.accounts.AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            "Wybierz konto Google do eksportu planu",
            null,
            null,
            null
        )
        runCatching { chooseSystemGoogleAccountLauncher.launch(intent) }
            .onFailure {
                isLoadingCloud = false
                cloudError = "Nie można otworzyć systemowego wyboru konta Google — użyj zakładki „Z telefonu”."
            }
    }

    fun signOutGoogleAccount() {
        cloudAccount = null
        cloudCalendars = emptyList()
        selectedCloudCalendarId = null
        pendingAccountHint = null
        preferredDeviceAccount = null
        cloudError = null
        onCredentialSignOut()
    }

    fun startGoogleAccountSelection(forceSwitchAccount: Boolean) {
        val act = activity ?: run {
            cloudError = "Brak aktywnego okna aplikacji do wyboru konta Google."
            return
        }
        scope.launch {
            isLoadingCloud = true
            cloudError = null
            when (
                val outcome = GoogleCredentialAuthManager.signInWithCredentialManager(
                    activity = act,
                    forceAccountChooser = forceSwitchAccount
                )
            ) {
                is CredentialSignInOutcome.Success -> {
                    onCredentialSignedIn(outcome.session)
                    authorizeGoogleAccess(outcome.session.email)
                }
                is CredentialSignInOutcome.Cancelled -> {
                    isLoadingCloud = false
                    cloudError = "${outcome.message} Możesz spróbować ponownie lub wybrać kalendarz Google z telefonu."
                }
                is CredentialSignInOutcome.NoCredentialOrUnavailable -> {
                    if (outcome.fallbackToSystemPicker) {
                        launchSystemGoogleAccountChooser()
                    } else {
                        isLoadingCloud = false
                        cloudError = outcome.reason
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val act = activity ?: return@LaunchedEffect
        val existing = credentialSession
        if (existing != null) {
            if (existing.calendarAccessToken.isNotBlank() && cloudAccount == null) {
                loadCloudAccountAndCalendars(
                    accessToken = existing.calendarAccessToken,
                    fallbackEmail = existing.email,
                    fallbackName = existing.displayName,
                    onTokenExpired = { hint -> authorizeGoogleAccess(hint ?: existing.email) }
                )
            } else if (backend == CalendarExportBackend.GOOGLE_CLOUD_OAUTH && cloudAccount == null) {
                authorizeGoogleAccess(existing.email)
            }
        } else {
            val silentSession = GoogleCredentialAuthManager.trySilentAuthorizedSignIn(act)
            if (silentSession != null) {
                onCredentialSignedIn(silentSession)
                authorizeGoogleAccess(silentSession.email)
            }
        }
    }

    LaunchedEffect(backend, hasDeviceCalPermission) {
        if (backend == CalendarExportBackend.DEVICE_CALENDAR_PROVIDER && hasDeviceCalPermission && deviceCalendars.isEmpty()) {
            refreshDeviceCalendars(preferredAccount = preferredDeviceAccount ?: credentialSession?.email)
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
                            text = "1. Zaloguj się przez Google Credential Manager",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        val activeSession = credentialSession
                        if (cloudAccount == null && activeSession == null) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        Text("Logowanie przez Google...")
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.AccountCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Zaloguj się przez Google")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        switchToDeviceCalendarForAccount(
                                            accountEmail = null,
                                            notice = "Wybierz konto Google zsynchronizowane na urządzeniu lub lokalny kalendarz."
                                        )
                                    },
                                    enabled = !isLoadingCloud && !isExporting,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("device_google_account_fallback_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhoneAndroid,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Wybierz kalendarz Google z telefonu")
                                }
                            }
                        } else {
                            val displayName = cloudAccount?.displayName?.ifBlank { activeSession?.displayName.orEmpty() }
                                ?: activeSession?.displayName.orEmpty()
                            val displayEmail = cloudAccount?.email?.ifBlank { activeSession?.email.orEmpty() }
                                ?: activeSession?.email.orEmpty()
                            GoogleSignedInAccountCard(
                                displayName = displayName,
                                email = displayEmail,
                                subtitle = if (cloudAccount != null) {
                                    "Połączono z Kalendarzem Google"
                                } else {
                                    "Wybrano konto Google — wymagana autoryzacja Kalendarza"
                                },
                                enabled = !isLoadingCloud && !isExporting,
                                onSwitchAccount = { startGoogleAccountSelection(forceSwitchAccount = true) },
                                onSignOut = { signOutGoogleAccount() }
                            )
                            if (isLoadingCloud) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(
                                        text = "Pobieram uprawnienia i kalendarze Google...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else if (cloudAccount == null && activeSession != null) {
                                Button(
                                    onClick = { authorizeGoogleAccess(activeSession.email) },
                                    enabled = !isExporting,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("google_retry_connect_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Połącz z Kalendarzem Google")
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
                                            switchToDeviceCalendarForAccount(
                                                accountEmail = pendingAccountHint ?: credentialSession?.email,
                                                notice = null
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                            .testTag("switch_to_device_calendar_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PhoneAndroid,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Użyj zsynchronizowanego kalendarza z telefonu")
                                    }
                                }
                            }
                        }
                    } else {
                        // Device Calendar Provider Account Selection
                        if (credentialSession != null) {
                            GoogleSignedInAccountCard(
                                displayName = credentialSession.displayName,
                                email = credentialSession.email,
                                subtitle = "Zalogowano przez Google Credential Manager",
                                enabled = !isLoadingDevice && !isExporting,
                                onSwitchAccount = {
                                    backend = CalendarExportBackend.GOOGLE_CLOUD_OAUTH
                                    startGoogleAccountSelection(forceSwitchAccount = true)
                                },
                                onSignOut = { signOutGoogleAccount() }
                            )
                        }
                        if (deviceFallbackNotice != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = deviceFallbackNotice!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                        Text(
                            text = "1. Konto kalendarza na urządzeniu",
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
                                            "Nowy kalendarz na telefonie (widoczny w aplikacji Kalendarz Google)"
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
                                                try {
                                                    if (backend == CalendarExportBackend.GOOGLE_CLOUD_OAUTH) {
                                                        val acc = cloudAccount
                                                        if (acc == null) {
                                                            exportError = "Najpierw połącz konto Google."
                                                        } else {
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

@Composable
private fun GoogleSignedInAccountCard(
    displayName: String,
    email: String,
    subtitle: String,
    enabled: Boolean,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("google_signed_in_account_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (displayName.isNotBlank()) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onSwitchAccount,
                    enabled = enabled,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("google_switch_account_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Zmień konto")
                }
                TextButton(
                    onClick = onSignOut,
                    enabled = enabled,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("google_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Wyloguj")
                }
            }
        }
    }
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
            10 -> "Konfiguracja OAuth dla aplikacji nie ma jeszcze dodanego certyfikatu Android (kod 10)."
            12501 -> "Logowanie Google zostało anulowane."
            else -> "Błąd autoryzacji Google (kod ${err.statusCode})."
        }
    }
    return err.message ?: "Nie udało się autoryzować konta Google."
}

private fun buildCloudFailureDiagnostic(
    context: Context,
    identityErr: Throwable?,
    fallbackReason: String?,
    isConfigErr: Boolean
): String {
    val pkg = context.packageName
    val sha1 = GoogleCredentialAuthManager.getAppSigningSha1(context)
    val baseMsg = when {
        identityErr is ApiException && identityErr.statusCode == 12501 ->
            "Autoryzacja konta Google została anulowana."
        isConfigErr || (identityErr as? ApiException)?.statusCode == 10 -> buildString {
            append("Google Cloud OAuth wymaga rejestracji certyfikatu APK Androida w Google Cloud Console (pakiet: $pkg")
            if (!sha1.isNullOrBlank()) append(", SHA-1: $sha1")
            append(").")
        }
        !fallbackReason.isNullOrBlank() -> fallbackReason
        identityErr != null -> formatAuthErrorMessage(identityErr)
        else -> "Nie udało się połączyć z API Kalendarza Google."
    }
    return "$baseMsg Możesz od razu użyć zsynchronizowanego konta Google w zakładce „Z telefonu” poniżej."
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
