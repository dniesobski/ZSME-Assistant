package com.example.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

sealed interface InstallSessionEvent {
    data class PendingUserAction(val confirmIntent: Intent) : InstallSessionEvent
    data object Succeeded : InstallSessionEvent
    data class Failed(val message: String, val shouldFallbackToActionView: Boolean) : InstallSessionEvent
}

object AppUpdateInstallerBridge {
    private val _events = MutableSharedFlow<InstallSessionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<InstallSessionEvent> = _events.asSharedFlow()

    internal fun emit(event: InstallSessionEvent) {
        _events.tryEmit(event)
    }
}

class AppUpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMsg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    AppUpdateInstallerBridge.emit(InstallSessionEvent.PendingUserAction(confirmIntent))
                } else {
                    AppUpdateInstallerBridge.emit(
                        InstallSessionEvent.Failed(
                            message = "System nie przekazał okna potwierdzenia instalacji.",
                            shouldFallbackToActionView = true
                        )
                    )
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                AppUpdateInstallerBridge.emit(InstallSessionEvent.Succeeded)
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                AppUpdateInstallerBridge.emit(
                    InstallSessionEvent.Failed(
                        message = "Instalacja aktualizacji została anulowana.",
                        shouldFallbackToActionView = false
                    )
                )
            }

            else -> {
                val friendly = when (status) {
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "Nowa wersja APK nie jest zgodna z tym urządzeniem."
                    PackageInstaller.STATUS_FAILURE_CONFLICT ->
                        "Konflikt podpisu lub wersji zainstalowanej aplikacji. Jeśli instalujesz wydanie inaczej podpisane, odinstaluj poprzednią wersję lub użyj zgodnego podpisu."
                    PackageInstaller.STATUS_FAILURE_STORAGE ->
                        "Brak wolnego miejsca w pamięci urządzenia."
                    PackageInstaller.STATUS_FAILURE_INVALID ->
                        "Plik APK jest uszkodzony lub nieprawidłowy."
                    else ->
                        if (statusMsg.isNotBlank()) "Błąd instalacji: $statusMsg"
                        else "Nie udało się zainstalować aktualizacji bezpośrednio (kod $status)."
                }
                AppUpdateInstallerBridge.emit(
                    InstallSessionEvent.Failed(
                        message = friendly,
                        shouldFallbackToActionView = status != PackageInstaller.STATUS_FAILURE_CONFLICT &&
                            status != PackageInstaller.STATUS_FAILURE_INVALID
                    )
                )
            }
        }
    }
}

object AppUpdateInstaller {
    private const val ACTION_INSTALL_COMMIT = "com.example.update.ACTION_INSTALL_COMMIT"

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun installApkInApp(context: Context, apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists() && apkFile.length() > 100L) {
            "Plik instalacyjny APK nie istnieje lub jest uszkodzony."
        }

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(apkFile.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        val sessionId = packageInstaller.createSession(params)
        var session: PackageInstaller.Session? = null
        try {
            session = packageInstaller.openSession(sessionId)
            session.openWrite("zsme_update.apk", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { input ->
                    input.copyTo(out, bufferSize = 32 * 1024)
                }
                session.fsync(out)
            }

            val callbackIntent = Intent(context, AppUpdateInstallReceiver::class.java).apply {
                action = ACTION_INSTALL_COMMIT
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val statusReceiver = PendingIntent.getBroadcast(
                context,
                sessionId,
                callbackIntent,
                flags
            )
            session.commit(statusReceiver.intentSender)
        } catch (t: Throwable) {
            runCatching { session?.abandon() }
            runCatching { packageInstaller.abandonSession(sessionId) }
            throw t
        } finally {
            runCatching { session?.close() }
        }
    }

    fun launchLegacyFileProviderInstaller(context: Context, apkFile: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }
}
