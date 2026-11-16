package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.AppVersionHelper
import com.example.data.model.StudentGroup
import com.example.data.model.TeacherSchoolConfig
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
import com.example.data.model.UserRole
import com.example.notifications.NotificationHelper
import com.example.ui.ZsmeUiState
import com.example.ui.theme.SchoolColorPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: ZsmeUiState,
    onSelectUserRole: (UserRole) -> Unit,
    onSaveTeacherProfile: (String, String) -> Unit,
    onAddTeacherSchool: (schoolName: String, teacherAbbreviation: String, planUrl: String) -> Unit,
    onUpdateTeacherSchool: (TeacherSchoolConfig) -> Unit,
    onRemoveTeacherSchool: (String) -> Unit,
    onRescanTeacherSchools: () -> Unit,
    onOpenClassPicker: () -> Unit,
    onSelectGroup: (StudentGroup) -> Unit,
    onUpdateCustomGroupFilter: (String) -> Unit,
    onSavePortalUrls: (String, String) -> Unit,
    onResetPortalUrls: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSelectInterval: (Long) -> Unit,
    onCheckNow: () -> Unit,
    onSendTestNotification: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleAutoCheckAppUpdates: (Boolean) -> Unit,
    onSaveAppUpdateUrl: (String) -> Unit,
    onCheckAppUpdate: (repoUrl: String?, forceDownload: Boolean) -> Unit,
    onInstallDownloadedApk: () -> Unit,
    onOpenSekretariatWeb: () -> Unit,
    onOpenPlanWeb: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = uiState.preferences
    val updateState = uiState.appUpdateState

    var teacherAbbrInput by remember(prefs.teacherAbbreviation) { mutableStateOf(prefs.teacherAbbreviation) }
    var teacherFullNameInput by remember(prefs.teacherFullName) { mutableStateOf(prefs.teacherFullName) }
    var substitutionsUrlInput by remember(prefs.substitutionsUrl) { mutableStateOf(prefs.substitutionsUrl) }
    var planUrlInput by remember(prefs.planUrl) { mutableStateOf(prefs.planUrl) }
    var appUpdateUrlInput by remember(prefs.appUpdateUrl) {
        mutableStateOf(prefs.appUpdateUrl.ifBlank { UserPreferences.DEFAULT_GITHUB_RELEASES_URL })
    }
    var customFilterInput by remember(prefs.customGroupFilter) { mutableStateOf(prefs.customGroupFilter) }
    var hasNotifPermission by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
    var isAddSchoolDialogOpen by remember { mutableStateOf(false) }
    var editingSchool by remember { mutableStateOf<TeacherSchoolConfig?>(null) }
    var isTeacherPasswordDialogOpen by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "settings_title") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Ustawienia",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Tryb ucznia lub nauczyciela, adresy portali i powiadomienia ZSME",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 0. Wybór roli (Uczeń / Nauczyciel)
            item(key = "role_selector_card") {
                SettingsSectionCard(
                    title = "Tryb aplikacji",
                    icon = if (prefs.userRole == UserRole.TEACHER) Icons.Filled.Badge else Icons.Filled.School
                ) {
                    Text(
                        text = "Wybierz, czy korzystasz z aplikacji jako uczeń (klasy i grupy), czy jako nauczyciel (Twój plan i Twoje zastępstwa; wymaga hasła dostępowego).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UserRole.entries.forEachIndexed { index, role ->
                            SegmentedButton(
                                selected = prefs.userRole == role,
                                onClick = {
                                    if (role == UserRole.TEACHER && prefs.userRole != UserRole.TEACHER) {
                                        isTeacherPasswordDialogOpen = true
                                    } else if (role != prefs.userRole) {
                                        onSelectUserRole(role)
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = UserRole.entries.size
                                ),
                                icon = {
                                    SegmentedButtonDefaults.Icon(active = prefs.userRole == role) {
                                        Icon(
                                            imageVector = if (role == UserRole.TEACHER) Icons.Filled.Badge else Icons.Filled.School,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("settings_role_${role.name.lowercase()}")
                            ) {
                                Text(role.label)
                            }
                        }
                    }
                }
            }

            // 1. Klasa i grupa (Uczeń) lub Profil nauczyciela (Nauczyciel)
            if (prefs.userRole == UserRole.TEACHER) {
                item(key = "teacher_profile_card") {
                    val primaryTokens = SchoolColorPalette.tokensForIndex(0)
                    val primaryLabel = UserPreferences.deriveSchoolLabel(prefs.planUrl, "ZSME")
                    SettingsSectionCard(
                        title = "Profil nauczyciela (szkoła główna)",
                        icon = Icons.Filled.Badge
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = primaryTokens.container.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, primaryTokens.accent.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(primaryTokens.accent)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Placówka główna: $primaryLabel",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryTokens.onContainer
                                    )
                                    Text(
                                        text = prefs.planUrl.ifBlank { UserPreferences.DEFAULT_PLAN_URL },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Wpisz swój skrót z planu lekcji (np. JK) oraz opcjonalnie imię i nazwisko (np. Jan Kowalski lub Kowalski). W zakładce Plan aplikacja przeszuka wszystkie plany pod kątem skrótu, a w Zastępstwach pokaże zmiany dotyczące Ciebie.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = teacherAbbrInput,
                            onValueChange = { teacherAbbrInput = it.uppercase() },
                            label = { Text("Skrót nauczyciela z planu (np. JK)") },
                            placeholder = { Text("np. JK, AB") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_teacher_abbr_input")
                        )
                        OutlinedTextField(
                            value = teacherFullNameInput,
                            onValueChange = { teacherFullNameInput = it },
                            label = { Text("Nazwisko i imię (do zastępstw)") },
                            placeholder = { Text("np. Kowalski lub Jan Kowalski") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_teacher_name_input")
                        )
                        Button(
                            onClick = { onSaveTeacherProfile(teacherAbbrInput, teacherFullNameInput) },
                            enabled = teacherAbbrInput.isNotBlank() || teacherFullNameInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("save_teacher_profile_button")
                        ) {
                            Text("Zapisz i wyszukaj mój plan")
                        }
                    }
                }

                item(key = "teacher_additional_schools_card") {
                    SettingsSectionCard(
                        title = "Dodatkowe szkoły (łączenie planów)",
                        icon = Icons.Filled.Domain
                    ) {
                        Text(
                            text = "Jeśli pracujesz w kilku placówkach, dodaj kolejne szkoły z Twoim skrótem oraz linkiem do ich planu lekcji. Wszystkie zajęcia zostaną połączone w jeden plan, a każda szkoła otrzyma własny kolor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (prefs.additionalSchools.isEmpty()) {
                            Text(
                                text = "Brak dodatkowych szkół. Kliknij poniżej, aby dodać kolejną placówkę.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                prefs.additionalSchools.forEachIndexed { idx, school ->
                                    val cIdx = if (school.colorIndex > 0) school.colorIndex else (idx + 1)
                                    val tokens = SchoolColorPalette.tokensForIndex(cIdx)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = tokens.container.copy(alpha = 0.45f),
                                        border = BorderStroke(1.dp, tokens.accent.copy(alpha = 0.55f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("additional_school_item_${school.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(tokens.accent)
                                            )
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = school.schoolName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Skrót: ${school.teacherAbbreviation}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = tokens.onContainer
                                                )
                                                Text(
                                                    text = school.planUrl,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = { editingSchool = school },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("edit_teacher_school_${school.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Edit,
                                                    contentDescription = "Edytuj szkołę ${school.schoolName}"
                                                )
                                            }
                                            IconButton(
                                                onClick = { onRemoveTeacherSchool(school.id) },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("delete_teacher_school_${school.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.DeleteOutline,
                                                    contentDescription = "Usuń szkołę ${school.schoolName}",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.isRefreshingTimetable) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = "Przeszukuję wszystkie plany w skonfigurowanych szkołach...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (uiState.timetable != null && uiState.timetable.generatedInfo.isNotBlank()) {
                            Text(
                                text = "Ostatnie połączenie planów: ${uiState.timetable.generatedInfo}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (uiState.timetableError != null) {
                            Text(
                                text = uiState.timetableError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        FilledTonalButton(
                            onClick = { isAddSchoolDialogOpen = true },
                            enabled = !uiState.isRefreshingTimetable,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("add_teacher_school_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Dodaj kolejną szkołę")
                        }

                        if (prefs.allTeacherSchools().isNotEmpty()) {
                            OutlinedButton(
                                onClick = onRescanTeacherSchools,
                                enabled = !uiState.isRefreshingTimetable,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("rescan_teacher_schools_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Przeszukaj wszystkie plany i zaktualizuj")
                            }
                        }
                    }
                }
            } else {
                item(key = "class_group_card") {
                    SettingsSectionCard(
                        title = "Klasa i grupa ucznia",
                        icon = Icons.Filled.School
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (prefs.selectedClassName.isBlank()) "Nie wybrano klasy"
                                    else "Klasa ${prefs.selectedClassName}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Grupa: ${prefs.selectedGroup.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = onOpenClassPicker,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_pick_class_button")
                            ) {
                                Text(if (prefs.selectedClassName.isBlank()) "Wybierz" else "Zmień")
                            }
                        }

                        Text(
                            text = "Podgrupa w klasie:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudentGroup.entries.forEach { group ->
                                FilterChip(
                                    selected = prefs.selectedGroup == group,
                                    onClick = { onSelectGroup(group) },
                                    label = { Text(group.shortLabel) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("settings_group_${group.name.lowercase()}")
                                )
                            }
                        }

                        OutlinedTextField(
                            value = customFilterInput,
                            onValueChange = {
                                customFilterInput = it
                                onUpdateCustomGroupFilter(it)
                            },
                            label = { Text("Dodatkowy tag grupy (opcjonalnie)") },
                            placeholder = { Text("np. ang, niem, inf") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_group_input")
                        )
                    }
                }
            }

            // 2. Edytowalne linki do zastępstw i planu lekcji
            item(key = "portal_urls_card") {
                SettingsSectionCard(
                    title = "Adresy stron (zastępstwa i plan)",
                    icon = Icons.Filled.Link
                ) {
                    Text(
                        text = "Możesz zmienić źródłowe adresy URL. Domyślnie zastępstwa pobierane są bezpośrednio z sekretariat.zsme.pl/tv.php.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = substitutionsUrlInput,
                        onValueChange = { substitutionsUrlInput = it },
                        label = { Text("Link do zastępstw") },
                        placeholder = { Text(UserPreferences.DEFAULT_SUBSTITUTIONS_URL) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("substitutions_url_input")
                    )
                    OutlinedTextField(
                        value = planUrlInput,
                        onValueChange = { planUrlInput = it },
                        label = { Text("Link do planu lekcji") },
                        placeholder = { Text(UserPreferences.DEFAULT_PLAN_URL) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("plan_url_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                substitutionsUrlInput = UserPreferences.DEFAULT_SUBSTITUTIONS_URL
                                planUrlInput = UserPreferences.DEFAULT_PLAN_URL
                                onResetPortalUrls()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("reset_urls_button")
                        ) {
                            Text("Przywróć domyślne")
                        }
                        Button(
                            onClick = { onSavePortalUrls(substitutionsUrlInput, planUrlInput) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("save_urls_button")
                        ) {
                            Text("Zapisz linki")
                        }
                    }
                }
            }

            // 3. Aktualizacja aplikacji z GitHuba / repozytorium APK
            item(key = "app_update_card") {
                SettingsSectionCard(
                    title = "Aktualizacja aplikacji (GitHub / APK)",
                    icon = Icons.Filled.SystemUpdate
                ) {
                    Text(
                        text = "Wersja zainstalowana: ${AppVersionHelper.formatDisplayVersion(BuildConfig.VERSION_NAME)}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Automatyczne sprawdzanie aktualizacji",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Szukaj nowej wersji na GitHubie przy uruchomieniu aplikacji.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.autoCheckAppUpdates,
                            onCheckedChange = onToggleAutoCheckAppUpdates,
                            modifier = Modifier.testTag("auto_check_app_updates_switch")
                        )
                    }
                    Text(
                        text = "Wybierz źródło aktualizacji lub wklej bezpośredni link do pliku .apk / wydania GitHub Releases:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            "GitHub (najnowsze)" to UserPreferences.DEFAULT_GITHUB_RELEASES_URL,
                            "Serwer ZSME" to UserPreferences.DEFAULT_APP_UPDATE_URL
                        )
                        presets.forEach { (label, presetUrl) ->
                            FilterChip(
                                selected = appUpdateUrlInput.trim().equals(presetUrl, ignoreCase = true),
                                onClick = {
                                    appUpdateUrlInput = presetUrl
                                    onSaveAppUpdateUrl(presetUrl)
                                },
                                label = { Text(label) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                        }
                    }

                    OutlinedTextField(
                        value = appUpdateUrlInput,
                        onValueChange = { appUpdateUrlInput = it },
                        label = { Text("Link do aktualizacji (GitHub / APK)") },
                        placeholder = { Text(UserPreferences.DEFAULT_GITHUB_RELEASES_URL) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_update_url_input")
                    )

                    if (updateState.isChecking) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Sprawdzanie dostępności pliku APK...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (updateState.isDownloading) {
                        LinearProgressIndicator(
                            progress = { (updateState.downloadProgress.coerceIn(0, 100)) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Pobieranie pliku APK: ${updateState.downloadProgress}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    updateState.checkResult?.let { res ->
                        val cand = res.candidate
                        Text(
                            text = res.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (res.updateAvailable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (cand != null) {
                            val metaDetails = buildString {
                                append("Plik: ${cand.fileName}")
                                if (cand.versionName.isNotBlank()) {
                                    append(" • ${AppVersionHelper.formatDisplayVersion(cand.versionName)}")
                                }
                                if (cand.contentLength > 0L) {
                                    val mb = cand.contentLength / (1024f * 1024f)
                                    append(" • ${"%.1f".format(mb)} MB")
                                }
                                val dateInfo = cand.lastModifiedHeader.ifBlank { cand.directoryDateText }
                                if (dateInfo.isNotBlank()) append(" • Data: $dateInfo")
                            }
                            Text(
                                text = metaDetails,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (updateState.errorMessage != null) {
                        Text(
                            text = updateState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onCheckAppUpdate(appUpdateUrlInput.trim(), false) },
                            enabled = !updateState.isChecking && !updateState.isDownloading,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("check_app_update_button")
                        ) {
                            if (updateState.isChecking || updateState.isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Sprawdź aktualizacje")
                        }
                        if (updateState.downloadedApkPath != null && !updateState.isDownloading) {
                            OutlinedButton(
                                onClick = onInstallDownloadedApk,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("install_downloaded_apk_button")
                            ) {
                                Text("Zainstaluj")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { onCheckAppUpdate(appUpdateUrlInput.trim(), true) },
                        enabled = !updateState.isChecking && !updateState.isDownloading && appUpdateUrlInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("force_download_apk_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pobierz i zainstaluj APK z podanego linku")
                    }
                }
            }

            // 4. Powiadomienia push / w tle
            item(key = "notifications_card") {
                SettingsSectionCard(
                    title = "Powiadomienia o zmianach",
                    icon = Icons.Filled.NotificationsActive
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Monitorowanie w tle",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (prefs.userRole == UserRole.TEACHER) {
                                    "Automatyczne powiadomienia o zastępstwach dotyczących Twojego skrótu lub nazwiska."
                                } else {
                                    "Automatyczne powiadomienia o nowych zastępstwach i zmianach planu dla Twojej grupy."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.notificationsEnabled,
                            onCheckedChange = onToggleNotifications,
                            modifier = Modifier.testTag("notifications_switch")
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                        OutlinedButton(
                            onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                        ) {
                            Text("Przyznaj uprawnienie powiadomień systemu Android")
                        }
                    }

                    Text(
                        text = "Częstotliwość sprawdzania:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15L to "15 min", 30L to "30 min", 60L to "1 godz.", 120L to "2 godz.").forEach { (mins, label) ->
                            FilterChip(
                                selected = prefs.checkIntervalMinutes == mins,
                                onClick = { onSelectInterval(mins) },
                                label = { Text(label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("interval_chip_$mins")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCheckNow,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("check_now_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sprawdź teraz")
                        }
                        OutlinedButton(
                            onClick = onSendTestNotification,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("test_notification_button")
                        ) {
                            Text("Test alertu")
                        }
                    }
                }
            }

            // 5. Wygląd
            item(key = "appearance_card") {
                SettingsSectionCard(
                    title = "Wygląd aplikacji",
                    icon = Icons.Filled.Palette
                ) {
                    Text(
                        text = "Motyw:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = prefs.themeMode == mode,
                                onClick = { onSelectThemeMode(mode) },
                                label = { Text(mode.displayName) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("theme_${mode.name.lowercase()}")
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Kolory dynamiczne (Material You)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Dopasuj kolory do tapety urządzenia.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = prefs.useDynamicColor,
                                onCheckedChange = onToggleDynamicColor
                            )
                        }
                    }
                }
            }

            // 6. Portale szkolne
            item(key = "portals_card") {
                SettingsSectionCard(
                    title = "Bezpośredni podgląd WWW",
                    icon = Icons.Filled.OpenInBrowser
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenSekretariatWeb,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) {
                            Text("Zastępstwa WWW")
                        }
                        OutlinedButton(
                            onClick = onOpenPlanWeb,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) {
                            Text("Plan lekcji WWW")
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (isAddSchoolDialogOpen) {
        val nextColorIdx = (prefs.additionalSchools.maxOfOrNull { it.colorIndex } ?: 0) + 1
        TeacherSchoolEditorDialog(
            initialSchool = null,
            previewColorIndex = nextColorIdx,
            onDismiss = { isAddSchoolDialogOpen = false },
            onSave = { name, abbr, url ->
                onAddTeacherSchool(name, abbr, url)
                isAddSchoolDialogOpen = false
            }
        )
    }

    editingSchool?.let { current ->
        TeacherSchoolEditorDialog(
            initialSchool = current,
            previewColorIndex = if (current.colorIndex > 0) current.colorIndex else 1,
            onDismiss = { editingSchool = null },
            onSave = { name, abbr, url ->
                val resolvedName = name.trim().ifBlank {
                    UserPreferences.deriveSchoolLabel(url, fallback = current.schoolName)
                }
                onUpdateTeacherSchool(
                    current.copy(
                        schoolName = resolvedName,
                        teacherAbbreviation = abbr.trim().uppercase(),
                        planUrl = url.trim()
                    )
                )
                editingSchool = null
            }
        )
    }

    if (isTeacherPasswordDialogOpen) {
        TeacherAccessPasswordDialog(
            onDismiss = { isTeacherPasswordDialogOpen = false },
            onUnlocked = {
                isTeacherPasswordDialogOpen = false
                onSelectUserRole(UserRole.TEACHER)
            }
        )
    }
}

@Composable
private fun TeacherAccessPasswordDialog(
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    fun attemptUnlock() {
        if (UserPreferences.isValidTeacherAccessPassword(passwordInput)) {
            hasError = false
            onUnlocked()
        } else {
            hasError = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Włącz profil nauczyciela",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Aby przełączyć aplikację na profil nauczyciela, wpisz hasło dostępowe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        if (hasError) hasError = false
                    },
                    label = { Text("Hasło nauczyciela") },
                    placeholder = { Text("Wpisz hasło") },
                    singleLine = true,
                    isError = hasError,
                    supportingText = if (hasError) {
                        { Text("Nieprawidłowe hasło. Spróbuj ponownie.") }
                    } else {
                        null
                    },
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { attemptUnlock() }
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Ukryj hasło" else "Pokaż hasło"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("teacher_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = ::attemptUnlock,
                enabled = passwordInput.isNotBlank(),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("teacher_password_confirm_button")
            ) {
                Text("Odblokuj")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("teacher_password_cancel_button")
            ) {
                Text("Anuluj")
            }
        }
    )
}

@Composable
private fun TeacherSchoolEditorDialog(
    initialSchool: TeacherSchoolConfig?,
    previewColorIndex: Int,
    onDismiss: () -> Unit,
    onSave: (schoolName: String, teacherAbbreviation: String, planUrl: String) -> Unit
) {
    var schoolName by remember(initialSchool?.id) {
        mutableStateOf(initialSchool?.schoolName.orEmpty())
    }
    var abbreviation by remember(initialSchool?.id) {
        mutableStateOf(initialSchool?.teacherAbbreviation.orEmpty())
    }
    var planUrl by remember(initialSchool?.id) {
        mutableStateOf(initialSchool?.planUrl.orEmpty())
    }
    val previewTokens = SchoolColorPalette.tokensForIndex(previewColorIndex)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(previewTokens.accent)
                )
                Text(
                    text = if (initialSchool == null) "Dodaj kolejną szkołę" else "Edytuj szkołę",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Podaj nazwę placówki, swój skrót nauczyciela w jej planie oraz adres URL do planu lekcji (np. strona główna Optivum lub bezpośredni link do planu n*.html).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    label = { Text("Nazwa szkoły (opcjonalnie)") },
                    placeholder = { Text("np. ZST, I LO") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_school_name_input")
                )
                OutlinedTextField(
                    value = abbreviation,
                    onValueChange = { abbreviation = it.uppercase() },
                    label = { Text("Twój skrót w tej szkole") },
                    placeholder = { Text("np. JK, KOW") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_school_abbr_input")
                )
                OutlinedTextField(
                    value = planUrl,
                    onValueChange = { planUrl = it },
                    label = { Text("Link do planu tej szkoły") },
                    placeholder = { Text("https://plan.szkola.pl/") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_school_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(schoolName, abbreviation, planUrl) },
                enabled = abbreviation.isNotBlank() && planUrl.isNotBlank(),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dialog_school_save_button")
            ) {
                Text("Zapisz i połącz plan")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Anuluj")
            }
        }
    )
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}
