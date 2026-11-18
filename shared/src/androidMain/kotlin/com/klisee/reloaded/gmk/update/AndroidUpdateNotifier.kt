package com.klisee.reloaded.gmk.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Aviso del sistema cuando hay una versión nueva, como el de Metrolist: se toca y abre el
 * launcher. Se comprueba el permiso porque desde Android 13 hay que pedirlo en tiempo de
 * ejecución; si no está, no se notifica nada (la pantalla de actualizaciones sigue funcionando).
 */
object AndroidUpdateNotifier {
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 1400

    fun notifyAvailable(context: Context, version: String, title: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title ?: "Reloaded OpenGMK $version disponible")
            .setContentText("Toca para ver las novedades y actualizar")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { contentIntent?.let(::setContentIntent) }
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisa cuando hay una versión nueva de Reloaded OpenGMK"
        }
        runCatching { manager.createNotificationChannel(channel) }
    }
}
