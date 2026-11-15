package com.example.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class BleMeshManager(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val router = MeshRouter()
    private val assembler = MeshPacketChunker.FrameAssembler()
    private val gattTxMutex = Mutex()

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter
    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var maintenanceJob: Job? = null
    private var scanFallbackJob: Job? = null

    private val discoveredPeers = ConcurrentHashMap<String, MeshPeer>()
    private val multiHopNicks = ConcurrentHashMap<String, MeshNickAvailability>()
    private val deviceMap = ConcurrentHashMap<String, BluetoothDevice>()
    private val peerRetryTimestamps = ConcurrentHashMap<String, Long>()

    private val _radioState = MutableStateFlow(MeshRadioState.IDLE)
    val radioState: StateFlow<MeshRadioState> = _radioState.asStateFlow()

    private val _peers = MutableStateFlow<List<MeshPeer>>(emptyList())
    val peers: StateFlow<List<MeshPeer>> = _peers.asStateFlow()

    private val _knownNetworkNicks = MutableStateFlow<List<MeshNickAvailability>>(emptyList())
    val knownNetworkNicks: StateFlow<List<MeshNickAvailability>> = _knownNetworkNicks.asStateFlow()

    private val _nickAvailabilityChecks = MutableStateFlow<Map<String, MeshNickAvailability>>(emptyMap())
    val nickAvailabilityChecks: StateFlow<Map<String, MeshNickAvailability>> = _nickAvailabilityChecks.asStateFlow()

    private val _stats = MutableStateFlow(MeshStats())
    val stats: StateFlow<MeshStats> = _stats.asStateFlow()

    private val _statusDetail = MutableStateFlow<String?>(null)
    val statusDetail: StateFlow<String?> = _statusDetail.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 32)
    val incomingMessages: SharedFlow<MeshMessage> = _incomingMessages.asSharedFlow()

    val myNodeId: String get() = router.myNodeId

    @Volatile
    private var currentNodeLabel: String = "ZSME"

    @Volatile
    private var isAdvertisingActive: Boolean = false

    @Volatile
    private var isScanningActive: Boolean = false

    @Volatile
    private var usingUnfilteredScan: Boolean = false

    @Volatile
    private var triedAdvWithoutScanResp: Boolean = false

    private var receiverRegistered = false

    private val systemStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                LocationManager.MODE_CHANGED_ACTION -> {
                    refreshReadiness()
                }
            }
        }
    }

    init {
        registerSystemReceiver()
        refreshReadiness()
    }

    private fun registerSystemReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                systemStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
    }

    fun shutdown() {
        stopMesh()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(systemStateReceiver) }
            receiverRegistered = false
        }
    }

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasBlePermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasBleHardware(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun isLocationEnabledIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return true
        val lm = locationManager ?: return true
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    fun evaluateReadiness(): MeshRadioState {
        if (!hasBleHardware()) return MeshRadioState.NO_HARDWARE
        if (!hasBlePermissions()) return MeshRadioState.MISSING_PERMISSIONS
        if (!isBluetoothEnabled()) return MeshRadioState.BLUETOOTH_DISABLED
        if (!isLocationEnabledIfNeeded()) return MeshRadioState.LOCATION_DISABLED
        return _radioState.value
    }

    fun refreshReadiness() {
        val current = _radioState.value
        val blocker = when {
            !hasBleHardware() -> MeshRadioState.NO_HARDWARE
            !hasBlePermissions() -> MeshRadioState.MISSING_PERMISSIONS
            !isBluetoothEnabled() -> MeshRadioState.BLUETOOTH_DISABLED
            !isLocationEnabledIfNeeded() -> MeshRadioState.LOCATION_DISABLED
            else -> null
        }

        if (blocker != null) {
            if (current == MeshRadioState.ACTIVE || current == MeshRadioState.STARTING) {
                stopMesh()
            }
            _radioState.value = blocker
            _statusDetail.value = when (blocker) {
                MeshRadioState.NO_HARDWARE -> "To urządzenie nie posiada modułu Bluetooth Low Energy."
                MeshRadioState.MISSING_PERMISSIONS -> "Przyznaj uprawnienia Bluetooth, aby dołączyć do sieci kratowej."
                MeshRadioState.BLUETOOTH_DISABLED -> "Włącz Bluetooth w telefonie, aby nadawać i przekazywać wiadomości."
                MeshRadioState.LOCATION_DISABLED -> "Włącz lokalizację (GPS) w systemie — Android poniżej wersji 12 wymaga jej do skanowania BLE."
                else -> null
            }
        } else if (current == MeshRadioState.MISSING_PERMISSIONS ||
            current == MeshRadioState.BLUETOOTH_DISABLED ||
            current == MeshRadioState.LOCATION_DISABLED
        ) {
            _radioState.value = MeshRadioState.IDLE
            _statusDetail.value = null
        }
    }

    @SuppressLint("MissingPermission")
    fun updateNodeLabel(nodeLabel: String) {
        val clean = nodeLabel.trim().ifBlank { router.myNodeId }.take(14)
        if (clean == currentNodeLabel) return
        currentNodeLabel = clean
        if (_radioState.value == MeshRadioState.ACTIVE) {
            val adapter = bluetoothAdapter ?: return
            runCatching { advertiser?.stopAdvertising(advertiseCallback) }
            isAdvertisingActive = false
            triedAdvWithoutScanResp = false
            startAdvertising(adapter, includeScanResponse = true)
        }
    }

    @SuppressLint("MissingPermission")
    fun startMesh(nodeLabel: String) {
        currentNodeLabel = nodeLabel.trim().ifBlank { router.myNodeId }.take(14)

        refreshReadiness()
        val readiness = evaluateReadiness()
        if (readiness != MeshRadioState.IDLE &&
            readiness != MeshRadioState.ERROR &&
            readiness != MeshRadioState.ACTIVE &&
            readiness != MeshRadioState.STARTING
        ) {
            return
        }

        if (_radioState.value == MeshRadioState.ACTIVE || _radioState.value == MeshRadioState.STARTING) {
            return
        }

        val adapter = bluetoothAdapter ?: return
        _radioState.value = MeshRadioState.STARTING
        _statusDetail.value = "Uruchamianie serwera GATT, nadawania i skanowania BLE..."
        triedAdvWithoutScanResp = false
        usingUnfilteredScan = false

        try {
            startGattServer()
            startAdvertising(adapter, includeScanResponse = true)
            startScanning(adapter, unfiltered = false)
            startMaintenanceLoop()
            scheduleScanFilterFallback()
            _radioState.value = MeshRadioState.ACTIVE
            updateActiveDiagnosticDetail()
        } catch (se: SecurityException) {
            _radioState.value = MeshRadioState.MISSING_PERMISSIONS
            _statusDetail.value = "Brak uprawnień Bluetooth: ${se.message ?: "przyznaj uprawnienia w ustawieniach"}"
            stopMeshInternal(keepErrorState = true)
        } catch (t: Throwable) {
            _radioState.value = MeshRadioState.ERROR
            _statusDetail.value = t.message ?: "Nie udało się uruchomić sieci kratowej BLE."
            stopMeshInternal(keepErrorState = true)
        }
    }

    fun stopMesh() {
        stopMeshInternal(keepErrorState = false)
    }

    @SuppressLint("MissingPermission")
    private fun stopMeshInternal(keepErrorState: Boolean) {
        maintenanceJob?.cancel()
        maintenanceJob = null
        scanFallbackJob?.cancel()
        scanFallbackJob = null
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Throwable) {
        }
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Throwable) {
        }
        try {
            gattServer?.close()
        } catch (_: Throwable) {
        }
        scanner = null
        advertiser = null
        gattServer = null
        isAdvertisingActive = false
        isScanningActive = false
        discoveredPeers.clear()
        multiHopNicks.clear()
        deviceMap.clear()
        peerRetryTimestamps.clear()
        _peers.value = emptyList()
        _knownNetworkNicks.value = emptyList()

        if (!keepErrorState) {
            _radioState.value = when {
                !hasBleHardware() -> MeshRadioState.NO_HARDWARE
                !hasBlePermissions() -> MeshRadioState.MISSING_PERMISSIONS
                !isBluetoothEnabled() -> MeshRadioState.BLUETOOTH_DISABLED
                !isLocationEnabledIfNeeded() -> MeshRadioState.LOCATION_DISABLED
                else -> MeshRadioState.IDLE
            }
            if (_radioState.value == MeshRadioState.IDLE) {
                _statusDetail.value = null
            }
        }
    }

    fun broadcastMessage(
        senderName: String,
        senderClass: String,
        channel: String,
        text: String
    ): MeshMessage = sendMessage(
        senderName = senderName,
        senderClass = senderClass,
        channel = channel,
        text = text,
        targetRecipient = ""
    )

    fun sendMessage(
        senderName: String,
        senderClass: String,
        channel: String,
        text: String,
        targetRecipient: String = ""
    ): MeshMessage {
        currentNodeLabel = senderName.trim().ifBlank { router.myNodeId }.take(14)
        val msg = router.createLocalMessage(
            senderName = senderName,
            senderClass = senderClass,
            channel = channel,
            text = text,
            targetRecipient = targetRecipient
        )
        _stats.update { it.copy(sentCount = it.sentCount + 1) }

        if (_radioState.value == MeshRadioState.ACTIVE) {
            scope.launch {
                relayPacketToNeighbors(msg, excludeAddress = null)
            }
        }
        return msg
    }

    fun checkNicknameAvailability(
        targetNick: String,
        senderName: String
    ): MeshNickAvailability {
        val query = targetNick.trim()
        val key = query.lowercase()
        val now = System.currentTimeMillis()
        currentNodeLabel = senderName.trim().ifBlank { router.myNodeId }.take(14)

        val directMatch = discoveredPeers.values.firstOrNull { peer ->
            peer.displayName.equals(query, ignoreCase = true) ||
                peer.nodeId.equals(query, ignoreCase = true) ||
                (query.length >= 2 && peer.displayName.contains(query, ignoreCase = true))
        }
        val cachedMultiHop = multiHopNicks[key]?.takeIf { now - it.checkedAtMillis <= 90_000L }

        val initialResult = when {
            directMatch != null -> MeshNickAvailability(
                nick = directMatch.displayName,
                isAvailable = true,
                viaMeshHops = 0,
                rssi = directMatch.rssi,
                nodeId = directMatch.nodeId,
                checkedAtMillis = now
            )
            cachedMultiHop != null -> cachedMultiHop.copy(checkedAtMillis = now)
            else -> MeshNickAvailability(
                nick = query,
                isAvailable = false,
                viaMeshHops = 0,
                rssi = null,
                nodeId = null,
                checkedAtMillis = now
            )
        }
        _nickAvailabilityChecks.update { it + (key to initialResult) }

        if (_radioState.value == MeshRadioState.ACTIVE && query.isNotBlank()) {
            val probePacket = router.createLocalMessage(
                senderName = currentNodeLabel,
                senderClass = "",
                channel = MeshMessage.DIRECT_CHANNEL,
                text = "PING",
                targetRecipient = query,
                isProbe = true
            )
            scope.launch {
                relayPacketToNeighbors(probePacket, excludeAddress = null)
            }
        }
        return initialResult
    }

    private fun refreshKnownNetworkNicksLocked() {
        val now = System.currentTimeMillis()
        val combined = linkedMapOf<String, MeshNickAvailability>()
        discoveredPeers.values.forEach { peer ->
            val key = peer.displayName.trim().lowercase()
            combined[key] = MeshNickAvailability(
                nick = peer.displayName,
                isAvailable = true,
                viaMeshHops = 0,
                rssi = peer.rssi,
                nodeId = peer.nodeId,
                checkedAtMillis = peer.lastSeenMillis
            )
        }
        multiHopNicks.entries.removeIf { now - it.value.checkedAtMillis > 90_000L }
        multiHopNicks.forEach { (key, info) ->
            combined.putIfAbsent(key, info)
        }
        _knownNetworkNicks.value = combined.values.sortedWith(
            compareBy({ it.viaMeshHops }, { -(it.rssi ?: -999) }, { it.nick.lowercase() })
        )
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        val manager = bluetoothManager ?: error("Brak BluetoothManager")
        val server = manager.openGattServer(context, gattServerCallback)
            ?: error("Nie udało się otworzyć serwera GATT")

        val service = BluetoothGattService(
            MESH_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val packetCharacteristic = BluetoothGattCharacteristic(
            MESH_PACKET_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(packetCharacteristic)
        server.addService(service)
        gattServer = server
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(adapter: BluetoothAdapter, includeScanResponse: Boolean) {
        val adv = adapter.bluetoothLeAdvertiser
        if (adv == null) {
            isAdvertisingActive = false
            updateActiveDiagnosticDetail("Brak sprzętowego nadawania BLE Peripheral — działa tylko skanowanie sąsiadów.")
            return
        }
        advertiser = adv
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // Strictly <= 21 bytes (Flags 3B + 128-bit UUID 18B) so legacy 31-byte BLE advertising NEVER overflows!
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            .build()

        if (includeScanResponse) {
            val payload = MeshAdvertCodec.encodeScanResponsePayload(
                nodeId = router.myNodeId,
                label = currentNodeLabel
            )
            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(MeshAdvertCodec.MANUFACTURER_ID, payload)
                .build()
            adv.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
        } else {
            adv.startAdvertising(settings, advertiseData, advertiseCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning(adapter: BluetoothAdapter, unfiltered: Boolean) {
        val leScanner = adapter.bluetoothLeScanner ?: error("Brak BluetoothLeScanner")
        scanner = leScanner
        runCatching { leScanner.stopScan(scanCallback) }
        usingUnfilteredScan = unfiltered

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settingsBuilder.setLegacy(false)
        }

        val filters = if (unfiltered) {
            emptyList()
        } else {
            listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
                    .build(),
                ScanFilter.Builder()
                    .setManufacturerData(MeshAdvertCodec.MANUFACTURER_ID, byteArrayOf(), byteArrayOf())
                    .build()
            )
        }

        leScanner.startScan(filters, settingsBuilder.build(), scanCallback)
        isScanningActive = true
    }

    /**
     * Some Android chipsets (notably certain MediaTek/Qualcomm OEMs) fail or silently drop 128-bit
     * hardware ScanFilters. If no peer is spotted within 6 seconds, switch to an unfiltered
     * foreground scan and match MESH_SERVICE_UUID / MANUFACTURER_ID in Kotlin.
     */
    @SuppressLint("MissingPermission")
    private fun scheduleScanFilterFallback() {
        scanFallbackJob?.cancel()
        scanFallbackJob = scope.launch {
            delay(6_000L)
            if (_radioState.value == MeshRadioState.ACTIVE &&
                discoveredPeers.isEmpty() &&
                !usingUnfilteredScan
            ) {
                val adapter = bluetoothAdapter ?: return@launch
                runCatching {
                    startScanning(adapter, unfiltered = true)
                    updateActiveDiagnosticDetail()
                }
            }
        }
    }

    private fun startMaintenanceLoop() {
        maintenanceJob?.cancel()
        maintenanceJob = scope.launch {
            while (true) {
                delay(8_000L)
                val now = System.currentTimeMillis()
                val cutoff = now - 45_000L
                val removed = discoveredPeers.entries.removeIf { entry ->
                    val stale = entry.value.lastSeenMillis < cutoff
                    if (stale) {
                        deviceMap.remove(entry.key)
                    }
                    stale
                }
                if (removed) {
                    _peers.value = discoveredPeers.values.sortedByDescending { it.rssi }
                }
                refreshKnownNetworkNicksLocked()

                // Periodically retry undelivered store-and-carry messages for currently reachable peers
                if (_radioState.value == MeshRadioState.ACTIVE) {
                    for ((addr, peer) in discoveredPeers) {
                        val device = deviceMap[addr] ?: continue
                        val peerKey = peerDeliveryKey(addr, peer.nodeId)
                        val pending = router.pendingPacketsForPeer(peerKey)
                        if (pending.isNotEmpty()) {
                            syncPendingToPeer(device, addr, peerKey, pending)
                        }
                    }
                }
            }
        }
    }

    private fun updateActiveDiagnosticDetail(overrideNotice: String? = null) {
        if (overrideNotice != null) {
            _statusDetail.value = overrideNotice
            return
        }
        val advStatus = if (isAdvertisingActive) "Nadawanie BLE: aktywne" else "Nadawanie BLE: uruchamianie"
        val scanStatus = if (isScanningActive) {
            if (usingUnfilteredScan) "Skanowanie: szerokie" else "Skanowanie: aktywne"
        } else {
            "Skanowanie: nieaktywne"
        }
        _statusDetail.value = "$advStatus • $scanStatus"
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertisingActive = true
            updateActiveDiagnosticDetail()
        }

        override fun onStartFailure(errorCode: Int) {
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE && !triedAdvWithoutScanResp) {
                triedAdvWithoutScanResp = true
                val adapter = bluetoothAdapter
                if (adapter != null) {
                    scope.launch {
                        delay(150L)
                        runCatching { startAdvertising(adapter, includeScanResponse = false) }
                    }
                    return
                }
            }
            isAdvertisingActive = false
            val reason = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "zbyt duży pakiet"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "limit nadajników BLE w systemie"
                ADVERTISE_FAILED_ALREADY_STARTED -> "już uruchomione"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "błąd wewnętrzny Bluetooth"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "model nie wspiera BLE Advertising"
                else -> "kod $errorCode"
            }
            updateActiveDiagnosticDetail("Nadawanie BLE ograniczone ($reason) — skanowanie sąsiadów działa.")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (res in results) {
                handleScanResult(res)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            if (!usingUnfilteredScan) {
                val adapter = bluetoothAdapter
                if (adapter != null && hasBlePermissions() && isBluetoothEnabled()) {
                    scope.launch {
                        delay(200L)
                        runCatching { startScanning(adapter, unfiltered = true) }
                    }
                    return
                }
            }
            isScanningActive = false
            updateActiveDiagnosticDetail("Błąd skanowania BLE (kod $errorCode). Spróbuj wyłączyć i włączyć Bluetooth.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord
        val hasServiceUuid = record?.serviceUuids?.any { it.uuid == MESH_SERVICE_UUID } == true
        val mfgBytes = record?.getManufacturerSpecificData(MeshAdvertCodec.MANUFACTURER_ID)
        val serviceDataBytes = record?.getServiceData(ParcelUuid(MESH_SERVICE_UUID))

        if (!hasServiceUuid && mfgBytes == null && serviceDataBytes == null) {
            return
        }

        val device = result.device ?: return
        val address = device.address ?: return

        val mfgMeta = MeshAdvertCodec.decodeScanResponsePayload(mfgBytes)
        val legacyNodeId = serviceDataBytes?.let { String(it, Charsets.UTF_8) }?.takeIf { it.isNotBlank() }

        val resolvedNodeId = mfgMeta?.nodeId
            ?: legacyNodeId
            ?: discoveredPeers[address]?.nodeId
            ?: address.takeLast(5)

        val devName = mfgMeta?.displayLabel?.takeIf { it.isNotBlank() }
            ?: runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: discoveredPeers[address]?.displayName
            ?: "Węzeł $resolvedNodeId"

        // Deduplicate rotated BLE MAC addresses belonging to the same remote nodeId
        if (mfgMeta?.nodeId != null || legacyNodeId != null) {
            val staleAddrs = discoveredPeers.entries
                .filter { it.key != address && it.value.nodeId.equals(resolvedNodeId, ignoreCase = true) }
                .map { it.key }
            for (stale in staleAddrs) {
                discoveredPeers.remove(stale)
                deviceMap.remove(stale)
            }
        }

        val isNewPeer = !discoveredPeers.containsKey(address)
        deviceMap[address] = device
        discoveredPeers[address] = MeshPeer(
            deviceAddress = address,
            nodeId = resolvedNodeId,
            displayName = devName,
            rssi = result.rssi,
            lastSeenMillis = System.currentTimeMillis()
        )
        _peers.value = discoveredPeers.values.sortedByDescending { it.rssi }
        refreshKnownNetworkNicksLocked()

        val peerKey = peerDeliveryKey(address, resolvedNodeId)
        val pending = router.pendingPacketsForPeer(peerKey)
        if (pending.isNotEmpty() && (isNewPeer || shouldRetryPeer(peerKey))) {
            scope.launch {
                syncPendingToPeer(device, address, peerKey, pending)
            }
        }
    }

    private fun peerDeliveryKey(address: String, nodeId: String): String {
        return if (nodeId.isNotBlank() && nodeId != address.takeLast(5)) "NODE:$nodeId" else "MAC:$address"
    }

    private fun shouldRetryPeer(peerKey: String): Boolean {
        val now = System.currentTimeMillis()
        val prev = peerRetryTimestamps[peerKey] ?: 0L
        return if (now - prev >= 10_000L) {
            peerRetryTimestamps[peerKey] = now
            true
        } else {
            false
        }
    }

    private suspend fun syncPendingToPeer(
        device: BluetoothDevice,
        address: String,
        peerKey: String,
        pending: List<MeshMessage>
    ) {
        peerRetryTimestamps[peerKey] = System.currentTimeMillis()
        for (pkt in pending) {
            val sent = sendPacketToPeer(device, address, pkt)
            if (sent) {
                router.markDeliveredToPeer(peerKey, pkt.id)
            } else {
                break
            }
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                try {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                } catch (_: Throwable) {
                }
            }
            if (characteristic.uuid != MESH_PACKET_CHAR_UUID || value == null) return

            val sourceAddr = device.address ?: "unknown"
            val assembled = assembler.acceptFrame(sourceAddr, value) ?: return
            val knownPeer = discoveredPeers[sourceAddr]
            val sourceKey = if (knownPeer != null) {
                peerDeliveryKey(sourceAddr, knownPeer.nodeId)
            } else {
                "MAC:$sourceAddr"
            }

            val outcome = router.onIncomingPacket(
                packet = assembled,
                fromPeerAddress = sourceKey,
                myRelayTag = currentNodeLabel
            )

            outcome.acceptedMessage?.let { msg ->
                _stats.update { it.copy(receivedCount = it.receivedCount + 1) }
                multiHopNicks[msg.senderName.trim().lowercase()] = MeshNickAvailability(
                    nick = msg.senderName,
                    isAvailable = true,
                    viaMeshHops = msg.hopCount,
                    rssi = discoveredPeers[sourceAddr]?.rssi,
                    nodeId = msg.originNodeId,
                    checkedAtMillis = System.currentTimeMillis()
                )
                refreshKnownNetworkNicksLocked()
                _incomingMessages.tryEmit(msg)
            }

            outcome.probeReplyPacket?.let { reply ->
                scope.launch {
                    relayPacketToNeighbors(reply, excludeAddress = null)
                }
            }

            outcome.probeAckFrom?.let { ack ->
                val availability = MeshNickAvailability(
                    nick = ack.senderName,
                    isAvailable = true,
                    viaMeshHops = ack.hopCount + 1,
                    rssi = discoveredPeers[sourceAddr]?.rssi,
                    nodeId = ack.originNodeId,
                    checkedAtMillis = System.currentTimeMillis()
                )
                val key = ack.senderName.trim().lowercase()
                multiHopNicks[key] = availability
                _nickAvailabilityChecks.update { it + (key to availability) }
                refreshKnownNetworkNicksLocked()
            }

            outcome.forwardPacket?.let { fwd ->
                scope.launch {
                    val relayedToAny = relayPacketToNeighbors(fwd, excludeAddress = sourceAddr)
                    if (relayedToAny) {
                        _stats.update { it.copy(relayedCount = it.relayedCount + 1) }
                    }
                }
            }
        }
    }

    private suspend fun relayPacketToNeighbors(
        packet: MeshMessage,
        excludeAddress: String?
    ): Boolean {
        val targets = discoveredPeers.entries.filter { it.key != excludeAddress }
        var anySent = false
        for ((addr, peer) in targets) {
            val device = deviceMap[addr] ?: continue
            val ok = sendPacketToPeer(device, addr, packet)
            if (ok) {
                router.markDeliveredToPeer(peerDeliveryKey(addr, peer.nodeId), packet.id)
                anySent = true
            }
        }
        return anySent
    }

    private suspend fun sendPacketToPeer(
        device: BluetoothDevice,
        address: String,
        packet: MeshMessage
    ): Boolean = gattTxMutex.withLock {
        if (!hasBlePermissions() || !isBluetoothEnabled()) return false
        val frames = runCatching { MeshPacketChunker.encodeToFrames(packet) }.getOrNull() ?: return false

        // Attempt once, and retry once if Android BLE stack returns transient GATT_ERROR (133)
        if (sendFramesOnce(device, frames)) return true
        delay(250L)
        return sendFramesOnce(device, frames)
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendFramesOnce(
        device: BluetoothDevice,
        frames: List<ByteArray>
    ): Boolean {
        var activeGatt: BluetoothGatt? = null
        return try {
            withTimeoutOrNull(8_000L) {
                suspendCoroutine { cont ->
                    var resumed = false
                    fun finish(result: Boolean, gatt: BluetoothGatt?) {
                        if (!resumed) {
                            resumed = true
                            try {
                                gatt?.disconnect()
                                gatt?.close()
                            } catch (_: Throwable) {
                            }
                            activeGatt = null
                            cont.resume(result)
                        }
                    }

                    val callback = object : BluetoothGattCallback() {
                        private var frameIdx = 0
                        private var targetChar: BluetoothGattCharacteristic? = null

                        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                                gatt.discoverServices()
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                                finish(false, gatt)
                            }
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                finish(false, gatt)
                                return
                            }
                            val char = gatt.getService(MESH_SERVICE_UUID)
                                ?.getCharacteristic(MESH_PACKET_CHAR_UUID)
                            if (char == null) {
                                finish(false, gatt)
                                return
                            }
                            targetChar = char
                            writeNextFrame(gatt, char)
                        }

                        override fun onCharacteristicWrite(
                            gatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            status: Int
                        ) {
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                finish(false, gatt)
                                return
                            }
                            frameIdx++
                            if (frameIdx >= frames.size) {
                                finish(true, gatt)
                            } else {
                                val ch = targetChar ?: characteristic
                                writeNextFrame(gatt, ch)
                            }
                        }

                        @Suppress("DEPRECATION")
                        private fun writeNextFrame(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
                            val frame = frames[frameIdx]
                            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeCharacteristic(
                                    ch,
                                    frame,
                                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                ) == BluetoothGatt.GATT_SUCCESS
                            } else {
                                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                ch.value = frame
                                gatt.writeCharacteristic(ch)
                            }
                            if (!ok) finish(false, gatt)
                        }
                    }

                    try {
                        activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                        } else {
                            device.connectGatt(context, false, callback)
                        }
                        if (activeGatt == null) finish(false, null)
                    } catch (_: Throwable) {
                        finish(false, activeGatt)
                    }
                }
            } ?: false
        } finally {
            // Crucial: guarantee BluetoothGatt is closed even when withTimeoutOrNull cancels!
            try {
                activeGatt?.disconnect()
                activeGatt?.close()
            } catch (_: Throwable) {
            }
            activeGatt = null
        }
    }

    companion object {
        val MESH_SERVICE_UUID: UUID = UUID.fromString("7A534D45-0001-4000-8000-5A534D454D45")
        val MESH_PACKET_CHAR_UUID: UUID = UUID.fromString("7A534D45-0002-4000-8000-5A534D454D45")
    }
}
