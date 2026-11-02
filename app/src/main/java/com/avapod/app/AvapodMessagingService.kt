package com.avapod.app

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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

object PushNotifications {
    const val CHANNEL = "avapod_announcements"
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.push_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}

class AvapodMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // Token is only exposed locally in debug builds for Console test messages.
        if (BuildConfig.DEBUG) android.util.Log.d("AvapodFCM", "Test token: $token")
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val title = (message.notification?.title ?: message.data["push_title"] ?: getString(R.string.app_name)).take(200)
        val body = (message.notification?.body ?: message.data["push_body"]).orEmpty().take(4000)
        if (body.isBlank()) return
        com.avapod.app.network.PushInbox.save(this, message.messageId, title, body)
        PushNotifications.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val id = (message.messageId ?: "$title:$body").hashCode()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_inbox", "1")
            putExtra("push_id", message.messageId)
            putExtra("push_title", title)
            putExtra("push_body", body)
        }
        val pending = PendingIntent.getActivity(this, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, PushNotifications.CHANNEL)
            .setSmallIcon(R.drawable.ic_push_notification)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .build()
        try { NotificationManagerCompat.from(this).notify("avapod_push", id, notification) }
        catch (_: SecurityException) { /* Permission may be revoked while building the notification. */ }
    }
}
