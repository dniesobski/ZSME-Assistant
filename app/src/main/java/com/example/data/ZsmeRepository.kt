package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.BuildConfig
import com.example.data.local.PreferencesRepository
import com.example.data.model.ApkCandidate
import com.example.data.model.AppUpdateCheckResult
import com.example.data.model.ClassTimetable
import com.example.data.model.NotificationLogEntry
import com.example.data.model.ScheduleFilterMatcher
import com.example.data.model.SchoolClass
import com.example.data.model.SubstitutionsSnapshot
import com.example.data.model.TimetableCatalog
import com.example.data.model.TimetableTarget
import com.example.data.model.UserPreferences
import com.example.data.model.UserRole
import com.example.data.network.ZsmeHtmlParser
import com.example.data.network.ZsmePortalClient
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class SyncCheckSummary(
    val substitutionsUpdated: Boolean = false,
    val timetableUpdated: Boolean = false,
    val matchingCount: Int = 0,
    val errorMessage: String? = null
)

class ZsmeRepository(
    private val context: Context,
    val preferencesRepository: PreferencesRepository,
    val portalClient: ZsmePortalClient
) {
    val userPreferences: Flow<UserPreferences> = preferencesRepository.userPreferences
    val cachedClasses: Flow<List<SchoolClass>> = preferencesRepository.cachedClasses
    val cachedCatalog: Flow<TimetableCatalog> = preferencesRepository.cachedCatalog
    val cachedTimetable: Flow<ClassTimetable?> = preferencesRepository.cachedTimetable
    val cachedSubstitutions: Flow<SubstitutionsSnapshot?> = preferencesRepository.cachedSubstitutions

    suspend fun refreshClassList(): Result<List<SchoolClass>> {
        return refreshTimetableCatalog().map { it.classes }
    }

    suspend fun refreshTimetableCatalog(): Result<TimetableCatalog> {
        val prefs = preferencesRepository.getPreferencesSnapshot()
        val result = portalClient.fetchTimetableCatalog(
            prefs.planUrl.ifBlank { UserPreferences.DEFAULT_PLAN_URL }
        )
        result.onSuccess { catalog ->
            preferencesRepository.saveCachedCatalog(catalog)
        }
        return result
    }

    suspend fun fetchTargetTimetable(
        target: TimetableTarget,
        forceRescan: Boolean = false
    ): Result<ClassTimetable> {
        val prefs = preferencesRepository.getPreferencesSnapshot()
        val planUrl = prefs.planUrl.ifBlank { UserPreferences.DEFAULT_PLAN_URL }
        val catalog = cachedCatalog.first()
        return portalClient.fetchTargetTimetable(
            target = target,
            baseUrl = planUrl,
            knownCatalog = catalog,
            forceRescan = forceRescan
        )
    }

    fun invalidateSchoolCache(planUrl: String) {
        portalClient.invalidateSchoolCache(planUrl)
    }

    suspend fun refreshTimetable(
        schoolClass: SchoolClass? = null,
        notifyOnChange: Boolean = false,
        forceRescan: Boolean = notifyOnChange
    ): Result<ClassTimetable> {
        val prefs = preferencesRepository.getPreferencesSnapshot()

        val (result, displayTargetName) = if (prefs.userRole == UserRole.TEACHER && schoolClass == null) {
            val schools = prefs.allTeacherSchools()
            if (schools.isEmpty()) {
                return Result.failure(IllegalStateException("Wpisz swój skrót nauczyciela w Ustawieniach."))
            }
            val catalog = cachedCatalog.first()
            val planUrl = prefs.planUrl.ifBlank { UserPreferences.DEFAULT_PLAN_URL }
            val teacherRes = portalClient.fetchMultiSchoolTeacherSchedule(
                schools = schools,
                knownPrimaryCatalog = catalog,
                primaryBaseUrl = planUrl,
                forceRescan = forceRescan
            )
            val label = if (schools.size > 1) {
                "Nauczyciel (${schools.size} placówki)"
            } else {
                "Nauczyciel ${prefs.teacherAbbreviation}"
            }
            teacherRes to label
        } else {
            val targetClass = schoolClass ?: if (prefs.selectedClassName.isNotBlank() && prefs.selectedClassUrl.isNotBlank()) {
                SchoolClass(
                    id = prefs.selectedClassId,
                    name = prefs.selectedClassName,
                    url = prefs.selectedClassUrl
                )
            } else {
                return Result.failure(IllegalStateException("Najpierw wybierz klasę w ustawieniach."))
            }
            portalClient.fetchTimetable(targetClass) to targetClass.name
        }

        result.onSuccess { timetable ->
            preferencesRepository.saveCachedTimetable(timetable)
            val prevHash = prefs.lastNotifiedTimetableHash
            if (notifyOnChange && prefs.notificationsEnabled && prevHash.isNotBlank() && prevHash != timetable.contentHash) {
                NotificationHelper.showTimetableChangedNotification(context, displayTargetName)
                preferencesRepository.appendNotificationLog(
                    NotificationLogEntry(
                        id = "${System.currentTimeMillis()}_tt",
                        title = "Zmiana planu lekcji ($displayTargetName)",
                        message = "Wykryto aktualizację planu na plan.zsme.pl.",
                        timestampMillis = System.currentTimeMillis(),
                        category = "TIMETABLE"
                    )
                )
            }
            if (timetable.contentHash.isNotBlank()) {
                preferencesRepository.updateNotifiedHashes(timetableHash = timetable.contentHash)
            }
        }
        return result
    }

    suspend fun refreshSubstitutions(
        notifyOnChange: Boolean = false
    ): Result<SubstitutionsSnapshot> {
        val prefs = preferencesRepository.getPreferencesSnapshot()
        val result = portalClient.fetchSubstitutions(
            accessKey = prefs.secretariatKey.ifBlank { UserPreferences.DEFAULT_SECRETARIAT_KEY },
            baseUrl = prefs.substitutionsUrl.ifBlank { UserPreferences.DEFAULT_SUBSTITUTIONS_URL }
        )
        result.onSuccess { snapshot ->
            preferencesRepository.saveCachedSubstitutions(snapshot)

            val isTeacherActive = prefs.userRole == UserRole.TEACHER &&
                (prefs.teacherAbbreviation.isNotBlank() || prefs.teacherFullName.isNotBlank())
            val isStudentActive = prefs.userRole == UserRole.STUDENT &&
                prefs.selectedClassName.isNotBlank()

            if (isTeacherActive || isStudentActive) {
                val matching = snapshot.items.filter { item ->
                    if (isTeacherActive) {
                        ScheduleFilterMatcher.matchesTeacherSubstitution(
                            item = item,
                            teacherAbbr = prefs.teacherAbbreviation,
                            teacherFullName = prefs.teacherFullName
                        )
                    } else {
                        ScheduleFilterMatcher.matchesSubstitution(
                            item = item,
                            selectedClassName = prefs.selectedClassName,
                            selectedGroup = prefs.selectedGroup,
                            customGroupFilter = prefs.customGroupFilter
                        )
                    }
                }
                val matchingHash = ZsmeHtmlParser.sha256(
                    matching.joinToString("|") {
                        "${it.dateLabel}:${it.period}:${it.subject}:${it.substituteTeacher}:${it.room}:${it.note}"
                    }
                )
                val prevHash = prefs.lastNotifiedSubstitutionsHash
                val notifTarget = if (isTeacherActive) {
                    prefs.teacherAbbreviation.ifBlank { prefs.teacherFullName }
                } else {
                    prefs.selectedClassName
                }
                val notifRoleLabel = if (isTeacherActive) "Nauczyciel" else prefs.selectedGroup.shortLabel

                if (notifyOnChange && prefs.notificationsEnabled && matching.isNotEmpty() && matchingHash != prevHash) {
                    NotificationHelper.showSubstitutionsNotification(
                        context = context,
                        className = notifTarget,
                        groupLabel = notifRoleLabel,
                        items = matching,
                        dateHeading = snapshot.dateHeading
                    )
                    preferencesRepository.appendNotificationLog(
                        NotificationLogEntry(
                            id = "${System.currentTimeMillis()}_sub",
                            title = "Zastępstwa dla $notifTarget ($notifRoleLabel)",
                            message = matching.take(3).joinToString("; ") {
                                "Lek. ${it.period.ifBlank { "?" }} (${it.className}): ${it.subject.ifBlank { it.note }}"
                            },
                            timestampMillis = System.currentTimeMillis(),
                            category = "SUBSTITUTION"
                        )
                    )
                }
                preferencesRepository.updateNotifiedHashes(substitutionsHash = matchingHash)
            }
        }
        return result
    }

    suspend fun runFullSyncAndNotify(): SyncCheckSummary {
        val prefs = preferencesRepository.getPreferencesSnapshot()
        var subUpdated = false
        var ttUpdated = false
        var matchingCount = 0
        var firstError: String? = null

        val prevSubHash = prefs.lastNotifiedSubstitutionsHash
        val prevTtHash = prefs.lastNotifiedTimetableHash

        val subResult = refreshSubstitutions(notifyOnChange = true)
        subResult.onSuccess { snapshot ->
            val matching = snapshot.items.filter { item ->
                if (prefs.userRole == UserRole.TEACHER) {
                    ScheduleFilterMatcher.matchesTeacherSubstitution(
                        item = item,
                        teacherAbbr = prefs.teacherAbbreviation,
                        teacherFullName = prefs.teacherFullName
                    )
                } else {
                    ScheduleFilterMatcher.matchesSubstitution(
                        item = item,
                        selectedClassName = prefs.selectedClassName,
                        selectedGroup = prefs.selectedGroup,
                        customGroupFilter = prefs.customGroupFilter
                    )
                }
            }
            matchingCount = matching.size
            val updatedPrefs = preferencesRepository.getPreferencesSnapshot()
            subUpdated = matching.isNotEmpty() && updatedPrefs.lastNotifiedSubstitutionsHash != prevSubHash
        }.onFailure { err ->
            firstError = err.message
        }

        val shouldCheckTimetable = if (prefs.userRole == UserRole.TEACHER) {
            prefs.allTeacherSchools().isNotEmpty()
        } else {
            prefs.selectedClassUrl.isNotBlank()
        }
        if (shouldCheckTimetable) {
            val ttResult = refreshTimetable(notifyOnChange = true)
            ttResult.onSuccess { timetable ->
                ttUpdated = prevTtHash.isNotBlank() && timetable.contentHash != prevTtHash
            }.onFailure { err ->
                if (firstError == null) firstError = err.message
            }
        }

        return SyncCheckSummary(
            substitutionsUpdated = subUpdated,
            timetableUpdated = ttUpdated,
            matchingCount = matchingCount,
            errorMessage = firstError
        )
    }

    suspend fun checkAppUpdate(
        repoUrl: String = UserPreferences.DEFAULT_GITHUB_APK_URL
    ): Result<AppUpdateCheckResult> {
        val prefs = preferencesRepository.getPreferencesSnapshot()
        val lastUpdateMillis = runCatching {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pkgInfo.lastUpdateTime
        }.getOrDefault(0L)

        return portalClient.checkAppUpdateInRepo(
            repoUrl = repoUrl,
            currentVersionName = BuildConfig.VERSION_NAME,
            installedLastUpdateMillis = lastUpdateMillis,
            prefs = prefs
        )
    }

    suspend fun downloadAppUpdate(
        candidate: ApkCandidate,
        onProgress: (Int) -> Unit
    ): Result<java.io.File> {
        val res = portalClient.downloadApkToCache(
            cacheDir = context.cacheDir,
            candidate = candidate,
            onProgress = onProgress
        )
        res.onSuccess {
            preferencesRepository.setLastKnownApkMetadata(candidate)
        }
        return res
    }

    suspend fun rememberCurrentApkMetadata(candidate: ApkCandidate) {
        preferencesRepository.setLastKnownApkMetadata(candidate)
    }
}
