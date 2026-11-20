package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.ZsmeApplication
import com.example.data.ZsmeRepository
import com.example.data.model.ApkCandidate
import com.example.data.model.AppUpdateCheckResult
import com.example.data.model.AppVersionHelper
import com.example.data.model.ClassTimetable
import com.example.data.model.PolishSchoolCalendar
import com.example.data.model.PrincipalDayOff
import com.example.data.model.ScheduleFilterMatcher
import com.example.data.model.SchoolClass
import com.example.data.model.StudentGroup
import com.example.data.model.SubstitutionItem
import com.example.data.model.SubstitutionsSnapshot
import com.example.data.model.TeacherSchoolConfig
import com.example.data.model.ThemeMode
import com.example.data.model.TimetableCatalog
import com.example.data.model.TimetableCategory
import com.example.data.model.TimetableTarget
import com.example.data.model.UserPreferences
import com.example.data.model.UserRole
import com.example.mesh.BleMeshManager
import com.example.mesh.MeshMessage
import com.example.mesh.MeshNickAvailability
import com.example.mesh.MeshPeer
import com.example.mesh.MeshRadioState
import com.example.mesh.MeshStats
import com.example.notifications.NotificationHelper
import com.example.notifications.ScheduleSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

enum class SubstitutionsFilterTab(val label: String) {
    MY_GROUP("Moja grupa"),
    ALL_CLASSES("Wszystkie"),
    ANNOUNCEMENTS("Informacje")
}

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val isInstalling: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val checkResult: AppUpdateCheckResult? = null,
    val pendingInstallApkPath: String? = null,
    val downloadedApkPath: String? = null,
    val isStartupUpdateDialogVisible: Boolean = false,
    val isUpdateProgressDialogVisible: Boolean = false,
    val installStatusMessage: String? = null,
    val errorMessage: String? = null
)

data class ZsmeUiState(
    val preferences: UserPreferences = UserPreferences(),
    val availableClasses: List<SchoolClass> = emptyList(),
    val timetableCatalog: TimetableCatalog = TimetableCatalog(),
    val selectedTimetableCategory: TimetableCategory = TimetableCategory.CLASS,
    val selectedTeacherOrRoom: TimetableTarget? = null,
    val timetable: ClassTimetable? = null,
    val targetTimetable: ClassTimetable? = null,
    val substitutions: SubstitutionsSnapshot? = null,
    val isRefreshingSubstitutions: Boolean = false,
    val isRefreshingTimetable: Boolean = false,
    val isLoadingClasses: Boolean = false,
    val substitutionsError: String? = null,
    val timetableError: String? = null,
    val classesError: String? = null,
    val selectedDayIndex: Int = defaultWeekdayIndex(),
    val substitutionsTab: SubstitutionsFilterTab = SubstitutionsFilterTab.MY_GROUP,
    val snackbarMessage: String? = null,
    val isClassPickerOpen: Boolean = false,
    val isTargetPickerOpen: Boolean = false,
    val isTeacherProfileDialogOpen: Boolean = false,
    // Mesh Messenger state
    val meshRadioState: MeshRadioState = MeshRadioState.IDLE,
    val meshPeers: List<MeshPeer> = emptyList(),
    val meshKnownNicks: List<MeshNickAvailability> = emptyList(),
    val meshNickChecks: Map<String, MeshNickAvailability> = emptyMap(),
    val selectedMeshRecipientNick: String = "",
    val meshStats: MeshStats = MeshStats(),
    val meshStatusDetail: String? = null,
    val meshMessages: List<MeshMessage> = emptyList(),
    val activeMeshChannel: String = "#ogólny",
    val myMeshNodeId: String = "",
    // App update state
    val appUpdateState: AppUpdateUiState = AppUpdateUiState()
) {
    val activeDisplayedTimetable: ClassTimetable?
        get() = if (preferences.userRole == UserRole.TEACHER || selectedTimetableCategory == TimetableCategory.CLASS) {
            timetable
        } else {
            targetTimetable
        }

    val myGroupSubstitutions: List<SubstitutionItem>
        get() {
            val items = substitutions?.items.orEmpty()
            return if (preferences.userRole == UserRole.TEACHER) {
                if (preferences.teacherAbbreviation.isBlank() && preferences.teacherFullName.isBlank()) {
                    items
                } else {
                    items.filter { item ->
                        ScheduleFilterMatcher.matchesTeacherSubstitution(
                            item = item,
                            teacherAbbr = preferences.teacherAbbreviation,
                            teacherFullName = preferences.teacherFullName
                        )
                    }
                }
            } else {
                if (preferences.selectedClassName.isBlank()) return items
                items.filter { item ->
                    ScheduleFilterMatcher.matchesSubstitution(
                        item = item,
                        selectedClassName = preferences.selectedClassName,
                        selectedGroup = preferences.selectedGroup,
                        customGroupFilter = preferences.customGroupFilter
                    )
                }
            }
        }

    companion object {
        fun defaultWeekdayIndex(): Int {
            val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            return when (day) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                else -> 0
            }
        }
    }
}

private data class TransientUiFlags(
    val isRefreshingSubstitutions: Boolean = false,
    val isRefreshingTimetable: Boolean = false,
    val isLoadingClasses: Boolean = false,
    val substitutionsError: String? = null,
    val timetableError: String? = null,
    val classesError: String? = null,
    val selectedDayIndex: Int = ZsmeUiState.defaultWeekdayIndex(),
    val substitutionsTab: SubstitutionsFilterTab = SubstitutionsFilterTab.MY_GROUP,
    val snackbarMessage: String? = null,
    val isClassPickerOpen: Boolean = false,
    val isTargetPickerOpen: Boolean = false,
    val isTeacherProfileDialogOpen: Boolean = false,
    val selectedTimetableCategory: TimetableCategory = TimetableCategory.CLASS,
    val selectedTeacherOrRoom: TimetableTarget? = null,
    val targetTimetable: ClassTimetable? = null,
    val activeMeshChannel: String = "#ogólny",
    val selectedMeshRecipientNick: String = ""
)

private data class MeshLiveSnapshot(
    val radioState: MeshRadioState = MeshRadioState.IDLE,
    val peers: List<MeshPeer> = emptyList(),
    val knownNicks: List<MeshNickAvailability> = emptyList(),
    val nickChecks: Map<String, MeshNickAvailability> = emptyMap(),
    val stats: MeshStats = MeshStats(),
    val statusDetail: String? = null,
    val messages: List<MeshMessage> = emptyList()
)

class ZsmeViewModel(application: Application) : AndroidViewModel(application) {

    private val app: ZsmeApplication = application as ZsmeApplication
    private val repository: ZsmeRepository = app.repository
    private val prefsRepo = repository.preferencesRepository
    val meshManager: BleMeshManager = app.meshManager
    private val meshStorageMutex = Mutex()

    @Volatile
    private var userManuallyPausedMesh = false

    private val transientFlags = MutableStateFlow(TransientUiFlags())
    private val appUpdateFlow = MutableStateFlow(AppUpdateUiState())

    private val meshNickFlow = combine(
        meshManager.knownNetworkNicks,
        meshManager.nickAvailabilityChecks
    ) { known, checks -> known to checks }

    private val meshBasicFlow = combine(
        meshManager.radioState,
        meshManager.peers,
        meshManager.stats,
        meshManager.statusDetail,
        prefsRepo.cachedMeshMessages
    ) { radioState, peers, stats, detail, cachedMsgs ->
        MeshLiveSnapshot(
            radioState = radioState,
            peers = peers,
            stats = stats,
            statusDetail = detail,
            messages = cachedMsgs
        )
    }

    private val meshLiveFlow = combine(
        meshBasicFlow,
        meshNickFlow
    ) { basic, (known, checks) ->
        basic.copy(knownNicks = known, nickChecks = checks)
    }

    private val coreDataFlow = combine(
        repository.userPreferences,
        repository.cachedCatalog,
        repository.cachedTimetable,
        repository.cachedSubstitutions
    ) { prefs, catalog, timetable, subs ->
        CoreData(prefs, catalog, timetable, subs)
    }

    private data class CoreData(
        val prefs: UserPreferences,
        val catalog: TimetableCatalog,
        val timetable: ClassTimetable?,
        val subs: SubstitutionsSnapshot?
    )

    val uiState: StateFlow<ZsmeUiState> = combine(
        coreDataFlow,
        transientFlags,
        meshLiveFlow,
        appUpdateFlow
    ) { core, flags, mesh, updateState ->
        val effectiveRadioState = if (mesh.radioState == MeshRadioState.IDLE) {
            meshManager.evaluateReadiness()
        } else {
            mesh.radioState
        }
        ZsmeUiState(
            preferences = core.prefs,
            availableClasses = core.catalog.classes,
            timetableCatalog = core.catalog,
            selectedTimetableCategory = flags.selectedTimetableCategory,
            selectedTeacherOrRoom = flags.selectedTeacherOrRoom,
            timetable = core.timetable,
            targetTimetable = flags.targetTimetable,
            substitutions = core.subs,
            isRefreshingSubstitutions = flags.isRefreshingSubstitutions,
            isRefreshingTimetable = flags.isRefreshingTimetable,
            isLoadingClasses = flags.isLoadingClasses,
            substitutionsError = flags.substitutionsError,
            timetableError = flags.timetableError,
            classesError = flags.classesError,
            selectedDayIndex = flags.selectedDayIndex,
            substitutionsTab = flags.substitutionsTab,
            snackbarMessage = flags.snackbarMessage,
            isClassPickerOpen = flags.isClassPickerOpen,
            isTargetPickerOpen = flags.isTargetPickerOpen,
            isTeacherProfileDialogOpen = flags.isTeacherProfileDialogOpen,
            meshRadioState = effectiveRadioState,
            meshPeers = mesh.peers,
            meshKnownNicks = mesh.knownNicks,
            meshNickChecks = mesh.nickChecks,
            selectedMeshRecipientNick = flags.selectedMeshRecipientNick,
            meshStats = mesh.stats,
            meshStatusDetail = mesh.statusDetail,
            meshMessages = mesh.messages,
            activeMeshChannel = flags.activeMeshChannel,
            myMeshNodeId = meshManager.myNodeId,
            appUpdateState = updateState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ZsmeUiState(myMeshNodeId = meshManager.myNodeId)
    )

    init {
        refreshAllInitial()
    }

    private fun refreshAllInitial() {
        viewModelScope.launch {
            val prefs = prefsRepo.getPreferencesSnapshot()
            refreshClassList(silent = true)
            refreshSubstitutions(notifyOnChange = false)
            if (prefs.userRole == UserRole.TEACHER && prefs.allTeacherSchools().isNotEmpty()) {
                refreshTimetable(notifyOnChange = false)
            } else if (prefs.userRole == UserRole.STUDENT && prefs.selectedClassUrl.isNotBlank()) {
                refreshTimetable(notifyOnChange = false)
            }
            checkForAppUpdateOnStartup()
        }
    }

    fun refreshClassList(silent: Boolean = false) {
        viewModelScope.launch {
            transientFlags.update { it.copy(isLoadingClasses = true, classesError = null) }
            val result = repository.refreshTimetableCatalog()
            result.onSuccess { catalog ->
                transientFlags.update { it.copy(isLoadingClasses = false, classesError = null) }
                val activeCat = transientFlags.value.selectedTimetableCategory
                val currentTarget = transientFlags.value.selectedTeacherOrRoom
                if (activeCat != TimetableCategory.CLASS && currentTarget?.category != activeCat) {
                    val firstTarget = when (activeCat) {
                        TimetableCategory.TEACHER -> catalog.teachers.firstOrNull()
                        TimetableCategory.ROOM -> catalog.rooms.firstOrNull()
                        TimetableCategory.CLASS -> null
                    }
                    if (firstTarget != null) {
                        selectTeacherOrRoom(firstTarget)
                    }
                }
            }.onFailure { err ->
                transientFlags.update {
                    it.copy(
                        isLoadingClasses = false,
                        classesError = if (silent) null else (err.message ?: "Nie udało się pobrać listy planów")
                    )
                }
            }
        }
    }

    fun refreshSubstitutions(notifyOnChange: Boolean = true) {
        viewModelScope.launch {
            transientFlags.update { it.copy(isRefreshingSubstitutions = true, substitutionsError = null) }
            val result = repository.refreshSubstitutions(notifyOnChange = notifyOnChange)
            result.onSuccess { snapshot ->
                val errorMsg = if (!snapshot.authSucceeded) {
                    "Strona wymaga poprawnego klucza dostępu. Sprawdź klucz w Ustawieniach lub użyj adresu tv.php."
                } else null
                transientFlags.update {
                    it.copy(
                        isRefreshingSubstitutions = false,
                        substitutionsError = errorMsg
                    )
                }
            }.onFailure { err ->
                transientFlags.update {
                    it.copy(
                        isRefreshingSubstitutions = false,
                        substitutionsError = err.message ?: "Błąd połączenia ze stroną zastępstw"
                    )
                }
            }
        }
    }

    fun refreshTimetable(notifyOnChange: Boolean = true, forceRescan: Boolean = notifyOnChange) {
        val role = uiState.value.preferences.userRole
        val category = transientFlags.value.selectedTimetableCategory
        val selectedTarget = transientFlags.value.selectedTeacherOrRoom
        if (role != UserRole.TEACHER && category != TimetableCategory.CLASS && selectedTarget != null) {
            fetchTeacherOrRoomTimetable(selectedTarget, forceRescan = true)
            return
        }

        viewModelScope.launch {
            transientFlags.update { it.copy(isRefreshingTimetable = true, timetableError = null) }
            val result = repository.refreshTimetable(notifyOnChange = notifyOnChange, forceRescan = forceRescan)
            result.onSuccess { tt ->
                transientFlags.update {
                    it.copy(
                        isRefreshingTimetable = false,
                        timetableError = null,
                        snackbarMessage = if (forceRescan && role == UserRole.TEACHER) {
                            "Zaktualizowano plan (${tt.generatedInfo})"
                        } else {
                            it.snackbarMessage
                        }
                    )
                }
            }.onFailure { err ->
                transientFlags.update {
                    it.copy(
                        isRefreshingTimetable = false,
                        timetableError = err.message ?: "Nie udało się pobrać planu lekcji z plan.zsme.pl"
                    )
                }
            }
        }
    }

    fun selectTimetableCategory(category: TimetableCategory) {
        transientFlags.update {
            it.copy(
                selectedTimetableCategory = category,
                timetableError = null
            )
        }
        val catalog = uiState.value.timetableCatalog
        val needsCatalogRefresh = when (category) {
            TimetableCategory.TEACHER -> catalog.teachers.isEmpty() ||
                catalog.teachers.none { it.url.startsWith(com.example.data.network.ZsmePortalClient.TEACHER_SCAN_URL_PREFIX) }
            TimetableCategory.ROOM -> catalog.rooms.isEmpty()
            TimetableCategory.CLASS -> false
        }
        if (needsCatalogRefresh) {
            refreshClassList(silent = catalog.teachers.isNotEmpty())
        }
        if (category != TimetableCategory.CLASS && transientFlags.value.selectedTeacherOrRoom?.category != category) {
            val firstTarget = when (category) {
                TimetableCategory.TEACHER -> catalog.teachers.firstOrNull()
                TimetableCategory.ROOM -> catalog.rooms.firstOrNull()
                TimetableCategory.CLASS -> null
            }
            if (firstTarget != null) {
                selectTeacherOrRoom(firstTarget)
            } else {
                transientFlags.update { it.copy(selectedTeacherOrRoom = null, targetTimetable = null) }
            }
        }
    }

    fun selectTeacherOrRoom(target: TimetableTarget) {
        transientFlags.update {
            it.copy(
                selectedTimetableCategory = target.category,
                selectedTeacherOrRoom = target,
                isTargetPickerOpen = false
            )
        }
        fetchTeacherOrRoomTimetable(target, forceRescan = false)
    }

    private fun fetchTeacherOrRoomTimetable(
        target: TimetableTarget,
        forceRescan: Boolean = false
    ) {
        viewModelScope.launch {
            transientFlags.update { it.copy(isRefreshingTimetable = true, timetableError = null) }
            val result = repository.fetchTargetTimetable(target, forceRescan = forceRescan)
            result.onSuccess { tt ->
                transientFlags.update {
                    it.copy(
                        isRefreshingTimetable = false,
                        targetTimetable = tt,
                        timetableError = null
                    )
                }
            }.onFailure { err ->
                transientFlags.update {
                    it.copy(
                        isRefreshingTimetable = false,
                        timetableError = err.message ?: "Nie udało się pobrać planu dla ${target.name}"
                    )
                }
            }
        }
    }

    fun setTargetPickerOpen(open: Boolean) {
        transientFlags.update { it.copy(isTargetPickerOpen = open) }
        if (open) {
            val catalog = uiState.value.timetableCatalog
            val category = transientFlags.value.selectedTimetableCategory
            val missingForCat = (category == TimetableCategory.TEACHER && catalog.teachers.isEmpty()) ||
                (category == TimetableCategory.ROOM && catalog.rooms.isEmpty()) ||
                (catalog.teachers.isEmpty() && catalog.rooms.isEmpty())
            if (missingForCat) {
                refreshClassList(silent = false)
            }
        }
    }

    fun selectClass(schoolClass: SchoolClass) {
        viewModelScope.launch {
            prefsRepo.setSelectedClass(schoolClass)
            transientFlags.update {
                it.copy(
                    isClassPickerOpen = false,
                    selectedTimetableCategory = TimetableCategory.CLASS,
                    snackbarMessage = "Wybrano klasę ${schoolClass.name}"
                )
            }
            if (schoolClass.url.isNotBlank()) {
                refreshTimetable(notifyOnChange = false)
            }
            refreshSubstitutions(notifyOnChange = false)
        }
    }

    fun selectManualClass(className: String) {
        val clean = className.trim().uppercase()
        if (clean.isBlank()) return
        val existing = uiState.value.availableClasses.firstOrNull {
            ScheduleFilterMatcher.normalizeClassToken(it.name) == ScheduleFilterMatcher.normalizeClassToken(clean)
        }
        if (existing != null) {
            selectClass(existing)
        } else {
            selectClass(
                SchoolClass(
                    id = clean.lowercase(),
                    name = clean,
                    url = ""
                )
            )
        }
    }

    fun selectGroup(group: StudentGroup) {
        viewModelScope.launch {
            prefsRepo.setSelectedGroup(group)
            transientFlags.update {
                it.copy(snackbarMessage = "Aktywna grupa: ${group.shortLabel}")
            }
        }
    }

    fun setUserRole(role: UserRole) {
        viewModelScope.launch {
            prefsRepo.setUserRole(role)
            transientFlags.update {
                it.copy(
                    selectedTimetableCategory = TimetableCategory.CLASS,
                    timetableError = null,
                    snackbarMessage = if (role == UserRole.TEACHER) {
                        "Tryb nauczyciela — wpisz swój skrót i nazwisko"
                    } else {
                        "Tryb ucznia — wybierz klasę i grupę"
                    }
                )
            }
            val prefs = prefsRepo.getPreferencesSnapshot()
            if (role == UserRole.TEACHER && prefs.allTeacherSchools().isNotEmpty()) {
                refreshTimetable(notifyOnChange = false)
            } else if (role == UserRole.STUDENT && prefs.selectedClassUrl.isNotBlank()) {
                refreshTimetable(notifyOnChange = false)
            }
            refreshSubstitutions(notifyOnChange = false)
        }
    }

    fun saveTeacherProfile(abbreviation: String, fullName: String) {
        val cleanAbbr = abbreviation.trim().uppercase()
        val cleanName = fullName.trim()
        if (cleanAbbr.isBlank() && cleanName.isBlank()) return
        viewModelScope.launch {
            prefsRepo.setTeacherProfile(cleanAbbr, cleanName)
            val prefs = prefsRepo.getPreferencesSnapshot()
            repository.invalidateSchoolCache(prefs.planUrl)
            transientFlags.update {
                it.copy(
                    isTeacherProfileDialogOpen = false,
                    timetableError = null,
                    snackbarMessage = "Zapisano profil nauczyciela: ${cleanAbbr.ifBlank { cleanName }}"
                )
            }
            if (cleanAbbr.isNotBlank() || prefs.allTeacherSchools().isNotEmpty()) {
                runTeacherMultiSchoolRescan(targetAbbr = cleanAbbr, targetSchoolLabel = null)
            }
            refreshSubstitutions(notifyOnChange = false)
        }
    }

    fun addTeacherSchool(schoolName: String, teacherAbbreviation: String, planUrl: String) {
        val cleanAbbr = teacherAbbreviation.trim().uppercase()
        val cleanUrl = planUrl.trim()
        if (cleanAbbr.isBlank() || cleanUrl.isBlank()) return
        viewModelScope.launch {
            prefsRepo.addTeacherSchool(
                schoolName = schoolName,
                teacherAbbreviation = cleanAbbr,
                planUrl = cleanUrl
            )
            repository.invalidateSchoolCache(cleanUrl)
            val resolvedLabel = schoolName.trim().ifBlank {
                UserPreferences.deriveSchoolLabel(cleanUrl, fallback = "Dodatkowa szkoła")
            }
            transientFlags.update {
                it.copy(
                    timetableError = null,
                    snackbarMessage = "Dodano szkołę $resolvedLabel ($cleanAbbr) — przeszukuję wszystkie plany..."
                )
            }
            runTeacherMultiSchoolRescan(targetAbbr = cleanAbbr, targetSchoolLabel = resolvedLabel)
        }
    }

    fun updateTeacherSchool(school: TeacherSchoolConfig) {
        if (school.teacherAbbreviation.isBlank() || school.planUrl.isBlank()) return
        viewModelScope.launch {
            prefsRepo.updateTeacherSchool(school)
            repository.invalidateSchoolCache(school.planUrl)
            transientFlags.update {
                it.copy(
                    timetableError = null,
                    snackbarMessage = "Zaktualizowano ${school.schoolName} — przeszukuję plany..."
                )
            }
            runTeacherMultiSchoolRescan(
                targetAbbr = school.teacherAbbreviation,
                targetSchoolLabel = school.schoolName
            )
        }
    }

    fun removeTeacherSchool(schoolId: String) {
        viewModelScope.launch {
            prefsRepo.removeTeacherSchool(schoolId)
            transientFlags.update {
                it.copy(
                    timetableError = null,
                    snackbarMessage = "Usunięto dodatkową szkołę — aktualizuję plan..."
                )
            }
            val prefs = prefsRepo.getPreferencesSnapshot()
            if (prefs.allTeacherSchools().isNotEmpty()) {
                runTeacherMultiSchoolRescan(targetAbbr = null, targetSchoolLabel = null)
            }
        }
    }

    private suspend fun runTeacherMultiSchoolRescan(
        targetAbbr: String?,
        targetSchoolLabel: String?
    ) {
        transientFlags.update { it.copy(isRefreshingTimetable = true, timetableError = null) }
        val result = repository.refreshTimetable(notifyOnChange = false, forceRescan = true)
        result.onSuccess { tt ->
            val allLessons = tt.days.flatMap { it.lessons }
            val matchingCount = if (!targetSchoolLabel.isNullOrBlank()) {
                allLessons.count { it.schoolName.equals(targetSchoolLabel, ignoreCase = true) }
            } else {
                allLessons.size
            }
            val hasPartialFailure = !targetSchoolLabel.isNullOrBlank() &&
                tt.generatedInfo.contains("Błąd pobierania", ignoreCase = true) &&
                tt.generatedInfo.contains(targetSchoolLabel, ignoreCase = true)

            val statusMsg = when {
                hasPartialFailure ->
                    "Nie udało się pobrać planu dla $targetSchoolLabel (${tt.generatedInfo.substringAfter("Błąd pobierania:")})"
                !targetSchoolLabel.isNullOrBlank() && matchingCount > 0 ->
                    "Zaktualizowano $targetSchoolLabel: znaleziono $matchingCount lekcji dla skrótu $targetAbbr."
                !targetSchoolLabel.isNullOrBlank() && matchingCount == 0 ->
                    "Przeszukano $targetSchoolLabel, ale nie znaleziono lekcji dla skrótu $targetAbbr."
                else ->
                    "Zaktualizowano plan (${tt.generatedInfo})"
            }
            transientFlags.update {
                it.copy(
                    isRefreshingTimetable = false,
                    timetableError = if (hasPartialFailure) statusMsg else null,
                    snackbarMessage = statusMsg
                )
            }
        }.onFailure { err ->
            val errMsg = err.message ?: "Nie udało się przeszukać planów szkoły"
            transientFlags.update {
                it.copy(
                    isRefreshingTimetable = false,
                    timetableError = errMsg,
                    snackbarMessage = errMsg
                )
            }
        }
    }

    fun setTeacherProfileDialogOpen(open: Boolean) {
        transientFlags.update { it.copy(isTeacherProfileDialogOpen = open) }
    }

    fun updateCustomGroupFilter(filter: String) {
        viewModelScope.launch {
            prefsRepo.setCustomGroupFilter(filter)
        }
    }

    fun updateSecretariatKey(key: String) {
        viewModelScope.launch {
            prefsRepo.setSecretariatKey(key)
            transientFlags.update { it.copy(snackbarMessage = "Zapisano klucz dostępu do sekretariatu") }
            refreshSubstitutions(notifyOnChange = false)
        }
    }

    fun updatePortalUrls(substitutionsUrl: String, planUrl: String) {
        viewModelScope.launch {
            prefsRepo.setPortalUrls(substitutionsUrl, planUrl)
            transientFlags.update { it.copy(snackbarMessage = "Zapisano adresy portali i odświeżono dane") }
            refreshSubstitutions(notifyOnChange = false)
            refreshClassList(silent = false)
        }
    }

    fun resetPortalUrls() {
        updatePortalUrls(
            substitutionsUrl = UserPreferences.DEFAULT_SUBSTITUTIONS_URL,
            planUrl = UserPreferences.DEFAULT_PLAN_URL
        )
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setNotificationsEnabled(enabled)
            val app = getApplication<ZsmeApplication>()
            if (enabled) {
                val interval = uiState.value.preferences.checkIntervalMinutes
                ScheduleSyncWorker.schedulePeriodicSync(app, interval)
                transientFlags.update { it.copy(snackbarMessage = "Powiadomienia o zastępstwach włączone") }
            } else {
                ScheduleSyncWorker.cancelPeriodicSync(app)
                transientFlags.update { it.copy(snackbarMessage = "Powiadomienia w tle wyłączone") }
            }
        }
    }

    fun setCheckIntervalMinutes(minutes: Long) {
        viewModelScope.launch {
            prefsRepo.setCheckIntervalMinutes(minutes)
            val app = getApplication<ZsmeApplication>()
            if (uiState.value.preferences.notificationsEnabled) {
                ScheduleSyncWorker.schedulePeriodicSync(app, minutes)
            }
            transientFlags.update { it.copy(snackbarMessage = "Częstotliwość sprawdzania: co $minutes min") }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefsRepo.setThemeMode(mode)
        }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setUseDynamicColor(enabled)
        }
    }

    fun runImmediateSyncCheck() {
        viewModelScope.launch {
            transientFlags.update { it.copy(isRefreshingSubstitutions = true, isRefreshingTimetable = true) }
            val summary = repository.runFullSyncAndNotify()
            val msg = when {
                summary.errorMessage != null -> "Błąd sprawdzania: ${summary.errorMessage}"
                summary.substitutionsUpdated -> "Wykryto nowe zastępstwa (${summary.matchingCount}) — wysłano powiadomienie!"
                summary.timetableUpdated -> "Wykryto aktualizację planu lekcji — wysłano powiadomienie!"
                else -> "Sprawdzono portale ZSME. Brak nowych zmian dla Twojej grupy (${summary.matchingCount} pasujących zastępstw)."
            }
            transientFlags.update {
                it.copy(
                    isRefreshingSubstitutions = false,
                    isRefreshingTimetable = false,
                    snackbarMessage = msg
                )
            }
        }
    }

    fun sendTestNotification() {
        val app = getApplication<ZsmeApplication>()
        val prefs = uiState.value.preferences
        val sent = if (prefs.userRole == UserRole.TEACHER) {
            NotificationHelper.showTestNotification(
                context = app,
                className = prefs.teacherAbbreviation.ifBlank { prefs.teacherFullName },
                groupLabel = "Nauczyciel"
            )
        } else {
            NotificationHelper.showTestNotification(
                context = app,
                className = prefs.selectedClassName,
                groupLabel = prefs.selectedGroup.shortLabel
            )
        }
        transientFlags.update {
            it.copy(
                snackbarMessage = if (sent) {
                    "Wysłano testowe powiadomienie systemowe"
                } else {
                    "Brak uprawnień do powiadomień. Zezwól na powiadomienia w systemie."
                }
            )
        }
    }

    // --- Bluetooth Mesh Messenger actions ---

    fun toggleMeshRadio() {
        if (meshManager.radioState.value == MeshRadioState.ACTIVE ||
            meshManager.radioState.value == MeshRadioState.STARTING
        ) {
            userManuallyPausedMesh = true
            meshManager.stopMesh()
        } else {
            userManuallyPausedMesh = false
            meshManager.startMesh(resolveMeshNodeLabel())
        }
    }

    fun startMeshIfPermitted() {
        userManuallyPausedMesh = false
        meshManager.refreshReadiness()
        meshManager.startMesh(resolveMeshNodeLabel())
    }

    fun refreshMeshReadiness(autoStartIfReady: Boolean = true) {
        meshManager.refreshReadiness()
        if (autoStartIfReady &&
            !userManuallyPausedMesh &&
            meshManager.hasBleHardware() &&
            meshManager.hasBlePermissions() &&
            meshManager.isBluetoothEnabled() &&
            meshManager.isLocationEnabledIfNeeded() &&
            meshManager.radioState.value != MeshRadioState.ACTIVE &&
            meshManager.radioState.value != MeshRadioState.STARTING
        ) {
            meshManager.startMesh(resolveMeshNodeLabel())
        }
    }

    private fun resolveMeshNodeLabel(): String {
        val prefs = uiState.value.preferences
        return prefs.meshNickname.ifBlank {
            if (prefs.userRole == UserRole.TEACHER && prefs.teacherAbbreviation.isNotBlank()) {
                prefs.teacherAbbreviation
            } else {
                prefs.selectedClassName.ifBlank { "ZSME" }
            }
        }
    }

    fun updateMeshNickname(nickname: String) {
        viewModelScope.launch {
            prefsRepo.setMeshNickname(nickname)
            val fallbackLabel = resolveMeshNodeLabel()
            meshManager.updateNodeLabel(nickname.ifBlank { fallbackLabel })
            transientFlags.update { it.copy(snackbarMessage = "Zapisano nazwę w sieci Bluetooth") }
        }
    }

    fun selectMeshChannel(channel: String) {
        transientFlags.update { it.copy(activeMeshChannel = channel) }
    }

    fun setMeshRecipientNick(nick: String) {
        val clean = nick.trim()
        transientFlags.update { flags ->
            flags.copy(
                selectedMeshRecipientNick = clean,
                activeMeshChannel = if (clean.isNotBlank()) MeshMessage.DIRECT_CHANNEL else flags.activeMeshChannel
            )
        }
    }

    fun checkMeshNickAvailability(nick: String) {
        val clean = nick.trim()
        if (clean.isBlank()) return
        val senderLabel = resolveMeshNodeLabel()
        if (meshManager.radioState.value != MeshRadioState.ACTIVE &&
            meshManager.hasBleHardware() &&
            meshManager.hasBlePermissions() &&
            meshManager.isBluetoothEnabled() &&
            meshManager.isLocationEnabledIfNeeded()
        ) {
            userManuallyPausedMesh = false
            meshManager.startMesh(senderLabel)
        }

        val result = meshManager.checkNicknameAvailability(
            targetNick = clean,
            senderName = senderLabel
        )
        val msg = if (result.isAvailable) {
            if (result.viaMeshHops == 0) {
                "Nick $clean jest dostępny bezpośrednio w zasięgu BLE."
            } else {
                "Nick $clean jest dostępny w sieci (${result.viaMeshHops} skoki)."
            }
        } else {
            "Wysłano zapytanie BLE o dostępność nicka $clean w całej sieci..."
        }
        transientFlags.update { it.copy(snackbarMessage = msg) }
    }

    fun sendMeshMessage(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return

        val prefs = uiState.value.preferences
        val flags = transientFlags.value
        val targetRecipient = flags.selectedMeshRecipientNick.trim()
        val effectiveChannel = if (targetRecipient.isNotBlank()) {
            MeshMessage.DIRECT_CHANNEL
        } else {
            flags.activeMeshChannel
        }
        if (effectiveChannel == MeshMessage.DIRECT_CHANNEL && targetRecipient.isBlank()) {
            transientFlags.update { it.copy(snackbarMessage = "Wybierz lub wpisz nick odbiorcy wiadomości prywatnej.") }
            return
        }

        val senderName = prefs.meshNickname.ifBlank {
            if (prefs.userRole == UserRole.TEACHER && prefs.teacherAbbreviation.isNotBlank()) {
                "Nauczyciel ${prefs.teacherAbbreviation}"
            } else if (prefs.selectedClassName.isNotBlank()) {
                "Uczeń ${prefs.selectedClassName}"
            } else {
                "Uczeń ZSME"
            }
        }

        if (meshManager.radioState.value != MeshRadioState.ACTIVE &&
            meshManager.hasBleHardware() &&
            meshManager.hasBlePermissions() &&
            meshManager.isBluetoothEnabled() &&
            meshManager.isLocationEnabledIfNeeded()
        ) {
            userManuallyPausedMesh = false
            meshManager.startMesh(senderName)
        }

        val msg = meshManager.sendMessage(
            senderName = senderName,
            senderClass = if (prefs.userRole == UserRole.TEACHER) prefs.teacherAbbreviation else prefs.selectedClassName,
            channel = effectiveChannel,
            text = clean,
            targetRecipient = targetRecipient
        )
        viewModelScope.launch {
            meshStorageMutex.withLock {
                val current = prefsRepo.cachedMeshMessages.first()
                prefsRepo.saveMeshMessages(current + msg)
            }
        }
    }

    fun clearMeshHistory() {
        viewModelScope.launch {
            meshStorageMutex.withLock {
                prefsRepo.saveMeshMessages(emptyList())
            }
            transientFlags.update { it.copy(snackbarMessage = "Wyczyszczono historię wiadomości") }
        }
    }

    // --- App Repository Update actions ---

    fun updateAppUpdateUrl(url: String) {
        val trimmed = url.trim()
        val clean = if (trimmed.isBlank() || trimmed.equals(UserPreferences.LEGACY_GITHUB_V106_URL, ignoreCase = true)) {
            UserPreferences.DEFAULT_GITHUB_RELEASES_URL
        } else {
            trimmed
        }
        viewModelScope.launch {
            prefsRepo.setAppUpdateUrl(clean)
            transientFlags.update { it.copy(snackbarMessage = "Zapisano adres aktualizacji: $clean") }
        }
    }

    fun setAutoCheckAppUpdates(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAutoCheckAppUpdates(enabled)
            transientFlags.update {
                it.copy(
                    snackbarMessage = if (enabled) {
                        "Automatyczne sprawdzanie aktualizacji włączone"
                    } else {
                        "Automatyczne sprawdzanie aktualizacji wyłączone"
                    }
                )
            }
            if (enabled && appUpdateFlow.value.checkResult == null) {
                checkForAppUpdateOnStartup()
            }
        }
    }

    private fun checkForAppUpdateOnStartup() {
        viewModelScope.launch {
            if (appUpdateFlow.value.isChecking || appUpdateFlow.value.isDownloading) return@launch
            val prefs = prefsRepo.getPreferencesSnapshot()
            if (!prefs.autoCheckAppUpdates) return@launch
            val rawUrl = prefs.appUpdateUrl.trim()
            val effectiveUrl = if (rawUrl.isBlank() ||
                rawUrl.equals(UserPreferences.LEGACY_GITHUB_V106_URL, ignoreCase = true)
            ) {
                UserPreferences.DEFAULT_GITHUB_RELEASES_URL
            } else {
                rawUrl
            }
            val result = repository.checkAppUpdate(effectiveUrl)
            result.onSuccess { checkResult ->
                val candidate = checkResult.candidate
                if (checkResult.updateAvailable && candidate != null) {
                    appUpdateFlow.update {
                        it.copy(
                            checkResult = checkResult,
                            isStartupUpdateDialogVisible = true,
                            errorMessage = null
                        )
                    }
                    if (prefs.notificationsEnabled) {
                        NotificationHelper.showAppUpdateNotification(
                            context = app,
                            candidate = candidate,
                            currentVersionName = checkResult.currentVersionName
                        )
                    }
                } else {
                    appUpdateFlow.update {
                        it.copy(
                            checkResult = it.checkResult ?: checkResult
                        )
                    }
                    if (candidate != null && prefs.lastApkUrl.isBlank()) {
                        repository.rememberCurrentApkMetadata(candidate)
                    }
                }
            }
        }
    }

    fun dismissStartupUpdateDialog() {
        appUpdateFlow.update { it.copy(isStartupUpdateDialogVisible = false) }
    }

    fun dismissUpdateProgressDialog() {
        appUpdateFlow.update { it.copy(isUpdateProgressDialogVisible = false) }
    }

    fun acceptStartupAppUpdate() {
        val state = appUpdateFlow.value
        appUpdateFlow.update {
            it.copy(
                isStartupUpdateDialogVisible = false,
                isUpdateProgressDialogVisible = true,
                errorMessage = null
            )
        }
        val downloaded = state.downloadedApkPath
        if (!downloaded.isNullOrBlank() && java.io.File(downloaded).exists()) {
            installDownloadedApk()
            return
        }
        val candidate = state.checkResult?.candidate
        if (candidate != null) {
            if (state.isChecking || state.isDownloading || state.isInstalling) return
            viewModelScope.launch {
                downloadAndPrepareUpdate(candidate)
            }
        } else {
            checkForAppUpdate(forceDownload = true)
        }
    }

    fun checkForAppUpdate(repoUrl: String? = null, forceDownload: Boolean = false) {
        if (appUpdateFlow.value.isChecking || appUpdateFlow.value.isDownloading || appUpdateFlow.value.isInstalling) return
        viewModelScope.launch {
            val rawCandidate = repoUrl?.trim()?.takeIf { it.isNotBlank() }
                ?: uiState.value.preferences.appUpdateUrl.trim()
            val effectiveUrl = if (rawCandidate.isBlank() ||
                rawCandidate.equals(UserPreferences.LEGACY_GITHUB_V106_URL, ignoreCase = true)
            ) {
                UserPreferences.DEFAULT_GITHUB_RELEASES_URL
            } else {
                rawCandidate
            }

            if (effectiveUrl != uiState.value.preferences.appUpdateUrl) {
                prefsRepo.setAppUpdateUrl(effectiveUrl)
            }

            appUpdateFlow.update {
                it.copy(
                    isChecking = true,
                    errorMessage = null,
                    installStatusMessage = null,
                    downloadProgress = 0,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    isUpdateProgressDialogVisible = forceDownload
                )
            }
            val result = repository.checkAppUpdate(effectiveUrl)
            result.onSuccess { checkResult ->
                appUpdateFlow.update {
                    it.copy(
                        isChecking = false,
                        checkResult = checkResult,
                        errorMessage = null
                    )
                }
                val candidate = checkResult.candidate
                if (candidate != null && (checkResult.updateAvailable || forceDownload)) {
                    appUpdateFlow.update {
                        it.copy(isUpdateProgressDialogVisible = true)
                    }
                    downloadAndPrepareUpdate(candidate)
                } else {
                    if (candidate != null && uiState.value.preferences.lastApkUrl.isBlank()) {
                        repository.rememberCurrentApkMetadata(candidate)
                    }
                    appUpdateFlow.update {
                        it.copy(isUpdateProgressDialogVisible = false)
                    }
                    transientFlags.update {
                        it.copy(
                            snackbarMessage = if (candidate == null) {
                                "Nie znaleziono pliku APK pod adresem $effectiveUrl"
                            } else {
                                "Aplikacja jest aktualna (${AppVersionHelper.formatDisplayVersion(BuildConfig.VERSION_NAME)})"
                            }
                        )
                    }
                }
            }.onFailure { err ->
                val msg = err.message ?: "Nie udało się sprawdzić aktualizacji pod adresem $effectiveUrl"
                appUpdateFlow.update {
                    it.copy(
                        isChecking = false,
                        errorMessage = msg
                    )
                }
                transientFlags.update { it.copy(snackbarMessage = msg) }
            }
        }
    }

    private suspend fun downloadAndPrepareUpdate(candidate: ApkCandidate) {
        appUpdateFlow.update {
            it.copy(
                isDownloading = true,
                isInstalling = false,
                downloadProgress = 0,
                downloadedBytes = 0L,
                totalBytes = candidate.contentLength.coerceAtLeast(0L),
                installStatusMessage = "Pobieranie pliku ${candidate.fileName}...",
                errorMessage = null,
                isUpdateProgressDialogVisible = true
            )
        }
        val downloadRes = repository.downloadAppUpdate(candidate) { progress, downloaded, total ->
            appUpdateFlow.update {
                it.copy(
                    downloadProgress = progress,
                    downloadedBytes = downloaded,
                    totalBytes = if (total > 0L) total else it.totalBytes
                )
            }
        }
        downloadRes.onSuccess { file ->
            appUpdateFlow.update {
                it.copy(
                    isDownloading = false,
                    downloadProgress = 100,
                    downloadedBytes = file.length(),
                    totalBytes = maxOf(file.length(), it.totalBytes),
                    downloadedApkPath = file.absolutePath,
                    pendingInstallApkPath = file.absolutePath,
                    installStatusMessage = "Pobrano! Uruchamiam instalację w aplikacji...",
                    errorMessage = null
                )
            }
        }.onFailure { err ->
            val msg = err.message ?: "Błąd pobierania pliku APK"
            appUpdateFlow.update {
                it.copy(
                    isDownloading = false,
                    isInstalling = false,
                    installStatusMessage = null,
                    errorMessage = msg
                )
            }
            transientFlags.update { it.copy(snackbarMessage = msg) }
        }
    }

    fun installDownloadedApk() {
        val path = appUpdateFlow.value.downloadedApkPath ?: return
        appUpdateFlow.update {
            it.copy(
                pendingInstallApkPath = path,
                isUpdateProgressDialogVisible = true,
                errorMessage = null,
                installStatusMessage = "Przygotowywanie pakietu do instalacji..."
            )
        }
    }

    fun consumePendingApkInstall() {
        appUpdateFlow.update { it.copy(pendingInstallApkPath = null) }
    }

    fun markUpdateInstalling(installing: Boolean, statusMessage: String? = null) {
        appUpdateFlow.update {
            it.copy(
                isInstalling = installing,
                installStatusMessage = statusMessage ?: it.installStatusMessage
            )
        }
    }

    fun onInstallSessionSucceeded() {
        appUpdateFlow.update {
            it.copy(
                isInstalling = false,
                isUpdateProgressDialogVisible = false,
                installStatusMessage = "Zaktualizowano pomyślnie!",
                errorMessage = null
            )
        }
        transientFlags.update {
            it.copy(snackbarMessage = "Aktualizacja aplikacji została zainstalowana.")
        }
    }

    fun onInstallSessionFailed(message: String) {
        appUpdateFlow.update {
            it.copy(
                isInstalling = false,
                installStatusMessage = null,
                errorMessage = message
            )
        }
        transientFlags.update { it.copy(snackbarMessage = message) }
    }

    // --- Principal Days Off / Polish School Calendar actions ---

    fun addPrincipalDaysOff(startIso: String, dayCount: Int, title: String) {
        val items = PolishSchoolCalendar.expandPrincipalDateRange(startIso, dayCount, title)
        if (items.isEmpty()) return
        viewModelScope.launch {
            prefsRepo.addPrincipalDaysOff(
                datesIso = items.map { it.dateIso },
                title = items.first().title
            )
            transientFlags.update {
                it.copy(
                    snackbarMessage = if (items.size == 1) {
                        "Dodano dzień wolny: ${items.first().title} (${items.first().dateIso})"
                    } else {
                        "Dodano ${items.size} dni dyrektorskich: ${items.first().title}"
                    }
                )
            }
        }
    }

    fun removePrincipalDayOff(dateIso: String) {
        viewModelScope.launch {
            prefsRepo.removePrincipalDayOff(dateIso)
            transientFlags.update {
                it.copy(snackbarMessage = "Usunięto dzień dyrektorski ($dateIso)")
            }
        }
    }

    fun setDayIndex(index: Int) {
        transientFlags.update { it.copy(selectedDayIndex = index.coerceIn(0, 4)) }
    }

    fun setSubstitutionsTab(tab: SubstitutionsFilterTab) {
        transientFlags.update { it.copy(substitutionsTab = tab) }
    }

    fun setClassPickerOpen(open: Boolean) {
        transientFlags.update { it.copy(isClassPickerOpen = open) }
        if (open && uiState.value.availableClasses.isEmpty()) {
            refreshClassList()
        }
    }

    fun consumeSnackbar() {
        transientFlags.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        // Keep application-scoped BLE mesh radio running if user enabled it so push notifications continue arriving
    }
}
