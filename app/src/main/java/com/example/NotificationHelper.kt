package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "sdmx_channel"
    
    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SDMX Auto-Renew",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de estado del proceso SDMX"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showSuccess(title: String, message: String) {
        showNotification(1, title, message)
    }

    fun showError(title: String, message: String) {
        showNotification(2, title, message)
    }
    
    fun getProgressNotification(title: String, message: String): android.app.Notification {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun showNotification(id: Int, title: String, message: String) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(id, builder.build())
    }
}
