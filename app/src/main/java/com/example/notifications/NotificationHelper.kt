package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.SubstitutionItem
import com.example.mesh.MeshMessage

object NotificationHelper {

    const val CHANNEL_ID = "zsme_substitutions_channel"
    const val MESH_CHANNEL_ID = "zsme_mesh_channel"
    const val EXTRA_OPEN_ROUTE = "extra_open_route"
    const val EXTRA_MESH_CHANNEL = "extra_mesh_channel"
    const val ROUTE_MESSENGER = "messenger"
    const val ROUTE_SETTINGS = "settings"

    private const val NOTIFICATION_ID_SUBSTITUTIONS = 1001
    private const val NOTIFICATION_ID_TIMETABLE = 1002
    private const val NOTIFICATION_ID_TEST = 1003
    private const val NOTIFICATION_ID_APP_UPDATE = 1004
    private const val NOTIFICATION_ID_MESH_BASE = 2000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val subChannel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
            }
            manager.createNotificationChannel(subChannel)

            val meshChannel = NotificationChannel(
                MESH_CHANNEL_ID,
                context.getString(R.string.mesh_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.mesh_notification_channel_desc)
                enableVibration(true)
            }
            manager.createNotificationChannel(meshChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showSubstitutionsNotification(
        context: Context,
        className: String,
        groupLabel: String,
        items: List<SubstitutionItem>,
        dateHeading: String
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannel(context)

        val title = "Nowe zastępstwa • $className ($groupLabel)"
        val lines = items.take(6).map { item ->
            val periodPart = if (item.period.isNotBlank()) "Lek. ${item.period}" else "Zmiana"
            val subjPart = item.subject.ifBlank { "Zajęcia" }
            val actionPart = when {
                item.isCancellation -> "ODWOŁANE"
                item.substituteTeacher.isNotBlank() -> "zast. ${item.substituteTeacher}"
                item.note.isNotBlank() -> item.note
                else -> "sprawdź szczegóły"
            }
            val roomPart = if (item.room.isNotBlank()) " (s. ${item.room})" else ""
            "$periodPart: $subjPart — $actionPart$roomPart"
        }
        val summary = buildString {
            if (dateHeading.isNotBlank()) appendLine(dateHeading)
            lines.forEach { appendLine("• $it") }
            if (items.size > 6) append("...i jeszcze ${items.size - 6}")
        }.trim()

        postNotification(
            context = context,
            channelId = CHANNEL_ID,
            notificationId = NOTIFICATION_ID_SUBSTITUTIONS,
            title = title,
            shortText = lines.firstOrNull() ?: "Wykryto aktualizację na sekretariat.zsme.pl",
            bigText = summary
        )
    }

    fun showTimetableChangedNotification(
        context: Context,
        className: String
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannel(context)

        postNotification(
            context = context,
            channelId = CHANNEL_ID,
            notificationId = NOTIFICATION_ID_TIMETABLE,
            title = "Aktualizacja planu lekcji • $className",
            shortText = "Na plan.zsme.pl opublikowano zmiany w planie Twojej klasy.",
            bigText = "Wykryto aktualizację harmonogramu na plan.zsme.pl dla klasy $className. Otwórz aplikację, aby sprawdzić aktualne godziny i sale."
        )
    }

    fun showMeshMessageNotification(
        context: Context,
        message: MeshMessage
    ) {
        if (message.isMine || !hasNotificationPermission(context)) return
        createNotificationChannel(context)

        val senderBadge = buildString {
            append(message.senderName)
            if (message.senderClass.isNotBlank()) append(" (${message.senderClass})")
        }
        val title = if (message.targetRecipient.isNotBlank()) {
            "Wiadomość prywatna • $senderBadge"
        } else {
            "$senderBadge • ${message.channel}"
        }
        val hopNote = if (message.hopCount > 0) " [przekazano: ${message.hopCount} skok(i)]" else ""
        val body = "${message.text}$hopNote"
        val notifId = NOTIFICATION_ID_MESH_BASE + ((message.channel.hashCode() and 0x7FFFFFFF) % 500)

        postNotification(
            context = context,
            channelId = MESH_CHANNEL_ID,
            notificationId = notifId,
            title = title,
            shortText = message.text,
            bigText = body,
            openRoute = ROUTE_MESSENGER,
            meshChannel = message.channel
        )
    }

    fun showTestNotification(
        context: Context,
        className: String,
        groupLabel: String
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        createNotificationChannel(context)

        val targetDesc = if (className.isNotBlank()) "$className ($groupLabel)" else "ZSME"
        postNotification(
            context = context,
            channelId = CHANNEL_ID,
            notificationId = NOTIFICATION_ID_TEST,
            title = "Powiadomienia aktywne • $targetDesc",
            shortText = "Alerty o zastępstwach z sekretariat.zsme.pl działają poprawnie.",
            bigText = "Monitorowanie zmian dla $targetDesc jest włączone. Gdy pojawią się nowe zastępstwa lub wiadomości w komunikatorze, otrzymasz powiadomienie."
        )
        return true
    }

    fun showAppUpdateNotification(
        context: Context,
        candidate: com.example.data.model.ApkCandidate,
        currentVersionName: String
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannel(context)

        val versionLabel = candidate.versionName.takeIf { it.isNotBlank() }?.let { "v$it" } ?: candidate.fileName
        val title = "Dostępna aktualizacja • $versionLabel"
        val body = "Nowa wersja aplikacji ($versionLabel) jest gotowa do pobrania z GitHuba (zainstalowana: $currentVersionName). Dotknij, aby otworzyć aktualizację."
        postNotification(
            context = context,
            channelId = CHANNEL_ID,
            notificationId = NOTIFICATION_ID_APP_UPDATE,
            title = title,
            shortText = "Nowa wersja $versionLabel jest gotowa do instalacji.",
            bigText = body,
            openRoute = ROUTE_SETTINGS
        )
    }

    private fun postNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        shortText: String,
        bigText: String,
        openRoute: String? = null,
        meshChannel: String? = null
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openRoute != null) putExtra(EXTRA_OPEN_ROUTE, openRoute)
            if (meshChannel != null) putExtra(EXTRA_MESH_CHANNEL, meshChannel)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }
}
