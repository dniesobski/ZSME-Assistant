package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Room
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.NotificationLogEntry
import com.example.data.model.ScheduleFilterMatcher
import com.example.data.model.SubstitutionItem
import com.example.data.model.UserPreferences
import com.example.data.model.UserRole
import com.example.ui.SubstitutionsFilterTab
import com.example.ui.ZsmeUiState
import com.example.ui.components.ClassAndGroupPill
import com.example.ui.components.NotificationPermissionBanner
import com.example.ui.components.TeacherHeroOnboardingCard
import com.example.ui.components.TeacherProfilePill
import com.example.ui.components.ZsmeHeroOnboardingCard
import com.example.ui.components.formatTimestampPl

@Composable
fun SubstitutionsScreen(
    uiState: ZsmeUiState,
    onRefresh: () -> Unit,
    onSelectTab: (SubstitutionsFilterTab) -> Unit,
    onOpenClassPicker: () -> Unit,
    onOpenTeacherSetup: () -> Unit,
    onOpenPortalWebView: () -> Unit,
    onPermissionResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    val isTeacherMode = prefs.userRole == UserRole.TEACHER
    val snapshot = uiState.substitutions
    val matchingItems = uiState.myGroupSubstitutions
    val allItems = snapshot?.items.orEmpty()
    val announcements = snapshot?.announcements.orEmpty()
    val notes = snapshot?.notes.orEmpty()

    var showHistory by remember { mutableStateOf(false) }

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
                SubstitutionsHeader(
                    isTeacherMode = isTeacherMode,
                    teacherAbbreviation = prefs.teacherAbbreviation,
                    teacherFullName = prefs.teacherFullName,
                    className = prefs.selectedClassName,
                    groupLabel = prefs.selectedGroup.shortLabel,
                    dateHeading = snapshot?.dateHeading?.ifBlank { "Zastępstwa ZSME" } ?: "Zastępstwa ZSME",
                    lastUpdatedMillis = snapshot?.fetchedAtMillis ?: 0L,
                    isRefreshing = uiState.isRefreshingSubstitutions,
                    onPickClass = onOpenClassPicker,
                    onPickTeacher = onOpenTeacherSetup,
                    onRefresh = onRefresh,
                    onOpenWeb = onOpenPortalWebView
                )
            }

            if (uiState.isRefreshingSubstitutions) {
                item(key = "progress") {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            item(key = "permission_banner") {
                NotificationPermissionBanner(onPermissionResult = onPermissionResult)
            }

            if (isTeacherMode && prefs.teacherAbbreviation.isBlank() && prefs.teacherFullName.isBlank()) {
                item(key = "onboarding_teacher_hero") {
                    TeacherHeroOnboardingCard(onSetupTeacherClick = onOpenTeacherSetup)
                }
            } else if (!isTeacherMode && prefs.selectedClassName.isBlank()) {
                item(key = "onboarding_hero") {
                    ZsmeHeroOnboardingCard(onPickClassClick = onOpenClassPicker)
                }
            }

            if (uiState.substitutionsError != null) {
                item(key = "error_card") {
                    SubstitutionsErrorCard(
                        message = uiState.substitutionsError,
                        onRetry = onRefresh,
                        onOpenWeb = onOpenPortalWebView
                    )
                }
            }

            if (announcements.isNotEmpty() && uiState.substitutionsTab != SubstitutionsFilterTab.ANNOUNCEMENTS) {
                item(key = "top_info_frame") {
                    FramedSectionBox(
                        title = "Informacje",
                        lines = announcements,
                        icon = Icons.Filled.Info,
                        accentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        testTag = "top_informacje_frame"
                    )
                }
            }

            item(key = "filter_tabs") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubstitutionsFilterTab.entries.forEach { tab ->
                        val count = when (tab) {
                            SubstitutionsFilterTab.MY_GROUP -> matchingItems.size
                            SubstitutionsFilterTab.ALL_CLASSES -> allItems.size
                            SubstitutionsFilterTab.ANNOUNCEMENTS -> announcements.size
                        }
                        val tabTitle = if (tab == SubstitutionsFilterTab.MY_GROUP && isTeacherMode) {
                            "Moje zastępstwa"
                        } else {
                            tab.label
                        }
                        FilterChip(
                            selected = uiState.substitutionsTab == tab,
                            onClick = { onSelectTab(tab) },
                            label = { Text("$tabTitle ($count)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sub_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            when (uiState.substitutionsTab) {
                SubstitutionsFilterTab.MY_GROUP -> {
                    if (matchingItems.isEmpty() && !uiState.isRefreshingSubstitutions) {
                        item(key = "empty_my_group") {
                            val teacherQuery = listOf(prefs.teacherAbbreviation, prefs.teacherFullName)
                                .filter { it.isNotBlank() }
                                .joinToString(" / ")
                            EmptySubstitutionsCard(
                                title = when {
                                    isTeacherMode && teacherQuery.isBlank() -> "Nie podano skrótu ani nazwiska"
                                    isTeacherMode -> "Brak zastępstw dla: $teacherQuery"
                                    prefs.selectedClassName.isBlank() -> "Nie wybrano jeszcze klasy"
                                    else -> "Brak zastępstw dla klasy ${prefs.selectedClassName}"
                                },
                                subtitle = when {
                                    isTeacherMode && teacherQuery.isBlank() ->
                                        "Wpisz swój skrót i nazwisko w profilu nauczyciela, aby wyświetlać tylko zastępstwa, które prowadzisz lub które są za Ciebie."
                                    isTeacherMode ->
                                        "Na sekretariat.zsme.pl nie ma obecnie zastępstw powiązanych ze skrótem lub nazwiskiem ($teacherQuery)."
                                    prefs.selectedClassName.isBlank() ->
                                        "Wybierz swoją klasę i grupę, aby automatycznie odfiltrować zastępstwa z sekretariat.zsme.pl."
                                    else ->
                                        "Na portalu sekretariat.zsme.pl nie opublikowano obecnie zmian dla Twojej grupy (${prefs.selectedGroup.shortLabel})."
                                },
                                showAllCount = if ((isTeacherMode && teacherQuery.isNotBlank()) || (!isTeacherMode && prefs.selectedClassName.isNotBlank())) {
                                    allItems.size
                                } else 0,
                                onShowAllClick = { onSelectTab(SubstitutionsFilterTab.ALL_CLASSES) },
                                onOpenWeb = onOpenPortalWebView
                            )
                        }
                    } else {
                        items(matchingItems, key = { it.id }) { item ->
                            val teacherMatch = if (isTeacherMode) {
                                ScheduleFilterMatcher.classifyTeacherSubstitution(
                                    item = item,
                                    teacherAbbr = prefs.teacherAbbreviation,
                                    teacherFullName = prefs.teacherFullName
                                )
                            } else {
                                ScheduleFilterMatcher.TeacherSubstitutionMatch.NONE
                            }
                            SubstitutionCard(
                                item = item,
                                highlightForMyGroup = true,
                                teacherMatch = teacherMatch
                            )
                        }
                    }
                }

                SubstitutionsFilterTab.ALL_CLASSES -> {
                    if (allItems.isEmpty() && !uiState.isRefreshingSubstitutions) {
                        item(key = "empty_all") {
                            EmptySubstitutionsCard(
                                title = "Brak opublikowanych zastępstw",
                                subtitle = "Możesz otworzyć bezpośredni podgląd sekretariat.zsme.pl (klucz: ${prefs.secretariatKey}).",
                                onOpenWeb = onOpenPortalWebView
                            )
                        }
                    } else {
                        items(allItems, key = { it.id }) { item ->
                            val teacherMatch = if (isTeacherMode) {
                                ScheduleFilterMatcher.classifyTeacherSubstitution(
                                    item = item,
                                    teacherAbbr = prefs.teacherAbbreviation,
                                    teacherFullName = prefs.teacherFullName
                                )
                            } else {
                                ScheduleFilterMatcher.TeacherSubstitutionMatch.NONE
                            }
                            SubstitutionCard(
                                item = item,
                                highlightForMyGroup = teacherMatch != ScheduleFilterMatcher.TeacherSubstitutionMatch.NONE,
                                teacherMatch = teacherMatch
                            )
                        }
                    }
                }

                SubstitutionsFilterTab.ANNOUNCEMENTS -> {
                    if (announcements.isEmpty()) {
                        item(key = "empty_announcements") {
                            EmptySubstitutionsCard(
                                title = "Brak dodatkowych informacji",
                                subtitle = "Gdy dyrekcja lub sekretariat opublikują informacje na górze strony zastępstw, pojawią się tutaj.",
                                onOpenWeb = onOpenPortalWebView
                            )
                        }
                    } else {
                        item(key = "tab_info_frame") {
                            FramedSectionBox(
                                title = "Informacje",
                                lines = announcements,
                                icon = Icons.Filled.Info,
                                accentColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                                testTag = "tab_informacje_frame"
                            )
                        }
                    }
                }
            }

            if (notes.isNotEmpty()) {
                item(key = "bottom_notes_frame") {
                    FramedSectionBox(
                        title = "Uwagi",
                        lines = notes,
                        icon = Icons.Filled.Description,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.30f),
                        testTag = "bottom_uwagi_frame"
                    )
                }
            }

            if (prefs.recentNotifications.isNotEmpty()) {
                item(key = "history_section") {
                    NotificationHistoryCard(
                        logs = prefs.recentNotifications,
                        expanded = showHistory,
                        onToggle = { showHistory = !showHistory }
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SubstitutionsHeader(
    isTeacherMode: Boolean,
    teacherAbbreviation: String,
    teacherFullName: String,
    className: String,
    groupLabel: String,
    dateHeading: String,
    lastUpdatedMillis: Long,
    isRefreshing: Boolean,
    onPickClass: () -> Unit,
    onPickTeacher: () -> Unit,
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
                    text = dateHeading,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "sekretariat.zsme.pl • ${formatTimestampPl(lastUpdatedMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenWeb,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_sekretariat_web_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInBrowser,
                        contentDescription = "Otwórz sekretariat.zsme.pl w przeglądarce"
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_substitutions_button")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Odśwież zastępstwa"
                        )
                    }
                }
            }
        }

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
    }
}

@Composable
private fun SubstitutionCard(
    item: SubstitutionItem,
    highlightForMyGroup: Boolean,
    teacherMatch: ScheduleFilterMatcher.TeacherSubstitutionMatch = ScheduleFilterMatcher.TeacherSubstitutionMatch.NONE
) {
    val accentColor = when {
        item.isCancellation -> MaterialTheme.colorScheme.error
        teacherMatch == ScheduleFilterMatcher.TeacherSubstitutionMatch.CONDUCTING -> MaterialTheme.colorScheme.primary
        highlightForMyGroup -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val badgeContainer = when {
        item.isCancellation -> MaterialTheme.colorScheme.errorContainer
        teacherMatch == ScheduleFilterMatcher.TeacherSubstitutionMatch.CONDUCTING -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val badgeTextColor = when {
        item.isCancellation -> MaterialTheme.colorScheme.onErrorContainer
        teacherMatch == ScheduleFilterMatcher.TeacherSubstitutionMatch.CONDUCTING -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("substitution_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(110.dp)
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.className.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = item.className,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (item.period.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Lekcja ${item.period}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (item.groupTag != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = item.groupTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeContainer
                    ) {
                        val badgeLabel = when {
                            item.isCancellation -> "ODWOŁANE"
                            teacherMatch == ScheduleFilterMatcher.TeacherSubstitutionMatch.CONDUCTING -> "PROWADZISZ"
                            teacherMatch == ScheduleFilterMatcher.TeacherSubstitutionMatch.REPLACED -> "ZA CIEBIE"
                            else -> "ZASTĘPSTWO"
                        }
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (item.subject.isNotBlank()) {
                    Text(
                        text = item.subject,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (item.absentTeacher.isNotBlank() || item.substituteTeacher.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        val teacherDesc = buildString {
                            if (item.absentTeacher.isNotBlank()) append(item.absentTeacher)
                            if (item.absentTeacher.isNotBlank() && item.substituteTeacher.isNotBlank()) append(" -> ")
                            if (item.substituteTeacher.isNotBlank()) append(item.substituteTeacher)
                        }
                        Text(
                            text = teacherDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.room.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Room,
                                contentDescription = "Sala lekcyjna",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Sala ${item.room}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    if (item.note.isNotBlank()) {
                        Text(
                            text = item.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FramedSectionBox(
    title: String,
    lines: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    containerColor: androidx.compose.ui.graphics.Color,
    testTag: String
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.55f)),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                lines.forEach { line ->
                    Text(
                        text = if (lines.size > 1) "• $line" else line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySubstitutionsCard(
    title: String,
    subtitle: String,
    showAllCount: Int = 0,
    onShowAllClick: () -> Unit = {},
    onOpenWeb: () -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_no_substitutions),
                contentDescription = "Brak zastępstw",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(136.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showAllCount > 0) {
                Button(
                    onClick = onShowAllClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("show_all_substitutions_button")
                ) {
                    Text("Zobacz wszystkie zastępstwa ($showAllCount)")
                }
            }
            OutlinedButton(
                onClick = onOpenWeb,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Podgląd strony WWW")
            }
        }
    }
}

@Composable
private fun SubstitutionsErrorCard(
    message: String,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    text = "Problem z pobraniem zastępstw",
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
                    Text("Spróbuj ponownie")
                }
                OutlinedButton(
                    onClick = onOpenWeb,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Otwórz WWW (${UserPreferences.DEFAULT_SECRETARIAT_KEY})")
                }
            }
        }
    }
}

@Composable
private fun NotificationHistoryCard(
    logs: List<NotificationLogEntry>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Historia powiadomień (${logs.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Zwiń historię" else "Rozwiń historię"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    logs.take(10).forEach { log ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (log.category == "SUBSTITUTION") MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.title,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = log.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatTimestampPl(log.timestampMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
