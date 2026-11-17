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

object WeatherNotificationHelper {
  const val CHANNEL_ID = "weather_packing_alerts"
  const val EXTRA_TRIP_ID = "extra_trip_id"

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Weather Packing Alerts",
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = "Real-time weather changes and gear packing reminders for upcoming trips"
      }
      val manager = context.getSystemService(NotificationManager::class.java)
      manager?.createNotificationChannel(channel)
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun postWeatherAlertNotification(
    context: Context,
    tripId: String,
    destination: String,
    title: String,
    message: String,
  ): Boolean {
    ensureChannel(context)
    if (!hasNotificationPermission(context)) return false

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_TRIP_ID, tripId)
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      tripId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("$destination · $title")
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    NotificationManagerCompat.from(context).notify(tripId.hashCode(), notification)
    return true
  }
}
