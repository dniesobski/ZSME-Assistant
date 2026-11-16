package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ApkCandidate
import com.example.data.model.ClassTimetable
import com.example.data.model.NotificationLogEntry
import com.example.data.model.SchoolClass
import com.example.data.model.StudentGroup
import com.example.data.model.SubstitutionsSnapshot
import com.example.data.model.TeacherSchoolConfig
import com.example.data.model.ThemeMode
import com.example.data.model.TimetableCatalog
import com.example.data.model.UserPreferences
import com.example.data.model.UserRole
import com.example.mesh.MeshMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zsme_plan_prefs")

class PreferencesRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val USER_ROLE = stringPreferencesKey("user_role")
        val TEACHER_ABBREVIATION = stringPreferencesKey("teacher_abbreviation")
        val TEACHER_FULL_NAME = stringPreferencesKey("teacher_full_name")
        val SELECTED_CLASS_ID = stringPreferencesKey("selected_class_id")
        val SELECTED_CLASS_NAME = stringPreferencesKey("selected_class_name")
        val SELECTED_CLASS_URL = stringPreferencesKey("selected_class_url")
        val SELECTED_GROUP = stringPreferencesKey("selected_group")
        val CUSTOM_GROUP_FILTER = stringPreferencesKey("custom_group_filter")
        val SUBSTITUTIONS_URL = stringPreferencesKey("substitutions_url")
        val PLAN_URL = stringPreferencesKey("plan_url")
        val SECRETARIAT_KEY = stringPreferencesKey("secretariat_key")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val CHECK_INTERVAL_MINUTES = longPreferencesKey("check_interval_minutes")
        val LAST_NOTIFIED_SUB_HASH = stringPreferencesKey("last_notified_sub_hash")
        val LAST_NOTIFIED_TIMETABLE_HASH = stringPreferencesKey("last_notified_timetable_hash")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val RECENT_NOTIFICATIONS_JSON = stringPreferencesKey("recent_notifications_json")
        val MESH_NICKNAME = stringPreferencesKey("mesh_nickname")
        val LAST_APK_URL = stringPreferencesKey("last_apk_url")
        val LAST_APK_FILENAME = stringPreferencesKey("last_apk_filename")
        val LAST_APK_VERSION = stringPreferencesKey("last_apk_version")
        val LAST_APK_LAST_MODIFIED = stringPreferencesKey("last_apk_last_modified")
        val LAST_APK_ETAG = stringPreferencesKey("last_apk_etag")
        val LAST_APK_CONTENT_LENGTH = longPreferencesKey("last_apk_content_length")
        val APP_UPDATE_URL = stringPreferencesKey("app_update_url")
        val TEACHER_ADDITIONAL_SCHOOLS_JSON = stringPreferencesKey("teacher_additional_schools_json")

        val CACHED_CLASSES_JSON = stringPreferencesKey("cached_classes_json")
        val CACHED_CATALOG_JSON = stringPreferencesKey("cached_catalog_json")
        val CACHED_TIMETABLE_JSON = stringPreferencesKey("cached_timetable_json")
        val CACHED_SUBSTITUTIONS_JSON = stringPreferencesKey("cached_substitutions_json")
        val CACHED_MESH_MESSAGES_JSON = stringPreferencesKey("cached_mesh_messages_json")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val groupName = prefs[Keys.SELECTED_GROUP] ?: StudentGroup.ALL.name
        val group = StudentGroup.entries.firstOrNull { it.name == groupName } ?: StudentGroup.ALL

        val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM

        val logs = prefs[Keys.RECENT_NOTIFICATIONS_JSON]?.let { raw ->
            runCatching { json.decodeFromString<List<NotificationLogEntry>>(raw) }.getOrNull()
        }.orEmpty()

        val roleName = prefs[Keys.USER_ROLE] ?: UserRole.STUDENT.name
        val userRole = UserRole.entries.firstOrNull { it.name == roleName } ?: UserRole.STUDENT

        val extraSchools = prefs[Keys.TEACHER_ADDITIONAL_SCHOOLS_JSON]?.let { raw ->
            runCatching { json.decodeFromString<List<TeacherSchoolConfig>>(raw) }.getOrNull()
        }.orEmpty()

        UserPreferences(
            userRole = userRole,
            teacherAbbreviation = prefs[Keys.TEACHER_ABBREVIATION].orEmpty(),
            teacherFullName = prefs[Keys.TEACHER_FULL_NAME].orEmpty(),
            selectedClassId = prefs[Keys.SELECTED_CLASS_ID].orEmpty(),
            selectedClassName = prefs[Keys.SELECTED_CLASS_NAME].orEmpty(),
            selectedClassUrl = prefs[Keys.SELECTED_CLASS_URL].orEmpty(),
            selectedGroup = group,
            customGroupFilter = prefs[Keys.CUSTOM_GROUP_FILTER].orEmpty(),
            substitutionsUrl = prefs[Keys.SUBSTITUTIONS_URL]?.takeIf { it.isNotBlank() }
                ?: UserPreferences.DEFAULT_SUBSTITUTIONS_URL,
            planUrl = prefs[Keys.PLAN_URL]?.takeIf { it.isNotBlank() }
                ?: UserPreferences.DEFAULT_PLAN_URL,
            secretariatKey = prefs[Keys.SECRETARIAT_KEY] ?: UserPreferences.DEFAULT_SECRETARIAT_KEY,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            checkIntervalMinutes = prefs[Keys.CHECK_INTERVAL_MINUTES] ?: 15L,
            lastNotifiedSubstitutionsHash = prefs[Keys.LAST_NOTIFIED_SUB_HASH].orEmpty(),
            lastNotifiedTimetableHash = prefs[Keys.LAST_NOTIFIED_TIMETABLE_HASH].orEmpty(),
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
            themeMode = themeMode,
            recentNotifications = logs,
            meshNickname = prefs[Keys.MESH_NICKNAME].orEmpty(),
            lastApkUrl = prefs[Keys.LAST_APK_URL].orEmpty(),
            lastApkFileName = prefs[Keys.LAST_APK_FILENAME].orEmpty(),
            lastApkVersion = prefs[Keys.LAST_APK_VERSION].orEmpty(),
            lastApkLastModified = prefs[Keys.LAST_APK_LAST_MODIFIED].orEmpty(),
            lastApkETag = prefs[Keys.LAST_APK_ETAG].orEmpty(),
            lastApkContentLength = prefs[Keys.LAST_APK_CONTENT_LENGTH] ?: 0L,
            appUpdateUrl = prefs[Keys.APP_UPDATE_URL]?.takeIf { it.isNotBlank() }
                ?: UserPreferences.DEFAULT_GITHUB_APK_URL,
            additionalSchools = extraSchools
        )
    }

    val cachedClasses: Flow<List<SchoolClass>> = context.dataStore.data.map { prefs ->
        prefs[Keys.CACHED_CLASSES_JSON]?.let { raw ->
            runCatching { json.decodeFromString<List<SchoolClass>>(raw) }.getOrNull()
        }.orEmpty()
    }

    val cachedCatalog: Flow<TimetableCatalog> = context.dataStore.data.map { prefs ->
        prefs[Keys.CACHED_CATALOG_JSON]?.let { raw ->
            runCatching { json.decodeFromString<TimetableCatalog>(raw) }.getOrNull()
        } ?: TimetableCatalog()
    }

    val cachedTimetable: Flow<ClassTimetable?> = context.dataStore.data.map { prefs ->
        prefs[Keys.CACHED_TIMETABLE_JSON]?.let { raw ->
            runCatching { json.decodeFromString<ClassTimetable>(raw) }.getOrNull()
        }
    }

    val cachedSubstitutions: Flow<SubstitutionsSnapshot?> = context.dataStore.data.map { prefs ->
        prefs[Keys.CACHED_SUBSTITUTIONS_JSON]?.let { raw ->
            runCatching { json.decodeFromString<SubstitutionsSnapshot>(raw) }.getOrNull()
        }
    }

    val cachedMeshMessages: Flow<List<MeshMessage>> = context.dataStore.data.map { prefs ->
        prefs[Keys.CACHED_MESH_MESSAGES_JSON]?.let { raw ->
            runCatching { json.decodeFromString<List<MeshMessage>>(raw) }.getOrNull()
        }.orEmpty()
    }

    suspend fun getPreferencesSnapshot(): UserPreferences = userPreferences.first()

    suspend fun setUserRole(role: UserRole) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ROLE] = role.name
            prefs[Keys.LAST_NOTIFIED_SUB_HASH] = ""
            prefs[Keys.LAST_NOTIFIED_TIMETABLE_HASH] = ""
        }
    }

    suspend fun setTeacherProfile(abbreviation: String, fullName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ROLE] = UserRole.TEACHER.name
            prefs[Keys.TEACHER_ABBREVIATION] = abbreviation.trim().uppercase()
            prefs[Keys.TEACHER_FULL_NAME] = fullName.trim()
            prefs[Keys.LAST_NOTIFIED_SUB_HASH] = ""
            prefs[Keys.LAST_NOTIFIED_TIMETABLE_HASH] = ""
        }
    }

    suspend fun addTeacherSchool(
        schoolName: String,
        teacherAbbreviation: String,
        planUrl: String
    ) {
        val normalizedUrl = normalizeHttpUrl(planUrl, "")
        val cleanAbbr = teacherAbbreviation.trim().uppercase()
        if (cleanAbbr.isBlank() || normalizedUrl.isBlank()) return
        val cleanName = schoolName.trim().ifBlank {
            UserPreferences.deriveSchoolLabel(normalizedUrl, fallback = "Dodatkowa szkoła")
        }
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TEACHER_ADDITIONAL_SCHOOLS_JSON]?.let { raw ->
                runCatching { json.decodeFromString<List<TeacherSchoolConfig>>(raw) }.getOrNull()
            }.orEmpty()
            val usedColors = current.map { it.colorIndex }.toSet()
            val nextColor = (1..12).firstOrNull { !usedColors.contains(it) } ?: (current.size + 1)
            val newItem = TeacherSchoolConfig(
                id = "school_${UUID.randomUUID().toString().take(8)}",
                schoolName = cleanName,
                teacherAbbreviation = cleanAbbr,
                planUrl = normalizedUrl,
                colorIndex = nextColor
            )
            prefs[Keys.TEACHER_ADDITIONAL_SCHOOLS_JSON] = json.encodeToString(current + newItem)
            prefs[Keys.LAST_NOTIFIED_TIMETABLE_HASH] = ""
        }
    }

    suspend fun updateTeacherSchool(updated: TeacherSchoolConfig) {
        val normalizedUrl = normalizeHttpUrl(updated.planUrl, "")
        val cleanAbbr = updated.teacherAbbreviation.trim().uppercase()
        if (cleanAbbr.isBlank() || normalizedUrl.isBlank()) return
        val cleanName = updated.schoolName.trim().ifBlank {
            UserPreferences.deriveSchoolLabel(normalizedUrl, fallback = "Dodatkowa szkoła")
        }
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TEACHER_ADDITIONAL_SCHOOLS_JSON]?.let { raw ->
                runCatching { json.decodeFromString<List<TeacherSchoolConfig>>(raw) }.getOrNull()
            }.orEmpty()
            val next = current.map { item ->
                if (item.id == updated.id) {
                    item.copy(
                        schoolName = cleanName,
                        teacherAbbreviation = cleanAbbr,
                        planUrl = normalizedUrl
                    )
                } else {
                    item
                }
            }
            prefs[Keys.TEACHER_ADDITIONAL_SCHOOLS_JSON] = json.encodeToString(next)
            prefs[Keys.LAST_NOTIFIED_TIMETABLE_HASH] = ""
        }
    }

    suspend fun removeTeacherSchool(schoolId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TEACHER_ADDITIONAL_SCHOOLS_JSON]?.let { raw ->
                runCatching { json.decodeFromString<List<TeacherSchoolConfig>>(raw) }.getOrNull()
            }.orEmpty()
            val next = current.filterNot { it.id == schoolId }
            prefs[Keys.TEACHER_ADDITIONAL_SCHOOLS_JSON] = json.encodeToString(next)
            prefs[Keys.LAST_NOTIFIED_TIMETABLE_HASH] = ""
        }
    }

    suspend fun setSelectedClass(schoolClass: SchoolClass) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_CLASS_ID] = schoolClass.id
            prefs[Keys.SELECTED_CLASS_NAME] = schoolClass.name
            prefs[Keys.SELECTED_CLASS_URL] = schoolClass.url
            prefs[Keys.LAST_NOTIFIED_SUB_HASH] = ""
            prefs[Keys.LAST_NOTIFIED_TIMETABLE_HASH] = ""
        }
    }

    suspend fun setSelectedGroup(group: StudentGroup) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_GROUP] = group.name
            prefs[Keys.LAST_NOTIFIED_SUB_HASH] = ""
        }
    }

    suspend fun setCustomGroupFilter(filter: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_GROUP_FILTER] = filter.trim()
        }
    }

    suspend fun setPortalUrls(substitutionsUrl: String, planUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SUBSTITUTIONS_URL] = normalizeHttpUrl(
                substitutionsUrl,
                UserPreferences.DEFAULT_SUBSTITUTIONS_URL
            )
            prefs[Keys.PLAN_URL] = normalizeHttpUrl(
                planUrl,
                UserPreferences.DEFAULT_PLAN_URL
            )
            prefs[Keys.LAST_NOTIFIED_SUB_HASH] = ""
            prefs[Keys.LAST_NOTIFIED_TIMETABLE_HASH] = ""
        }
    }

    suspend fun setSecretariatKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SECRETARIAT_KEY] = key.trim().ifBlank { UserPreferences.DEFAULT_SECRETARIAT_KEY }
        }
    }

    suspend fun setMeshNickname(nickname: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MESH_NICKNAME] = nickname.trim().take(24)
        }
    }

    suspend fun setLastKnownApkMetadata(candidate: ApkCandidate) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_APK_URL] = candidate.downloadUrl
            prefs[Keys.LAST_APK_FILENAME] = candidate.fileName
            prefs[Keys.LAST_APK_VERSION] = candidate.versionName
            prefs[Keys.LAST_APK_LAST_MODIFIED] = candidate.lastModifiedHeader.ifBlank { candidate.directoryDateText }
            prefs[Keys.LAST_APK_ETAG] = candidate.eTag
            prefs[Keys.LAST_APK_CONTENT_LENGTH] = candidate.contentLength
        }
    }

    suspend fun setAppUpdateUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_UPDATE_URL] = normalizeHttpUrl(
                url,
                UserPreferences.DEFAULT_GITHUB_APK_URL
            )
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setCheckIntervalMinutes(minutes: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CHECK_INTERVAL_MINUTES] = minutes.coerceAtLeast(15L)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USE_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun updateNotifiedHashes(
        substitutionsHash: String? = null,
        timetableHash: String? = null
    ) {
        context.dataStore.edit { prefs ->
            if (substitutionsHash != null) prefs[Keys.LAST_NOTIFIED_SUB_HASH] = substitutionsHash
            if (timetableHash != null) prefs[Keys.LAST_NOTIFIED_TIMETABLE_HASH] = timetableHash
        }
    }

    suspend fun appendNotificationLog(entry: NotificationLogEntry) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_NOTIFICATIONS_JSON]?.let { raw ->
                runCatching { json.decodeFromString<List<NotificationLogEntry>>(raw) }.getOrNull()
            }.orEmpty()
            val updated = (listOf(entry) + current).take(25)
            prefs[Keys.RECENT_NOTIFICATIONS_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun clearNotificationLogs() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.RECENT_NOTIFICATIONS_JSON)
        }
    }

    suspend fun saveCachedClasses(classes: List<SchoolClass>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHED_CLASSES_JSON] = json.encodeToString(classes)
        }
    }

    suspend fun saveCachedCatalog(catalog: TimetableCatalog) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHED_CATALOG_JSON] = json.encodeToString(catalog)
            prefs[Keys.CACHED_CLASSES_JSON] = json.encodeToString(catalog.classes)
        }
    }

    suspend fun saveCachedTimetable(timetable: ClassTimetable) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHED_TIMETABLE_JSON] = json.encodeToString(timetable)
        }
    }

    suspend fun saveCachedSubstitutions(snapshot: SubstitutionsSnapshot) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHED_SUBSTITUTIONS_JSON] = json.encodeToString(snapshot)
        }
    }

    suspend fun saveMeshMessages(messages: List<MeshMessage>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHED_MESH_MESSAGES_JSON] = json.encodeToString(messages.takeLast(120))
        }
    }

    private fun normalizeHttpUrl(raw: String, fallback: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return fallback
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}
