package com.morimil.app.runtime

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object RestCycleNotifier {
    private const val CHANNEL_ID = "morimil_rest_cycle"
    private const val CHANNEL_NAME = "Morimil rest cycle"
    private const val NOTIFICATION_ID = 7001

    @SuppressLint("MissingPermission")
    fun notifyRestCycleChecked(context: Context, didConsolidate: Boolean) {
        if (!notificationsAllowed(context)) return
        ensureChannel(context)

        val title = if (didConsolidate) {
            "Descanso de Morimil completado"
        } else {
            "Descanso de Morimil revisado"
        }
        val body = if (didConsolidate) {
            "Se ejecuto mantenimiento local: auditoria, reconciliacion y consolidacion."
        } else {
            "El mantenimiento local reviso la memoria y no necesitaba consolidar."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(buildLaunchIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos locales del ciclo de descanso y mantenimiento de memoria."
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildLaunchIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    private fun notificationsAllowed(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }
}
