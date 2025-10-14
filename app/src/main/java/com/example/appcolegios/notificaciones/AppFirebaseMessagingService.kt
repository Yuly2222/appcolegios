package com.example.appcolegios.notificaciones

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.appcolegios.MainActivity
import com.example.appcolegios.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Suscribir al usuario al tópico personal si está autenticado
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseMessaging.getInstance().subscribeToTopic("user_$uid")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Si la app está en primer plano o background, mostramos notificación local
        val title = message.notification?.title ?: getString(R.string.notifications)
        val body = message.notification?.body ?: message.data["body"] ?: ""

        // Abrir MainActivity con startDestination=notifications para UI Compose persistente
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("startDestination", "notifications")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "appcolegios_default"
        createChannelIfNeeded(channelId)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val hasPermission = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        if (hasPermission) {
            try {
                NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
            } catch (_: SecurityException) {
                // Ignorar si no se otorgó permiso
            }
        }
    }

    private fun createChannelIfNeeded(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = getString(R.string.notifications)
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = channelName
                enableLights(true)
                lightColor = Color.BLUE
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
