package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SchoolClass
import com.example.data.model.StudentGroup
import com.example.notifications.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ZsmeHeroOnboardingCard(
    onPickClassClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_zsme_banner),
                    contentDescription = "Ilustracja Zespołu Szkół Mechaniczno-Elektrycznych",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.15f),
                                    Color(0xFF0A1D37).copy(alpha = 0.85f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ZSME • PLAN & ZASTĘPSTWA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Wybierz swoją klasę i grupę",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Wskaż klasę z planu lekcji (plan.zsme.pl), aby aplikacja automatycznie filtrowała Twoje zajęcia i wysyłała powiadomienia o zastępstwach z sekretariat.zsme.pl.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Button(
                    onClick = onPickClassClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("onboarding_pick_class_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Wybierz klasę i grupę")
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionBanner(
    onPermissionResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Włącz powiadomienia o zastępstwach",
    description: String = "Zezwól na powiadomienia systemowe, aby otrzymywać alerty dla swojej grupy."
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission) {
        return
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        onPermissionResult(granted)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
            FilledTonalButton(
                onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("grant_notification_permission_button")
            ) {
                Text("Zezwól")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassPickerDialog(
    availableClasses: List<SchoolClass>,
    selectedClassName: String,
    selectedGroup: StudentGroup,
    isLoading: Boolean,
    errorMessage: String?,
    onRefreshClasses: () -> Unit,
    onSelectClass: (SchoolClass) -> Unit,
    onManualClassSubmit: (String) -> Unit,
    onSelectGroup: (StudentGroup) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var manualInput by remember(selectedClassName) { mutableStateOf(selectedClassName) }

    val filtered = remember(availableClasses, searchQuery) {
        if (searchQuery.isBlank()) availableClasses
        else availableClasses.filter {
            it.name.contains(searchQuery.trim(), ignoreCase = true)
        }
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
                    text = "Wybierz klasę i grupę",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Zamknij okno wyboru klasy"
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Twoja grupa:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudentGroup.entries.forEach { group ->
                        val isSelected = group == selectedGroup
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectGroup(group) }
                                .testTag("dialog_group_${group.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = group.shortLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Szukaj klasy (np. 2TI, 3TM)") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("class_search_input")
                    )
                    IconButton(
                        onClick = onRefreshClasses,
                        enabled = !isLoading,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_class_list_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Odśwież listę klas z plan.zsme.pl"
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
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.url.ifBlank { it.id } }) { schoolClass ->
                            val isSelected = schoolClass.name.equals(selectedClassName, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectClass(schoolClass) }
                                    .testTag("class_option_${schoolClass.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Klasa ${schoolClass.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Wybrana klasa",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isLoading) {
                                "Pobieranie listy klas z plan.zsme.pl..."
                            } else {
                                "Brak klas z sieci — możesz wpisać symbol klasy ręcznie (np. 2TI, 3TE):"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualInput,
                                onValueChange = { manualInput = it.uppercase() },
                                label = { Text("Symbol klasy") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manual_class_input")
                            )
                            OutlinedButton(
                                onClick = { onManualClassSubmit(manualInput) },
                                enabled = manualInput.isNotBlank(),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("manual_class_confirm_button")
                            ) {
                                Text("Zapisz")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Gotowe")
            }
        }
    )
}

@Composable
fun ClassAndGroupPill(
    className: String,
    groupLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("class_badge_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (className.isBlank()) "Wybierz klasę" else "$className • $groupLabel",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TeacherHeroOnboardingCard(
    onSetupTeacherClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Tryb nauczyciela",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Wpisz swój skrót z planu i nazwisko",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
            Text(
                text = "Aplikacja przeszuka wszystkie plany na plan.zsme.pl pod kątem Twojego skrótu i wyświetli tylko Twoje lekcje, a w zastępstwach pokaże wyłącznie zmiany dotyczące Ciebie.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Button(
                onClick = onSetupTeacherClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("onboarding_setup_teacher_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Badge,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wpisz skrót i nazwisko")
            }
        }
    }
}

@Composable
fun TeacherProfilePill(
    abbreviation: String,
    fullName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("teacher_badge_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Badge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
            val label = when {
                abbreviation.isNotBlank() && fullName.isNotBlank() -> "Nauczyciel: $abbreviation • $fullName"
                abbreviation.isNotBlank() -> "Nauczyciel: $abbreviation • Zmień"
                fullName.isNotBlank() -> "Nauczyciel: $fullName • Zmień"
                else -> "Wpisz skrót nauczyciela"
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TeacherProfileDialog(
    currentAbbreviation: String,
    currentFullName: String,
    onSave: (abbreviation: String, fullName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var abbrInput by remember(currentAbbreviation) { mutableStateOf(currentAbbreviation) }
    var nameInput by remember(currentFullName) { mutableStateOf(currentFullName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profil nauczyciela",
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
                OutlinedTextField(
                    value = abbrInput,
                    onValueChange = { abbrInput = it.uppercase() },
                    label = { Text("Skrót z planu (np. JK, AB)") },
                    placeholder = { Text("np. JK") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_teacher_abbr_input")
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Nazwisko i imię (do zastępstw)") },
                    placeholder = { Text("np. Kowalski lub Jan Kowalski") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_teacher_name_input")
                )
                Text(
                    text = "Skrót służy do przeszukania planów na plan.zsme.pl, a skrót i nazwisko filtrują Twoje zastępstwa na sekretariat.zsme.pl.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(abbrInput, nameInput) },
                enabled = abbrInput.isNotBlank() || nameInput.isNotBlank(),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dialog_save_teacher_button")
            ) {
                Text("Zapisz i szukaj")
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

fun formatTimestampPl(millis: Long): String {
    if (millis <= 0L) return "Jeszcze nie pobrano"
    val fmt = SimpleDateFormat("dd.MM, HH:mm", Locale("pl", "PL"))
    return fmt.format(Date(millis))
}
