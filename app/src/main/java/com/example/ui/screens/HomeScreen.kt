package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Room
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.LiveLessonItem
import com.example.data.model.LiveLessonSlot
import com.example.data.model.LiveScheduleEvaluation
import com.example.data.model.LiveScheduleStatus
import com.example.data.model.LiveScheduleTracker
import com.example.data.model.UserRole
import com.example.ui.ZsmeUiState
import com.example.ui.components.ClassAndGroupPill
import com.example.ui.components.TeacherHeroOnboardingCard
import com.example.ui.components.TeacherProfilePill
import com.example.ui.components.ZsmeHeroOnboardingCard
import com.example.ui.theme.SchoolColorPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: ZsmeUiState,
    onRefresh: () -> Unit,
    onOpenClassPicker: () -> Unit,
    onOpenTeacherSetup: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenSubstitutions: () -> Unit,
    onStartAppUpdateDownload: () -> Unit,
    onInstallDownloadedApk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    val isTeacherMode = prefs.userRole == UserRole.TEACHER
    val teacherSchools = remember(prefs) { prefs.allTeacherSchools() }
    val showMultiSchoolColors = isTeacherMode &&
        (teacherSchools.size > 1 || teacherSchools.any { it.colorIndex > 0 })

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            val remainder = 1000L - (nowMillis % 1000L)
            delay(remainder.coerceIn(250L, 1000L))
        }
    }

    val evaluation: LiveScheduleEvaluation = remember(
        uiState.timetable,
        uiState.substitutions,
        prefs,
        nowMillis / 1000L
    ) {
        LiveScheduleTracker.evaluate(
            timetable = uiState.timetable,
            substitutions = uiState.substitutions,
            userRole = prefs.userRole,
            selectedClassName = prefs.selectedClassName,
            selectedGroup = prefs.selectedGroup,
            customGroupFilter = prefs.customGroupFilter,
            teacherAbbr = prefs.teacherAbbreviation,
            teacherFullName = prefs.teacherFullName,
            nowMillis = nowMillis
        )
    }

    val currentClockLabel = remember(nowMillis / 1000L) {
        SimpleDateFormat("HH:mm:ss", Locale("pl", "PL")).format(Date(nowMillis))
    }
    val todayDateLabel = remember(nowMillis / 60_000L) {
        SimpleDateFormat("d MMMM", Locale("pl", "PL")).format(Date(nowMillis))
    }

    val updateState = uiState.appUpdateState
    val availableUpdate = updateState.checkResult?.takeIf { it.updateAvailable && it.candidate != null }

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
            item(key = "home_header") {
                HomeHeaderCard(
                    dayName = evaluation.todayDayName,
                    dateLabel = todayDateLabel,
                    clockLabel = currentClockLabel,
                    isTeacherMode = isTeacherMode,
                    teacherAbbreviation = prefs.teacherAbbreviation,
                    teacherFullName = prefs.teacherFullName,
                    className = prefs.selectedClassName,
                    groupLabel = prefs.selectedGroup.shortLabel,
                    isRefreshing = uiState.isRefreshingTimetable || uiState.isRefreshingSubstitutions,
                    onPickClass = onOpenClassPicker,
                    onPickTeacher = onOpenTeacherSetup,
                    onRefresh = onRefresh
                )
            }

            if (availableUpdate != null) {
                item(key = "home_update_banner") {
                    HomeUpdateBannerCard(
                        versionLabel = availableUpdate.candidate?.versionName.orEmpty(),
                        fileName = availableUpdate.candidate?.fileName.orEmpty(),
                        isDownloading = updateState.isDownloading,
                        downloadProgress = updateState.downloadProgress,
                        downloadedApkPath = updateState.downloadedApkPath,
                        onDownloadAndInstall = onStartAppUpdateDownload,
                        onInstallDownloaded = onInstallDownloadedApk
                    )
                }
            }

            when (val status = evaluation.status) {
                is LiveScheduleStatus.NoTimetable -> {
                    item(key = "home_no_timetable") {
                        if (isTeacherMode) {
                            TeacherHeroOnboardingCard(onSetupTeacherClick = onOpenTeacherSetup)
                        } else {
                            ZsmeHeroOnboardingCard(onPickClassClick = onOpenClassPicker)
                        }
                    }
                }

                is LiveScheduleStatus.DuringLesson -> {
                    item(key = "home_live_during_lesson") {
                        DuringLessonHeroCard(
                            status = status,
                            showSchoolBadge = showMultiSchoolColors
                        )
                    }
                    item(key = "home_next_lesson_after_current") {
                        if (status.nextSlot != null) {
                            val startsInSeconds = (status.nextSlot.startSecondOfDay -
                                (status.currentSlot.endSecondOfDay - status.remainingSeconds)).coerceAtLeast(0)
                            NextLessonCard(
                                heading = "Następna lekcja",
                                subtitle = "Za ${LiveScheduleTracker.formatDurationWordsPl(startsInSeconds)} • ${status.nextSlot.timeRangeLabel}",
                                slot = status.nextSlot,
                                showSchoolBadge = showMultiSchoolColors
                            )
                        } else {
                            EndOfDayHintCard(
                                title = "To ostatnia lekcja dzisiaj",
                                subtitle = "Po zakończeniu lekcji o ${status.currentSlot.timeRangeLabel.substringAfterLast('–').substringAfterLast('-').trim()} kończysz dzisiejsze zajęcia."
                            )
                        }
                    }
                }

                is LiveScheduleStatus.Break -> {
                    item(key = "home_live_break") {
                        BreakCountdownHeroCard(status = status)
                    }
                    item(key = "home_next_lesson_during_break") {
                        NextLessonCard(
                            heading = "Następna lekcja",
                            subtitle = "Lekcja ${status.nextSlot.period} • ${status.nextSlot.timeRangeLabel}",
                            slot = status.nextSlot,
                            showSchoolBadge = showMultiSchoolColors,
                            emphasized = true
                        )
                    }
                }

                is LiveScheduleStatus.BeforeLessons -> {
                    item(key = "home_live_before_lessons") {
                        BeforeLessonsHeroCard(status = status)
                    }
                    item(key = "home_first_lesson") {
                        NextLessonCard(
                            heading = "Następna lekcja (pierwsza dzisiaj)",
                            subtitle = "Lekcja ${status.firstSlot.period} • ${status.firstSlot.timeRangeLabel}",
                            slot = status.firstSlot,
                            showSchoolBadge = showMultiSchoolColors,
                            emphasized = true
                        )
                    }
                }

                is LiveScheduleStatus.AfterLessons -> {
                    item(key = "home_after_lessons") {
                        FinishedDayHeroCard(
                            isWeekend = false,
                            dayName = evaluation.todayDayName,
                            cancelledCountToday = evaluation.cancelledCountToday
                        )
                    }
                    if (status.nextDayFirstSlot != null) {
                        item(key = "home_next_day_lesson") {
                            NextLessonCard(
                                heading = "Następna lekcja • ${status.nextDayLabel.orEmpty()}",
                                subtitle = "Lekcja ${status.nextDayFirstSlot.period} • ${status.nextDayFirstSlot.timeRangeLabel}",
                                slot = status.nextDayFirstSlot,
                                showSchoolBadge = showMultiSchoolColors
                            )
                        }
                    }
                }

                is LiveScheduleStatus.Weekend -> {
                    item(key = "home_weekend") {
                        FinishedDayHeroCard(
                            isWeekend = true,
                            dayName = evaluation.todayDayName,
                            cancelledCountToday = 0
                        )
                    }
                    if (status.nextDayFirstSlot != null) {
                        item(key = "home_next_week_lesson") {
                            NextLessonCard(
                                heading = "Następna lekcja • ${status.nextDayLabel.orEmpty()}",
                                subtitle = "Lekcja ${status.nextDayFirstSlot.period} • ${status.nextDayFirstSlot.timeRangeLabel}",
                                slot = status.nextDayFirstSlot,
                                showSchoolBadge = showMultiSchoolColors
                            )
                        }
                    }
                }
            }

            if (evaluation.substitutionBanner != null || uiState.myGroupSubstitutions.isNotEmpty()) {
                item(key = "home_substitutions_summary") {
                    HomeSubstitutionsSummaryCard(
                        count = uiState.myGroupSubstitutions.size,
                        banner = evaluation.substitutionBanner,
                        onOpenSubstitutions = onOpenSubstitutions
                    )
                }
            }

            if (evaluation.todaySlots.isNotEmpty()) {
                item(key = "home_today_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dzisiejszy rozkład (${evaluation.todaySlots.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = onOpenTimetable,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_open_full_timetable_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarViewWeek,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cały plan")
                        }
                    }
                }

                val activeStartSec = when (val s = evaluation.status) {
                    is LiveScheduleStatus.DuringLesson -> s.currentSlot.startSecondOfDay
                    is LiveScheduleStatus.Break -> s.nextSlot.startSecondOfDay
                    is LiveScheduleStatus.BeforeLessons -> s.firstSlot.startSecondOfDay
                    else -> Int.MAX_VALUE
                }
                val isCurrentlyInLesson = evaluation.status is LiveScheduleStatus.DuringLesson

                items(
                    items = evaluation.todaySlots,
                    key = { "home_slot_${it.period}_${it.startSecondOfDay}" }
                ) { slot ->
                    val isNow = isCurrentlyInLesson && slot.startSecondOfDay == activeStartSec
                    val isNext = !isCurrentlyInLesson && slot.startSecondOfDay == activeStartSec
                    val isPast = when (val s = evaluation.status) {
                        is LiveScheduleStatus.DuringLesson -> slot.endSecondOfDay <= s.currentSlot.startSecondOfDay
                        is LiveScheduleStatus.Break -> slot.endSecondOfDay <= s.nextSlot.startSecondOfDay
                        is LiveScheduleStatus.AfterLessons -> true
                        else -> false
                    }
                    TodaySlotTimelineRow(
                        slot = slot,
                        isNow = isNow,
                        isNext = isNext,
                        isPast = isPast,
                        showSchoolBadge = showMultiSchoolColors
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeaderCard(
    dayName: String,
    dateLabel: String,
    clockLabel: String,
    isTeacherMode: Boolean,
    teacherAbbreviation: String,
    teacherFullName: String,
    className: String,
    groupLabel: String,
    isRefreshing: Boolean,
    onPickClass: () -> Unit,
    onPickTeacher: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ekran główny",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "$dayName, $dateLabel • $clockLabel",
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isTeacherMode) {
                TeacherProfilePill(
                    abbreviation = teacherAbbreviation,
                    fullName = teacherFullName,
                    onClick = onPickTeacher
                )
            } else {
                ClassAndGroupPill(
                    className = className,
                    groupLabel = groupLabel,
                    onClick = onPickClass
                )
            }
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("home_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Odśwież plan i zastępstwa"
                )
            }
        }
    }
}

@Composable
private fun HomeUpdateBannerCard(
    versionLabel: String,
    fileName: String,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadedApkPath: String?,
    onDownloadAndInstall: () -> Unit,
    onInstallDownloaded: () -> Unit
) {
    val label = versionLabel.takeIf { it.isNotBlank() }?.let { "v$it" } ?: fileName
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_update_banner")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nowa wersja na GitHubie ($label)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (isDownloading) {
                            "Pobieranie aktualizacji: $downloadProgress%"
                        } else {
                            "Dostępna jest aktualizacja pliku $fileName."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { downloadProgress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Button(
                    onClick = if (downloadedApkPath != null) onInstallDownloaded else onDownloadAndInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("home_install_update_button")
                ) {
                    Text(if (downloadedApkPath != null) "Zainstaluj pobraną aktualizację" else "Pobierz i zaktualizuj teraz")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DuringLessonHeroCard(
    status: LiveScheduleStatus.DuringLesson,
    showSchoolBadge: Boolean
) {
    val slot = status.currentSlot
    val progress = ((status.totalSeconds - status.remainingSeconds).toFloat() / status.totalSeconds.toFloat())
        .coerceIn(0f, 1f)
    val countdownText = LiveScheduleTracker.formatCountdown(status.remainingSeconds)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_live_status_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "TRWA LEKCJA",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Lekcja ${slot.period} • ${slot.timeRangeLabel}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Do końca lekcji pozostało",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        AnimatedContent(
                            targetState = countdownText,
                            label = "lesson_countdown"
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.displaySmall.merge(
                                    TextStyle(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFeatureSettings = "tnum"
                                    )
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.testTag("home_countdown_text")
                            )
                        }
                    }
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)
                )

                SlotItemsBlock(
                    items = slot.items,
                    showSchoolBadge = showSchoolBadge,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun BreakCountdownHeroCard(status: LiveScheduleStatus.Break) {
    val progress = ((status.totalSeconds - status.remainingSeconds).toFloat() / status.totalSeconds.toFloat())
        .coerceIn(0f, 1f)
    val countdownText = LiveScheduleTracker.formatCountdown(status.remainingSeconds)
    val isLongGap = status.totalSeconds > 25 * 60

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_live_status_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Coffee,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isLongGap) "PRZERWA / OKIENKO" else "TRWA PRZERWA",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Po ${status.previousSlot.period}. lekcji",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Czas do zakończenia przerwy",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = countdownText,
                            style = MaterialTheme.typography.displayMedium.merge(
                                TextStyle(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFeatureSettings = "tnum"
                                )
                            ),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.testTag("home_countdown_text")
                        )
                    }
                    Text(
                        text = "${LiveScheduleTracker.formatDurationWordsPl(status.totalSeconds)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.14f)
                )
            }
        }
    }
}

@Composable
private fun BeforeLessonsHeroCard(status: LiveScheduleStatus.BeforeLessons) {
    val countdownText = LiveScheduleTracker.formatCountdown(status.remainingSeconds)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_live_status_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Zajęcia jeszcze się nie rozpoczęły",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = "Do rozpoczęcia ${status.firstSlot.period}. lekcji pozostało",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = countdownText,
                style = MaterialTheme.typography.displaySmall.merge(
                    TextStyle(
                        fontWeight = FontWeight.ExtraBold,
                        fontFeatureSettings = "tnum"
                    )
                ),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("home_countdown_text")
            )
        }
    }
}

@Composable
private fun FinishedDayHeroCard(
    isWeekend: Boolean,
    dayName: String,
    cancelledCountToday: Int
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_live_status_card")
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isWeekend) Icons.Filled.Weekend else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isWeekend) "Weekend — brak zajęć" else "Zajęcia na dziś zakończone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isWeekend) {
                        "$dayName • odpocznij przed kolejnym tygodniem."
                    } else if (cancelledCountToday > 0) {
                        "Wszystkie aktywne lekcje zostały zakończone (odwołanych dzisiaj: $cancelledCountToday)."
                    } else {
                        "Wszystkie dzisiejsze lekcje dobiegły końca."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NextLessonCard(
    heading: String,
    subtitle: String,
    slot: LiveLessonSlot,
    showSchoolBadge: Boolean,
    emphasized: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        border = BorderStroke(
            width = if (emphasized) 1.5.dp else 1.dp,
            color = if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (emphasized) 2.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_next_lesson_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SlotItemsBlock(
                items = slot.items,
                showSchoolBadge = showSchoolBadge,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotItemsBlock(
    items: List<LiveLessonItem>,
    showSchoolBadge: Boolean,
    contentColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { idx, item ->
            val lesson = item.lesson
            val sub = item.substitution
            if (idx > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(contentColor.copy(alpha = 0.14f))
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = lesson.subject.ifBlank { lesson.rawText }.ifBlank { "Zajęcia" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val effectiveRoom = sub?.room?.takeIf { it.isNotBlank() } ?: lesson.room
                    if (effectiveRoom.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Room,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Sala $effectiveRoom",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor
                            )
                        }
                    }
                    if (lesson.classGroupLabel.isNotBlank()) {
                        Text(
                            text = "Klasa ${lesson.classGroupLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.85f)
                        )
                    } else if (lesson.teacher.isNotBlank()) {
                        Text(
                            text = lesson.teacher,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.85f)
                        )
                    }
                    if (!lesson.groupTag.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = contentColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "gr. ${lesson.groupTag}",
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (showSchoolBadge && lesson.schoolName.isNotBlank()) {
                        val accent = SchoolColorPalette.tokensForIndex(lesson.schoolColorIndex).accent
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accent.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                                Text(
                                    text = lesson.schoolName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }
                if (sub != null && !sub.isCancellation) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        val detail = listOf(
                            sub.substituteTeacher.takeIf { it.isNotBlank() }?.let { "Zastępstwo: $it" },
                            sub.note.takeIf { it.isNotBlank() }
                        ).filterNotNull().joinToString(" • ").ifBlank { "Zastępstwo" }
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EndOfDayHintCard(
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_next_lesson_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HomeSubstitutionsSummaryCard(
    count: Int,
    banner: String?,
    onOpenSubstitutions: () -> Unit
) {
    Surface(
        onClick = onOpenSubstitutions,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("home_substitutions_summary_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count > 0) "Zastępstwa dla Ciebie: $count" else "Zmiany w planie na dziś",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (!banner.isNullOrBlank()) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Text(
                text = "Pokaż",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun TodaySlotTimelineRow(
    slot: LiveLessonSlot,
    isNow: Boolean,
    isNext: Boolean,
    isPast: Boolean,
    showSchoolBadge: Boolean
) {
    val first = slot.items.firstOrNull()?.lesson ?: return
    val subjectsLabel = slot.items.joinToString(" / ") {
        it.lesson.subject.ifBlank { it.lesson.rawText }.ifBlank { "Lekcja" }
    }
    val roomsLabel = slot.items.mapNotNull { item ->
        val r = item.substitution?.room?.takeIf { it.isNotBlank() } ?: item.lesson.room
        r.takeIf { it.isNotBlank() }
    }.distinct().joinToString(", ")

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            isNow -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            isNext -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isNow || isNext) 1.5.dp else 1.dp,
            color = when {
                isNow -> MaterialTheme.colorScheme.primary
                isNext -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isPast) 0.55f else 1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(56.dp)
            ) {
                Text(
                    text = "${slot.period}.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = slot.timeRangeLabel.substringBefore('-').substringBefore('–').trim(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subjectsLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isNow || isNext) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildList {
                    if (roomsLabel.isNotBlank()) add("s. $roomsLabel")
                    if (first.classGroupLabel.isNotBlank()) add("kl. ${first.classGroupLabel}")
                    else if (first.teacher.isNotBlank()) add(first.teacher)
                    if (showSchoolBadge && first.schoolName.isNotBlank()) add(first.schoolName)
                }.joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isNow || isNext) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    contentColor = if (isNow) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary
                ) {
                    Text(
                        text = if (isNow) "TERAZ" else "NASTĘPNA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
