package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Room
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.LessonEntry
import com.example.data.model.ScheduleFilterMatcher
import com.example.data.model.StudentGroup
import com.example.data.model.SubstitutionItem
import com.example.data.model.TeacherSchoolConfig
import com.example.data.model.TimetableCategory
import com.example.data.model.TimetableTarget
import com.example.data.model.UserPreferences
import com.example.data.model.UserRole
import com.example.ui.ZsmeUiState
import com.example.ui.components.ClassAndGroupPill
import com.example.ui.components.TeacherHeroOnboardingCard
import com.example.ui.components.TeacherProfilePill
import com.example.ui.components.ZsmeHeroOnboardingCard
import com.example.ui.components.formatTimestampPl
import com.example.ui.theme.SchoolColorPalette
import java.util.Calendar

private val shortDayLabels = listOf("Pn", "Wt", "Śr", "Cz", "Pt")
private val fullDayLabels = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek")

@Composable
fun TimetableScreen(
    uiState: ZsmeUiState,
    onSelectCategory: (TimetableCategory) -> Unit,
    onSelectTarget: (TimetableTarget) -> Unit,
    onOpenTargetPicker: (Boolean) -> Unit,
    onSelectDay: (Int) -> Unit,
    onSelectGroup: (StudentGroup) -> Unit,
    onRefresh: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onOpenClassPicker: () -> Unit,
    onOpenTeacherSetup: () -> Unit,
    onOpenPortalWebView: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    val isTeacherMode = prefs.userRole == UserRole.TEACHER
    val teacherSchools = remember(prefs) { prefs.allTeacherSchools() }
    val showMultiSchoolColors = isTeacherMode &&
        (teacherSchools.size > 1 || teacherSchools.any { it.colorIndex > 0 })
    val category = if (isTeacherMode) TimetableCategory.TEACHER else uiState.selectedTimetableCategory
    val isStudentTeacherView = !isTeacherMode && category == TimetableCategory.TEACHER
    val timetable = uiState.activeDisplayedTimetable

    val todayWeekdayIndex: Int? = remember {
        when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            else -> null
        }
    }
    val effectiveDayIndex: Int? = if (isStudentTeacherView) todayWeekdayIndex else uiState.selectedDayIndex
    val daySchedule = if (effectiveDayIndex != null) {
        timetable?.days?.getOrNull(effectiveDayIndex)
    } else {
        null
    }

    var hiddenSchoolIdsList by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val hiddenSchoolIds = remember(hiddenSchoolIdsList, teacherSchools) {
        val validIds = teacherSchools.map { it.id }.toSet()
        hiddenSchoolIdsList.toSet().intersect(validIds)
    }
    val onToggleSchoolVisibility: (String) -> Unit = { schoolId ->
        hiddenSchoolIdsList = if (schoolId in hiddenSchoolIdsList) {
            hiddenSchoolIdsList - schoolId
        } else {
            hiddenSchoolIdsList + schoolId
        }
    }
    val onShowAllSchools: () -> Unit = { hiddenSchoolIdsList = emptyList() }

    var isExportCalendarDialogOpen by remember { mutableStateOf(false) }

    val filteredWeekLessons = remember(
        timetable,
        isTeacherMode,
        category,
        prefs.selectedGroup,
        prefs.customGroupFilter,
        hiddenSchoolIds,
        teacherSchools
    ) {
        if (timetable == null) {
            emptyMap()
        } else {
            (0..4).associateWith { idx ->
                val raw = timetable.days.getOrNull(idx)?.lessons.orEmpty()
                when {
                    isTeacherMode -> ScheduleFilterMatcher.filterLessonsByVisibleSchools(
                        lessons = raw,
                        hiddenSchoolIds = hiddenSchoolIds,
                        fallbackSchoolId = teacherSchools.firstOrNull()?.id ?: UserPreferences.PRIMARY_SCHOOL_ID
                    )
                    category != TimetableCategory.CLASS -> raw
                    else -> raw.filter { lesson ->
                        ScheduleFilterMatcher.matchesGroup(
                            groupTag = lesson.groupTag,
                            combinedText = "${lesson.subject} ${lesson.rawText}",
                            selectedGroup = prefs.selectedGroup,
                            customGroupFilter = prefs.customGroupFilter
                        )
                    }
                }
            }
        }
    }

    val filteredLessons = remember(effectiveDayIndex, filteredWeekLessons) {
        if (effectiveDayIndex != null) filteredWeekLessons[effectiveDayIndex].orEmpty() else emptyList()
    }
    val canExportCalendar = !isStudentTeacherView &&
        timetable != null &&
        filteredWeekLessons.values.any { it.isNotEmpty() }

    val isViewingToday = if (isStudentTeacherView) {
        todayWeekdayIndex != null
    } else {
        todayWeekdayIndex != null && effectiveDayIndex == todayWeekdayIndex
    }

    val activeWebUrl = if (isTeacherMode) {
        timetable?.classUrl?.takeIf { it.startsWith("http", ignoreCase = true) && it != prefs.planUrl }
            ?: prefs.planUrl.ifBlank { UserPreferences.DEFAULT_PLAN_URL }
    } else {
        when (category) {
            TimetableCategory.CLASS -> prefs.selectedClassUrl.ifBlank { null }
            TimetableCategory.TEACHER, TimetableCategory.ROOM ->
                uiState.selectedTeacherOrRoom?.url?.takeIf { it.startsWith("http", ignoreCase = true) }
                    ?: prefs.planUrl.ifBlank { UserPreferences.DEFAULT_PLAN_URL }
        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                TimetableHeader(
                    isTeacherMode = isTeacherMode,
                    isStudentTeacherView = isStudentTeacherView,
                    teacherAbbreviation = prefs.teacherAbbreviation,
                    teacherFullName = prefs.teacherFullName,
                    category = category,
                    className = prefs.selectedClassName,
                    groupLabel = prefs.selectedGroup.shortLabel,
                    selectedTargetName = uiState.selectedTeacherOrRoom?.name.orEmpty(),
                    lastUpdatedMillis = timetable?.fetchedAtMillis ?: 0L,
                    isRefreshing = uiState.isRefreshingTimetable || (isStudentTeacherView && uiState.isLoadingClasses),
                    canExportCalendar = canExportCalendar,
                    onPickClass = onOpenClassPicker,
                    onPickTeacherProfile = onOpenTeacherSetup,
                    onPickTarget = { onOpenTargetPicker(true) },
                    onExportCalendar = { isExportCalendarDialogOpen = true },
                    onRefresh = onRefresh,
                    onOpenWeb = { onOpenPortalWebView(activeWebUrl) }
                )
            }

            if (!isTeacherMode) {
                item(key = "category_switcher") {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TimetableCategory.entries.forEachIndexed { index, cat ->
                            SegmentedButton(
                                selected = category == cat,
                                onClick = { onSelectCategory(cat) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = TimetableCategory.entries.size
                                ),
                                icon = {
                                    SegmentedButtonDefaults.Icon(active = category == cat) {
                                        Icon(
                                            imageVector = when (cat) {
                                                TimetableCategory.CLASS -> Icons.Filled.Groups
                                                TimetableCategory.TEACHER -> Icons.Filled.Badge
                                                TimetableCategory.ROOM -> Icons.Filled.MeetingRoom
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("timetable_cat_${cat.name.lowercase()}")
                            ) {
                                Text(
                                    text = cat.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isRefreshingTimetable || (isStudentTeacherView && uiState.isLoadingClasses)) {
                item(key = "progress") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                        )
                        if (isTeacherMode && teacherSchools.isNotEmpty()) {
                            val scanDesc = if (teacherSchools.size > 1) {
                                "Łączę plany z ${teacherSchools.size} szkół (${teacherSchools.joinToString(", ") { it.schoolName }})..."
                            } else {
                                "Przeszukuję plany w ${teacherSchools.first().schoolName} dla skrótu ${teacherSchools.first().teacherAbbreviation}..."
                            }
                            Text(
                                text = scanDesc,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (isStudentTeacherView && uiState.isLoadingClasses) {
                            Text(
                                text = "Skanuję wszystkie plany na plan.zsme.pl, aby zebrać skróty nauczycieli...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!isTeacherMode && category != TimetableCategory.CLASS) {
                val quickTargets = if (category == TimetableCategory.TEACHER) {
                    uiState.timetableCatalog.teachers
                } else {
                    uiState.timetableCatalog.rooms
                }
                if (quickTargets.isNotEmpty()) {
                    item(key = "quick_target_bar_${category.name}") {
                        QuickTargetSelectorBar(
                            category = category,
                            targets = quickTargets,
                            selectedTarget = uiState.selectedTeacherOrRoom,
                            onSelectTarget = onSelectTarget,
                            onOpenAllPicker = { onOpenTargetPicker(true) }
                        )
                    }
                }
            }

            val needsSelection = if (isTeacherMode) {
                teacherSchools.isEmpty()
            } else {
                when (category) {
                    TimetableCategory.CLASS -> prefs.selectedClassName.isBlank()
                    TimetableCategory.TEACHER, TimetableCategory.ROOM -> uiState.selectedTeacherOrRoom == null
                }
            }

            if (needsSelection) {
                item(key = "onboarding") {
                    if (isTeacherMode) {
                        TeacherHeroOnboardingCard(onSetupTeacherClick = onOpenTeacherSetup)
                    } else if (category == TimetableCategory.CLASS) {
                        ZsmeHeroOnboardingCard(onPickClassClick = onOpenClassPicker)
                    } else {
                        val targetsCount = if (category == TimetableCategory.TEACHER) {
                            uiState.timetableCatalog.teachers.size
                        } else {
                            uiState.timetableCatalog.rooms.size
                        }
                        SelectTargetPromptCard(
                            category = category,
                            availableCount = targetsCount,
                            isLoading = uiState.isLoadingClasses,
                            onOpenPicker = { onOpenTargetPicker(true) },
                            onRefreshCatalog = onRefreshCatalog
                        )
                    }
                }
            } else {
                if (isTeacherMode && timetable != null && timetable.generatedInfo.isNotBlank()) {
                    item(key = "teacher_scan_summary") {
                        val headerPrefix = if (teacherSchools.size > 1) {
                            "Plan z ${teacherSchools.size} placówek"
                        } else {
                            "Plan nauczyciela ${teacherSchools.firstOrNull()?.teacherAbbreviation ?: prefs.teacherAbbreviation}"
                        }
                        val hiddenSuffix = if (hiddenSchoolIds.isNotEmpty()) {
                            " • ukryto: ${hiddenSchoolIds.size}"
                        } else {
                            ""
                        }
                        Text(
                            text = "$headerPrefix • ${timetable.generatedInfo}$hiddenSuffix",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (showMultiSchoolColors) {
                    item(key = "teacher_school_legend") {
                        TeacherSchoolsLegendBar(
                            schools = teacherSchools,
                            hiddenSchoolIds = hiddenSchoolIds,
                            onToggleSchool = onToggleSchoolVisibility,
                            onShowAll = onShowAllSchools
                        )
                    }
                }

                if (!isTeacherMode && category == TimetableCategory.CLASS) {
                    item(key = "group_filter") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudentGroup.entries.forEach { group ->
                                FilterChip(
                                    selected = prefs.selectedGroup == group,
                                    onClick = { onSelectGroup(group) },
                                    label = { Text(group.shortLabel) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("timetable_group_${group.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                if (!isTeacherMode && category == TimetableCategory.ROOM && timetable != null && uiState.selectedTeacherOrRoom != null) {
                    item(key = "room_live_occupancy") {
                        RoomLiveOccupancyCard(
                            roomName = uiState.selectedTeacherOrRoom.name,
                            timetable = timetable
                        )
                    }
                }

                if (!isStudentTeacherView) {
                    item(key = "weekday_selector") {
                        WeekdaySelectorRow(
                            selectedDayIndex = uiState.selectedDayIndex,
                            onSelectDay = onSelectDay
                        )
                    }
                }

                if (uiState.timetableError != null) {
                    item(key = "timetable_error") {
                        TimetableErrorCard(
                            message = uiState.timetableError,
                            onRetry = onRefresh,
                            showWebButton = !isStudentTeacherView,
                            onOpenWeb = { onOpenPortalWebView(activeWebUrl) }
                        )
                    }
                }

                if (filteredLessons.isEmpty() && !uiState.isRefreshingTimetable) {
                    item(key = "empty_day") {
                        val allSchoolsHidden = isTeacherMode &&
                            teacherSchools.isNotEmpty() &&
                            hiddenSchoolIds.size >= teacherSchools.size
                        val hiddenByLegendFilter = isTeacherMode &&
                            hiddenSchoolIds.isNotEmpty() &&
                            daySchedule?.lessons?.isNotEmpty() == true

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (allSchoolsHidden || hiddenByLegendFilter) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.CalendarMonth
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = when {
                                        allSchoolsHidden ->
                                            "Wszystkie placówki są ukryte"
                                        hiddenByLegendFilter ->
                                            "Brak lekcji w widocznych placówkach (${fullDayLabels.getOrElse(uiState.selectedDayIndex) { "tym dniu" }})"
                                        isStudentTeacherView && effectiveDayIndex == null ->
                                            "Dzisiaj (weekend) — brak lekcji"
                                        isStudentTeacherView && effectiveDayIndex != null ->
                                            "Brak lekcji dzisiaj (${fullDayLabels[effectiveDayIndex]})"
                                        else ->
                                            "Brak lekcji w ${fullDayLabels.getOrElse(uiState.selectedDayIndex) { "tym dniu" }}"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = when {
                                        allSchoolsHidden || hiddenByLegendFilter ->
                                            "Kliknij wybraną placówkę w legendzie powyżej lub przywróć widoczność wszystkich placówek."
                                        isStudentTeacherView ->
                                            "Widok nauczyciela dla ucznia pokazuje wyłącznie lekcje z aktualnego dnia (sala i klasa)."
                                        else ->
                                            "Odśwież plan z plan.zsme.pl lub sprawdź bezpośredni widok strony szkolnej."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (allSchoolsHidden || hiddenByLegendFilter) {
                                        Button(
                                            onClick = onShowAllSchools,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("empty_show_all_schools_button")
                                        ) {
                                            Text("Pokaż wszystkie placówki")
                                        }
                                    } else {
                                        Button(
                                            onClick = onRefresh,
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text("Odśwież")
                                        }
                                        if (!isStudentTeacherView) {
                                            OutlinedButton(
                                                onClick = { onOpenPortalWebView(activeWebUrl) },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Text("Podgląd WWW")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (effectiveDayIndex != null) {
                    item(key = "day_banner") {
                        AnimatedContent(
                            targetState = effectiveDayIndex,
                            label = "day_title_anim"
                        ) { idx ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isStudentTeacherView) {
                                        "Dzisiaj: ${fullDayLabels.getOrElse(idx) { "" }}"
                                    } else {
                                        fullDayLabels.getOrElse(idx) { "" }
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${filteredLessons.size} godz.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(
                        items = filteredLessons,
                        key = { "${isTeacherMode}_${category}_${effectiveDayIndex}_${it.schoolId}_${it.schoolColorIndex}_${it.period}_${it.subject}_${it.classGroupLabel}_${it.room}_${it.groupTag}" }
                    ) { lesson ->
                        val matchingSub = if ((isTeacherMode || category == TimetableCategory.CLASS) && isViewingToday) {
                            uiState.myGroupSubstitutions.firstOrNull { sub ->
                                sub.period.filter { ch -> ch.isDigit() }.toIntOrNull() == lesson.period
                            }
                        } else null

                        val isCurrentNow = isViewingToday && isLessonActiveNow(lesson.timeRange)

                        LessonTimelineCard(
                            lesson = lesson,
                            isCurrentNow = isCurrentNow,
                            hideTeacherName = isStudentTeacherView,
                            showSchoolHighlight = isTeacherMode && (showMultiSchoolColors || lesson.schoolColorIndex > 0),
                            matchingSubstitution = matchingSub
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (!isTeacherMode && uiState.isTargetPickerOpen) {
        val targetList = if (category == TimetableCategory.TEACHER) {
            uiState.timetableCatalog.teachers
        } else {
            uiState.timetableCatalog.rooms
        }
        TargetPickerDialog(
            category = category,
            targets = targetList,
            selectedTarget = uiState.selectedTeacherOrRoom,
            isLoading = uiState.isLoadingClasses,
            errorMessage = uiState.classesError,
            onRefresh = onRefreshCatalog,
            onSelectTarget = onSelectTarget,
            onDismiss = { onOpenTargetPicker(false) }
        )
    }

    if (isExportCalendarDialogOpen && timetable != null) {
        GoogleCalendarExportDialog(
            timetable = timetable,
            filteredDayLessons = filteredWeekLessons,
            isTeacherMode = isTeacherMode,
            teacherSchools = teacherSchools,
            onExportCompleted = { _ -> },
            onDismiss = { isExportCalendarDialogOpen = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimetableHeader(
    isTeacherMode: Boolean,
    isStudentTeacherView: Boolean,
    teacherAbbreviation: String,
    teacherFullName: String,
    category: TimetableCategory,
    className: String,
    groupLabel: String,
    selectedTargetName: String,
    lastUpdatedMillis: Long,
    isRefreshing: Boolean,
    canExportCalendar: Boolean,
    onPickClass: () -> Unit,
    onPickTeacherProfile: () -> Unit,
    onPickTarget: () -> Unit,
    onExportCalendar: () -> Unit,
    onRefresh: () -> Unit,
    onOpenWeb: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isTeacherMode) "Mój plan nauczyciela" else "Plan lekcji ZSME",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isStudentTeacherView) {
                        "plan.zsme.pl • tylko aktualny dzień"
                    } else {
                        "plan.zsme.pl • ${formatTimestampPl(lastUpdatedMillis)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canExportCalendar) {
                    IconButton(
                        onClick = onExportCalendar,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_google_calendar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EventAvailable,
                            contentDescription = "Eksportuj plan do Kalendarza Google"
                        )
                    }
                }
                if (!isStudentTeacherView) {
                    IconButton(
                        onClick = onOpenWeb,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_plan_web_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInBrowser,
                            contentDescription = "Otwórz plan.zsme.pl w przeglądarce"
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_timetable_button")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Odśwież plan lekcji"
                        )
                    }
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isTeacherMode) {
                TeacherProfilePill(
                    abbreviation = teacherAbbreviation,
                    fullName = teacherFullName,
                    onClick = onPickTeacherProfile
                )
            } else if (category == TimetableCategory.CLASS) {
                ClassAndGroupPill(
                    className = className,
                    groupLabel = groupLabel,
                    onClick = onPickClass
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onPickTarget)
                        .minimumInteractiveComponentSize()
                        .testTag("pick_teacher_room_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (category == TimetableCategory.TEACHER) Icons.Filled.Badge else Icons.Filled.MeetingRoom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        val label = when {
                            selectedTargetName.isNotBlank() && category == TimetableCategory.TEACHER -> "Nauczyciel: $selectedTargetName • Zmień"
                            selectedTargetName.isNotBlank() && category == TimetableCategory.ROOM -> "Sala: $selectedTargetName • Zmień"
                            category == TimetableCategory.TEACHER -> "Wybierz nauczyciela (skrót)"
                            else -> "Wybierz salę lekcyjną"
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (canExportCalendar) {
                Surface(
                    onClick = onExportCalendar,
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("export_google_calendar_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EventAvailable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Eksportuj do Kalendarza",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectTargetPromptCard(
    category: TimetableCategory,
    availableCount: Int,
    isLoading: Boolean,
    onOpenPicker: () -> Unit,
    onRefreshCatalog: () -> Unit
) {
    val isTeacher = category == TimetableCategory.TEACHER
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isTeacher) Icons.Filled.Badge else Icons.Filled.MeetingRoom,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = if (isTeacher) "Wybierz skrót nauczyciela" else "Wybierz salę lekcyjną",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (availableCount > 0) {
                    "Pobrano $availableCount pozycji z plan.zsme.pl. Wybierz z listy lub wyszukaj po skrócie."
                } else if (isLoading) {
                    "Pobieranie listy z plan.zsme.pl..."
                } else {
                    "Pobierz aktualny katalog nauczycieli i sal ze strony plan.zsme.pl."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenPicker,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_target_picker_button")
                ) {
                    Text(if (isTeacher) "Lista nauczycieli" else "Lista sal")
                }
                OutlinedButton(
                    onClick = onRefreshCatalog,
                    enabled = !isLoading,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Odśwież listę")
                }
            }
        }
    }
}

@Composable
private fun TargetPickerDialog(
    category: TimetableCategory,
    targets: List<TimetableTarget>,
    selectedTarget: TimetableTarget?,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onSelectTarget: (TimetableTarget) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val uniqueTargets = remember(targets) {
        targets.distinctBy { it.url.ifBlank { "${it.category}_${it.name}" } }
    }
    val filtered = remember(uniqueTargets, query) {
        if (query.isBlank()) uniqueTargets
        else uniqueTargets.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (category == TimetableCategory.TEACHER) "Nauczyciele (skróty)" else "Sale lekcyjne",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Zamknij"
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                if (category == TimetableCategory.TEACHER) "Szukaj skrótu (np. JK, AB)"
                                else "Szukaj sali (np. 102, 204)"
                            )
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("target_search_input")
                    )
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_targets_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Odśwież katalog"
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (filtered.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { "${it.category}_${it.url}_${it.name}" }) { item ->
                            val isSelected = item.url == selectedTarget?.url
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectTarget(item) }
                                    .testTag("target_option_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (category == TimetableCategory.ROOM) "Sala ${item.name}" else item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Wybrano",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (isLoading) {
                            "Ładowanie listy z plan.zsme.pl..."
                        } else {
                            "Brak wyników. Kliknij odśwież, aby pobrać listę z plan.zsme.pl."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Zamknij")
            }
        }
    )
}

@Composable
private fun QuickTargetSelectorBar(
    category: TimetableCategory,
    targets: List<TimetableTarget>,
    selectedTarget: TimetableTarget?,
    onSelectTarget: (TimetableTarget) -> Unit,
    onOpenAllPicker: () -> Unit
) {
    val uniqueTargets = remember(targets) {
        targets.distinctBy { it.url.ifBlank { "${it.category}_${it.name}" } }
    }
    val rowState = rememberLazyListState()
    LaunchedEffect(selectedTarget?.url, uniqueTargets.size) {
        val idx = uniqueTargets.indexOfFirst { it.url == selectedTarget?.url }
        if (idx >= 0) {
            rowState.animateScrollToItem(idx)
        }
    }

    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "open_search_picker") {
            OutlinedButton(
                onClick = onOpenAllPicker,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("quick_open_target_search")
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (category == TimetableCategory.ROOM) {
                        "Sale (${uniqueTargets.size})"
                    } else {
                        "Szukaj (${uniqueTargets.size})"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        items(uniqueTargets, key = { "${it.category}_${it.url}_${it.name}" }) { item ->
            val selected = item.url == selectedTarget?.url
            FilterChip(
                selected = selected,
                onClick = { onSelectTarget(item) },
                label = {
                    Text(
                        text = if (category == TimetableCategory.ROOM) "Sala ${item.name}" else item.name,
                        maxLines = 1
                    )
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("quick_chip_${item.id}")
            )
        }
    }
}

@Composable
private fun RoomLiveOccupancyCard(
    roomName: String,
    timetable: com.example.data.model.ClassTimetable
) {
    val cal = remember { Calendar.getInstance() }
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
    val todayIdx = when (dayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        else -> 0
    }
    val todayLessons = if (isWeekday) {
        timetable.days.getOrNull(todayIdx)?.lessons.orEmpty()
    } else {
        emptyList()
    }
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val activeNow = todayLessons.filter { isLessonActiveNow(it.timeRange) }
    val nextToday = if (activeNow.isEmpty() && isWeekday) {
        todayLessons.firstOrNull { lesson ->
            val start = lesson.timeRange.split("-", "–").firstOrNull()?.let(::parseHmToMinutes)
            start != null && start > nowMin
        }
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("room_live_occupancy_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (activeNow.isNotEmpty()) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = if (activeNow.isNotEmpty()) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = when {
                        !isWeekday -> "Weekend — sala $roomName"
                        activeNow.isNotEmpty() -> "Akurat teraz w sali $roomName"
                        nextToday != null -> "Aktualnie sala $roomName jest wolna"
                        else -> "Brak dalszych zajęć dziś w sali $roomName"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            when {
                !isWeekday -> {
                    Text(
                        text = "Wybierz dzień tygodnia poniżej, aby sprawdzić zajęcia i klasy w tej sali.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                activeNow.isNotEmpty() -> {
                    activeNow.forEach { lesson ->
                        val details = buildString {
                            if (lesson.classGroupLabel.isNotBlank()) append("Klasa ${lesson.classGroupLabel} • ")
                            append(lesson.subject.ifBlank { lesson.rawText })
                            if (lesson.teacher.isNotBlank()) append(" • ${lesson.teacher}")
                            if (lesson.timeRange.isNotBlank()) append(" (${lesson.timeRange})")
                        }
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                nextToday != null -> {
                    val nextDetails = buildString {
                        append("Następna lekcja: ")
                        if (nextToday.classGroupLabel.isNotBlank()) append("kl. ${nextToday.classGroupLabel} • ")
                        append(nextToday.subject.ifBlank { nextToday.rawText })
                        if (nextToday.teacher.isNotBlank()) append(" (${nextToday.teacher})")
                        if (nextToday.timeRange.isNotBlank()) append(" o ${nextToday.timeRange}")
                    }
                    Text(
                        text = nextDetails,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = "Wszystkie dzisiejsze lekcje w tej sali już się zakończyły.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdaySelectorRow(
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            shortDayLabels.forEachIndexed { idx, label ->
                val isSelected = idx == selectedDayIndex
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectDay(idx) }
                        .minimumInteractiveComponentSize()
                        .testTag("day_tab_$idx")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeacherSchoolsLegendBar(
    schools: List<TeacherSchoolConfig>,
    hiddenSchoolIds: Set<String>,
    onToggleSchool: (String) -> Unit,
    onShowAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("teacher_schools_legend"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Placówki (kliknij, aby ukryć lub pokazać):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hiddenSchoolIds.isNotEmpty()) {
                TextButton(
                    onClick = onShowAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("legend_show_all_schools")
                ) {
                    Text(
                        text = "Pokaż wszystkie",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            schools.forEach { school ->
                val isVisible = school.id !in hiddenSchoolIds
                val tokens = SchoolColorPalette.tokensForIndex(school.colorIndex)
                val bgColor by animateColorAsState(
                    targetValue = if (isVisible) {
                        tokens.container.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    label = "legend_bg_${school.id}"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isVisible) {
                        tokens.accent.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    label = "legend_border_${school.id}"
                )
                val labelColor by animateColorAsState(
                    targetValue = if (isVisible) {
                        tokens.onContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    label = "legend_text_${school.id}"
                )

                Surface(
                    onClick = { onToggleSchool(school.id) },
                    shape = RoundedCornerShape(999.dp),
                    color = bgColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("legend_school_${school.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isVisible) tokens.accent else tokens.accent.copy(alpha = 0.3f)
                                )
                        )
                        Text(
                            text = "${school.schoolName} (${school.teacherAbbreviation})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                textDecoration = if (isVisible) TextDecoration.None else TextDecoration.LineThrough
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = labelColor
                        )
                        Icon(
                            imageVector = if (isVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (isVisible) {
                                "Ukryj plan placówki ${school.schoolName}"
                            } else {
                                "Pokaż plan placówki ${school.schoolName}"
                            },
                            tint = if (isVisible) tokens.accent else labelColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonTimelineCard(
    lesson: LessonEntry,
    isCurrentNow: Boolean,
    hideTeacherName: Boolean = false,
    showSchoolHighlight: Boolean = false,
    matchingSubstitution: SubstitutionItem?
) {
    val schoolTokens = if (showSchoolHighlight && lesson.schoolColorIndex >= 0) {
        SchoolColorPalette.tokensForIndex(lesson.schoolColorIndex)
    } else {
        null
    }

    val borderHighlight = when {
        matchingSubstitution != null -> MaterialTheme.colorScheme.tertiary
        isCurrentNow -> MaterialTheme.colorScheme.secondary
        schoolTokens != null -> schoolTokens.accent
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    }

    val cardContainerColor = when {
        schoolTokens != null -> schoolTokens.container.copy(alpha = if (isCurrentNow) 0.62f else 0.34f)
        isCurrentNow -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }

    val cardBorder = if (schoolTokens != null) {
        BorderStroke(
            width = if (isCurrentNow) 2.dp else 1.5.dp,
            color = if (isCurrentNow) MaterialTheme.colorScheme.secondary else schoolTokens.accent.copy(alpha = 0.75f)
        )
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lesson_card_${lesson.period}"),
        shape = MaterialTheme.shapes.medium,
        border = cardBorder,
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentNow || matchingSubstitution != null) 2.dp else 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isCurrentNow -> MaterialTheme.colorScheme.secondary
                                schoolTokens != null -> schoolTokens.container
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = "${lesson.period}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isCurrentNow -> MaterialTheme.colorScheme.onSecondary
                            schoolTokens != null -> schoolTokens.onContainer
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    if (lesson.timeRange.isNotBlank()) {
                        Text(
                            text = lesson.timeRange.replace("-", "\n"),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = when {
                                isCurrentNow -> MaterialTheme.colorScheme.onSecondary
                                schoolTokens != null -> schoolTokens.onContainer.copy(alpha = 0.85f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lesson.subject.ifBlank { lesson.rawText },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCurrentNow) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondary
                            ) {
                                Text(
                                    text = "TERAZ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (schoolTokens != null && lesson.schoolName.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = schoolTokens.container,
                                border = BorderStroke(1.dp, schoolTokens.accent.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(schoolTokens.accent)
                                    )
                                    Text(
                                        text = lesson.schoolName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = schoolTokens.onContainer
                                    )
                                }
                            }
                        }
                        if (lesson.classGroupLabel.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "kl. ${lesson.classGroupLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (!hideTeacherName && lesson.teacher.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PersonOutline,
                                    contentDescription = "Nauczyciel",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = lesson.teacher,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (lesson.room.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Room,
                                    contentDescription = "Sala",
                                    tint = schoolTokens?.accent ?: MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "s. ${lesson.room}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = schoolTokens?.accent ?: MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (lesson.groupTag != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = borderHighlight.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "gr. ${lesson.groupTag}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (matchingSubstitution != null) {
                Surface(
                    color = if (matchingSubstitution.isCancellation) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = "Zastępstwo na tej lekcji",
                            tint = if (matchingSubstitution.isCancellation) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        val alertText = buildString {
                            append(if (matchingSubstitution.isCancellation) "ODWOŁANE: " else "ZASTĘPSTWO: ")
                            if (matchingSubstitution.substituteTeacher.isNotBlank()) {
                                append(matchingSubstitution.substituteTeacher)
                            } else if (matchingSubstitution.note.isNotBlank()) {
                                append(matchingSubstitution.note)
                            } else {
                                append(matchingSubstitution.subject)
                            }
                            if (matchingSubstitution.room.isNotBlank()) {
                                append(" • sala ${matchingSubstitution.room}")
                            }
                        }
                        Text(
                            text = alertText,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (matchingSubstitution.isCancellation) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableErrorCard(
    message: String,
    onRetry: () -> Unit,
    showWebButton: Boolean = true,
    onOpenWeb: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Nie udało się pobrać planu lekcji",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Odśwież")
                }
                if (showWebButton) {
                    OutlinedButton(
                        onClick = onOpenWeb,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Otwórz plan.zsme.pl")
                    }
                }
            }
        }
    }
}

private fun isLessonActiveNow(timeRange: String): Boolean {
    val parts = timeRange.split("-", "–")
    if (parts.size != 2) return false
    val startMin = parseHmToMinutes(parts[0]) ?: return false
    val endMin = parseHmToMinutes(parts[1]) ?: return false
    val now = Calendar.getInstance()
    val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    return nowMin in startMin..endMin
}

private fun parseHmToMinutes(raw: String): Int? {
    val clean = raw.trim().replace(".", ":")
    val pieces = clean.split(":")
    if (pieces.size != 2) return null
    val h = pieces[0].filter { it.isDigit() }.toIntOrNull() ?: return null
    val m = pieces[1].filter { it.isDigit() }.toIntOrNull() ?: return null
    return h * 60 + m
}
