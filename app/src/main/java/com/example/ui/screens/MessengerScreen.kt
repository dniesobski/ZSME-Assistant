package com.example.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.UserRole
import com.example.mesh.MeshMessage
import com.example.mesh.MeshNickAvailability
import com.example.mesh.MeshPeer
import com.example.mesh.MeshRadioState
import com.example.ui.ZsmeUiState
import com.example.ui.components.NotificationPermissionBanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessengerScreen(
    uiState: ZsmeUiState,
    requiredBlePermissions: Array<String>,
    onToggleMesh: () -> Unit,
    onPermissionsGranted: () -> Unit,
    onRefreshReadiness: () -> Unit,
    onSelectChannel: (String) -> Unit,
    onCheckNickAvailability: (String) -> Unit,
    onSelectRecipientNick: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onSaveNickname: (String) -> Unit,
    onClearHistory: () -> Unit,
    onNotificationPermissionResult: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    val classChannel = remember(prefs.selectedClassName, prefs.userRole) {
        if (prefs.userRole == UserRole.STUDENT && prefs.selectedClassName.isNotBlank()) {
            "#${prefs.selectedClassName.lowercase()}"
        } else null
    }
    val channels = remember(classChannel) {
        listOfNotNull("#ogólny", MeshMessage.DIRECT_CHANNEL, classChannel, "#zastępstwa", "#pomoc").distinct()
    }

    var messageInput by remember { mutableStateOf("") }
    var nickLookupInput by remember { mutableStateOf("") }
    var showPeers by remember { mutableStateOf(false) }
    var showNickAvailabilityPanel by remember { mutableStateOf(true) }
    var showNickDialog by remember { mutableStateOf(false) }

    val channelMessages = remember(uiState.meshMessages, uiState.activeMeshChannel) {
        if (uiState.activeMeshChannel == MeshMessage.DIRECT_CHANNEL) {
            uiState.meshMessages.filter {
                it.channel == MeshMessage.DIRECT_CHANNEL || it.targetRecipient.isNotBlank()
            }
        } else {
            uiState.meshMessages.filter {
                it.channel == uiState.activeMeshChannel && it.targetRecipient.isBlank()
            }
        }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(channelMessages.size) {
        if (channelMessages.isNotEmpty()) {
            listState.animateScrollToItem(channelMessages.lastIndex)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshReadiness()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        onRefreshReadiness()
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onPermissionsGranted()
        } else {
            onRefreshReadiness()
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshReadiness()
    }

    val enableLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshReadiness()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Komunikator szkolny BLE",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val displayNick = prefs.meshNickname.ifBlank {
                        if (prefs.selectedClassName.isNotBlank()) "Uczeń ${prefs.selectedClassName}" else "Uczeń ZSME"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showNickDialog = true }
                            .padding(vertical = 2.dp)
                            .testTag("edit_mesh_nickname_button")
                    ) {
                        Text(
                            text = "Nick: $displayNick • Węzeł ${uiState.myMeshNodeId}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Zmień nick",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (uiState.meshMessages.isNotEmpty()) {
                    IconButton(
                        onClick = onClearHistory,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_mesh_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Wyczyść historię czatu"
                        )
                    }
                }
            }

            // Mesh Radio & Peer Relay Status Card
            MeshStatusCard(
                uiState = uiState,
                showPeers = showPeers,
                onToggleExpandPeers = { showPeers = !showPeers },
                onToggleMesh = {
                    when (uiState.meshRadioState) {
                        MeshRadioState.MISSING_PERMISSIONS -> blePermissionLauncher.launch(requiredBlePermissions)
                        MeshRadioState.BLUETOOTH_DISABLED -> enableBluetoothLauncher.launch(
                            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        )
                        MeshRadioState.LOCATION_DISABLED -> enableLocationLauncher.launch(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        )
                        else -> onToggleMesh()
                    }
                },
                onRequestPermissions = { blePermissionLauncher.launch(requiredBlePermissions) },
                onEnableBluetooth = {
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                },
                onEnableLocation = {
                    enableLocationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            )

            NotificationPermissionBanner(
                onPermissionResult = onNotificationPermissionResult,
                title = "Powiadomienia o nowych wiadomościach",
                description = "Zezwól na powiadomienia systemowe, aby otrzymywać alerty po odebraniu nowej wiadomości w sieci BLE."
            )

            // Nickname Availability & Direct Recipient Card
            NickAvailabilityCard(
                nickInput = nickLookupInput,
                onNickInputChange = { nickLookupInput = it },
                expanded = showNickAvailabilityPanel,
                onToggleExpanded = { showNickAvailabilityPanel = !showNickAvailabilityPanel },
                knownNicks = uiState.meshKnownNicks,
                nickChecks = uiState.meshNickChecks,
                selectedRecipientNick = uiState.selectedMeshRecipientNick,
                onCheckNick = { onCheckNickAvailability(nickLookupInput) },
                onSelectRecipient = { nick ->
                    onSelectRecipientNick(nick)
                }
            )

            // Channel Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                channels.forEach { channel ->
                    val count = if (channel == MeshMessage.DIRECT_CHANNEL) {
                        uiState.meshMessages.count { it.channel == MeshMessage.DIRECT_CHANNEL || it.targetRecipient.isNotBlank() }
                    } else {
                        uiState.meshMessages.count { it.channel == channel && it.targetRecipient.isBlank() }
                    }
                    FilterChip(
                        selected = uiState.activeMeshChannel == channel,
                        onClick = {
                            onSelectChannel(channel)
                            if (channel != MeshMessage.DIRECT_CHANNEL && uiState.selectedMeshRecipientNick.isNotBlank()) {
                                onSelectRecipientNick("")
                            }
                        },
                        label = {
                            val title = if (channel == MeshMessage.DIRECT_CHANNEL) "Prywatne" else channel
                            Text(if (count > 0) "$title ($count)" else title)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("mesh_channel_${channel.removePrefix("#").removePrefix("@")}")
                    )
                }
            }

            // Messages Feed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (channelMessages.isEmpty()) {
                    MeshEmptyStateCard(channel = uiState.activeMeshChannel)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(channelMessages, key = { it.id }) { msg ->
                            MeshMessageBubble(message = msg)
                        }
                    }
                }
            }

            // Active direct recipient indicator above composer
            if (uiState.selectedMeshRecipientNick.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Wiadomość prywatna do: ${uiState.selectedMeshRecipientNick}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                onSelectRecipientNick("")
                                if (uiState.activeMeshChannel == MeshMessage.DIRECT_CHANNEL) {
                                    onSelectChannel("#ogólny")
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("clear_mesh_recipient_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Anuluj wiadomość prywatną",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Message Composer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = {
                        val hint = if (uiState.selectedMeshRecipientNick.isNotBlank()) {
                            "Wiadomość prywatna do ${uiState.selectedMeshRecipientNick}..."
                        } else {
                            "Wiadomość w sieci kratowej (${uiState.activeMeshChannel})..."
                        }
                        Text(hint)
                    },
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mesh_message_input")
                )
                FilledIconButton(
                    onClick = {
                        if (messageInput.isNotBlank()) {
                            onSendMessage(messageInput)
                            messageInput = ""
                        }
                    },
                    enabled = messageInput.isNotBlank() &&
                        (uiState.activeMeshChannel != MeshMessage.DIRECT_CHANNEL || uiState.selectedMeshRecipientNick.isNotBlank()),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("mesh_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Wyślij wiadomość przez Bluetooth Mesh"
                    )
                }
            }
        }
    }

    if (showNickDialog) {
        var nickInput by remember(prefs.meshNickname) { mutableStateOf(prefs.meshNickname) }
        AlertDialog(
            onDismissRequest = { showNickDialog = false },
            title = { Text("Nazwa w sieci Bluetooth") },
            text = {
                OutlinedTextField(
                    value = nickInput,
                    onValueChange = { nickInput = it },
                    label = { Text("Twój nick (widoczny dla innych uczniów)") },
                    placeholder = { Text("np. Tomek 2TI") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mesh_nickname_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveNickname(nickInput)
                        showNickDialog = false
                    }
                ) {
                    Text("Zapisz")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNickDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

@Composable
private fun MeshStatusCard(
    uiState: ZsmeUiState,
    showPeers: Boolean,
    onToggleExpandPeers: () -> Unit,
    onToggleMesh: () -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onEnableLocation: () -> Unit
) {
    val state = uiState.meshRadioState
    val isActive = state == MeshRadioState.ACTIVE || state == MeshRadioState.STARTING

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (state) {
                                    MeshRadioState.ACTIVE, MeshRadioState.STARTING -> Icons.Filled.BluetoothConnected
                                    MeshRadioState.BLUETOOTH_DISABLED, MeshRadioState.NO_HARDWARE -> Icons.Filled.BluetoothDisabled
                                    else -> Icons.Filled.Bluetooth
                                },
                                contentDescription = null,
                                tint = if (isActive) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = state.label,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "W zasięgu: ${uiState.meshPeers.size} tel. • Przekazano dalej: ${uiState.meshStats.relayedCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (state != MeshRadioState.NO_HARDWARE) {
                    Switch(
                        checked = isActive,
                        onCheckedChange = { onToggleMesh() },
                        modifier = Modifier.testTag("mesh_radio_switch")
                    )
                }
            }

            if (uiState.meshStatusDetail != null) {
                Text(
                    text = uiState.meshStatusDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (state) {
                MeshRadioState.MISSING_PERMISSIONS -> {
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("grant_ble_permissions_button")
                    ) {
                        Text("Przyznaj uprawnienia Bluetooth (Urządzenia w pobliżu)")
                    }
                }
                MeshRadioState.BLUETOOTH_DISABLED -> {
                    OutlinedButton(
                        onClick = onEnableBluetooth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("enable_bluetooth_button")
                    ) {
                        Text("Włącz Bluetooth w telefonie")
                    }
                }
                MeshRadioState.LOCATION_DISABLED -> {
                    OutlinedButton(
                        onClick = onEnableLocation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("enable_location_button")
                    ) {
                        Text("Włącz lokalizację (wymagana dla Bluetooth BLE)")
                    }
                }
                else -> Unit
            }

            if (uiState.meshPeers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleExpandPeers)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aktywne telefony pośredniczące (${uiState.meshPeers.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (showPeers) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showPeers) "Ukryj węzły" else "Pokaż węzły"
                    )
                }

                AnimatedVisibility(visible = showPeers) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        uiState.meshPeers.forEach { peer ->
                            PeerRow(peer = peer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerRow(peer: MeshPeer) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${peer.displayName} (${peer.nodeId})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SignalCellularAlt,
                    contentDescription = "Siła sygnału",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${peer.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NickAvailabilityCard(
    nickInput: String,
    onNickInputChange: (String) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    knownNicks: List<MeshNickAvailability>,
    nickChecks: Map<String, MeshNickAvailability>,
    selectedRecipientNick: String,
    onCheckNick: () -> Unit,
    onSelectRecipient: (String) -> Unit
) {
    val cleanQuery = nickInput.trim()
    val checkResult = remember(cleanQuery, nickChecks) {
        if (cleanQuery.isBlank()) null else nickChecks[cleanQuery.lowercase()]
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleExpanded)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonSearch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Dostępność nicków w sieci (${knownNicks.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Zwiń listę nicków" else "Rozwiń listę nicków"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = nickInput,
                            onValueChange = onNickInputChange,
                            placeholder = { Text("Wpisz nick lub ID węzła...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mesh_nick_lookup_input")
                        )
                        FilledTonalButton(
                            onClick = onCheckNick,
                            enabled = cleanQuery.isNotBlank(),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("check_nick_availability_button")
                        ) {
                            Text("Sprawdź")
                        }
                    }

                    if (checkResult != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (checkResult.isAvailable) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val statusText = when {
                                        checkResult.isAvailable && checkResult.viaMeshHops == 0 ->
                                            "${checkResult.nick} — dostępny bezpośrednio (${checkResult.rssi ?: 0} dBm)"
                                        checkResult.isAvailable ->
                                            "${checkResult.nick} — dostępny w sieci (${checkResult.viaMeshHops} skoki)"
                                        else ->
                                            "${checkResult.nick} — brak potwierdzenia (wysłano zapytanie BLE)"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                TextButton(
                                    onClick = { onSelectRecipient(checkResult.nick) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("write_to_checked_nick_button")
                                ) {
                                    Text("Napisz")
                                }
                            }
                        }
                    }

                    if (knownNicks.isNotEmpty()) {
                        Text(
                            text = "Wykryte osoby (dotknij, aby napisać prywatnie):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            knownNicks.forEach { user ->
                                val isSelected = selectedRecipientNick.equals(user.nick, ignoreCase = true)
                                val hopDesc = if (user.viaMeshHops == 0 && user.rssi != null) {
                                    "${user.rssi} dBm"
                                } else {
                                    "${user.viaMeshHops} sk."
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectRecipient(if (isSelected) "" else user.nick)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("${user.nick} • $hopDesc") },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("mesh_known_nick_${user.nick}")
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Lista dostępnych nicków zapełni się automatycznie po wykryciu telefonów w sieci Bluetooth lub po odpowiedzi na zapytanie.",
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
private fun MeshMessageBubble(message: MeshMessage) {
    val align = if (message.isMine) Alignment.End else Alignment.Start
    val isPrivate = message.targetRecipient.isNotBlank() || message.channel == MeshMessage.DIRECT_CHANNEL
    val containerColor = when {
        message.isMine && isPrivate -> MaterialTheme.colorScheme.tertiaryContainer
        message.isMine -> MaterialTheme.colorScheme.primaryContainer
        isPrivate -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        message.isMine && isPrivate -> MaterialTheme.colorScheme.onTertiaryContainer
        message.isMine -> MaterialTheme.colorScheme.onPrimaryContainer
        isPrivate -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val timeText = remember(message.timestampMillis) {
        SimpleDateFormat("HH:mm", Locale("pl", "PL")).format(Date(message.timestampMillis))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isMine) 16.dp else 4.dp,
                bottomEnd = if (message.isMine) 4.dp else 16.dp
            ),
            color = containerColor,
            tonalElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .testTag("mesh_message_bubble")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val senderBadge = buildString {
                        append(message.senderName)
                        if (message.senderClass.isNotBlank()) append(" • ${message.senderClass}")
                    }
                    Text(
                        text = senderBadge,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }

                if (isPrivate) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (message.isMine && message.targetRecipient.isNotBlank()) {
                                "Prywatnie do: ${message.targetRecipient}"
                            } else {
                                "Wiadomość prywatna"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )

                val hopLabel = when {
                    message.isMine -> "Wysłano do sieci kratowej (TTL ${message.ttl})"
                    message.hopCount == 0 -> "Bezpośrednio przez Bluetooth"
                    else -> {
                        val trail = if (message.relayTrail.isNotEmpty()) {
                            " (${message.relayTrail.joinToString(" -> ")})"
                        } else ""
                        "Przekazano: ${message.hopCount} skok(i)$trail"
                    }
                }
                Text(
                    text = hopLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun MeshEmptyStateCard(channel: String) {
    val isPrivate = channel == MeshMessage.DIRECT_CHANNEL
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
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPrivate) Icons.Filled.Lock else Icons.Filled.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = if (isPrivate) "Wiadomości prywatne (Bluetooth Mesh)" else "Kanał $channel (Bluetooth Mesh)",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isPrivate) {
                    "Wybierz wykryty nick lub wpisz nick odbiorcy powyżej, sprawdź jego dostępność i wyślij wiadomość widoczną tylko dla tej osoby (przekazywaną przez inne telefony jako węzły pośrednie)."
                } else {
                    "Wiadomości przesyłane są bez internetu przez Bluetooth Low Energy. Każde inne urządzenie z włączonym komunikatorem automatycznie działa jako przekaźnik (do 5 przeskoków po całej szkole)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
