package com.example

import android.app.Application
import com.example.data.ZsmeRepository
import com.example.data.local.PreferencesRepository
import com.example.data.network.ZsmePortalClient
import com.example.mesh.BleMeshManager
import com.example.notifications.NotificationHelper
import com.example.notifications.ScheduleSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ZsmeApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val meshStorageMutex = Mutex()

    lateinit var repository: ZsmeRepository
        private set

    lateinit var meshManager: BleMeshManager
        private set

    override fun onCreate() {
        super.onCreate()
        val preferencesRepository = PreferencesRepository(this)
        val portalClient = ZsmePortalClient()
        repository = ZsmeRepository(
            context = this,
            preferencesRepository = preferencesRepository,
            portalClient = portalClient
        )
        meshManager = BleMeshManager(this)

        NotificationHelper.createNotificationChannel(this)

        appScope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            if (prefs.notificationsEnabled) {
                runCatching {
                    ScheduleSyncWorker.schedulePeriodicSync(this@ZsmeApplication, prefs.checkIntervalMinutes)
                }
            }
        }

        appScope.launch {
            meshManager.incomingMessages.collect { incoming ->
                var isNew = false
                meshStorageMutex.withLock {
                    val current = preferencesRepository.cachedMeshMessages.first()
                    if (current.none { it.id == incoming.id }) {
                        preferencesRepository.saveMeshMessages(current + incoming)
                        isNew = true
                    }
                }
                if (isNew && !incoming.isMine) {
                    val prefs = preferencesRepository.getPreferencesSnapshot()
                    if (prefs.notificationsEnabled) {
                        NotificationHelper.showMeshMessageNotification(this@ZsmeApplication, incoming)
                    }
                }
            }
        }
    }
}
