package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CalendarDayInfo
import com.example.data.model.DayOffCategory
import com.example.data.model.PolishSchoolCalendar
import com.example.data.model.PrincipalDayOff
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchoolCalendarScreen(
    principalDaysOff: List<PrincipalDayOff>,
    onAddPrincipalDaysOff: (startIso: String, dayCount: Int, title: String) -> Unit,
    onRemovePrincipalDayOff: (dateIso: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todayCal = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val todayIso = remember {
        PolishSchoolCalendar.toIsoDate(
            todayCal.get(Calendar.YEAR),
            todayCal.get(Calendar.MONTH),
            todayCal.get(Calendar.DAY_OF_MONTH)
        )
    }

    var displayedYear by remember { mutableIntStateOf(todayCal.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(todayCal.get(Calendar.MONTH)) }
    var selectedDateIso by remember { mutableStateOf(todayIso) }
    var isAddDialogOpen by remember { mutableStateOf(false) }

    val countdowns = remember(principalDaysOff, todayIso) {
        PolishSchoolCalendar.evaluateHomeCountdowns(
            customDaysOff = principalDaysOff,
            nowMillis = System.currentTimeMillis()
        )
    }

    val monthDayInfos = remember(displayedYear, displayedMonth, principalDaysOff) {
        val monthCal = PolishSchoolCalendar.startOfDayCalendar(displayedYear, displayedMonth, 1)
        val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        (1..daysInMonth).map { day ->
            PolishSchoolCalendar.getDayInfo(
                year = displayedYear,
                monthZeroBased = displayedMonth,
                dayOfMonth = day,
                customDaysOff = principalDaysOff
            )
        }
    }

    val firstDayOffset = remember(displayedYear, displayedMonth) {
        val first = PolishSchoolCalendar.startOfDayCalendar(displayedYear, displayedMonth, 1)
        when (first.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
    }

    val selectedDayInfo = remember(selectedDateIso, principalDaysOff) {
        val parsed = PolishSchoolCalendar.parseIsoDate(selectedDateIso)
            ?: Triple(
                todayCal.get(Calendar.YEAR),
                todayCal.get(Calendar.MONTH),
                todayCal.get(Calendar.DAY_OF_MONTH)
            )
        PolishSchoolCalendar.getDayInfo(
            year = parsed.first,
            monthZeroBased = parsed.second,
            dayOfMonth = parsed.third,
            customDaysOff = principalDaysOff
        )
    }

    val specialDaysInMonth = remember(monthDayInfos) {
        monthDayInfos.filter {
            it.category == DayOffCategory.PUBLIC_HOLIDAY ||
                it.category == DayOffCategory.SCHOOL_BREAK ||
                it.category == DayOffCategory.PRINCIPAL_DAY_OFF ||
                it.category == DayOffCategory.SUMMER_BREAK
        }
    }

    fun shiftMonth(delta: Int) {
        val c = PolishSchoolCalendar.startOfDayCalendar(displayedYear, displayedMonth, 1)
        c.add(Calendar.MONTH, delta)
        displayedYear = c.get(Calendar.YEAR)
        displayedMonth = c.get(Calendar.MONTH)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Kalendarz dni wolnych",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("calendar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wróć"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddDialogOpen = true },
                modifier = Modifier.testTag("add_principal_day_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Dodaj dzień dyrektorski"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick countdown summary
                item(key = "calendar_countdown_summary") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Najbliższy dzień wolny",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = if (countdowns.isTodayFree) "Dziś wolne!" else PolishSchoolCalendar.formatDaysPl(countdowns.nextFreeDayDays),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${countdowns.nextFreeDayTitle} • ${countdowns.nextFreeDayDateLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = countdowns.vacationTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = PolishSchoolCalendar.formatDaysPl(countdowns.daysToVacation),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = countdowns.vacationTargetDateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Month navigation + Grid
                item(key = "calendar_month_grid") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { shiftMonth(-1) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("calendar_prev_month")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Poprzedni miesiąc"
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${PolishSchoolCalendar.polishMonthNames[displayedMonth]} $displayedYear",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (displayedYear != todayCal.get(Calendar.YEAR) || displayedMonth != todayCal.get(Calendar.MONTH)) {
                                        TextButton(
                                            onClick = {
                                                displayedYear = todayCal.get(Calendar.YEAR)
                                                displayedMonth = todayCal.get(Calendar.MONTH)
                                                selectedDateIso = todayIso
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text("Wróć do dziś", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { shiftMonth(1) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("calendar_next_month")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Następny miesiąc"
                                    )
                                }
                            }

                            // Weekday header Mon..Sun
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val headers = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")
                                headers.forEachIndexed { idx, label ->
                                    Text(
                                        text = label,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (idx >= 5) MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Weeks matrix
                            val totalCells = firstDayOffset + monthDayInfos.size
                            val weeksCount = (totalCells + 6) / 7
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (week in 0 until weeksCount) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        for (col in 0..6) {
                                            val cellIndex = week * 7 + col
                                            val dayIndex = cellIndex - firstDayOffset
                                            if (dayIndex in monthDayInfos.indices) {
                                                val info = monthDayInfos[dayIndex]
                                                CalendarDayCell(
                                                    info = info,
                                                    isSelected = info.dateIso == selectedDateIso,
                                                    isToday = info.dateIso == todayIso,
                                                    onClick = { selectedDateIso = info.dateIso },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f).height(46.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // Legend
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LegendChip(
                                    color = MaterialTheme.colorScheme.error,
                                    label = "Święto w Polsce"
                                )
                                LegendChip(
                                    color = MaterialTheme.colorScheme.primary,
                                    label = "Dzień dyrektorski"
                                )
                                LegendChip(
                                    color = MaterialTheme.colorScheme.tertiary,
                                    label = "Przerwa / Wakacje"
                                )
                            }
                        }
                    }
                }

                // Selected day details + 1-tap toggle principal day off
                item(key = "calendar_selected_day") {
                    SelectedDayDetailCard(
                        info = selectedDayInfo,
                        onOpenAddPrincipalDialog = { isAddDialogOpen = true },
                        onRemovePrincipalDay = { onRemovePrincipalDayOff(selectedDayInfo.dateIso) }
                    )
                }

                // Holidays / breaks in the viewed month
                if (specialDaysInMonth.isNotEmpty()) {
                    item(key = "month_special_days_header") {
                        Text(
                            text = "Dni wolne i przerwy w miesiącu (${specialDaysInMonth.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(
                        items = specialDaysInMonth,
                        key = { "month_special_${it.dateIso}" }
                    ) { info ->
                        Surface(
                            onClick = { selectedDateIso = info.dateIso },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(categoryAccentColor(info.category))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = info.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = PolishSchoolCalendar.formatPlDate(
                                            info.year,
                                            info.monthZeroBased,
                                            info.dayOfMonth
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (info.customPrincipalDay != null) {
                                    IconButton(
                                        onClick = { onRemovePrincipalDayOff(info.dateIso) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Usuń dzień dyrektorski",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // All saved custom Principal Days Off
                item(key = "saved_principal_days_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dodane dni dyrektorskie (${principalDaysOff.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = { isAddDialogOpen = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("calendar_inline_add_principal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dodaj")
                        }
                    }
                }

                if (principalDaysOff.isEmpty()) {
                    item(key = "empty_principal_days") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Brak zapisanych dni dyrektorskich. Wybierz dowolną datę w kalendarzu powyżej i kliknij „Dodaj dzień dyrektorski”.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(
                        items = principalDaysOff,
                        key = { "principal_saved_${it.dateIso}" }
                    ) { dayOff ->
                        val parsed = PolishSchoolCalendar.parseIsoDate(dayOff.dateIso)
                        val niceDate = parsed?.let {
                            PolishSchoolCalendar.formatPlDate(it.first, it.second, it.third)
                        } ?: dayOff.dateIso
                        Surface(
                            onClick = {
                                if (parsed != null) {
                                    displayedYear = parsed.first
                                    displayedMonth = parsed.second
                                    selectedDateIso = dayOff.dateIso
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("principal_day_item_${dayOff.dateIso}")
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EventAvailable,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dayOff.title.ifBlank { "Dzień dyrektorski" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = niceDate,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onRemovePrincipalDayOff(dayOff.dateIso) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("delete_principal_day_${dayOff.dateIso}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Usuń dzień dyrektorski $niceDate",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isAddDialogOpen) {
        AddPrincipalDayOffDialog(
            initialDateIso = selectedDateIso,
            onDismiss = { isAddDialogOpen = false },
            onSave = { count, title ->
                onAddPrincipalDaysOff(selectedDateIso, count, title)
                isAddDialogOpen = false
            }
        )
    }
}

@Composable
private fun CalendarDayCell(
    info: CalendarDayInfo,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when (info.category) {
        DayOffCategory.PUBLIC_HOLIDAY -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
        DayOffCategory.PRINCIPAL_DAY_OFF -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        DayOffCategory.SCHOOL_BREAK,
        DayOffCategory.SUMMER_BREAK -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        DayOffCategory.WEEKEND,
        DayOffCategory.SCHOOL_DAY -> Color.Transparent
    }
    val textColor = when (info.category) {
        DayOffCategory.PUBLIC_HOLIDAY -> MaterialTheme.colorScheme.onErrorContainer
        DayOffCategory.PRINCIPAL_DAY_OFF -> MaterialTheme.colorScheme.onPrimaryContainer
        DayOffCategory.SCHOOL_BREAK,
        DayOffCategory.SUMMER_BREAK -> MaterialTheme.colorScheme.onTertiaryContainer
        DayOffCategory.WEEKEND -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        DayOffCategory.SCHOOL_DAY -> MaterialTheme.colorScheme.onSurface
    }
    val borderStroke = when {
        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        isToday -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
        else -> null
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = borderStroke,
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("calendar_day_${info.dateIso}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = info.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected || info.isSpecialNonWeekendFree) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            if (info.isSpecialNonWeekendFree) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(categoryAccentColor(info.category))
                )
            }
        }
    }
}

@Composable
private fun categoryAccentColor(category: DayOffCategory): Color {
    return when (category) {
        DayOffCategory.PUBLIC_HOLIDAY -> MaterialTheme.colorScheme.error
        DayOffCategory.PRINCIPAL_DAY_OFF -> MaterialTheme.colorScheme.primary
        DayOffCategory.SCHOOL_BREAK,
        DayOffCategory.SUMMER_BREAK -> MaterialTheme.colorScheme.tertiary
        DayOffCategory.WEEKEND -> MaterialTheme.colorScheme.outline
        DayOffCategory.SCHOOL_DAY -> MaterialTheme.colorScheme.outlineVariant
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectedDayDetailCard(
    info: CalendarDayInfo,
    onOpenAddPrincipalDialog: () -> Unit,
    onRemovePrincipalDay: () -> Unit
) {
    val niceDate = PolishSchoolCalendar.formatPlDate(info.year, info.monthZeroBased, info.dayOfMonth)
    val icon = when (info.category) {
        DayOffCategory.PUBLIC_HOLIDAY -> Icons.Filled.Celebration
        DayOffCategory.SUMMER_BREAK -> Icons.Filled.BeachAccess
        DayOffCategory.PRINCIPAL_DAY_OFF -> Icons.Filled.EventAvailable
        DayOffCategory.SCHOOL_BREAK,
        DayOffCategory.WEEKEND -> Icons.Filled.EventBusy
        DayOffCategory.SCHOOL_DAY -> Icons.Filled.CalendarMonth
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar_selected_day_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = categoryAccentColor(info.category)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = niceDate,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (info.description.isNotBlank()) {
                        Text(
                            text = info.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (info.customPrincipalDay != null) {
                OutlinedButton(
                    onClick = onRemovePrincipalDay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("remove_selected_principal_day_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Usuń dzień dyrektorski", color = MaterialTheme.colorScheme.error)
                }
            } else if (info.category != DayOffCategory.PUBLIC_HOLIDAY) {
                Button(
                    onClick = onOpenAddPrincipalDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("add_selected_principal_day_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dodaj dzień dyrektorski od tego dnia")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPrincipalDayOffDialog(
    initialDateIso: String,
    onDismiss: () -> Unit,
    onSave: (dayCount: Int, title: String) -> Unit
) {
    var titleInput by remember { mutableStateOf("Dzień dyrektorski") }
    var dayCount by remember { mutableIntStateOf(1) }

    val parsed = PolishSchoolCalendar.parseIsoDate(initialDateIso)
    val startLabel = parsed?.let { PolishSchoolCalendar.formatPlDate(it.first, it.second, it.third) }
        ?: initialDateIso

    val previewDates = remember(initialDateIso, dayCount) {
        PolishSchoolCalendar.expandPrincipalDateRange(initialDateIso, dayCount, titleInput)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.EventAvailable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Dodaj dzień dyrektorski")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Data początkowa: $startLabel",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Nazwa / okazja") },
                    placeholder = { Text("np. Dzień dyrektorski, Matury") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("principal_day_title_input")
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Dzień dyrektorski", "Matura pisemna", "Rekolekcje", "Dzień wolny").forEach { preset ->
                        FilterChip(
                            selected = titleInput == preset,
                            onClick = { titleInput = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text(
                    text = "Liczba dni wolnych:",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 2, 3, 5).forEach { count ->
                        FilterChip(
                            selected = dayCount == count,
                            onClick = { dayCount = count },
                            label = { Text(PolishSchoolCalendar.formatDaysPl(count)) },
                            modifier = Modifier.testTag("principal_days_count_$count")
                        )
                    }
                }

                if (previewDates.size > 1) {
                    Text(
                        text = "Zakres: ${previewDates.first().dateIso} – ${previewDates.last().dateIso}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(dayCount, titleInput) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("save_principal_day_confirm")
            ) {
                Text("Zapisz")
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
